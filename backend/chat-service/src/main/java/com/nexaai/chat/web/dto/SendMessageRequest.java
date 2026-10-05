package com.nexaai.chat.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A new user message.
 *
 * <p>Content is required and non-blank. A send with no text would create an empty turn and a
 * PENDING reply to nothing, and would still consume the account's quota when generation is
 * enabled.
 */
public record SendMessageRequest(
        @NotBlank(message = "content is required and must not be blank")
        @Size(max = 32_000, message = "content must be at most 32000 characters")
        String content,

        /**
         * Optional model override for this turn only.
         *
         * <p>Null means "use whatever this conversation is pinned to". The override is not
         * persisted onto the conversation, because pinning a model and using one for a single
         * turn are different intentions and conflating them makes the conversation's model
         * mean nothing.
         */
        @Size(max = 128, message = "model must be at most 128 characters")
        String model) {
}