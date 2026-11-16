package com.academicrag.backend.cli;

import com.academicrag.backend.ingestion.IngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Dispara a ingestão quando a aplicação é iniciada com {@code --ingest}.
 *
 * <p>Exemplo: {@code ./mvnw spring-boot:run -Dspring-boot.run.arguments=--ingest}
 */
@Component
public class IngestionRunner implements ApplicationRunner {

	static final String INGEST_OPTION = "ingest";

	private static final Logger log = LoggerFactory.getLogger(IngestionRunner.class);

	private final IngestionService ingestionService;

	public IngestionRunner(IngestionService ingestionService) {
		this.ingestionService = ingestionService;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!args.containsOption(INGEST_OPTION)) {
			return;
		}
		log.info("Opção --{} recebida: iniciando a ingestão.", INGEST_OPTION);
		ingestionService.ingest();
		log.info("Ingestão concluída.");
	}

}
