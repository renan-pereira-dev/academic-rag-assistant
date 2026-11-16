package com.academicrag.backend.ingestion;

import org.springframework.stereotype.Service;

/**
 * Esqueleto do fluxo de ingestão: PDF -> trechos (chunks) -> embeddings -> pgvector.
 *
 * <p>A lógica deste pacote é escrita manualmente pelo responsável do projeto
 * (Etapa 1). Ver CLAUDE.md, seção "Proteção da arquitetura RAG".
 */
@Service
public class IngestionService {

	// TODO (Etapa 1): ler o PDF página por página, preservando o número da página.
	// TODO (Etapa 1): dividir o texto em trechos (chunking) com tamanho e sobreposição configuráveis.
	// TODO (Etapa 1): anexar metadados a cada trecho (nome do documento e página).
	// TODO (Etapa 1): gravar os trechos no VectorStore (os embeddings são gerados na gravação).

}
