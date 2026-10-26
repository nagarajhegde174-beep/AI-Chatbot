package com.nexaai.chat.dto.event;

import java.time.Instant;
import java.util.UUID;

public record AiRequestedPayload(
        String requestId,
        UUID conversationId,
        UUID userId,
        String provider,
        String model,
        int inputTokensEstimated,
        Instant requestedAt
) {}
