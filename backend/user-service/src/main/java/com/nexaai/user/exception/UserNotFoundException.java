package com.nexaai.user.exception;

import java.util.UUID;

/**
 * Thrown when a profile that was expected to exist does not.
 *
 * <p>Carries the id that was asked for. Deliberately does <em>not</em> distinguish "no such
 * user" from "not yours" for the self-service path: a user probing ids should not be able to
 * learn which ids are real. The controller returns 404 for both.
 */
public class UserNotFoundException extends RuntimeException {

    private final UUID requestedId;

    public UserNotFoundException(UUID requestedId) {
        super("No user profile for auth user id " + requestedId);
        this.requestedId = requestedId;
    }

    public UUID getRequestedId() {
        return requestedId;
    }
}