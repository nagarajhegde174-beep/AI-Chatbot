package com.nexaai.auth.domain;

/**
 * The two roles NexaAI has. Exactly two.
 *
 * <p>{@code SUPER_ADMIN}, {@code MODERATOR}, {@code SUPPORT} and every other tier are
 * prohibited by {@code docs/RULES.md} section 8. The enum exists so that adding a role is
 * a compile error rather than a string that slips through validation, and the database
 * constrains the same set with a CHECK.
 */
public enum Role {
    /** Standard registered user. */
    USER,
    /** Platform administrator. */
    ADMIN
}