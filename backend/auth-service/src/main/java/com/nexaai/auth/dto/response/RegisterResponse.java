package com.nexaai.auth.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RegisterResponse(
        UUID userId,
        String email,
        String displayName,
        List<String> roles,
        String status,
        Instant createdAt
) {}
