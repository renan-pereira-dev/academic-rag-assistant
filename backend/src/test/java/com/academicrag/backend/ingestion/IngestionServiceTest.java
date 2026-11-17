package com.academicrag.backend.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.academicrag.backend.ingestion.IngestionService.IngestionResult;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

	private static final int CHUNK_SIZE = 60;

	private static final int OVERLAP = 10;

	// Lote grande o bastante para um PDF de teste caber em uma única chamada ao VectorStore.
	private static final int ONE_BATCH = 1_000;

	private static final long NO_DELAY = 0;

	@TempDir
	Path docsDir;

	@Mock
	VectorStore vectorStore;

	@Captor
	ArgumentCaptor<List<Document>> chunksCaptor;

	@Test
	void everyChunkKeepsDocumentNameAndPageNumber() throws IOException {
		writeManual("regulamento.pdf");

		List<Document> chunks = ingestAndCapture();

		assertThat(chunks).isNotEmpty();
		assertThat(chunks).allSatisfy(chunk -> {
			assertThat(chunk.getMetadata()).containsEntry(IngestionService.METADATA_DOCUMENT, "regulamento.pdf");
			assertThat(chunk.getMetadata().get(IngestionService.METADATA_PAGE)).isIn(1, 3);
		});
		// A página 1 é longa e precisa virar vários chunks; a página 3 é curta e vira um só.
		assertThat(chunksOfPage(chunks, 1)).hasSizeGreaterThan(1);
		assertThat(chunksOfPage(chunks, 3)).hasSize(1);
	}

	@Test
	void pagesWithoutTextAreCountedAndSkipped() throws IOException {
		writeManual("regulamento.pdf");

		IngestionResult result = service().ingest();

		verify(vectorStore).add(chunksCaptor.capture());
		assertThat(result.pages()).isEqualTo(3);
		assertThat(result.emptyPages()).isEqualTo(1);
		assertThat(result.chunks()).isEqualTo(chunksCaptor.getValue().size());
		assertThat(chunksOfPage(chunksCaptor.getValue(), 2)).isEmpty();
	}

	@Test
	void chunkStartsWithTheLastTokensOfThePreviousChunkOfTheSamePage() throws IOException {
		writeManual("regulamento.pdf");

		List<Document> page1 = chunksOfPage(ingestAndCapture(), 1);

		String expectedOverlap = lastTokens(page1.get(0).getText(), OVERLAP).strip();
		assertThat(expectedOverlap).isNotEmpty();
		assertThat(page1.get(1).getText()).startsWith(expectedOverlap + " ");
	}

	@Test
	void overlapIsNotCarriedAcrossPages() throws IOException {
		writeManual("regulamento.pdf");

		Document page3 = chunksOfPage(ingestAndCapture(), 3).get(0);

		// O reader preserva o layout do PDF e insere espaços extras; normalizamos antes de comparar.
		String text = page3.getText().strip().replaceAll("\\s+", " ");
		assertThat(text).startsWith("Pagina tres").doesNotContain("Artigo");
	}

	@Test
	void ingestsOnlyPdfFilesAndWritesOneBatchPerFile() throws IOException {
		writeManual("a-graduacao.pdf");
		writeManual("b-estagios.pdf");
		Files.writeString(docsDir.resolve("anotacoes.txt"), "não é PDF");

		IngestionResult result = service().ingest();

		verify(vectorStore, times(2)).add(chunksCaptor.capture());
		assertThat(result.files()).isEqualTo(2);
		assertThat(chunksCaptor.getAllValues()).extracting(
				batch -> batch.get(0).getMetadata().get(IngestionService.METADATA_DOCUMENT))
			.containsExactly("a-graduacao.pdf", "b-estagios.pdf");
	}

	@Test
	void emptyDirectoryWritesNothing() {
		IngestionResult result = service().ingest();

		assertThat(result).isEqualTo(new IngestionResult(0, 0, 0, 0));
		verifyNoInteractions(vectorStore);
	}

	@Test
	void missingDirectoryFailsFast() {
		IngestionService service = new IngestionService(vectorStore, docsDir.resolve("nao-existe").toString(),
				CHUNK_SIZE, OVERLAP, ONE_BATCH, NO_DELAY);

		assertThatThrownBy(service::ingest).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("nao-existe");
	}

	@Test
	void acceptsRelativeDocsDirWithParentSegment() throws IOException {
		// Mesmo formato do default "../docs", que quebrava quando injetado como Path.
		Files.createDirectories(docsDir.resolve("backend"));
		writeManual("regulamento.pdf");
		String relativeDocsDir = docsDir.resolve("backend").resolve("..").toString();

		IngestionResult result = new IngestionService(vectorStore, relativeDocsDir, CHUNK_SIZE, OVERLAP, ONE_BATCH,
				NO_DELAY)
			.ingest();

		assertThat(result.files()).isEqualTo(1);
	}

	@Test
	void rejectsOverlapNotSmallerThanChunkSize() {
		assertThatThrownBy(() -> new IngestionService(vectorStore, docsDir.toString(), 100, 100, ONE_BATCH, NO_DELAY))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void sendsChunksInBatchesOfConfiguredSizeKeepingOrder() throws IOException {
		writeManual("regulamento.pdf");
		List<Document> allChunks = ingestAndCapture();
		clearInvocations(vectorStore);

		IngestionResult result = new IngestionService(vectorStore, docsDir.toString(), CHUNK_SIZE, OVERLAP, 2,
				NO_DELAY)
			.ingest();

		// Captor novo: o chunksCaptor ainda guarda a captura da execução de referência acima.
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Document>> batchCaptor = ArgumentCaptor.forClass(List.class);
		verify(vectorStore, atLeast(2)).add(batchCaptor.capture());
		List<List<Document>> batches = batchCaptor.getAllValues();
		assertThat(batches).allSatisfy(batch -> assertThat(batch).hasSizeBetween(1, 2));
		assertThat(batches.stream().flatMap(List::stream).map(Document::getText))
			.containsExactlyElementsOf(allChunks.stream().map(Document::getText).toList());
		assertThat(result.chunks()).isEqualTo(allChunks.size());
	}

	@Test
	void pausesBeforeEveryBatchExceptTheFirstIncludingAcrossFiles() throws IOException {
		writeManual("a-graduacao.pdf");
		writeManual("b-estagios.pdf");
		List<Long> pauses = new ArrayList<>();
		IngestionService service = new IngestionService(vectorStore, docsDir.toString(), CHUNK_SIZE, OVERLAP, 2,
				25_000) {
			@Override
			void pause(long millis) {
				pauses.add(millis);
			}
		};

		service.ingest();

		verify(vectorStore, atLeast(4)).add(chunksCaptor.capture());
		int batches = chunksCaptor.getAllValues().size();
		assertThat(pauses).hasSize(batches - 1).containsOnly(25_000L);
	}

	@Test
	void zeroDelayNeverPauses() throws IOException {
		writeManual("regulamento.pdf");
		List<Long> pauses = new ArrayList<>();
		IngestionService service = new IngestionService(vectorStore, docsDir.toString(), CHUNK_SIZE, OVERLAP, 2,
				NO_DELAY) {
			@Override
			void pause(long millis) {
				pauses.add(millis);
			}
		};

		service.ingest();

		verify(vectorStore, atLeast(2)).add(chunksCaptor.capture());
		assertThat(pauses).isEmpty();
	}

	@Test
	void removesNullCharsBeforeSendingToVectorStore() throws IOException {
		writeManual("regulamento.pdf"); // só fornece a contagem de 3 páginas; o texto vem do override abaixo
		IngestionService service = new IngestionService(vectorStore, docsDir.toString(), CHUNK_SIZE, OVERLAP,
				ONE_BATCH, NO_DELAY) {
			@Override
			List<Document> readPages(Path pdf) {
				return List.of(extractedPage(1, "Art. 1\u0000 O aluno\u0000 deve se matricular."),
						extractedPage(2, "\u0000\u0000 \u0000"));
			}
		};

		IngestionResult result = service.ingest();

		verify(vectorStore).add(chunksCaptor.capture());
		List<Document> chunks = chunksCaptor.getValue();
		assertThat(chunks).isNotEmpty().allSatisfy(chunk -> assertThat(chunk.getText()).doesNotContain("\u0000"));
		assertThat(chunks.get(0).getText()).contains("Art. 1 O aluno deve se matricular.");
		// A página 2 só tinha NUL e espaço: depois de limpa, conta como vazia.
		assertThat(chunksOfPage(chunks, 2)).isEmpty();
		assertThat(result.emptyPages()).isEqualTo(2);
	}

	@Test
	void rejectsInvalidBatchSettings() {
		assertThatThrownBy(() -> new IngestionService(vectorStore, docsDir.toString(), CHUNK_SIZE, OVERLAP, 0,
				NO_DELAY))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new IngestionService(vectorStore, docsDir.toString(), CHUNK_SIZE, OVERLAP, 1, -1))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private IngestionService service() {
		return new IngestionService(vectorStore, docsDir.toString(), CHUNK_SIZE, OVERLAP, ONE_BATCH, NO_DELAY);
	}

	private List<Document> ingestAndCapture() {
		service().ingest();
		verify(vectorStore).add(chunksCaptor.capture());
		return chunksCaptor.getValue();
	}

	private static Document extractedPage(int pageNumber, String text) {
		return Document.builder()
			.text(text)
			.metadata(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER, pageNumber)
			.build();
	}

	private static List<Document> chunksOfPage(List<Document> chunks, int page) {
		return chunks.stream()
			.filter(c -> Integer.valueOf(page).equals(c.getMetadata().get(IngestionService.METADATA_PAGE)))
			.toList();
	}

	private static String lastTokens(String text, int count) {
		Encoding encoding = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
		IntArrayList tokens = encoding.encode(text);
		IntArrayList tail = new IntArrayList();
		for (int i = Math.max(0, tokens.size() - count); i < tokens.size(); i++) {
			tail.add(tokens.get(i));
		}
		return encoding.decode(tail);
	}

	/** Página 1 longa (vários chunks), página 2 em branco (como uma imagem escaneada), página 3 curta. */
	private void writeManual(String fileName) throws IOException {
		List<String> longPage = IntStream.rangeClosed(1, 30)
			.mapToObj(i -> "Artigo " + i + ". O aluno deve cumprir a carga horaria minima prevista no curso.")
			.toList();
		try (PDDocument document = new PDDocument()) {
			addPage(document, longPage);
			document.addPage(new PDPage());
			addPage(document, List.of("Pagina tres: o prazo de rematricula e definido no calendario academico."));
			document.save(docsDir.resolve(fileName).toFile());
		}
	}

	private static void addPage(PDDocument document, List<String> lines) throws IOException {
		PDPage page = new PDPage();
		document.addPage(page);
		try (PDPageContentStream content = new PDPageContentStream(document, page)) {
			content.beginText();
			content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
			content.setLeading(14);
			content.newLineAtOffset(40, 750);
			for (String line : lines) {
				content.showText(line);
				content.newLine();
			}
			content.endText();
		}
	}

}
