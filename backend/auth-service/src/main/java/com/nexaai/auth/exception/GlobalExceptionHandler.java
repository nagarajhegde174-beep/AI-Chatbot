package com.nexaai.auth.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorEnvelope> handleAuthException(AuthException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        log.warn("AuthException [{}]: {} (Correlation: {})", ex.getErrorCode(), ex.getMessage(), correlationId);
        ErrorEnvelope envelope = ErrorEnvelope.of(ex.getErrorCode(), ex.getMessage(), correlationId, ex.getDetails());
        return ResponseEntity.status(ex.getHttpStatus()).body(envelope);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        Map<String, String> details = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            details.put(error.getField(), error.getDefaultMessage());
        }
        log.warn("Validation failure (Correlation: {}): {}", correlationId, details);
        ErrorEnvelope envelope = ErrorEnvelope.of(
                ErrorCode.VALIDATION_FAILED,
                "Request validation failed.",
                correlationId,
                details
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(envelope);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorEnvelope> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        ErrorEnvelope envelope = ErrorEnvelope.of(
                ErrorCode.AUTH_INVALID_CREDENTIALS,
                "Email or password is incorrect.",
                correlationId,
                null
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(envelope);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorEnvelope> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        ErrorEnvelope envelope = ErrorEnvelope.of(
                ErrorCode.AUTH_ACCOUNT_SUSPENDED,
                "Access is denied.",
                correlationId,
                null
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(envelope);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleGenericException(Exception ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
        log.error("Unhandled exception (Correlation: {})", correlationId, ex);
        ErrorEnvelope envelope = ErrorEnvelope.of(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred.",
                correlationId,
                null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(envelope);
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader(CORRELATION_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            Object attr = request.getAttribute(CORRELATION_HEADER);
            if (attr instanceof String s && !s.isBlank()) {
                correlationId = s;
            } else {
                correlationId = UUID.randomUUID().toString();
            }
        }
        return correlationId;
    }
}
