package com.nexaai.chat.web;

import com.nexaai.chat.exception.ChatAccessDeniedException;
import com.nexaai.chat.exception.ChatResourceNotFoundException;
import com.nexaai.chat.web.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns exceptions into a uniform {@link ApiError}.
 *
 * <p><strong>Messages are written for the reader, and nothing more.</strong> No handler returns an
 * unexpected exception's own message: that is how a SQL constraint name or a class path ends up in
 * a browser. Unexpected exceptions get a fixed message plus a {@code traceId}, and the detail goes
 * to the log.
 *
 * <p>The status choices worth stating:
 * <ul>
 *   <li>Another user's conversation is <strong>404, never 403</strong>. A 403 would confirm the
 *       conversation exists, which is a reliable oracle for discovering real ids.</li>
 *   <li>A forbidden <em>role</em> is 403. Collapsing 401 and 403 hides the difference between
 *       "log in" and "you may not do this", which a client needs in order to respond usefully.</li>
 *   <li>An illegal status transition is 409, not 400: the request was well formed and retrying it
 *       unchanged will never work.</li>
 * </ul>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Validation failed. Every offending field is listed so the form can mark each one. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onValidationFailure(MethodArgumentNotValidException e,
                                                        HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiError.FieldViolation(f.getField(), f.getDefaultMessage()))
                .toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, "VALIDATION_FAILED",
                        "The request body failed validation.", traceId(request), violations));
    }

    /** A body that could not be parsed, or a missing required body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> onUnreadableBody(HttpMessageNotReadableException e,
                                                     HttpServletRequest request) {
        log.debug("Unreadable request body on {}: {}",
                request.getRequestURI(), e.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, "MALFORMED_BODY",
                        "The request body could not be parsed.", traceId(request)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> onMissingParameter(
            MissingServletRequestParameterException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, "MISSING_PARAMETER",
                        "Required parameter '" + e.getParameterName() + "' is missing.",
                        traceId(request)));
    }

    /** A path variable or query parameter of the wrong type. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> onTypeMismatch(MethodArgumentTypeMismatchException e,
                                                   HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, "INVALID_PARAMETER",
                        "Parameter '" + e.getName() + "' has an invalid value for type "
                                + (e.getRequiredType() == null
                                ? "unknown" : e.getRequiredType().getSimpleName()) + ".",
                        traceId(request)));
    }

    /** A rejected query-string filter or an illegal argument. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> onIllegalArgument(IllegalArgumentException e,
                                                      HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, "INVALID_REQUEST", e.getMessage(), traceId(request)));
    }

    /**
     * No handler matched the path.
     *
     * <p>Handled explicitly because the catch-all below would otherwise turn every typo, stale
     * bookmark and scanner probe into a 500. A 404 is both correct and what a client needs; a 500
     * says the server broke, which sends an operator hunting a fault that does not exist.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiError> onNoHandler(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "NOT_FOUND",
                        "No endpoint matches " + request.getMethod() + " "
                                + request.getRequestURI() + ".",
                        traceId(request)));
    }

    /**
     * A conversation or message that is absent, or belongs to someone else.
     *
     * <p>The same answer for both, deliberately. Differentiating them turns the endpoint into an
     * oracle for discovering which ids are real.
     */
    @ExceptionHandler(ChatResourceNotFoundException.class)
    public ResponseEntity<ApiError> onNotFound(ChatResourceNotFoundException e,
                                               HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "NOT_FOUND",
                        "No " + e.getKind().name().toLowerCase(Locale.ROOT) + " was found.",
                        traceId(request)));
    }

    /** The caller's role does not permit this. Distinct from "not yours". */
    @ExceptionHandler({ChatAccessDeniedException.class, AccessDeniedException.class})
    public ResponseEntity<ApiError> onAccessDenied(RuntimeException e,
                                                    HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(403, "FORBIDDEN",
                        "You do not have permission to perform this action.", traceId(request)));
    }

    /** An operation the conversation's current state does not allow, such as sending to an archive. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> onIllegalState(IllegalStateException e,
                                                   HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "CONFLICT", e.getMessage(), traceId(request)));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> onDataIntegrity(DataIntegrityViolationException e,
                                                    HttpServletRequest request) {
        // The database message names tables and constraints. Logged, never returned.
        log.warn("Data integrity violation on {}: {}",
                request.getRequestURI(), e.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "CONFLICT",
                        "That change conflicts with existing data.", traceId(request)));
    }

    /** Anything not handled above. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> onUnexpected(Exception e, HttpServletRequest request) {
        String traceId = traceId(request);
        log.error("Unhandled exception on {} (trace {})",
                request.getRequestURI(), traceId, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(500, "INTERNAL_ERROR",
                        "Something went wrong. Quote the trace id if you report this.", traceId));
    }

    private static String traceId(HttpServletRequest request) {
        Object id = request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        return id == null ? null : id.toString();
    }
}