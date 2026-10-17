package com.nexaai.auth.dto.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserRegisteredPayload(
        UUID userId,
        String email,
        String displayName,
        List<String> roles,
        String status,
        Instant createdAt
) {}
