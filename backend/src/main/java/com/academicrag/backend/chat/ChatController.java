package com.academicrag.backend.chat;

import com.academicrag.backend.chat.dto.ChatRequest;
import com.academicrag.backend.chat.dto.ChatResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint HTTP do chat. Apenas valida a entrada e delega ao {@link ChatService};
 * busca, limiar de recusa e prompt ficam no serviço.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

	private final ChatService chatService;

	public ChatController(ChatService chatService) {
		this.chatService = chatService;
	}

	@PostMapping
	public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
		return chatService.answer(request.question());
	}

}
