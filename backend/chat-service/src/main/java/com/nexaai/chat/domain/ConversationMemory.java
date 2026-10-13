package com.nexaai.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * A conversation's compressed memory of its older turns.
 *
 * <p><strong>A derived index, not a record.</strong> The verbatim messages in
 * {@code chat_message} are the authoritative history and are never touched by anything here. This
 * row can be dropped and rebuilt at any time without losing a message, which is what makes "clear
 * memory" safe to offer at all.
 *
 * <p><strong>One row per conversation.</strong> Memory is conversation-specific, so keying it that
 * way makes cross-conversation leakage structurally impossible rather than merely guarded by a
 * predicate someone can forget. The owner is carried and filtered on anyway, because a row whose
 * owner disagrees with its conversation is a bug worth refusing rather than reading.
 *
 * <p><strong>Deliberately not {@code @ToString}-safe by accident.</strong> Like {@link ChatMessage},
 * {@code summary} holds the user's own words in compressed form, so the default {@code toString}
 * is overridden to keep it out of logs.
 */
@Entity
@Table(name = "conversation_memory")
public class ConversationMemory {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "conversation_id", nullable = false, unique = true)
    private UUID conversationId;

    @Column(name = "owner_auth_user_id", nullable = false)
    private UUID ownerAuthUserId;

    /** The compressed older turns. Null after a clear; absent row before any summarisation. */
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    /** Messages at or below this sequence are covered by {@link #summary}. Zero covers nothing. */
    @Column(name = "summarised_through_sequence", nullable = false)
    private long summarisedThroughSequence;

    @Column(name = "summarised_turn_count", nullable = false)
    private int summarisedTurnCount;

    @Column(name = "summarised_at")
    private Instant summarisedAt;

    /** What the summary was judged to cost. Decides whether another pass is needed without re-measuring. */
    @Column(name = "summary_estimated_tokens")
    private Integer summaryEstimatedTokens;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ConversationMemory() {
        // for JPA
    }

    private ConversationMemory(UUID conversationId, UUID ownerAuthUserId) {
        this.conversationId = conversationId;
        this.ownerAuthUserId = ownerAuthUserId;
    }

    public static ConversationMemory empty(UUID conversationId, UUID ownerAuthUserId) {
        return new ConversationMemory(conversationId, ownerAuthUserId);
    }

    /**
     * Replaces the summary with a newer one covering more of the conversation.
     *
     * @param throughSequence the newest sequence the summary covers
     */
    public void replaceSummary(String text, long throughSequence, int turnCount,
                               int estimatedTokens) {
        this.summary = text;
        this.summarisedThroughSequence = throughSequence;
        this.summarisedTurnCount = turnCount;
        this.summaryEstimatedTokens = estimatedTokens;
        this.summarisedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Forgets everything.
     *
     * <p>The row survives with an empty summary rather than being deleted, so that "cleared" is
     * distinguishable from "never summarised". The conversation's messages are untouched — clearing
     * memory and deleting history are different requests.
     */
    public void clear() {
        this.summary = null;
        this.summarisedThroughSequence = 0;
        this.summarisedTurnCount = 0;
        this.summaryEstimatedTokens = null;
        this.summarisedAt = null;
        this.updatedAt = Instant.now();
    }

    /** Whether this conversation has anything remembered. False both before any summary and after a clear. */
    public boolean hasSummary() {
        return summary != null && !summary.isBlank() && summarisedTurnCount > 0;
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getOwnerAuthUserId() {
        return ownerAuthUserId;
    }

    public String getSummary() {
        return summary;
    }

    public long getSummarisedThroughSequence() {
        return summarisedThroughSequence;
    }

    public int getSummarisedTurnCount() {
        return summarisedTurnCount;
    }

    public Instant getSummarisedAt() {
        return summarisedAt;
    }

    public Integer getSummaryEstimatedTokens() {
        return summaryEstimatedTokens;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Keeps the user's compressed words out of logs.
     *
     * <p>A summary is a paraphrase of what the user said, so a default {@code toString} would put
     * it in every log line that mentions this row — which is most of them.
     */
    @Override
    public String toString() {
        return "ConversationMemory[conversationId=" + conversationId
                + ", turns=" + summarisedTurnCount
                + ", hasSummary=" + hasSummary()
                + ", throughSequence=" + summarisedThroughSequence + "]";
    }
}