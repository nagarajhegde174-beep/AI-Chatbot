package com.nexaai.user.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Produces the error envelope from SERVICE_CONTRACTS.md §1.4.
 * Never returns stack traces, SQL messages, or internal exception types to callers.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UserException.class)
    public ResponseEntity<Map<String, Object>> handleUserException(UserException ex) {
        String correlationId = UUID.randomUUID().toString();
        log.warn("UserException [{}]: {} (Correlation: {})", ex.getCode(), ex.getMessage(), correlationId);
        return ResponseEntity.status(ex.getHttpStatus()).body(errorBody(ex.getCode(), ex.getMessage(), correlationId, null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String correlationId = UUID.randomUUID().toString();
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        log.warn("Validation failure (Correlation: {}): {}", correlationId, fieldErrors);
        return ResponseEntity.badRequest().body(errorBody("VALIDATION_FAILED", "Validation failed.", correlationId, fieldErrors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        String correlationId = UUID.randomUUID().toString();
        log.error("Unhandled exception (Correlation: {}): {}", correlationId, ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody("INTERNAL_ERROR", "An unexpected error occurred.", correlationId, null));
    }

    private Map<String, Object> errorBody(String code, String message, String correlationId, Object details) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.put("correlationId", correlationId);
        error.put("timestamp", Instant.now().toString());
        error.put("details", details);
        return Map.of("error", error);
    }
}
