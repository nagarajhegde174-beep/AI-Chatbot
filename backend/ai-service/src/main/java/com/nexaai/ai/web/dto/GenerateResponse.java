package com.nexaai.ai.web.dto;

import com.nexaai.ai.model.ModelDescriptor;
import java.time.Instant;

/**
 * A completed generation.
 *
 * @param fallbackUsed  true when the model that answered is not the one that was asked for. The
 *                      caller can see this rather than having to infer it
 * @param inputTokens   from the provider's own usage metadata, or null when the provider reported
 *                      none. Never invented as zero
 */
public record GenerateResponse(
        String requestId,
        String content,
        String model,
        String provider,
        Integer inputTokens,
        Integer outputTokens,
        boolean fallbackUsed,
        boolean streamed,
        long durationMillis,
        Instant completedAt) {

    public static GenerateResponse from(String requestId, String content,
                                        ModelDescriptor model, boolean fallbackUsed,
                                        boolean streamed, Integer inputTokens,
                                        Integer outputTokens, long durationMillis) {
        return new GenerateResponse(requestId, content, model.name(), model.provider(),
                inputTokens, outputTokens, fallbackUsed, streamed, durationMillis,
                Instant.now());
    }
}
