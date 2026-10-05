package com.nexaai.chat.service;

import com.nexaai.chat.config.ChatProperties;
import com.nexaai.chat.domain.ChatConversation;
import com.nexaai.chat.domain.ConversationStatus;
import com.nexaai.chat.domain.TitleFromFirstMessage;
import com.nexaai.chat.exception.ChatResourceNotFoundException;
import com.nexaai.chat.repository.ChatConversationRepository;
import com.nexaai.chat.web.dto.ConversationResponse;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conversations: the sessions.
 *
 * <p><strong>Every method takes the owning auth user id as its first argument.</strong> There is no
 * {@code getConversation(UUID)} here, because a service method that takes an arbitrary id is one
 * call away from being reachable from a path variable, and that is how one user ends up reading
 * another user's history.
 *
 * <p>Ownership is enforced in the repository query, not by a check after loading. Loading first
 * and comparing in Java is correct only for as long as nobody adds a code path that forgets the
 * comparison.
 */
@Service
@Transactional
public class ConversationService {

    private final ChatConversationRepository conversations;
    private final ChatProperties properties;

    public ConversationService(ChatConversationRepository conversations,
                               ChatProperties properties) {
        this.conversations = conversations;
        this.properties = properties;
    }

    // ------------------------------------------------------------------
    // Creating
    // ------------------------------------------------------------------

    /** Creates an empty conversation owned by the caller. */
    public ChatConversation create(UUID ownerAuthUserId, String title, String model) {
        ChatConversation conversation =
                ChatConversation.create(ownerAuthUserId, title, model);
        return conversations.save(conversation);
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    /**
     * Loads one of the caller's conversations.
     *
     * <p>A conversation owned by someone else is reported identically to one that does not exist,
     * so this cannot be used to discover which ids are real.
     */
    @Transactional(readOnly = true)
    public ChatConversation getOwned(UUID ownerAuthUserId, UUID conversationId) {
        return conversations.findByIdAndOwnerAuthUserId(conversationId, ownerAuthUserId)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ChatResourceNotFoundException(
                        ChatResourceNotFoundException.Kind.CONVERSATION, conversationId));
    }

    /**
     * Lists the caller's conversations, filtered by status and searched by title.
     *
     * <p><strong>With no status filter this excludes ARCHIVED.</strong> Archive exists to take a
     * conversation out of the sidebar without losing it, so an archived conversation appearing in
     * the default listing would defeat the feature. Archived conversations have their own route.
     *
     * <p>The page size is capped at {@code nexa.chat.limits.max-page-size} whatever the caller
     * asked for. An uncapped size parameter is a denial of service dressed as a convenience.
     */
    @Transactional(readOnly = true)
    public Page<ConversationResponse> listOwned(UUID ownerAuthUserId, ConversationStatus status,
                                                 String search, int page, int size) {
        Pageable pageable = pageable(page, size, Sort.by(Sort.Direction.DESC, "lastMessageAt"));
        String pattern = likePattern(search);
        return conversations.searchOwned(ownerAuthUserId, status, pattern, pageable)
                .map(ConversationResponse::from);
    }

    /**
     * The sidebar listing: active conversations only.
     *
     * <p>Named apart from {@code listOwned} so the exclusion of archived rows is visible at the
     * call site. Overloading one method with "null means active" would put that policy in a
     * conditional nobody reads.
     */
    @Transactional(readOnly = true)
    public Page<ConversationResponse> listActive(UUID ownerAuthUserId, String search,
                                                 int page, int size) {
        Pageable pageable = pageable(page, size, Sort.by(Sort.Direction.DESC, "lastMessageAt"));
        return conversations.searchOwned(ownerAuthUserId, ConversationStatus.ACTIVE,
                        likePattern(search), pageable)
                .map(ConversationResponse::from);
    }

    /** The archived listing. */
    @Transactional(readOnly = true)
    public Page<ConversationResponse> listArchived(UUID ownerAuthUserId, int page, int size) {
        Pageable pageable = pageable(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return conversations.searchOwned(ownerAuthUserId, ConversationStatus.ARCHIVED, null,
                        pageable)
                .map(ConversationResponse::from);
    }

    /**
     * Searches the caller's conversations by title <em>or</em> by message content.
     *
     * <p>Searching message text needs a join, so it is a separate query. A title search must not
     * pay for a join across every message the caller has ever sent.
     */
    @Transactional(readOnly = true)
    public Page<ConversationResponse> searchOwned(UUID ownerAuthUserId, String query,
                                                  int page, int size) {
        if (query == null || query.isBlank()) {
            return listOwned(ownerAuthUserId, null, null, page, size);
        }
        Pageable pageable = pageable(page, size, Sort.by(Sort.Direction.DESC, "lastMessageAt"));

        // Through likePattern, not raw concatenation. An unescaped '%' or '_' from the caller
        // becomes a wildcard, so "search for %" returns every conversation and the endpoint
        // silently stops being a filter.
        String pattern = likePattern(query);
        return conversations.searchOwnedByContent(ownerAuthUserId, pattern, pageable)
                .map(ConversationResponse::from);
    }

    /** The caller's conversation count, for the sidebar footer. */
    @Transactional(readOnly = true)
    public long countOwned(UUID ownerAuthUserId) {
        return conversations.countOwned(ownerAuthUserId);
    }

    // ------------------------------------------------------------------
    // Writing
    // ------------------------------------------------------------------

    /** Renames one of the caller's conversations. */
    public ConversationResponse rename(UUID ownerAuthUserId, UUID conversationId, String title) {
        ChatConversation conversation = getOwned(ownerAuthUserId, conversationId);
        conversation.rename(title);
        return ConversationResponse.from(conversations.save(conversation));
    }

    /** Pins or unpins the conversation's model. */
    public ConversationResponse changeModel(UUID ownerAuthUserId, UUID conversationId,
                                            String model) {
        ChatConversation conversation = getOwned(ownerAuthUserId, conversationId);
        conversation.useModel(model);
        return ConversationResponse.from(conversations.save(conversation));
    }

    /**
     * Archives a conversation.
     *
     * <p>Reversible, and separate from delete on purpose: archive is "get this out of my
     * sidebar", delete is "remove my history". Only one of those should be hard.
     */
    public ConversationResponse archive(UUID ownerAuthUserId, UUID conversationId) {
        ChatConversation conversation = getOwned(ownerAuthUserId, conversationId);
        conversation.archive();
        return ConversationResponse.from(conversations.save(conversation));
    }

    /** Returns an archived conversation to the sidebar. */
    public ConversationResponse restore(UUID ownerAuthUserId, UUID conversationId) {
        ChatConversation conversation = getOwned(ownerAuthUserId, conversationId);
        conversation.restore();
        return ConversationResponse.from(conversations.save(conversation));
    }

    /**
     * Soft-deletes a conversation.
     *
     * <p>The row is retained. An audit trail and any derived data elsewhere still reference it,
     * and a hard delete here would silently break those references.
     */
    public void delete(UUID ownerAuthUserId, UUID conversationId) {
        ChatConversation conversation = getOwned(ownerAuthUserId, conversationId);
        conversation.softDelete();
        conversations.save(conversation);
    }

    // ------------------------------------------------------------------
    // Internals used by the message service
    // ------------------------------------------------------------------

    /**
     * Loads a conversation with its row locked, for a write that will add messages.
     *
     * <p>The lock is what makes "next sequence number = max + 1" safe without a retry loop. Two
     * concurrent sends otherwise read the same maximum and collide; the unique constraint turns
     * that collision into a failure, which is better than a duplicate, but the lock avoids it.
     */
    ChatConversation getOwnedForUpdate(UUID ownerAuthUserId, UUID conversationId) {
        return conversations.findOwnedForUpdate(conversationId, ownerAuthUserId)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ChatResourceNotFoundException(
                        ChatResourceNotFoundException.Kind.CONVERSATION, conversationId));
    }

    void save(ChatConversation conversation) {
        conversations.save(conversation);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Pageable pageable(int page, int size, Sort sort) {
        int effectiveSize = Math.clamp(size, 1, properties.getLimits().getMaxPageSize());
        int safePage = Math.max(page, 0);
        return PageRequest.of(safePage, effectiveSize, sort);
    }

    /**
     * Builds a LIKE pattern, or null when there is no search.
     *
     * <p>The wildcards in a caller-supplied term are escaped, because an unescaped {@code %} makes
     * the query match everything, which turns "search for foo" into "return every conversation".
     */
    private static String likePattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String escaped = TitleFromFirstMessage.normaliseForSearch(search)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /** Parses a status filter, rejecting an unknown value rather than silently ignoring it. */
    public static ConversationStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ConversationStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown status '" + value + "'. Expected one of: "
                    + java.util.Arrays.toString(ConversationStatus.values()));
        }
    }
}