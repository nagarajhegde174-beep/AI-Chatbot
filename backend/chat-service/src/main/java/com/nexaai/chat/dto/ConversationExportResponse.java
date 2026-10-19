package com.nexaai.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationExportResponse(
        UUID conversationId,
        String title,
        String format,
        Instant exportedAt,
        String content
) {
}
