package com.academicrag.backend.config;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Tratamento global de erros da API no formato ProblemDetail (RFC 9457).
 *
 * <p>Estende {@link ResponseEntityExceptionHandler} para que os erros padrão do Spring MVC
 * (405, 415 etc.) mantenham o status correto. Aqui só personalizamos as mensagens de
 * entrada inválida e escondemos detalhes internos em erros inesperados.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<String> messages = ex.getBindingResult().getFieldErrors().stream()
				.map(FieldError::getDefaultMessage)
				.toList();
		return badRequest(String.join(" ", messages));
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return badRequest("O corpo da requisição está ausente ou não é um JSON válido.");
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
		log.error("Erro inesperado ao processar a requisição", ex);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				"Não foi possível processar a pergunta. Tente novamente mais tarde.");
		problem.setTitle("Erro interno");
		return ResponseEntity.internalServerError().body(problem);
	}

	private static ResponseEntity<Object> badRequest(String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
		problem.setTitle("Requisição inválida");
		return ResponseEntity.badRequest().body(problem);
	}

}
