package com.nexaai.gateway.filter;

import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Removes inbound headers that claim to describe the caller, before anything can read them.
 *
 * <p><strong>This filter exists because of one specific attack.</strong> The gateway passes
 * verified identity to downstream services as headers — {@code X-User-Id} and friends. Those
 * headers are only trustworthy if they arrive <em>from the gateway</em>. A client that can send
 * its own {@code X-User-Id: <someone else>} turns every identity check downstream into a
 * suggestion, and no amount of correct logic in user-service helps.
 *
 * <p>So: strip first, then set verified values. Never the other way round. Stripping is
 * case-insensitive because HTTP header names are, and a filter that only removed the exact
 * casing it set would be defeated by {@code x-user-id}.
 *
 * <p>Runs at the highest precedence so nothing else in this application — including the
 * authentication filter — ever observes the attacker's version.
 */
@Component
public class HeaderSanitizingGlobalFilter implements GlobalFilter, Ordered {

    /**
     * Headers that carry caller identity and are therefore never trusted from a client.
     *
     * <p>{@code X-Forwarded-*} is included beyond the obvious cases. A client that sets
     * {@code X-Forwarded-For} or {@code X-Forwarded-Proto} is attempting to spoof the address
     * an upstream believes it is talking to; Spring Cloud Gateway's own forwarding headers
     * must be computed here, not accepted.
     */
    private static final List<String> SPOOFABLE_HEADERS = List.of(
            "x-user-id",
            "x-user-email",
            "x-user-roles",
            "x-user-name",
            "x-authenticated",
            "x-forwarded-for",
            "x-forwarded-host",
            "x-forwarded-proto",
            "x-forwarded-port",
            "x-real-ip");

    /**
     * Ahead of the authentication filter.
     *
     * <p>{@code Ordered.HIGHEST_PRECEDENCE + 10} rather than {@code HIGHEST_PRECEDENCE}, so an
     * equally-hungrier filter could still be ordered ahead if one were ever needed, and the
     * relative order of the two is explicit rather than accidental.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest.Builder request = exchange.getRequest().mutate();
        for (String header : SPOOFABLE_HEADERS) {
            request.headers(headers -> headers.remove(header));
        }
        return chain.filter(exchange.mutate().request(request.build()).build());
    }
}