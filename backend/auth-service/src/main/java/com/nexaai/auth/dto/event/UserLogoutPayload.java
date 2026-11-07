package com.nexaai.auth.dto.event;

import java.time.Instant;
import java.util.UUID;

public record UserLogoutPayload(
        UUID userId,
        String email,
        Instant logoutTime,
        boolean allSessions
) {}
