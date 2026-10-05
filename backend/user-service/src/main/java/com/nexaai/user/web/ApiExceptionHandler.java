package com.nexaai.user.web;

import com.nexaai.user.domain.IllegalStateTransitionException;
import com.nexaai.user.exception.UserNotFoundException;
import com.nexaai.user.web.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
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
 * <p><strong>Messages here are written for the person reading them, and nothing more.</strong>
 * No handler returns an exception message from an unexpected exception type: that is how a
 * SQL constraint name or a class path ends up in a browser. Unexpected exceptions get a fixed
 * message plus a {@code traceId}, and the detail goes to the log.
 *
 * <p>Status choices worth stating:
 * <ul>
 *   <li>Unknown user on an administrative route is 404. On a self-service route it is also
 *       404, never 403, so a caller cannot distinguish "no such user" from "not yours" by
 *       probing ids.</li>
 *   <li>Forbidden is 403, unauthenticated is 401. Collapsing them hides the difference between
 *       "log in" and "you may not do this", which a client needs in order to respond usefully.</li>
 * </ul>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Validation failed. Every offending field is listed, so the form can mark each one. */
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

    /** A JSON body that could not be parsed, or a missing required body. */
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

    /**
     * A path variable or query parameter of the wrong type.
     *
     * <p>The expected form is named, because "type mismatch" on its own tells a caller
     * nothing about what to send.
     */
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

    /** A rejected query-string filter value, such as an unknown status. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> onIllegalArgument(IllegalArgumentException e,
                                                      HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(400, "INVALID_REQUEST", e.getMessage(), traceId(request)));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiError> onUserNotFound(UserNotFoundException e,
                                                   HttpServletRequest request) {
        // 404 for both "does not exist" and "is not yours". A 403 here would confirm the
        // existence of an id to someone probing for one.
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "USER_NOT_FOUND",
                        "No user profile was found.", traceId(request)));
    }

    /**
     * A status transition the lifecycle forbids.
     *
     * <p>409, not 400: the request was well-formed, and retrying it unchanged will never work.
     */
    @ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<ApiError> onIllegalTransition(IllegalStateTransitionException e,
                                                         HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(409, "ILLEGAL_STATUS_TRANSITION",
                        "Cannot change account status from " + e.getFrom() + " to " + e.getTo()
                                + ".",
                        traceId(request)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> onAccessDenied(AccessDeniedException e,
                                                    HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(403, "FORBIDDEN",
                        "You do not have permission to perform this action.", traceId(request)));
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

    /**
 * No handler matched the request path.
 *
 * <p>Handled explicitly because the catch-all {@code Exception} handler below would otherwise
 * turn every typo'd URL, stale bookmark and scanner probe into a 500. A 404 is both the
 * correct status and the answer a client actually needs; a 500 implies the server broke, which
 * sends an operator hunting a fault that does not exist and tells an attacker they found one.
 */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiError> onNoHandler(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "NOT_FOUND",
                        "No endpoint matches " + request.getMethod() + " "
                                + request.getRequestURI() + ".",
                        traceId(request)));
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