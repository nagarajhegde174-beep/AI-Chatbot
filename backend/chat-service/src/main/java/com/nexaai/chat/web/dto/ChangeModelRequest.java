package com.nexaai.chat.web.dto;

import jakarta.validation.constraints.Size;

/**
 * A change of the conversation's pinned model.
 *
 * <p>A null or blank model clears the pin, which means "use the account holder's default".
 */
public record ChangeModelRequest(
        @Size(max = 128, message = "model must be at most 128 characters")
        String model) {
}