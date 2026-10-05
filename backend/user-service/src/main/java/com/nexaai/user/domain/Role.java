package com.nexaai.user.domain;

/**
 * The two roles NexaAI has. Exactly two.
 *
 * <p>Duplicated from auth-service rather than shared: a shared enum would be a shared business
 * module, which is prohibited ({@code docs/RULES.md} §2). Both services constrain the same two
 * values in the database.
 */
public enum Role {
    /** Standard registered user. */
    USER,
    /** Platform administrator. */
    ADMIN;

    public boolean isAdmin() {
        return this == ADMIN;
    }
}