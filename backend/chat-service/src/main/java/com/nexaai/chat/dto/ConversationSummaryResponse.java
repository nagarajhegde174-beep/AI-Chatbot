package com.nexaai.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationSummaryResponse(
        UUID id,
        UUID userId,
        String title,
        String model,
        boolean isArchived,
        Instant createdAt,
        Instant updatedAt,
        Instant lastMessageAt,
        long messageCount
) {
}
