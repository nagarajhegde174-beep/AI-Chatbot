package com.nexaai.chat.web.dto;

import com.nexaai.chat.domain.ConversationStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * A conversation as an administrator sees it.
 *
 * <p><strong>Contains no message content and no feedback comments, by design.</strong>
 *
 * <p>It does include {@code ownerAuthUserId}, because knowing who owns a conversation is the
 * point of an administrative listing, and an owner id is an opaque identifier rather than
 * personal data an operator can read a name or an address from.
 *
 * <p>The absence of message text is the deliberate restriction described in
 * {@code AdminChatService}: moderation needs to know a conversation exists, who owns it and when,
 * not to read it. Where an operator genuinely must read content, that is a separate audited
 * feature and not this response.
 */
public record AdminConversationSummary(
        UUID id,
        UUID ownerAuthUserId,
        String title,
        ConversationStatus status,
        int messageCount,
        Instant lastMessageAt,
        Instant createdAt) {
}