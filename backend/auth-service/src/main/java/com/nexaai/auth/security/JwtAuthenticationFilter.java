package com.nexaai.auth.security;

import com.nexaai.auth.domain.AccountStatus;
import com.nexaai.auth.domain.AuthUser;
import com.nexaai.auth.domain.Role;
import com.nexaai.auth.repository.AuthUserRepository;
import com.nexaai.auth.repository.RevokedTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates a request from its access token.
 *
 * <p><strong>Why the account is re-read on every request.</strong> A JWT is self-contained,
 * so verification alone would happily accept a token belonging to an account suspended a
 * second ago. Re-reading the account is what makes suspension take effect immediately rather
 * than after the 15-minute token lifetime. It costs one indexed primary-key read per request,
 * which is the price of not needing a revocation list on every service
 * ({@code docs/SECURITY.md} section 2.2).
 *
 * <p><strong>What is checked.</strong> Signature and algorithm, expiry, issuer, audience,
 * revocation, {@code credentialsVersion} against the current account, and account status.
 * Any failure results in no authentication rather than an exception, because a filter that
 * throws on a bad token turns every malformed request into a 500.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final TokenCookieService cookieService;
    private final AuthUserRepository userRepository;
    private final RevokedTokenRepository revokedTokenRepository;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   TokenCookieService cookieService,
                                   AuthUserRepository userRepository,
                                   RevokedTokenRepository revokedTokenRepository) {
        this.jwtService = jwtService;
        this.cookieService = cookieService;
        this.userRepository = userRepository;
        this.revokedTokenRepository = revokedTokenRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        var token = cookieService.readAccessToken(request);

        if (token.isPresent()) {
            try {
                authenticate(token.get(), request);
            } catch (JwtException | IllegalArgumentException e) {
                // Deliberately quiet about the reason at INFO. The detail goes to DEBUG, and a
                // token is never logged at any level: it is a bearer credential.
                log.debug("Access token rejected: {}", e.getClass().getSimpleName());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        Claims claims = jwtService.verifyAccessToken(token);

        // Revoked-token check before anything else. A revoked token must not authenticate
        // even if every other claim is valid.
        String jti = claims.getId();
        if (jti != null && revokedTokenRepository.existsByJti(jti)) {
            log.debug("Access token is revoked");
            return;
        }

        UUID userId = UUID.fromString(claims.getSubject());
        AuthUser user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            // A valid token for a deleted account. The signature was fine; the subject is not
            // a live user.
            log.debug("Access token names user {} who no longer exists", userId);
            return;
        }

        // A password change bumps credentialsVersion, so tokens issued before it stop working.
        Integer tokenVersion = claims.get("cv", Integer.class);
        if (tokenVersion == null || tokenVersion != user.getCredentialsVersion()) {
            log.debug("Access token predates a credential change for user {}", userId);
            return;
        }

        // Status is checked from the database rather than the token, so suspension is immediate.
        if (user.getStatus() != AccountStatus.ACTIVE) {
            log.debug("Account {} is {}, refusing authentication", userId, user.getStatus());
            return;
        }

        List<GrantedAuthority> authorities = buildAuthorities(user);
        var authentication = new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(user.getId(), user.getEmail(), user.getRole(), user.getStatus()),
                null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * Builds authorities from the role.
     *
     * <p>Two forms are granted: {@code ROLE_USER} for Spring Security's role checks and
     * {@code ROLE_user} for route-level {@code hasRole(...)} expressions.
     */
    private List<GrantedAuthority> buildAuthorities(AuthUser user) {
        String role = user.getRole().name();
        return List.of(
                new SimpleGrantedAuthority("ROLE_" + role),
                new SimpleGrantedAuthority("SCOPE_" + role.toLowerCase(java.util.Locale.ROOT)));
    }

    /**
     * The authenticated principal.
     *
     * <p>Held in memory for the duration of the request. It is an immutable snapshot: it does
     * not expose the password hash and cannot be used to mutate the account.
     */
    public record AuthPrincipal(UUID userId, String email, Role role, AccountStatus status) {
        public boolean isAdmin() {
            return role == Role.ADMIN;
        }
    }
}