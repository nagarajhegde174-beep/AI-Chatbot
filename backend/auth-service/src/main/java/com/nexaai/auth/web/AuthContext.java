package com.nexaai.auth.web;

import com.nexaai.auth.exception.AuthExceptions;
import com.nexaai.auth.security.JwtAuthenticationFilter;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Helpers for reading the authenticated principal.
 *
 * <p>Kept in one place so every controller reads the principal the same way. A controller
 * that reads {@code SecurityContextHolder} itself, or casts the principal differently, is a
 * controller that can disagree with the others about what is authenticated.
 */
final class AuthContext {

    private AuthContext() {
    }

    /** The current principal, or null when the request is anonymous. */
    static JwtAuthenticationFilter.AuthPrincipal principal() {
        org.springframework.security.core.Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication.getPrincipal() instanceof JwtAuthenticationFilter.AuthPrincipal p) {
            return p;
        }
        return null;
    }

    static UUID currentUserId() {
        var principal = principal();
        return principal == null ? null : principal.userId();
    }

    /**
     * The current user id, or 401 if there is none.
     *
     * <p>Used by endpoints that are authenticated by the filter chain but still need the
     * identity in the body, so a missing principal is a clear failure rather than a null that
     * becomes a NullPointerException three frames later.
     */
    static UUID requireAuthenticated() {
        var principal = principal();
        if (principal == null) {
            throw new AuthExceptions.AuthException(
                    "AUTH_UNAUTHENTICATED", "Authentication is required.") {
                @Override
                public synchronized Throwable fillInStackTrace() {
                    // No stack trace: this is an expected control-flow path, and building one
                    // on every unauthenticated request is pure overhead.
                    return this;
                }
            };
        }
        return principal.userId();
    }
}