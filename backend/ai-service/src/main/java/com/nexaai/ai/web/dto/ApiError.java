package com.nexaai.ai.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * The error body every failure returns.
 *
 * <p>A uniform shape so a client parses errors without special-casing each endpoint, and a
 * machine-readable {@code code} so it branches on that rather than on a message string that will
 * be reworded.
 *
 * <p><strong>No handler returns an unexpected exception's own message.</strong> A provider
 * exception in particular may name an upstream URL or an internal status, and this body is
 * returned to a service that will render it to a person.
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