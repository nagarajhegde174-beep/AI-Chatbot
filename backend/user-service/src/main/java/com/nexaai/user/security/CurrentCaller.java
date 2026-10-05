package com.nexaai.user.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Reads the authenticated caller out of the security context.
 *
 * <p><strong>This is the single source of "who is asking".</strong> No controller accepts a
 * user id as a request parameter for its own profile, and no service method takes a caller id
 * from anywhere but here. Accepting an id from the request is how "get my profile" turns into
 * "get anyone's profile".
 */
public final class CurrentCaller {

    private CurrentCaller() {
    }

    /**
     * @throws org.springframework.security.access.AccessDeniedException if there is no
     *                                                                  authenticated caller,
     *                                                                  which means the security
     *                                                                  configuration let an
     *                                                                  unauthenticated request
     *                                                                  through
     */
    public static AuthenticatedCaller require() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof AuthenticatedCaller caller && caller.isAuthenticated()) {
            return caller;
        }
        throw new org.springframework.security.access.AccessDeniedException(
                "No authenticated caller on this request.");
    }

    /** The auth-service user id of the caller. */
    public static UUID authUserId() {
        return require().authUserId();
    }

    /** Whether the caller holds the ADMIN role. */
    public static boolean isAdmin() {
        return require().isAdmin();
    }
}