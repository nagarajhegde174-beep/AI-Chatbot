package com.nexaai.chat.dto.event;

import java.time.Instant;
import java.util.UUID;

public record ConversationCreatedPayload(
        UUID conversationId,
        UUID userId,
        String title,
        String model,
        Boolean ragEnabled,
        Instant createdAt
) {}
