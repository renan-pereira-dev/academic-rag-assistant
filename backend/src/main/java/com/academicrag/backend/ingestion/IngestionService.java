package com.academicrag.backend.ingestion;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

/**
 * Fluxo de ingestão: PDF -> páginas -> trechos (chunks) -> embeddings -> pgvector.
 *
 * <p>Cada PDF é lido página por página; páginas sem texto extraível (ex.: imagens
 * escaneadas) são contadas e ignoradas. Cada página é dividida pelo
 * {@link TokenTextSplitter} e, como ele não oferece overlap, os últimos
 * {@code chunkOverlap} tokens do chunk anterior <b>da mesma página</b> são repetidos no
 * início do chunk seguinte. Assim o metadado {@code page} continua verdadeiro.
 */
@Service
public class IngestionService {

	public static final String METADATA_DOCUMENT = "document";

	public static final String METADATA_PAGE = "page";

	private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

	private final VectorStore vectorStore;

	private final Path docsDir;

	private final int chunkOverlap;

	private final TokenTextSplitter splitter;

	private final int batchSize;

	private final long batchDelayMs;

	// Mesmo tokenizer usado por padrão no TokenTextSplitter, para o overlap ser medido na mesma escala.
	private final Encoding encoding = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

	// docsDir chega como String: injetado direto como Path, o PathEditor do Spring tenta resolvê-lo como
	// recurso do ServletContext do Tomcat e falha com caminhos relativos como "../docs".
	public IngestionService(VectorStore vectorStore, @Value("${app.ingestion.docs-dir:../docs}") String docsDir,
			@Value("${app.ingestion.chunk-size:800}") int chunkSize,
			@Value("${app.ingestion.chunk-overlap:100}") int chunkOverlap,
			@Value("${app.ingestion.batch-size:15}") int batchSize,
			@Value("${app.ingestion.batch-delay-ms:25000}") long batchDelayMs) {
		if (batchSize <= 0) {
			throw new IllegalArgumentException("app.ingestion.batch-size deve ser maior que zero.");
		}
		if (batchDelayMs < 0) {
			throw new IllegalArgumentException("app.ingestion.batch-delay-ms não pode ser negativo.");
		}
		if (chunkSize <= 0) {
			throw new IllegalArgumentException("app.ingestion.chunk-size deve ser maior que zero.");
		}
		if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
			throw new IllegalArgumentException("app.ingestion.chunk-overlap deve estar entre 0 e chunk-size - 1.");
		}
		this.vectorStore = vectorStore;
		this.docsDir = Path.of(docsDir);
		this.chunkOverlap = chunkOverlap;
		this.splitter = TokenTextSplitter.builder().withChunkSize(chunkSize).build();
		this.batchSize = batchSize;
		this.batchDelayMs = batchDelayMs;
	}

	/**
	 * Ingere todos os {@code .pdf} da pasta configurada e grava os chunks no VectorStore.
	 * Disparado pelo {@code IngestionRunner} quando a aplicação sobe com {@code --ingest}.
	 * @return resumo da execução (também registrado em log)
	 */
	public IngestionResult ingest() {
		List<Path> pdfs = listPdfs();
		if (pdfs.isEmpty()) {
			log.warn("Nenhum PDF encontrado em {}.", docsDir.toAbsolutePath());
		}
		int totalPages = 0;
		int totalEmptyPages = 0;
		int totalChunks = 0;
		int batchesSent = 0;
		for (Path pdf : pdfs) {
			String fileName = pdf.getFileName().toString();
			int pageCount = countPages(pdf);
			List<Document> pages = readPagesWithText(pdf);
			int emptyPages = pageCount - pages.size();

			List<Document> chunks = new ArrayList<>();
			for (Document page : pages) {
				chunks.addAll(chunkPage(fileName, page));
			}
			// Grava por arquivo: limita memória e preserva o que já foi ingerido se um PDF posterior falhar.
			batchesSent = addInBatches(fileName, chunks, batchesSent);
			log.info("{}: {} páginas, {} vazias (ignoradas), {} chunks gravados.", fileName, pageCount, emptyPages,
					chunks.size());
			totalPages += pageCount;
			totalEmptyPages += emptyPages;
			totalChunks += chunks.size();
		}
		IngestionResult result = new IngestionResult(pdfs.size(), totalPages, totalEmptyPages, totalChunks);
		log.info("Ingestão: {}", result);
		return result;
	}

	/**
	 * Envia os chunks ao VectorStore em lotes de {@code batchSize}, pausando {@code batchDelayMs} antes
	 * de cada lote exceto o primeiro da execução. Cada {@code add} gera os embeddings no Gemini, e o
	 * plano gratuito limita os tokens por minuto; a pausa vale também entre arquivos.
	 * @return total de lotes enviados na execução até agora
	 */
	private int addInBatches(String fileName, List<Document> chunks, int batchesSent) {
		int totalBatches = Math.ceilDiv(chunks.size(), batchSize);
		for (int batch = 0; batch < totalBatches; batch++) {
			if (batchesSent > 0 && batchDelayMs > 0) {
				pause(batchDelayMs);
			}
			int from = batch * batchSize;
			List<Document> slice = List.copyOf(chunks.subList(from, Math.min(from + batchSize, chunks.size())));
			vectorStore.add(slice);
			batchesSent++;
			log.info("{}: lote {}/{} gravado ({} chunks).", fileName, batch + 1, totalBatches, slice.size());
		}
		return batchesSent;
	}

	/** Pausa entre lotes; package-private para os testes verificarem as pausas sem esperar de verdade. */
	void pause(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Ingestão interrompida durante a pausa entre lotes.", ex);
		}
	}

	private List<Path> listPdfs() {
		if (!Files.isDirectory(docsDir)) {
			throw new IllegalStateException("Pasta de documentos não encontrada: " + docsDir.toAbsolutePath());
		}
		try (Stream<Path> files = Files.list(docsDir)) {
			return files.filter(Files::isRegularFile)
				.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
				.sorted()
				.toList();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Falha ao listar " + docsDir.toAbsolutePath(), ex);
		}
	}

	// O PagePdfDocumentReader descarta páginas sem texto sem avisar; o total vem do PDFBox para podermos contá-las.
	private int countPages(Path pdf) {
		try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
			return document.getNumberOfPages();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Falha ao abrir " + pdf, ex);
		}
	}

	// Remove NUL antes do filtro de vazias: uma página só com \u0000 também conta como vazia.
	private List<Document> readPagesWithText(Path pdf) {
		return readPages(pdf).stream()
			.filter(page -> page.getText() != null)
			.map(page -> page.mutate().text(removeNullChars(page.getText())).build())
			.filter(page -> !page.getText().isBlank())
			.toList();
	}

	/** Leitura bruta, uma página por Document; package-private para os testes simularem texto extraído. */
	List<Document> readPages(Path pdf) {
		PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder().withPagesPerDocument(1).build();
		return new PagePdfDocumentReader(new FileSystemResource(pdf), config).get();
	}

	// Alguns PDFs extraem caracteres NUL (\u0000), que o PostgreSQL rejeita em colunas de texto UTF-8.
	private static String removeNullChars(String text) {
		return text.replace("\u0000", "");
	}

	private List<Document> chunkPage(String fileName, Document page) {
		Map<String, Object> metadata = new HashMap<>(page.getMetadata());
		metadata.put(METADATA_DOCUMENT, fileName);
		metadata.put(METADATA_PAGE, page.getMetadata().get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER));
		Document source = Document.builder().text(page.getText()).metadata(metadata).build();
		// O TextSplitter copia os metadados da página para cada chunk.
		List<Document> chunks = splitter.split(source);
		if (chunkOverlap == 0 || chunks.size() < 2) {
			return chunks;
		}
		List<Document> overlapped = new ArrayList<>(chunks.size());
		overlapped.add(chunks.get(0));
		for (int i = 1; i < chunks.size(); i++) {
			String tail = lastTokens(chunks.get(i - 1).getText()).strip();
			Document chunk = chunks.get(i);
			overlapped.add(tail.isEmpty() ? chunk : chunk.mutate().text(tail + " " + chunk.getText()).build());
		}
		return overlapped;
	}

	private String lastTokens(String text) {
		IntArrayList tokens = encoding.encode(text);
		int start = Math.max(0, tokens.size() - chunkOverlap);
		IntArrayList tail = new IntArrayList(tokens.size() - start);
		for (int i = start; i < tokens.size(); i++) {
			tail.add(tokens.get(i));
		}
		return encoding.decode(tail);
	}

	/**
	 * Resumo de uma execução da ingestão.
	 * @param files PDFs processados
	 * @param pages total de páginas
	 * @param emptyPages páginas sem texto extraível (ignoradas)
	 * @param chunks chunks gravados no VectorStore
	 */
	public record IngestionResult(int files, int pages, int emptyPages, int chunks) {
	}

}
