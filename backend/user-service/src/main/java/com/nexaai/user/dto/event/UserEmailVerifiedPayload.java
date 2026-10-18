package com.nexaai.user.dto.event;

import java.time.Instant;

/**
 * Payload of auth.user.email_verified.v1.
 * Produced by auth-service, consumed here to mark the profile active.
 */
public record UserEmailVerifiedPayload(
        String userId,
        String email,
        Instant verifiedAt
) {}
