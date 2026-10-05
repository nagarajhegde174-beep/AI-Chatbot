package com.nexaai.chat.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * The error body every failure returns.
 *
 * <p>A uniform shape means a client parses errors without special-casing each endpoint, and a
 * machine-readable {@code code} lets the frontend branch on that rather than on a message string
 * that will be reworded.
 *
 * <p>No handler returns an unexpected exception's own message; stack traces, SQL and internal
 * identifiers go to the log with the {@code traceId}.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String traceId,
        List<FieldViolation> violations) {

    /** One rejected field. Present on validation failures only. */
    public record FieldViolation(String field, String message) {
    }

    public static ApiError of(int status, String code, String message, String traceId) {
        return new ApiError(Instant.now(), status, code, message, traceId, List.of());
    }

    public static ApiError of(int status, String code, String message, String traceId,
                              List<FieldViolation> violations) {
        return new ApiError(Instant.now(), status, code, message, traceId, violations);
    }
}