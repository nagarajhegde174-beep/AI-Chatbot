package com.nexaai.gateway.filter;

import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Assigns every request a correlation id, forwards the sanitised one, and echoes it back.
 *
 * <p>An inbound {@code X-Correlation-Id} is honoured so one trace spans every service;
 * otherwise one is generated. The id is put on the exchange so the gateway's own error bodies
 * carry it, and echoed in the response so a caller can quote it in a support ticket.
 *
 * <p><strong>Accepted only if it parses as a UUID.</strong> An arbitrary string from an
 * untrusted caller would otherwise be written straight into logs and error bodies, which is log
 * injection: newlines let a caller forge log entries, and a long string is a cheap way to fill
 * a log disk.
 *
 * <p><strong>The request header is rewritten, not merely validated.</strong> Sanitising the
 * response but leaving the original on the request being forwarded would still deliver the
 * attacker's value to every upstream — so the injection lands in the downstream services' logs,
 * which is where most of them are. The filter has to fix the request it passes on, not just
 * the one it received.
 */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String HEADER = "X-Correlation-Id";

    public static final String ATTRIBUTE = "nexa.correlationId";

    /** Ahead of everything else, so the id exists for the authenticator's error bodies. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = sanitize(exchange.getRequest().getHeaders().getFirst(HEADER));

        exchange.getAttributes().put(ATTRIBUTE, correlationId);
        exchange.getResponse().getHeaders().set(HEADER, correlationId);

        // header(), not addHeader(): a caller that sent the header twice must not end up with
        // two values reaching an upstream, where the joined result would be ambiguous.
        ServerHttpRequest sanitized = exchange.getRequest().mutate()
                .header(HEADER, correlationId)
                .build();

        return chain.filter(exchange.mutate().request(sanitized).build());
    }

    /**
     * Keeps a supplied id only if it is a UUID; otherwise generates one.
     *
     * <p>A UUID is the right shape to accept: fixed length, fixed character set, no
     * whitespace. Anything looser is either forgeable-looking to a human reading a log or
     * capable of breaking a log parser.
     */
    private static String sanitize(String candidate) {
        if (candidate != null) {
            try {
                return UUID.fromString(candidate.trim()).toString();
            } catch (IllegalArgumentException ignored) {
                // Not a UUID: generate one rather than trusting it.
            }
        }
        return UUID.randomUUID().toString();
    }
}