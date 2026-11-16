package com.academicrag.backend.chat.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Corpo de entrada de POST /api/chat.
 *
 * @param question pergunta do aluno em linguagem natural; não pode ser nula nem vazia
 */
public record ChatRequest(@NotBlank(message = "A pergunta não pode ser vazia.") String question) {
}
