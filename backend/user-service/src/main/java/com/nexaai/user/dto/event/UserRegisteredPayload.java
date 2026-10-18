package com.nexaai.user.dto.event;

import java.time.Instant;

/**
 * Payload of auth.user.registered.v1.
 * Produced by auth-service, consumed here to create a user profile.
 */
public record UserRegisteredPayload(
        String userId,
        String email,
        String displayName,
        Instant registeredAt
) {}
