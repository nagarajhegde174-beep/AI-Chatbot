package com.nexaai.chat.web;

import com.nexaai.chat.web.dto.ServiceInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal boundary endpoint. Proves the Chat Service is deployed as its own
 * application on its own port and knows only its own data. The real public API is
 * introduced in a later phase, see docs/TASKS.md.
 */
@RestController
@RequestMapping("/internal/v1/chat")
@Tag(name = "internal", description = "Infrastructure and boundary endpoints, not for end users")
public class ChatServiceInfoController {

    private final int port;

    public ChatServiceInfoController(
            @Value("${server.port}") int port) {
        this.port = port;
    }

    @GetMapping("/info")
    @Operation(summary = "Describe this service's boundary")
    public ServiceInfoResponse info() {
        return new ServiceInfoResponse(
                "chat-service",
                "Conversations, message history, prompt assembly orchestration and streamed answer relay.",
                port,
                "nexa_chat",
                List.of("chat.conversation.created.v1", "chat.message.completed.v1"));
    }
}