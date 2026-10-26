package com.nexaai.chat.dto.event;

import java.time.Instant;
import java.util.UUID;

public record MessageSentPayload(
        UUID messageId,
        UUID conversationId,
        UUID userId,
        String role,
        int sequenceNumber,
        int characterCount,
        Instant createdAt
) {}
