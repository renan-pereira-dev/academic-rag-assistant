package com.academicrag.backend;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifica a configuração base da Etapa 0 contra um PostgreSQL + pgvector real
 * (Testcontainers). Nenhum teste aqui chama a API do Gemini.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class InfrastructureConfigurationTests {

	@Autowired
	private ChatModel chatModel;

	@Autowired
	private EmbeddingModel embeddingModel;

	@Autowired
	private VectorStore vectorStore;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void chatModelIsGoogleGenAiUsingGemini25Flash() {
		assertThat(chatModel).isInstanceOf(GoogleGenAiChatModel.class);
		assertThat(chatModel.getDefaultOptions().getModel()).isEqualTo("gemini-3.8-flash");
	}

	@Test
	void embeddingModelIsGoogleGenAi() {
		assertThat(embeddingModel).isInstanceOf(GoogleGenAiTextEmbeddingModel.class);
	}

	@Test
	void vectorStoreIsPgVector() {
		assertThat(vectorStore).isInstanceOf(PgVectorStore.class);
	}

	@Test
	void pgvectorExtensionIsInstalled() {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM pg_extension WHERE extname = 'vector'", Integer.class);
		assertThat(count).isEqualTo(1);
	}

	@Test
	void vectorStoreTableUsesConfiguredEmbeddingDimension() {
		String columnType = jdbcTemplate.queryForObject("""
				SELECT format_type(a.atttypid, a.atttypmod)
				FROM pg_attribute a
				WHERE a.attrelid = 'public.vector_store'::regclass
				  AND a.attname = 'embedding'
				""", String.class);
		assertThat(columnType).isEqualTo("vector(768)");
	}

	@Test
	void vectorStoreTableHasHnswCosineIndex() {
		Integer count = jdbcTemplate.queryForObject("""
				SELECT count(*) FROM pg_indexes
				WHERE schemaname = 'public'
				  AND tablename = 'vector_store'
				  AND indexdef ILIKE '%USING hnsw%vector_cosine_ops%'
				""", Integer.class);
		assertThat(count).isEqualTo(1);
	}

}
