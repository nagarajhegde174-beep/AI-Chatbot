package com.nexaai.gateway.filter;

import com.nexaai.gateway.util.GatewayResponseUtil;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Denies external access to internal service endpoints (/internal/**) and management endpoints (/actuator/**),
 * with the sole exception of /actuator/health for load balancer orchestration.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class BlockInternalPathsWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Allow actuator health check
        if ("/actuator/health".equals(path) || "/actuator/health/".equals(path)) {
            return chain.filter(exchange);
        }

        // Allow gateway info boundary endpoint when accessed on gateway port
        if ("/internal/v1/gateway/info".equals(path)) {
            return chain.filter(exchange);
        }

        // Block all other /internal/** and /actuator/** paths
        if (path.startsWith("/internal/") || path.startsWith("/actuator/")) {
            String correlationId = exchange.getAttribute(CorrelationIdWebFilter.CORRELATION_ID_ATTR);
            return GatewayResponseUtil.writeErrorResponse(
                    exchange,
                    HttpStatus.FORBIDDEN,
                    "GATEWAY_FORBIDDEN",
                    "Access to internal or administrative endpoints is denied.",
                    correlationId
            );
        }

        return chain.filter(exchange);
    }
}
