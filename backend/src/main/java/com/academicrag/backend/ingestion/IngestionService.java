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

	// Mesmo tokenizer usado por padrão no TokenTextSplitter, para o overlap ser medido na mesma escala.
	private final Encoding encoding = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

	public IngestionService(VectorStore vectorStore, @Value("${app.ingestion.docs-dir:../docs}") Path docsDir,
			@Value("${app.ingestion.chunk-size:800}") int chunkSize,
			@Value("${app.ingestion.chunk-overlap:100}") int chunkOverlap) {
		if (chunkSize <= 0) {
			throw new IllegalArgumentException("app.ingestion.chunk-size deve ser maior que zero.");
		}
		if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
			throw new IllegalArgumentException("app.ingestion.chunk-overlap deve estar entre 0 e chunk-size - 1.");
		}
		this.vectorStore = vectorStore;
		this.docsDir = docsDir;
		this.chunkOverlap = chunkOverlap;
		this.splitter = TokenTextSplitter.builder().withChunkSize(chunkSize).build();
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
			if (!chunks.isEmpty()) {
				vectorStore.add(chunks);
			}
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

	private List<Document> readPagesWithText(Path pdf) {
		PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder().withPagesPerDocument(1).build();
		return new PagePdfDocumentReader(new FileSystemResource(pdf), config).get()
			.stream()
			.filter(page -> page.getText() != null && !page.getText().isBlank())
			.toList();
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
