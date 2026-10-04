package com.nexaai.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The caller metadata recorded with every authentication event.
 *
 * <p>A value type built from a request, not a bean: it holds no state and depends on nothing,
 * so registering it with the container would be ceremony.
 *
 * <p><strong>The correlation id is trusted only within reason.</strong> A client-supplied
 * {@code X-Correlation-Id} is adopted so a trace spans the whole request, but it is validated
 * to be short and to contain only characters safe in a header and a log line. Accepting an
 * arbitrary header verbatim is how log injection works.
 *
 * <p><strong>The IP address prefers forwarded headers</strong>, because behind the gateway the
 * socket address is the gateway itself and lockout decisions would then apply to the gateway
 * rather than to the user. {@code X-Forwarded-For} is trusted here only because this service
 * is not reachable except through the edge; in a deployment where it were, the proxy chain
 * would have to be restricted explicitly.
 */
public final class ClientContext {

    private static final Logger log = LoggerFactory.getLogger(ClientContext.class);

    private static final int MAX_CORRELATION_ID_LENGTH = 64;
    private static final int MAX_USER_AGENT_LENGTH = 512;

    private final String ipAddress;
    private final String userAgent;
    private final String correlationId;

    private ClientContext(String ipAddress, String userAgent, String correlationId) {
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.correlationId = correlationId;
    }

    public static ClientContext from(HttpServletRequest request) {
        return new ClientContext(
                resolveIp(request),
                truncate(request.getHeader("User-Agent"), MAX_USER_AGENT_LENGTH),
                resolveCorrelationId(request));
    }

    private static String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // Left-most entry is the original client.
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty() && first.length() <= 64) {
                return first;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank() && realIp.length() <= 64) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Adopts a client correlation id only if it is well formed.
     *
     * <p>Otherwise a caller could push newlines into every log line this request touches. The
     * generated fallback is what guarantees a correlation id is always present, which is the
     * property that makes tracing reliable.
     */
    private static String resolveCorrelationId(HttpServletRequest request) {
        String supplied = request.getHeader("X-Correlation-Id");
        if (supplied != null) {
            String trimmed = supplied.trim();
            if (trimmed.length() <= MAX_CORRELATION_ID_LENGTH
                    && trimmed.matches("[A-Za-z0-9._:-]+")) {
                return trimmed;
            }
            log.debug("Discarding a malformed X-Correlation-Id from the client");
        }
        return java.util.UUID.randomUUID().toString();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public String ipAddress() {
        return ipAddress;
    }

    public String userAgent() {
        return userAgent;
    }

    public String correlationId() {
        return correlationId;
    }
}
