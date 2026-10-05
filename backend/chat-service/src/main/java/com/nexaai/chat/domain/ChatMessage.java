package com.nexaai.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One message in a conversation.
 *
 * <p><strong>Messages are never overwritten in place.</strong> Editing a user message writes a
 * new row that points at the old one and marks the old one superseded. Regenerating an assistant
 * message does the same in reverse.
 *
 * <p>That is the decision worth defending. The intuitive implementation mutates the row, which
 * destroys the only record of what was originally said. Since edit and regenerate are exactly the
 * features whose value is showing the user the alternatives, an implementation that deletes the
 * previous attempt cannot implement them correctly. So history is append-only and the current
 * view is a projection over it.
 *
 * <p><strong>The owner is denormalised from the conversation.</strong> It means the isolation
 * predicate is one indexed column that cannot be forgotten on a code path that queries only
 * messages.
 */
@Entity
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Column(name = "owner_auth_user_id", nullable = false, updatable = false)
    private UUID ownerAuthUserId;

    /** Monotonic within the conversation. History order is not negotiable. */
    @Column(name = "sequence_no", nullable = false, updatable = false)
    private long sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, updatable = false, length = 16)
    private MessageRole role;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MessageStatus status = MessageStatus.COMPLETE;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    /** For a regenerate: the assistant message this one replaces. */
    @Column(name = "regenerated_from_id")
    private UUID regeneratedFromId;

    /** Set when an edit created this message: the message it replaced. */
    @Column(name = "edited_from_id")
    private UUID editedFromId;

    /** Set when this row stops appearing in normal history reads. */
    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ChatMessage() {
        // for JPA
    }

    // ------------------------------------------------------------------
    // Factories
    // ------------------------------------------------------------------

    /** A completed user message. */
    public static ChatMessage userMessage(UUID conversationId, UUID ownerAuthUserId,
                                          long sequenceNo, String content) {
        return build(conversationId, ownerAuthUserId, sequenceNo, MessageRole.USER, content,
                MessageStatus.COMPLETE, null, null, null);
    }

    /**
     * The AI response placeholder, written <em>before</em> generation is attempted.
     *
     * <p>This is the whole reason PENDING exists: an interrupted stream must leave visible
     * evidence, not a missing reply.
     */
    public static ChatMessage assistantPlaceholder(UUID conversationId, UUID ownerAuthUserId,
                                                   long sequenceNo, String model) {
        return build(conversationId, ownerAuthUserId, sequenceNo, MessageRole.ASSISTANT,
                "", MessageStatus.PENDING, model, null, null);
    }

    /** An assistant message that supersedes a previous attempt. */
    public static ChatMessage regeneratedAssistant(UUID conversationId, UUID ownerAuthUserId,
                                                   long sequenceNo, String model,
                                                   UUID regeneratedFromId) {
        ChatMessage message = assistantPlaceholder(conversationId, ownerAuthUserId, sequenceNo,
                model);
        message.regeneratedFromId = regeneratedFromId;
        return message;
    }

    /** A user message that supersedes an edited one. */
    public static ChatMessage editedUserMessage(UUID conversationId, UUID ownerAuthUserId,
                                                long sequenceNo, String content,
                                                UUID editedFromId) {
        ChatMessage message = userMessage(conversationId, ownerAuthUserId, sequenceNo, content);
        message.editedFromId = editedFromId;
        return message;
    }

    private static ChatMessage build(UUID conversationId, UUID ownerAuthUserId, long sequenceNo,
                                     MessageRole role, String content, MessageStatus status,
                                     String model, UUID regeneratedFromId, UUID editedFromId) {
        ChatMessage message = new ChatMessage();
        message.conversationId = conversationId;
        message.ownerAuthUserId = ownerAuthUserId;
        message.sequenceNo = sequenceNo;
        message.role = role;
        message.content = content == null ? "" : content;
        message.status = status;
        message.model = (model == null || model.isBlank()) ? null : model.trim();
        message.regeneratedFromId = regeneratedFromId;
        message.editedFromId = editedFromId;
        message.createdAt = Instant.now();
        message.updatedAt = message.createdAt;
        return message;
    }

    // ------------------------------------------------------------------
    // Behaviour
    // ------------------------------------------------------------------

    /**
     * Completes a PENDING assistant message.
     *
     * <p>Only valid from PENDING. A completed message is final, so silently rewriting a finished
     * answer would make history lie about what was said.
     */
    public void completeWith(String generatedContent, String generatingModel,
                             Integer inTokens, Integer outTokens) {
        if (this.status != MessageStatus.PENDING) {
            throw new IllegalStateException(
                    "Only a PENDING message can be completed; this one is " + this.status + ".");
        }
        this.content = generatedContent == null ? "" : generatedContent;
        if (generatingModel != null && !generatingModel.isBlank()) {
            this.model = generatingModel.trim();
        }
        this.inputTokens = inTokens;
        this.outputTokens = outTokens;
        this.status = MessageStatus.COMPLETE;
        this.failureReason = null;
        this.updatedAt = Instant.now();
    }

    /** Marks a PENDING message as failed, recording why. */
    public void fail(String reason) {
        if (this.status.isTerminal()) {
            return;
        }
        this.status = MessageStatus.FAILED;
        this.failureReason = (reason == null || reason.isBlank())
                ? "Generation did not complete." : reason.trim();
        this.updatedAt = Instant.now();
    }

    /** Takes this row out of the normal history view without deleting it. */
    public void supersede() {
        if (this.supersededAt == null) {
            this.supersededAt = Instant.now();
        }
    }

    /** Whether this message belongs to the given auth user. */
    public boolean isOwnedBy(UUID authUserId) {
        return authUserId != null && authUserId.equals(this.ownerAuthUserId);
    }

    /** Whether this row is the current version and appears in history reads. */
    public boolean isCurrent() {
        return supersededAt == null;
    }

    /** Whether feedback may be attached: a message has to exist and mean something. */
    public boolean acceptsFeedback() {
        return isCurrent() && this.role != MessageRole.SYSTEM;
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getOwnerAuthUserId() {
        return ownerAuthUserId;
    }

    public long getSequenceNo() {
        return sequenceNo;
    }

    public MessageRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public String getModel() {
        return model;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public UUID getRegeneratedFromId() {
        return regeneratedFromId;
    }

    public UUID getEditedFromId() {
        return editedFromId;
    }

    public Instant getSupersededAt() {
        return supersededAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Omits the content.
     *
     * <p>Message content is the user's private data. A default {@code toString} would put it in
     * every debug log, exception message and actuator dump.
     */
    @Override
    public String toString() {
        return "ChatMessage{id=%s, seq=%d, role=%s, status=%s}"
                .formatted(id, sequenceNo, role, status);
    }
}