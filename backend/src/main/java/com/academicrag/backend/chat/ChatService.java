package com.academicrag.backend.chat;

import com.academicrag.backend.chat.dto.ChatResponse;
import org.springframework.stereotype.Service;

/**
 * Esqueleto do fluxo de pergunta e resposta: pergunta -> busca vetorial -> prompt -> Gemini.
 *
 * <p>A lógica deste pacote é escrita manualmente pelo responsável do projeto
 * (Etapa 2). Ver CLAUDE.md, seção "Proteção da arquitetura RAG".
 */
@Service
public class ChatService {

	/**
	 * Responde a pergunta com base nos documentos indexados.
	 *
	 * <p>Contrato esperado pelo {@code ChatController}: em caso de recusa, devolver
	 * a mensagem de recusa em {@code answer} e a lista {@code sources} vazia.
	 *
	 * @param question pergunta do aluno (já validada como não vazia)
	 * @return resposta com as fontes usadas
	 */
	public ChatResponse answer(String question) {
		// TODO (Etapa 2): buscar os trechos mais parecidos com a pergunta (top-k).
		// TODO (Etapa 2): aplicar o limiar de similaridade e recusar ("não encontrei") sem chamar o LLM.
		// TODO (Etapa 2): montar o prompt que responde só com o contexto e cita documento e página.
		// TODO (Etapa 2): chamar o ChatModel e devolver a resposta com a lista de fontes.
		throw new UnsupportedOperationException("ChatService ainda não implementado (Etapa 2).");
	}

}
