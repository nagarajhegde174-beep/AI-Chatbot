package com.nexaai.ai.dto;

/**
 * Summarization response returned to Chat Service.
 */
public record SummarizeResponse(
        String requestId,
        String provider,
        String model,
        String summary,
        int tokensUsed
) {}
