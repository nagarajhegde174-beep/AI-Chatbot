package com.nexaai.auth.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorEnvelope(ErrorBody error) {

    public record ErrorBody(
            String code,
            String message,
            String correlationId,
            Instant timestamp,
            Object details
    ) {}

    public static ErrorEnvelope of(ErrorCode code, String message, String correlationId, Object details) {
        return new ErrorEnvelope(new ErrorBody(
                code.name(),
                message,
                correlationId,
                Instant.now(),
                details
        ));
    }
}
