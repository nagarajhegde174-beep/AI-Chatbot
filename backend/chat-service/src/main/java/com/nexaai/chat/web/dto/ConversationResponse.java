package com.nexaai.chat.web.dto;

import com.nexaai.chat.domain.ChatConversation;
import com.nexaai.chat.domain.ConversationStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * A conversation, as the account holder sees it.
 *
 * <p><strong>The owner id is not included.</strong> The caller already knows who they are, and a
 * field that echoes the caller's own identity back is noise that invites later use as an
 * authorisation input.
 */
public record ConversationResponse(
        UUID id,
        String title,
        String model,
        ConversationStatus status,
        int messageCount,
        Instant lastMessageAt,
        Instant createdAt,
        Instant updatedAt) {

    public static ConversationResponse from(ChatConversation c) {
        return new ConversationResponse(
                c.getId(),
                c.getTitle(),
                c.getModel(),
                c.getStatus(),
                c.getMessageCount(),
                c.getLastMessageAt(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}