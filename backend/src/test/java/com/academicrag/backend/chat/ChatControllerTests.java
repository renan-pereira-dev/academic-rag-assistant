package com.academicrag.backend.chat;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.academicrag.backend.chat.dto.ChatResponse;
import com.academicrag.backend.chat.dto.SourceDTO;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrato HTTP de POST /api/chat. O {@link ChatService} (e, portanto, o LLM) é sempre simulado.
 */
@WebMvcTest(ChatController.class)
class ChatControllerTests {

	private static final String FRONTEND_ORIGIN = "http://localhost:5173";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ChatService chatService;

	@Test
	void returnsAnswerWithSources() throws Exception {
		given(chatService.answer("A partir de qual período posso estagiar?")).willReturn(new ChatResponse(
				"A partir do 3º período.",
				List.of(new SourceDTO("Manual Institucional de Estágios 2025", 12, "O estágio poderá ser realizado..."),
						new SourceDTO("Manual de Graduação", 40, "Estágio supervisionado..."))));

		mockMvc.perform(post("/api/chat")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"question\":\"A partir de qual período posso estagiar?\"}"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.answer").value("A partir do 3º período."))
				.andExpect(jsonPath("$.sources.length()").value(2))
				.andExpect(jsonPath("$.sources[0].document").value("Manual Institucional de Estágios 2025"))
				.andExpect(jsonPath("$.sources[0].page").value(12))
				.andExpect(jsonPath("$.sources[0].excerpt").value("O estágio poderá ser realizado..."))
				.andExpect(jsonPath("$.sources[1].page").value(40));

		verify(chatService).answer("A partir de qual período posso estagiar?");
	}

	@Test
	void returnsRefusalWithEmptySources() throws Exception {
		given(chatService.answer("Quem é o reitor?"))
				.willReturn(new ChatResponse("Não encontrei essa informação nos documentos.", List.of()));

		mockMvc.perform(post("/api/chat")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"question\":\"Quem é o reitor?\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.answer").value("Não encontrei essa informação nos documentos."))
				.andExpect(jsonPath("$.sources").isArray())
				.andExpect(jsonPath("$.sources").isEmpty());
	}

	@Test
	void rejectsEmptyQuestion() throws Exception {
		mockMvc.perform(post("/api/chat")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"question\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.detail").value("A pergunta não pode ser vazia."));

		verify(chatService, never()).answer(anyString());
	}

	@Test
	void rejectsBlankQuestion() throws Exception {
		mockMvc.perform(post("/api/chat")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"question\":\"   \"}"))
				.andExpect(status().isBadRequest());

		verify(chatService, never()).answer(anyString());
	}

	@Test
	void rejectsMissingQuestionField() throws Exception {
		mockMvc.perform(post("/api/chat")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("A pergunta não pode ser vazia."));

		verify(chatService, never()).answer(anyString());
	}

	@Test
	void rejectsMalformedJson() throws Exception {
		mockMvc.perform(post("/api/chat")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"question\":"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("O corpo da requisição está ausente ou não é um JSON válido."));

		verify(chatService, never()).answer(anyString());
	}

	@Test
	void hidesInternalDetailsOnUnexpectedError() throws Exception {
		given(chatService.answer(anyString())).willThrow(new IllegalStateException("segredo interno"));

		mockMvc.perform(post("/api/chat")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"question\":\"Como funciona o trancamento?\"}"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.status").value(500))
				.andExpect(content().string(not(containsString("segredo interno"))));
	}

	@Test
	void keepsMethodNotAllowedStatus() throws Exception {
		mockMvc.perform(get("/api/chat"))
				.andExpect(status().isMethodNotAllowed());
	}

	@Test
	void allowsCorsPreflightFromFrontend() throws Exception {
		mockMvc.perform(options("/api/chat")
						.header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN));
	}

	@Test
	void rejectsCorsPreflightFromUnknownOrigin() throws Exception {
		mockMvc.perform(options("/api/chat")
						.header(HttpHeaders.ORIGIN, "http://evil.example.com")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
	}

}
