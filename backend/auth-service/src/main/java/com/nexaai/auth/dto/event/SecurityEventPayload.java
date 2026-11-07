package com.nexaai.auth.dto.event;

import java.time.Instant;

public record SecurityEventPayload(
        String eventType,
        String actor,
        String ipAddress,
        String details,
        Instant timestamp
) {}
