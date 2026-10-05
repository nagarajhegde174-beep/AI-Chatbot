package com.nexaai.user.event;

import java.time.Instant;
import java.util.UUID;

/**
 * The payload of {@code auth.user.registered.v1}.
 *
 * <p><strong>Note what is absent.</strong> No password hash, no password, no secret, no
 * token. auth-service publishes that an account exists and nothing about how it authenticates.
 * That is not politeness: if the hash travelled on this topic, every consumer would hold a
 * credential, and a topic is not a place credentials belong
 * ({@code docs/SERVICE_CONTRACTS.md} §12).
 */
public record UserRegisteredPayload(
        UUID authUserId,
        String email,
        String displayName,
        boolean emailVerified,
        String registeredVia,
        String accountStatus,
        String role) {

    /** The topic this payload arrives on. */
    public static final String TOPIC = "auth.user.registered.v1";

    /** The event type recorded in {@code processed_event}. */
    public static final String TYPE = "auth.user.registered.v1";
}