package com.nexaai.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * A conversation: one session.
 *
 * <p><strong>{@code ownerAuthUserId} is the isolation key.</strong> It is the auth-service user id,
 * not an email and not an email-derived value, because an email can change and this cannot. Every
 * read of a conversation filters on it, and the same column is denormalised onto messages so that
 * a query which forgets to join cannot accidentally leak.
 *
 * <p>No credential of any kind is stored here or anywhere in this service.
 */
@Entity
@Table(name = "chat_conversation")
public class ChatConversation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    /** Never null, never an email. The key every ownership check uses. */
    @Column(name = "owner_auth_user_id", nullable = false, updatable = false)
    private UUID ownerAuthUserId;

    @Column(name = "title", nullable = false, length = 200)
    private String title = "New conversation";

    /** Null means "use the account holder's default", resolved at send time. */
    @Column(name = "model", length = 128)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ConversationStatus status = ConversationStatus.ACTIVE;

    @Column(name = "message_count", nullable = false)
    private int messageCount;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * Optimistic locking.
     *
     * <p>Two tabs sending a message at once both read message_count and both increment it. Without
     * this, one increment is silently lost and the sidebar count drifts permanently. It is a
     * correctness issue in a counter the user can see, so it is worth the retry.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ChatConversation() {
        // for JPA
    }

    /** Creates an empty conversation for its owner. */
    public static ChatConversation create(UUID ownerAuthUserId, String requestedTitle,
                                         String model) {
        ChatConversation conversation = new ChatConversation();
        conversation.ownerAuthUserId = ownerAuthUserId;
        conversation.title = TitleFromFirstMessage.sanitise(requestedTitle, "New conversation");
        conversation.model = (model == null || model.isBlank()) ? null : model.trim();
        conversation.status = ConversationStatus.ACTIVE;
        conversation.createdAt = Instant.now();
        conversation.updatedAt = conversation.createdAt;
        return conversation;
    }

    // ------------------------------------------------------------------
    // Behaviour
    // ------------------------------------------------------------------

    /**
     * Whether this conversation belongs to the given auth user.
     *
     * <p>Compared as UUIDs. Never as strings, and never against an email: a string comparison on
     * a nullable or differently-cased field is how an isolation check quietly stops isolating.
     */
    public boolean isOwnedBy(UUID authUserId) {
        return authUserId != null && authUserId.equals(this.ownerAuthUserId);
    }

    /** Renames the conversation. A blank title is ignored rather than stored empty. */
    public void rename(String requestedTitle) {
        String sanitised = TitleFromFirstMessage.sanitise(requestedTitle, this.title);
        if (!sanitised.equals(this.title)) {
            this.title = sanitised;
            touch();
        }
    }

    /**
     * Sets the title from the first user message, but only while the conversation still has its
     * default title.
     *
     * <p>Guarded so a later message cannot silently overwrite a title the user renamed.
     */
    public void applyDerivedTitleIfUntitled(String messageContent) {
        if ("New conversation".equals(this.title)) {
            this.title = TitleFromFirstMessage.derive(messageContent);
        }
    }

    /** Explicit model override, or null to fall back to the account default. */
    public void useModel(String requestedModel) {
        String trimmed = (requestedModel == null || requestedModel.isBlank())
                ? null : requestedModel.trim();
        if (!java.util.Objects.equals(trimmed, this.model)) {
            this.model = trimmed;
            touch();
        }
    }

    /** Archives. Restoring is allowed, so archive is reversible and delete is not. */
    public void archive() {
        if (this.status != ConversationStatus.ARCHIVED) {
            this.status = ConversationStatus.ARCHIVED;
            touch();
        }
    }

    public void restore() {
        if (this.status != ConversationStatus.ACTIVE) {
            this.status = ConversationStatus.ACTIVE;
            touch();
        }
    }

    /**
     * Records that a message was added.
     *
     * <p>{@code lastMessageAt} moves only forward. A clock adjustment or an out-of-order write
     * must never make an old conversation jump to the top of the sidebar.
     */
    public void recordMessageAdded(int added, Instant occurredAt) {
        this.messageCount += added;
        if (this.lastMessageAt == null || occurredAt.isAfter(this.lastMessageAt)) {
            this.lastMessageAt = occurredAt;
        }
        touch();
    }

    /** Soft-deletes. The row is retained so an audit trail keeps resolving. */
    public void softDelete() {
        if (this.deletedAt == null) {
            this.deletedAt = Instant.now();
            touch();
        }
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public UUID getOwnerAuthUserId() {
        return ownerAuthUserId;
    }

    public String getTitle() {
        return title;
    }

    public String getModel() {
        return model;
    }

    public ConversationStatus getStatus() {
        return status;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    /**
     * Omits the owner id from the rendering.
     *
     * <p>A default {@code toString} on an entity is how a UUID nobody asked for ends up in every
     * log line about this conversation.
     */
    @Override
    public String toString() {
        return "ChatConversation{id=%s, status=%s, messages=%d}"
                .formatted(id, status, messageCount);
    }
}