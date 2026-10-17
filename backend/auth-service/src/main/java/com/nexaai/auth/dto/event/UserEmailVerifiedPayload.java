package com.nexaai.auth.dto.event;

import java.time.Instant;
import java.util.UUID;

public record UserEmailVerifiedPayload(
        UUID userId,
        String email,
        String status,
        Instant verifiedAt
) {}
