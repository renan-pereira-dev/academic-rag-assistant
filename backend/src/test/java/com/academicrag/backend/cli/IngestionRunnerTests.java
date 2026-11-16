package com.academicrag.backend.cli;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.academicrag.backend.ingestion.IngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class IngestionRunnerTests {

	private final IngestionService ingestionService = mock(IngestionService.class);

	private final IngestionRunner runner = new IngestionRunner(ingestionService);

	@Test
	void runsIngestionWhenStartedWithIngestOption() {
		runner.run(new DefaultApplicationArguments("--ingest"));

		verify(ingestionService).ingest();
	}

	@Test
	void doesNotRunIngestionWithoutArguments() {
		runner.run(new DefaultApplicationArguments());

		verify(ingestionService, never()).ingest();
	}

	@Test
	void doesNotRunIngestionForOtherArguments() {
		runner.run(new DefaultApplicationArguments("--server.port=9090", "ingest", "--ingestion"));

		verify(ingestionService, never()).ingest();
	}

	@Test
	void propagatesIngestionFailure() {
		willThrow(new IllegalStateException("falha na ingestão")).given(ingestionService).ingest();

		assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments("--ingest")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("falha na ingestão");
	}

}
