package com.nexaai.ai.web.dto;

import java.time.Instant;

/**
 * The result of compressing a run of older turns.
 *
 * <p><strong>Carries {@code summarisedTurnCount}, not the sequence numbers.</strong> The caller
 * already knows the range it asked about; what it needs to know afterwards is whether anything is
 * now covered by the summary, so a later request can decide which turns to resend verbatim. A count
 * is enough for that and cannot be misused to address messages outside the caller's own
 * conversation.
 *
 * @param summaryLengthChars measured rather than estimated, so a caller can alert on a summary
 *                           that did not actually compress
 */
public record SummarizeResponse(
        String requestId,
        String summary,
        String model,
        String provider,
        int summarisedTurnCount,
        int summaryLengthChars,
        Integer inputTokens,
        Integer outputTokens,
        long durationMillis,
        Instant completedAt) {
}