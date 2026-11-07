package com.nexaai.auth.dto.event;

import java.time.Instant;
import java.util.UUID;

public record PasswordChangedPayload(
        UUID userId,
        String email,
        Instant changedAt
) {}
