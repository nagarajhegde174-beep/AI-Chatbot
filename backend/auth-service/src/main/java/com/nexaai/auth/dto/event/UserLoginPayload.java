package com.nexaai.auth.dto.event;

import java.time.Instant;
import java.util.UUID;

public record UserLoginPayload(
        UUID userId,
        String email,
        Instant loginTime,
        String ipAddress,
        String userAgent
) {}
