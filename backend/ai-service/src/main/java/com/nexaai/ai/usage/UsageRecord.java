package com.nexaai.ai.usage;

import com.nexaai.ai.model.ModelDescriptor;
import java.time.Instant;
import java.util.List;

/**
 * One completed or failed generation, for metering.
 *
 * <p><strong>Prompt and completion text are deliberately absent.</strong> A usage record is the
 * thing most likely to be logged, exported to a metrics backend, and kept for a year. Carrying
 * the user's words through all of that would turn metering into a data-retention problem nobody
 * asked for. Only counts and identifiers.
 */
public record UsageRecord(
        String requestId,
        String provider,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        boolean fallbackUsed,
        boolean streamed,
        boolean success,
        ProviderFailureKindName failureKind,
        long durationMillis,
        Instant recordedAt) {

    /**
     * A failure kind, mirrored rather than referenced.
     *
     * <p>So this record has no compile-time dependency on the provider layer, and therefore no
     * accidental dependency on Spring AI either.
     */
    public record ProviderFailureKindName(String value) {
        public static ProviderFailureKindName of(
                com.nexaai.ai.provider.ProviderFailureKind kind) {
            return kind == null ? null : new ProviderFailureKindName(kind.name());
        }
    }

    /** Total tokens, or null when the provider reported neither count. */
    public Integer totalTokens() {
        if (inputTokens == null && outputTokens == null) {
            return null;
        }
        return (inputTokens == null ? 0 : inputTokens) + (outputTokens == null ? 0 : outputTokens);
    }

    public static UsageRecord success(String requestId, ModelDescriptor model, Integer inputTokens,
                               Integer outputTokens, boolean fallbackUsed, boolean streamed,
                               long durationMillis) {
        return new UsageRecord(requestId, model.provider(), model.name(), inputTokens,
                outputTokens, fallbackUsed, streamed, true, null, durationMillis, Instant.now());
    }

    public static UsageRecord failure(String requestId, ModelDescriptor model, boolean fallbackUsed,
                               boolean streamed, long durationMillis,
                               com.nexaai.ai.provider.ProviderFailureKind kind) {
        return new UsageRecord(requestId, model.provider(), model.name(), null, null,
                fallbackUsed, streamed, false, ProviderFailureKindName.of(kind), durationMillis,
                Instant.now());
    }

    /** Totals across records. Null counts stay null rather than becoming zero. */
    public static Totals totals(List<UsageRecord> records) {
        long input = 0;
        long output = 0;
        long successes = 0;
        long failures = 0;
        for (UsageRecord record : records) {
            if (record.inputTokens() != null) {
                input += record.inputTokens();
            }
            if (record.outputTokens() != null) {
                output += record.outputTokens();
            }
            if (record.success()) {
                successes++;
            } else {
                failures++;
            }
        }
        return new Totals(records.size(), successes, failures, input, output);
    }

    /**
     * Aggregate usage.
     *
     * <p>In-memory only: this service has no database. It is the seam a later phase backs with
     * real storage, which is why it is a record rather than a query result.
     */
    public record Totals(
            long requests,
            long successes,
            long failures,
            long inputTokens,
            long outputTokens) {
    }
}
