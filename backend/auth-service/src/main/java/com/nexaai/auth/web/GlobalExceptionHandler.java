package com.nexaai.auth.web;

import com.nexaai.auth.domain.IllegalStateTransitionException;
import com.nexaai.auth.exception.AuthExceptions;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Turns exceptions into the error envelope defined in
 * {@code docs/SERVICE_CONTRACTS.md} section 1.4.
 *
 * <p><strong>Two invariants this handler exists to hold.</strong>
 *
 * <ol>
 *   <li>No response ever contains a stack trace, a SQL message or a provider's raw error.
 *       Those leak schema and infrastructure detail, and they are the detail an attacker
 *       actually wants.</li>
 *   <li>The correlation id is present on every error, because it is how a user reports a real
 *       problem. An error without one cannot be traced.</li>
 * </ol>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AuthExceptions.InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(
            AuthExceptions.InvalidCredentialsException e, HttpServletRequest request) {
        // The identical response for an unknown email and a wrong password. Any difference
        // here would be an enumeration oracle.
        return build(HttpStatus.UNAUTHORIZED, e.getCode(), e.getMessage(), request, null);
    }

    @ExceptionHandler(AuthExceptions.TokenReuseDetectedException.class)
    public ResponseEntity<Map<String, Object>> handleTokenReuse(
            AuthExceptions.TokenReuseDetectedException e, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, e.getCode(), e.getMessage(), request, null);
    }

    @ExceptionHandler(AuthExceptions.InvalidTokenException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidToken(
            AuthExceptions.InvalidTokenException e, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, e.getCode(), e.getMessage(), request, null);
    }

    @ExceptionHandler(AuthExceptions.TokenExpiredException.class)
    public ResponseEntity<Map<String, Object>> handleExpiredToken(
            AuthExceptions.TokenExpiredException e, HttpServletRequest request) {
        return build(HttpStatus.GONE, e.getCode(), e.getMessage(), request, null);
    }

    @ExceptionHandler(AuthExceptions.TokenAlreadyConsumedException.class)
    public ResponseEntity<Map<String, Object>> handleConsumedToken(
            AuthExceptions.TokenAlreadyConsumedException e, HttpServletRequest request) {
        return build(HttpStatus.GONE, e.getCode(), e.getMessage(), request, null);
    }

    @ExceptionHandler(AuthExceptions.AccountLockedException.class)
    public ResponseEntity<Map<String, Object>> handleLocked(
            AuthExceptions.AccountLockedException e, HttpServletRequest request) {
        return build(HttpStatus.LOCKED, e.getCode(), e.getMessage(), request, null);
    }

    @ExceptionHandler(AuthExceptions.AccountNotActiveException.class)
    public ResponseEntity<Map<String, Object>> handleNotActive(
            AuthExceptions.AccountNotActiveException e, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, e.getCode(), e.getMessage(), request, null);
    }

    @ExceptionHandler(AuthExceptions.EmailAlreadyRegisteredException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateEmail(
            AuthExceptions.EmailAlreadyRegisteredException e, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, e.getCode(), e.getMessage(), request, null);
    }

    @ExceptionHandler(AuthExceptions.WrongPasswordException.class)
    public ResponseEntity<Map<String, Object>> handleWrongPassword(
            AuthExceptions.WrongPasswordException e, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, e.getCode(), e.getMessage(), request, null);
    }

    @ExceptionHandler(AuthExceptions.AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            AuthExceptions.AccessDeniedException e, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, e.getCode(), e.getMessage(), request, null);
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalTransition(
            IllegalStateTransitionException e, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "AUTH_ILLEGAL_STATE_TRANSITION",
                "That change is not permitted from the account's current state.", request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        // Field-level messages only. The details object never echoes a submitted value, so a
        // rejected password cannot end up in a response body.
        Map<String, String> details = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(fe -> details.putIfAbsent(fe.getField(), fe.getDefaultMessage()));

        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Some fields are invalid.", request, details);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> handleMalformed(
            Exception e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "REQUEST_MALFORMED",
                "The request could not be read.", request, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException e, HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "REQUEST_REJECTED",
                e.getMessage(), request, null);
    }

    @ExceptionHandler(AuthExceptions.AuthException.class)
    public ResponseEntity<Map<String, Object>> handleGenericAuth(
            AuthExceptions.AuthException e, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, e.getCode(), e.getMessage(), request, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleSpringAuth(
            AuthenticationException e, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHENTICATED",
                "Authentication is required.", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(
            Exception e, HttpServletRequest request) {
        // The detail goes to the log; the caller gets a generic message and the correlation id.
        // This is the single most important line in the file: an unexpected exception must not
        // become a description of the internals.
        String correlationId = correlationId(request);
        log.error("Unhandled exception [correlationId={}] {} {}",
                correlationId, request.getMethod(), request.getRequestURI(), e);

        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Something went wrong. Please try again.", request, null);
    }

    // ------------------------------------------------------------------

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String code, String message,
                                                      HttpServletRequest request, Object details) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message == null ? "Request failed." : message);
        error.put("correlationId", correlationId(request));
        error.put("timestamp", Instant.now().toString());
        error.put("details", details);

        Map<String, Object> body = Map.of("error", error);
        return ResponseEntity.status(status).body(body);
    }

    private String correlationId(HttpServletRequest request) {
        var existing = ClientContext.from(request).correlationId();
        return existing == null ? UUID.randomUUID().toString() : existing;
    }
}