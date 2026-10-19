package com.nexaai.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditMessageRequest(
        @NotBlank(message = "Message content must not be blank")
        @Size(max = 32000, message = "Message content must not exceed 32000 characters")
        String content
) {
}
