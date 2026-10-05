package com.nexaai.chat.client;

import java.util.UUID;

/**
 * Chat Service's view of user information.
 *
 * <p>Exists so Chat Service never grows a compile-time dependency on an unimplemented service,
 * and so the boundary to User Service is one interface rather than HTTP calls scattered through
 * the code.
 */
public interface UserDirectory {

    /** Whether the outbound call is configured and attempted at all. */
    boolean isEnabled();

    /** Looks up a user by the auth-service id carried in the verified token. */
    UserSummary findByAuthUserId(UUID authUserId);

    /**
     * The little this service needs to know about a user.
     *
     * @param available false when User Service could not be reached. Present as a field rather
     *                  than an exception so one unreachable service degrades a page instead of
     *                  failing it.
     */
    record UserSummary(
            boolean available,
            String displayName,
            String planName,
            String accountStatus) {

        public static UserSummary unavailable() {
            return new UserSummary(false, null, null, null);
        }
    }
}