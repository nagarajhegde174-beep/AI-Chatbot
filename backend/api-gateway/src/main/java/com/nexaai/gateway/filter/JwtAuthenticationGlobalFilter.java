package com.nexaai.gateway.filter;

import com.nexaai.gateway.config.GatewayProperties;
import com.nexaai.gateway.security.GatewayJwtVerifier;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Authenticates the request at the edge.
 *
 * <p>Verifies the access token and, on success, replaces the token with identity headers the
 * upstream can trust: {@code X-User-Id}, {@code X-User-Email}, {@code X-User-Roles}. The token
 * itself is <strong>removed</strong> before forwarding, so no upstream ever handles a bearer
 * credential it does not need — a compromised upstream then cannot replay the caller's token
 * against other upstreams.
 *
 * <p><strong>This is edge policy, not business logic.</strong> The filter decides only whether
 * a caller is authenticated. Whether they are allowed to <em>do</em> something is the upstream
 * service's judgement, and re-implementing it here would create a second place for it to drift.
 * That is why an ADMIN token can reach {@code /api/users/...} here and still be refused by
 * user-service if the path is not administrative.
 *
 * <p>Public paths bypass verification. The list is explicit configuration rather than a
 * pattern such as "anything containing login", because a heuristic here is a way to accidentally
 * expose an endpoint that ought to need a credential.
 */
@Component
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationGlobalFilter.class);

    /** Header carrying the caller's stable auth-service id, set only from a verified token. */
    public static final String HEADER_USER_ID = "X-User-Id";

    public static final String HEADER_USER_EMAIL = "X-User-Email";
    public static final String HEADER_USER_ROLES = "X-User-Roles";

    /** Exchange attribute holding the authenticated user id, for access logging. */
    public static final String AUTHENTICATED_USER_ID = "nexa.authenticatedUserId";

    private final GatewayJwtVerifier verifier;
    private final List<String> publicPaths;

    public JwtAuthenticationGlobalFilter(GatewayJwtVerifier verifier, GatewayProperties properties) {
        this.verifier = verifier;
        this.publicPaths = properties.getJwt().getPublicPaths();
    }

    /** Immediately after {@link HeaderSanitizingGlobalFilter}, so it sees the stripped request. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        if (isPublic(path)) {
            return chain.filter(exchange);
        }

        String token = readToken(request);
        if (token == null) {
            return reject(exchange, HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED", "Authentication is required for this endpoint.");
        }

        try {
            Claims claims = verifier.verify(token);
            UUID userId = verifier.subjectOf(claims);
            List<String> roles = verifier.rolesOf(claims);
            String email = verifier.emailOf(claims);

            ServerHttpRequest.Builder mutated = request.mutate()
                    // The credential stops here. Upstreams read the identity headers, and
                    // forwarding the token would hand every one of them a replayable secret.
                    .headers(headers -> headers.remove(HttpHeaders.AUTHORIZATION))
                    .header(HEADER_USER_ID, userId.toString())
                    .header(HEADER_USER_ROLES, String.join(",", roles));
            if (email != null) {
                mutated.header(HEADER_USER_EMAIL, email);
            }

            // The authenticated user id, for access logging. This is a private attribute; the correlation
            // id is the one shared with the caller.
            exchange.getAttributes().put(AUTHENTICATED_USER_ID, userId.toString());
            return chain.filter(exchange.mutate().request(mutated.build()).build());

        } catch (JwtException | IllegalArgumentException e) {
            // Logged at DEBUG with the exception TYPE only. The reason goes nowhere near the
            // token: a truncated token is still a credential, and error bodies get logged by
            // proxies and browser consoles.
            log.debug("Rejected access token at the edge: {}", e.getClass().getSimpleName());
            return reject(exchange, HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED", "The access token is missing, invalid or expired.");
        }
    }

    private boolean isPublic(String path) {
        return publicPaths.stream().anyMatch(pattern -> matches(pattern, path));
    }

    /**
     * Matches a configured pattern against a path.
     *
     * <p>Supports a trailing {@code /**} and an exact path. Hand-rolled rather than pulled in
     * as a dependency: the pattern language here is three tokens, and a general matcher makes
     * it easy to write a rule that matches more than its author thinks.
     */
    private static boolean matches(String pattern, String path) {
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return path.equals(prefix) || path.startsWith(prefix + "/");
        }
        return pattern.equals(path);
    }

    private static String readToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String value = header.substring(7).trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        // The access cookie, so a browser SPA works without JavaScript-held tokens. A
        // service-to-service caller sets the header and cannot be tricked into sending a
        // cookie, which is why the header is checked first.
        var cookie = request.getCookies().getFirst("nexa_access_token");
        if (cookie != null && !cookie.getValue().isBlank()) {
            return cookie.getValue();
        }
        return null;
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status,
                              String code, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // Assigned to Object first, deliberately.
//
// ServerWebExchange.getAttribute is declared <T> T getAttribute(String). Passing its result
// straight into String.valueOf makes javac infer T = char[], because valueOf(char[]) is a more
// specific overload than valueOf(Object). That compiles cleanly and then throws
// ClassCastException at runtime, because the attribute is really a String.
//
// The symptom is a 500 on every rejection path, which is the worst possible place for it: the
// gateway returns 500 instead of 401 precisely when something has gone wrong, so a monitoring
// alert says "server error" instead of "unauthorised".
        Object attribute = exchange.getAttribute(CorrelationIdGlobalFilter.ATTRIBUTE);
        String correlationId = attribute == null ? "unknown" : attribute.toString();

        byte[] body = ("{\"status\":%d,\"code\":\"%s\",\"message\":\"%s\",\"traceId\":\"%s\"}"
                .formatted(status.value(), code, message, correlationId))
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }
}