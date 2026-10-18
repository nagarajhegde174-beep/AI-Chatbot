package com.nexaai.gateway.filter;

import com.nexaai.gateway.security.JwtVerifier;
import com.nexaai.gateway.util.GatewayResponseUtil;
import io.jsonwebtoken.Claims;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

/**
 * Validates RS256 JWT access tokens on protected routes before routing downstream.
 * On success, propagates X-User-Id and X-User-Roles headers to downstream services.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class JwtAuthenticationWebFilter implements WebFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private static final Set<String> PUBLIC_EXACT_PATHS = Set.of(
            "/healthz",
            "/actuator/health",
            "/internal/v1/gateway/info",
            "/.well-known/jwks.json",
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/password/forgot",
            "/api/v1/auth/password/reset",
            "/api/v1/auth/email/verify"
    );

    private final JwtVerifier jwtVerifier;

    public JwtAuthenticationWebFilter(JwtVerifier jwtVerifier) {
        this.jwtVerifier = jwtVerifier;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String correlationId = exchange.getAttribute(CorrelationIdWebFilter.CORRELATION_ID_ATTR);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return GatewayResponseUtil.writeErrorResponse(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    "GATEWAY_UNAUTHORIZED",
                    "Missing or invalid Authorization header.",
                    correlationId
            );
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        try {
            Claims claims = jwtVerifier.validateAndGetClaims(token);
            String userId = jwtVerifier.getUserId(claims);
            List<String> roles = jwtVerifier.getRoles(claims);

            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", userId != null ? userId : "")
                    .header("X-User-Roles", String.join(",", roles))
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (JwtVerifier.JwtAccountSuspendedException e) {
            return GatewayResponseUtil.writeErrorResponse(
                    exchange,
                    HttpStatus.FORBIDDEN,
                    "GATEWAY_ACCOUNT_SUSPENDED",
                    e.getMessage(),
                    correlationId
            );
        } catch (JwtVerifier.JwtVerificationException e) {
            return GatewayResponseUtil.writeErrorResponse(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    e.getCode(),
                    e.getMessage(),
                    correlationId
            );
        } catch (Exception e) {
            return GatewayResponseUtil.writeErrorResponse(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    "GATEWAY_INVALID_TOKEN",
                    "Invalid or unparseable authorization token.",
                    correlationId
            );
        }
    }

    private boolean isPublicPath(String path) {
        if (PUBLIC_EXACT_PATHS.contains(path)) {
            return true;
        }
        return path.startsWith("/api/v1/auth/password/")
                || path.startsWith("/api/v1/auth/email/");
    }
}
