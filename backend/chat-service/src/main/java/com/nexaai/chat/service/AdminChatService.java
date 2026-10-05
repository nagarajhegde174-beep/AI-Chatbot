package com.nexaai.chat.service;

import com.nexaai.chat.domain.ChatConversation;
import com.nexaai.chat.domain.ConversationStatus;
import com.nexaai.chat.exception.ChatResourceNotFoundException;
import com.nexaai.chat.repository.ChatConversationRepository;
import com.nexaai.chat.repository.MessageFeedbackRepository;
import com.nexaai.chat.web.dto.AdminConversationSummary;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrative access to conversations.
 *
 * <p><strong>The documented rule this class implements: an ADMIN sees metadata, never
 * content.</strong>
 *
 * <p>An administrator can list and search across all users, and can see who owns a conversation,
 * when it was created, how many messages it has and whether it is archived. An administrator
 * cannot read the messages, and cannot export a conversation.
 *
 * <p>The reasoning is that message content is the user's private data, and an unbounded ability
 * to read it is a surveillance capability that no dashboard needs. Moderation needs to know a
 * conversation <em>exists</em>, who owns it and when it happened. Where an operator genuinely
 * must read content, that needs an audited, time-boxed support-access feature with a recorded
 * reason, which is not this route and is not built in this phase.
 *
 * <p>The consequence, stated plainly so it reads as a decision rather than an oversight:
 * <strong>this service provides no administrative route to message content at all.</strong>
 */
@Service
@Transactional(readOnly = true)
public class AdminChatService {

    private final ChatConversationRepository conversations;
    private final MessageFeedbackRepository feedback;

    public AdminChatService(ChatConversationRepository conversations,
                            MessageFeedbackRepository feedback) {
        this.conversations = conversations;
        this.feedback = feedback;
    }

    /**
     * Lists conversations across all owners.
     *
     * @param ownerAuthUserId optional filter to a single owner
     */
    public Page<AdminConversationSummary> listAll(UUID ownerAuthUserId, ConversationStatus status,
                                                  String search, int page, int size,
                                                  int maxPageSize) {
        int effectiveSize = Math.clamp(size, 1, maxPageSize);
        String pattern = (search == null || search.isBlank())
                ? null
                : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";

        return conversations.searchAll(ownerAuthUserId, status, pattern,
                        PageRequest.of(Math.max(page, 0), effectiveSize,
                                Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(this::toSummary);
    }

    /** Aggregate counts for an administrative dashboard. */
    public AdminCounts counts() {
        return new AdminCounts(
                conversations.countNotDeleted(),
                conversations.countByStatus(ConversationStatus.ACTIVE),
                conversations.countByStatus(ConversationStatus.ARCHIVED));
    }

    /**
     * One conversation's metadata.
     *
     * <p>Not ownership-filtered: an administrator is allowed to know a conversation exists. That
     * is the whole of what this returns.
     */
    public AdminConversationSummary metadata(UUID conversationId) {
        ChatConversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new ChatResourceNotFoundException(
                        ChatResourceNotFoundException.Kind.CONVERSATION, conversationId));
        return toSummary(conversation);
    }

    /**
     * The rating distribution across all feedback.
     *
     * <p>Counts only, never comments. A comment is free text a user wrote, and surfacing it to an
     * operator is a different decision from counting thumbs.
     */
    public Map<String, Long> feedbackTotals() {
        return feedback.countByRating();
    }

    private AdminConversationSummary toSummary(ChatConversation c) {
        return new AdminConversationSummary(
                c.getId(),
                c.getOwnerAuthUserId(),
                c.getTitle(),
                c.getStatus(),
                c.getMessageCount(),
                c.getLastMessageAt(),
                c.getCreatedAt());
    }

    /** Aggregate counts. */
    public record AdminCounts(long total, long active, long archived) {
    }
}