package com.nexaai.ai.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Global exception handler for the AI Service reactive stack.
 *
 * Rules:
 * - Error responses never contain stack traces, SQL messages, or raw provider
 *   error bodies (SERVICE_CONTRACTS.md §1.4).
 * - The Throwable.getMessage() for provider errors is deliberately not
 *   forwarded; only the classified message is returned.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AiException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleAiException(AiException ex) {
        // Log the cause (provider's raw error) at DEBUG only — never surfaces to client
        log.debug("AI provider error [{}] retryable={}", ex.getErrorCode(), ex.isRetryable(), ex);
        log.warn("AI provider classified error: code={} status={}", ex.getErrorCode(), ex.getHttpStatus());

        return Mono.just(ResponseEntity
                .status(ex.getHttpStatus())
                .body(errorBody(ex.getErrorCode(), ex.getMessage())));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleValidation(WebExchangeBindException ex) {
        log.debug("Validation failure", ex);
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorBody("AI_BAD_REQUEST",
                        "Request validation failed: " + ex.getBindingResult().getFieldError())));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleUnexpected(Exception ex) {
        // Never expose the exception message — it may contain internal details
        log.error("Unexpected error in AI service", ex);
        return Mono.just(ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody("AI_INTERNAL_ERROR", "An unexpected error occurred.")));
    }

    private static Map<String, Object> errorBody(String code, Object message) {
        return Map.of(
                "error", Map.of(
                        "code", code,
                        "message", String.valueOf(message),
                        "timestamp", Instant.now().toString()
                )
        );
    }
}
