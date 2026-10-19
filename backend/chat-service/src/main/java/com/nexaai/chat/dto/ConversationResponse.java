package com.nexaai.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        UUID userId,
        String title,
        String model,
        String systemPrompt,
        boolean ragEnabled,
        boolean isArchived,
        Instant createdAt,
        Instant updatedAt,
        Instant lastMessageAt,
        long messageCount
) {
}
