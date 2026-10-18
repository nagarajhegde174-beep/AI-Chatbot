package com.nexaai.gateway.filter;

import com.nexaai.gateway.config.RateLimiterProperties;
import com.nexaai.gateway.util.GatewayResponseUtil;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory token-window rate limiter per client IP address.
 * Applies strict rate limits to auth routes (10/min) and general API routes (300/min).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimiterWebFilter implements WebFilter {

    private final RateLimiterProperties properties;
    private final Map<String, WindowCounter> authCounters = new ConcurrentHashMap<>();
    private final Map<String, WindowCounter> apiCounters = new ConcurrentHashMap<>();

    public RateLimiterWebFilter(RateLimiterProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getURI().getPath();
        String clientIp = resolveClientIp(exchange);

        if (path.startsWith("/api/v1/auth/")) {
            if (!allowRequest(authCounters, clientIp, properties.getAuthRequestsPerMinute())) {
                String correlationId = exchange.getAttribute(CorrelationIdWebFilter.CORRELATION_ID_ATTR);
                return GatewayResponseUtil.writeErrorResponse(
                        exchange,
                        HttpStatus.TOO_MANY_REQUESTS,
                        "GATEWAY_RATE_LIMIT_EXCEEDED",
                        "Too many requests to authentication endpoints. Please try again later.",
                        correlationId
                );
            }
        } else if (path.startsWith("/api/")) {
            if (!allowRequest(apiCounters, clientIp, properties.getApiRequestsPerMinute())) {
                String correlationId = exchange.getAttribute(CorrelationIdWebFilter.CORRELATION_ID_ATTR);
                return GatewayResponseUtil.writeErrorResponse(
                        exchange,
                        HttpStatus.TOO_MANY_REQUESTS,
                        "GATEWAY_RATE_LIMIT_EXCEEDED",
                        "Rate limit exceeded. Please throttle your requests.",
                        correlationId
                );
            }
        }

        return chain.filter(exchange);
    }

    private boolean allowRequest(Map<String, WindowCounter> map, String key, int maxRequestsPerMinute) {
        long currentMinute = System.currentTimeMillis() / 60000;
        WindowCounter counter = map.compute(key, (k, existing) -> {
            if (existing == null || existing.minute != currentMinute) {
                return new WindowCounter(currentMinute, 1);
            }
            existing.count.incrementAndGet();
            return existing;
        });

        return counter.count.get() <= maxRequestsPerMinute;
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }

    private static class WindowCounter {
        final long minute;
        final AtomicInteger count;

        WindowCounter(long minute, int initialCount) {
            this.minute = minute;
            this.count = new AtomicInteger(initialCount);
        }
    }
}
