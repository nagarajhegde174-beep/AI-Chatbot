package com.nexaai.gateway.filter;

// Spring Boot 4 moved this out of spring-boot-autoconfigure and into the webflux module.
// The old path is still what most documentation and Stack Overflow answers show.
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Returns the gateway's own errors in the same shape as every service's errors.
 *
 * <p>Without this, a request that never reaches an upstream — an unroutable path, a malformed
 * URL — comes back as the framework's default HTML error page. A client parsing JSON then has
 * to handle two error formats depending on which layer failed, and a caller cannot tell an
 * edge rejection from an application error.
 *
 * <p>Messages are generic on purpose. An unroutable path reveals which prefixes exist, which is
 * reconnaissance; the correlation id is what lets an operator find the detail in the log.
 */
@Component
@Order(-1)
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorHandler.class);

    /**
     * {@inheritDoc}
     *
     * <p>Logs the cause before writing a body. An error handler that replaces a failure with
     * a tidy JSON response and says nothing about the exception underneath makes the platform
     * undebuggable: the response is fine, so nothing looks wrong, and the real error exists
     * only in the one place that is not logging it.
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable throwable) {
        if (exchange.getResponse().isCommitted()) {
            // Too late to change the status; the response has already begun. Passing through is
            // the only honest option.
            return Mono.error(throwable);
        }

        HttpStatus status = resolveStatus(throwable);

        log.error("Gateway error on {} {} (trace {}): {}",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath().value(),
                // Object-typed first: getAttribute is <T> T, so passing it straight into a
                // String-typed argument lets javac infer the wrong type.
                attributeAsString(exchange, CorrelationIdGlobalFilter.ATTRIBUTE),
                throwable.toString(),
                throwable);
        String code = switch (status) {
            case NOT_FOUND -> "NOT_FOUND";
            case BAD_REQUEST -> "BAD_REQUEST";
            // BAD_GATEWAY, not GATEWAY_BAD_GATEWAY: the latter was the older Spring spelling and no
            // longer exists on HttpStatus.
            case GATEWAY_TIMEOUT, BAD_GATEWAY -> "UPSTREAM_UNAVAILABLE";
            case METHOD_NOT_ALLOWED -> "METHOD_NOT_ALLOWED";
            case PAYLOAD_TOO_LARGE -> "PAYLOAD_TOO_LARGE";
            default -> "GATEWAY_ERROR";
        };

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // Read defensively. An error handler that itself throws replaces a useful failure with a
        // useless one, and the operator then debugs the handler instead of the request.
        String correlationId = correlationIdOf(exchange);

        String body = """
                {"status":%d,"code":"%s","message":"%s","traceId":"%s"}"""
                .formatted(status.value(), code, messageFor(status), correlationId);

        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * The correlation id, or a generated one if the filter never ran.
     *
     * <p>An error thrown before the correlation filter (a malformed request line, say) reaches
     * this handler with no id on the exchange. Emitting a fresh one keeps the error body
     * well-formed rather than carrying a literal "null" the caller would have to special-case.
     */
    private static String correlationIdOf(ServerWebExchange exchange) {
        Object id = exchange.getAttributes() == null
                ? null
                : exchange.getAttributes().get(CorrelationIdGlobalFilter.ATTRIBUTE);
        return id == null ? java.util.UUID.randomUUID().toString() : id.toString();
    }

    /** Reads an exchange attribute without letting generic inference pick the type. */
    private static String attributeAsString(ServerWebExchange exchange, String name) {
        Object value = exchange.getAttribute(name);
        return value == null ? "none" : value.toString();
    }

    private HttpStatus resolveStatus(Throwable throwable) {
        if (throwable instanceof NotFoundException) {
            return HttpStatus.NOT_FOUND;
        }
        if (throwable instanceof ResponseStatusException rse) {
            return HttpStatus.valueOf(rse.getStatusCode().value());
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * A generic message per status.
     *
     * <p>Deliberately says nothing about upstreams. Naming the service that failed turns an
     * error response into a map of the internal topology.
     */
    private String messageFor(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "No route matches this request.";
            case BAD_REQUEST -> "The request could not be understood.";
            case METHOD_NOT_ALLOWED -> "That method is not allowed on this route.";
            case PAYLOAD_TOO_LARGE -> "The request body is larger than this service accepts.";
            case GATEWAY_TIMEOUT -> "The upstream service did not respond in time.";
            case BAD_GATEWAY -> "The upstream service could not be reached.";
            default -> "The gateway could not complete the request.";
        };
    }
}