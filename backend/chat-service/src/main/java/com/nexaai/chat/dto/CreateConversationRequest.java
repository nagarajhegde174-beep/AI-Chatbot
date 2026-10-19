package com.nexaai.chat.dto;

import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @Size(max = 100, message = "Model name must not exceed 100 characters")
        String model,

        String systemPrompt,

        Boolean ragEnabled
) {
}
