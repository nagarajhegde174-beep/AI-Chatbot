package com.nexaai.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body sent by Chat Service to compress and summarize older conversation turns.
 */
public record SummarizeRequest(
        /**
         * Optional provider override (e.g. "OPENAI", "GEMINI", "GROQ").
         */
        String provider,

        /**
         * Optional model override (e.g. "gpt-4o-mini").
         */
        @Size(max = 128)
        String model,

        /**
         * Prior running summary, if this is an incremental compression.
         */
        String existingSummary,

        /**
         * Ordered conversation turns that need to be summarized.
         */
        @NotEmpty
        @Valid
        List<CompletionRequest.CompletionMessage> messages,

        /**
         * Hard upper bound on summary output tokens (default 300).
         */
        Integer maxOutputTokens,

        /**
         * Correlation identifier for tracing.
         */
        @NotBlank
        String requestId
) {}
