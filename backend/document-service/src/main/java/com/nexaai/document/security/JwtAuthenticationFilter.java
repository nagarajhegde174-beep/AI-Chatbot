package com.nexaai.document.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private final JwtVerifier jwtVerifier;

    public JwtAuthenticationFilter(JwtVerifier jwtVerifier) {
        this.jwtVerifier = jwtVerifier;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // Allow gateway-forwarded headers
        String gatewayUserId = request.getHeader("X-User-Id");
        String gatewayRoles = request.getHeader("X-User-Roles");

        if (gatewayUserId != null && !gatewayUserId.isBlank()) {
            try {
                UUID userId = UUID.fromString(gatewayUserId);
                List<String> roles = gatewayRoles != null && !gatewayRoles.isBlank()
                        ? List.of(gatewayRoles.split(","))
                        : List.of("USER");
                UserPrincipal principal = new UserPrincipal(userId, null, roles);
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (IllegalArgumentException ignored) {
            }
        } else if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();
            try {
                Claims claims = jwtVerifier.validateAndGetClaims(token);
                UUID userId = UUID.fromString(jwtVerifier.getUserId(claims));
                List<String> roles = jwtVerifier.getRoles(claims);
                String email = claims.get("email", String.class);

                UserPrincipal principal = new UserPrincipal(userId, email, roles);
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception ignored) {
                // Let request continue unauthenticated; Spring Security entry point handles refusal
            }
        }

        filterChain.doFilter(request, response);
    }
}
