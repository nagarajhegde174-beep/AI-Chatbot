package com.nexaai.ai.web;

import com.nexaai.ai.provider.ProviderException;
import com.nexaai.ai.provider.ProviderFailureKind;
import com.nexaai.ai.routing.ModelRouter;
import com.nexaai.ai.web.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * unexpected exception's own message: that is how a provider's upstream URL or a class path ends
 * up in a browser. Unexpected exceptions get a fixed message plus a {@code traceId}, and the
 * detail goes to the log.
 *
 * <p><strong>Status choices worth stating.</strong>
 * <ul>
 *   <li>An unknown model is <strong>400</strong>. The caller named something that does not exist,
 *       and the error lists the names it could have used. Answering with the default instead
 *       would return a confidently different answer to a question about a different model.</li>
 *   <li>A provider that refused the request is <strong>400</strong> when the request was at
 *       fault, and <strong>502</strong> when our own credential was. Collapsing them would tell a
 *       caller to fix a prompt that is fine.</li>
 *   <li>A provider that is down, throttling, or not configured is <strong>503</strong>. The
 *       caller did nothing wrong and retrying later is a reasonable response.</li>
 *   <li>A forbidden role is 403. Collapsing 401 and 403 hides the difference between "log in"
 *       and "you may not do this", which a client needs in order to respond usefully.</li>
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

    /** The caller's role does not permit this. Distinct from "not yours". */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> onAccessDenied(RuntimeException e,
                                                    HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(403, "FORBIDDEN",
                        "You do not have permission to perform this action.", traceId(request)));
    }

    /**
     * A model name that is not in the catalog.
     *
     * <p>400 rather than 404: the endpoint exists and the request was understood, the value was
     * simply not one of the permitted ones. The available names are returned because a client
     * that can see the correct options can correct itself, and one that cannot has to guess.
     */
    @ExceptionHandler(ModelRouter.UnknownModelException.class)
    public ResponseEntity<ApiError> onUnknownModel(ModelRouter.UnknownModelException e,
                                                    HttpServletRequest request) {
        log.debug("Unknown model '{}' requested on {}", e.requested(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(java.time.Instant.now(), 400, "UNKNOWN_MODEL",
                        "Unknown model '" + e.requested() + "'. Available models: "
                                + String.join(", ", e.available()),
                        traceId(request), List.of()));
    }

    /**
     * Every candidate model was unavailable.
     *
     * <p>503, because nothing about the request is wrong and retrying later plausibly works.
     * The per-candidate reasons come back because "no provider is available" on its own sends an
     * operator looking through logs instead of at the configuration they just changed.
     */
    @ExceptionHandler(ModelRouter.NoProviderAvailableException.class)
    public ResponseEntity<ApiError> onNoProvider(ModelRouter.NoProviderAvailableException e,
                                                  HttpServletRequest request) {
        log.warn("No provider available for request {} on {}: {}",
                e.requestId(), request.getRequestURI(), e.detail());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of(503, "NO_PROVIDER_AVAILABLE",
                        "No AI provider is currently available for model "
                                + e.model().name() + ". " + e.detail(), traceId(request)));
    }

    /**
     * A provider failed.
     *
     * <p>The status is derived from the classified failure kind, not from the exception type,
     * because the kind already carries the answer to "whose fault is this". Nothing from the
     * provider's own message reaches the body: it may name an upstream URL, an internal status, or
     * an account identifier.
     */
    @ExceptionHandler(ProviderException.class)
    public ResponseEntity<ApiError> onProviderFailure(ProviderException e,
                                                      HttpServletRequest request) {
        HttpStatus status = switch (e.kind()) {
            // The provider refused this prompt. Retrying it unchanged will be refused again.
            case REJECTED, LIMIT_EXCEEDED -> HttpStatus.BAD_REQUEST;
            // Transient, or not configured. The caller did nothing wrong.
            case UNAVAILABLE, THROTTLED, NOT_CONFIGURED -> HttpStatus.SERVICE_UNAVAILABLE;
            // Our credential or our wiring is wrong. Not the caller's to fix, and not a 400.
            case UNAUTHORISED, UNKNOWN -> HttpStatus.BAD_GATEWAY;
        };

        log.warn("Provider {} failed on {} ({}): {}", e.model() == null ? "unknown"
                : e.model().provider(), request.getRequestURI(), e.kind(), e.getMessage());

        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), "PROVIDER_" + e.kind().name(),
                        userFacingMessage(e.kind()), traceId(request)));
    }

    /**
     * A message safe to return to a caller.
     *
     * <p>Fixed text per kind rather than the exception's own. The provider's message is for the
     * log; this one is for a person, and the log line above already has it.
     */
    private static String userFacingMessage(ProviderFailureKind kind) {
        return switch (kind) {
            case REJECTED -> "The AI provider rejected this request.";
            case LIMIT_EXCEEDED -> "This request is larger than the selected model accepts.";
            case UNAUTHORISED -> "The AI provider rejected this service's credentials.";
            case THROTTLED -> "The AI provider is rate limiting requests. Try again shortly.";
            case UNAVAILABLE -> "The AI provider is not reachable. Try again shortly.";
            case NOT_CONFIGURED -> "No AI provider is configured for this model.";
            case UNKNOWN -> "The AI provider returned an unexpected error.";
        };
    }

    /**
     * A server-side configuration fault reached at request time.
     *
     * <p>500, deliberately. The other services map {@code IllegalStateException} to 409 because a
     * conversation cannot move from one state to another; there is no state machine here. Here
     * the same exception means "the default model is not in the catalog" or "a duplicate model
     * name was configured" — a fault an operator must fix, and calling it a conflict would tell
     * them to resolve a conflict they do not have.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> onMisconfiguration(IllegalStateException e,
                                                       HttpServletRequest request) {
        log.error("Configuration fault reached on {}", request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(500, "NOT_CONFIGURED",
                        "This service is not correctly configured. Quote the trace id if you "
                                + "report this.", traceId(request)));
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