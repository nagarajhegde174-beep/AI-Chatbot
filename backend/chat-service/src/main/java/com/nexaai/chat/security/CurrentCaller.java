package com.nexaai.chat.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;

/**
 * Reads the authenticated caller from the security context.
 *
 * <p><strong>This is the single source of "who is asking".</strong> No controller accepts a user
 * id to mean "my conversations", and no service method takes a caller id from anywhere but here.
 * Accepting an id from the request is precisely how "my conversations" becomes "everyone's
 * conversations".
 */
public final class CurrentCaller {

    private CurrentCaller() {
    }

    /**
     * @throws AccessDeniedException if there is no authenticated caller, which means the security
     *                              configuration let an unauthenticated request through
     */
    public static AuthenticatedCaller require() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof AuthenticatedCaller caller && caller.isAuthenticated()) {
            return caller;
        }
        throw new AccessDeniedException("No authenticated caller on this request.");
    }

    /** The auth-service user id of the caller. Every conversation query is scoped by this. */
    public static UUID authUserId() {
        return require().authUserId();
    }

    public static boolean isAdmin() {
        return require().isAdmin();
    }
}