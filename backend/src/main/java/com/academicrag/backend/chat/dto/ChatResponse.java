package com.academicrag.backend.chat.dto;

import java.util.List;

/**
 * Corpo de saída de POST /api/chat.
 *
 * <p>Quando o assistente recusa a pergunta, {@code sources} vem vazia.
 *
 * @param answer resposta gerada (ou a mensagem de recusa)
 * @param sources trechos usados como fonte da resposta; nunca nulo
 */
public record ChatResponse(String answer, List<SourceDTO> sources) {

	public ChatResponse {
		sources = sources == null ? List.of() : List.copyOf(sources);
	}

}
