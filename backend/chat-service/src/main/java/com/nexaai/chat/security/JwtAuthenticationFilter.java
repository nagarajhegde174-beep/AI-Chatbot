package com.nexaai.chat.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates a request from its access token.
 *
 * <p>The token is the only accepted credential. Nothing is read from a header, a query parameter
 * or a body for the purpose of deciding who the caller is.
 *
 * <p>A bad token results in <em>no authentication</em>, not an exception. A filter that throws on
 * a malformed token turns every junk request into a 500.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER = "Bearer ";
    private static final String ACCESS_COOKIE = "nexa_access_token";

    private final JwtVerifier verifier;
    private final CallTokenHolder callToken;

    public JwtAuthenticationFilter(JwtVerifier verifier, CallTokenHolder callToken) {
        this.verifier = verifier;
        this.callToken = callToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String token = readToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = verifier.verify(token);
                var caller = new AuthenticatedCaller(
                        verifier.subjectOf(claims),
                        claims.get("email", String.class),
                        verifier.isAdmin(claims),
                        rolesOf(claims));
                SecurityContextHolder.getContext().setAuthentication(caller);
                // Held only for this request, only so the outbound AI Service client can relay
                // it. Never on the Authentication, which travels far more widely.
                callToken.set(token);
            } catch (JwtException | IllegalArgumentException e) {
                // The reason goes to DEBUG by type only. The token never does: it is a bearer
                // credential, and a truncated token is still a credential.
                log.debug("Access token rejected: {}", e.getClass().getSimpleName());
                SecurityContextHolder.clearContext();
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            // Cleared in a finally, not on the success path: an exception mid-request would
            // otherwise leave a token attached to a pooled thread for the next request on it.
            // Tomcat reuses threads, so "attached to the thread" and "attached to the next
            // user's request" are the same thing.
            callToken.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private List<String> rolesOf(Claims claims) {
        Object roles = claims.get("roles");
        if (roles instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    /**
     * Reads the token from the {@code Authorization} header, then from the access cookie.
     *
     * <p>The header comes first because a service-to-service caller sets one and cannot be
     * tricked into sending a cookie.
     */
    private String readToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER)) {
            String value = header.substring(BEARER.length()).trim();
            if (!value.isEmpty()) {
                return value;
            }
        }

        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if (ACCESS_COOKIE.equals(cookie.getName())
                        && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}