package com.nexaai.user.security;

import java.util.List;
import java.util.UUID;

/**
 * Holds the authenticated principal extracted from a validated JWT.
 * Never carries a password, hash, or raw token.
 */
public record AuthenticatedUser(
        UUID userId,
        String email,
        List<String> roles
) {
    public boolean isAdmin() {
        return roles != null && roles.contains("ADMIN");
    }
}
