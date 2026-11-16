package com.academicrag.backend.chat.dto;

/**
 * Trecho de documento usado como fonte de uma resposta.
 *
 * @param document nome do documento (ex.: "Manual Institucional de Estágios 2025")
 * @param page número da página de onde o trecho foi extraído
 * @param excerpt trecho do documento exibido ao aluno
 */
public record SourceDTO(String document, int page, String excerpt) {
}
