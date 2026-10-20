package com.nexaai.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body sent by Chat Service for a non-streaming or streaming
 * completion. The caller assembles the full message list; AI Service does not
 * read conversation history from any store.
 */
public record CompletionRequest(

        /**
         * The provider to use. AI Service maps it to the correct ChatClient.
         */
        @NotNull
        String provider,

        /**
         * The model identifier within the provider, e.g. "gpt-4o-mini".
         */
        @NotBlank
        @Size(max = 128)
        String model,

        /**
         * Optional system prompt. When present it is prepended as the first
         * SYSTEM message. Duplicating it inside messages is allowed but not
         * required.
         */
        String systemPrompt,

        /**
         * The ordered conversation turns to submit to the model.
         * Must contain at least one message.
         */
        @NotEmpty
        @Valid
        List<CompletionMessage> messages,

        /**
         * Sampling temperature. Null means provider default.
         * Not all providers honour this field.
         */
        @Min(0)
        @Max(2)
        Double temperature,

        /**
         * Hard upper bound on output tokens. Required — a missing bound is an
         * unbounded bill (SERVICE_CONTRACTS.md §8.2).
         */
        @NotNull
        @Min(1)
        @Max(32768)
        Integer maxOutputTokens,

        /**
         * Caller-supplied correlation id propagated to the response and to the
         * Kafka event for metering traceability.
         */
        @NotBlank
        String requestId
) {

    /**
     * A single turn in the conversation.
     */
    public record CompletionMessage(
            @NotBlank String role,   // USER, ASSISTANT, SYSTEM
            @NotBlank String content
    ) {}
}
