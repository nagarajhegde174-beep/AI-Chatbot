package com.nexaai.user.security;

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
 * <p>Returns an {@link AuthenticatedCaller} carrying the auth-service user id. Nothing is read
 * from a header, a query parameter or a body for the purpose of deciding who the caller is: the
 * token is the only accepted credential.
 *
 * <p>A bad token results in <em>no authentication</em>, not an exception. A filter that throws
 * on a malformed token turns every junk request into a 500.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER = "Bearer ";

    private final JwtVerifier verifier;

    public JwtAuthenticationFilter(JwtVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String token = readToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = verifier.verify(token);
                List<String> roles = rolesOf(claims);
                var caller = new AuthenticatedCaller(
                        verifier.subjectOf(claims),
                        claims.get("email", String.class),
                        verifier.isAdmin(claims),
                        roles);
                SecurityContextHolder.getContext().setAuthentication(caller);
            } catch (JwtException | IllegalArgumentException e) {
                // The reason goes to DEBUG. The token never does: it is a bearer credential,
                // and a truncated token is still a credential.
                log.debug("Access token rejected: {}", e.getClass().getSimpleName());
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }

    @SuppressWarnings("unchecked")
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

        String cookieName = "nexa_access_token";
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if (cookieName.equals(cookie.getName())
                        && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}