package com.nexaai.ai.dto;

import com.nexaai.ai.domain.FinishReason;

/**
 * Response returned by POST /internal/v1/ai/completions.
 * All fields mirror SERVICE_CONTRACTS.md §8.3.
 */
public record CompletionResponse(
        String requestId,
        String provider,
        String model,
        String content,
        FinishReason finishReason,
        TokenUsage usage,
        long latencyMs
) {
    public record TokenUsage(int promptTokens, int completionTokens, int totalTokens) {}
}
