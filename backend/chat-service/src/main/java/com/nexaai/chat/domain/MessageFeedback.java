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
 * A user's opinion of one message.
 *
 * <p>One row per message: the current opinion is a single fact, and two contradictory rows would
 * make every read of it ambiguous. Re-submitting feedback updates this row.
 *
 * <p>Feedback may only be attached to a message the caller owns, and only to a message that is
 * still current. Rating a superseded answer would leave sentiment attached to something the user
 * no longer sees.
 */
@Entity
@Table(name = "message_feedback")
public class MessageFeedback {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;

    @Column(name = "owner_auth_user_id", nullable = false, updatable = false)
    private UUID ownerAuthUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rating", nullable = false, length = 8)
    private FeedbackRating rating;

    @Column(name = "comment", length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected MessageFeedback() {
        // for JPA
    }

    public static MessageFeedback of(UUID messageId, UUID ownerAuthUserId,
                                     FeedbackRating rating, String comment) {
        MessageFeedback feedback = new MessageFeedback();
        feedback.messageId = messageId;
        feedback.ownerAuthUserId = ownerAuthUserId;
        feedback.rating = rating;
        feedback.comment = sanitiseComment(comment);
        return feedback;
    }

    /**
     * Applies a new opinion.
     *
     * <p>{@code createdAt} is deliberately not moved. When an existing row is updated, its
     * creation time records when the user first rated the message, which is what "when did
     * sentiment arrive" means for reporting.
     */
    public void update(FeedbackRating newRating, String newComment) {
        this.rating = newRating;
        this.comment = sanitiseComment(newComment);
        this.updatedAt = Instant.now();
    }

    /** A blank comment is stored as null, not as an empty string. */
    private static String sanitiseComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        String trimmed = comment.trim();
        return trimmed.length() > 1000 ? trimmed.substring(0, 1000) : trimmed;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public UUID getOwnerAuthUserId() {
        return ownerAuthUserId;
    }

    public FeedbackRating getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "MessageFeedback{message=%s, rating=%s}".formatted(messageId, rating);
    }
}