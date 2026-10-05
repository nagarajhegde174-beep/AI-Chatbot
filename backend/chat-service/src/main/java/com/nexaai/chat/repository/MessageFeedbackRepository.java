package com.nexaai.chat.repository;

import com.nexaai.chat.domain.MessageFeedback;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@code message_feedback}.
 *
 * <p>Every method takes an owner. Feedback is a user's opinion about their own conversation, so
 * the same isolation rule applies as everywhere else in this service.
 */
public interface MessageFeedbackRepository extends JpaRepository<MessageFeedback, UUID> {

    /** The current feedback on a message, for one owner. */
    Optional<MessageFeedback> findByMessageIdAndOwnerAuthUserId(UUID messageId,
                                                               UUID ownerAuthUserId);

    /** An owner's feedback across a conversation, for the export. */
    List<MessageFeedback> findByOwnerAuthUserId(UUID ownerAuthUserId);

    /**
     * The rating distribution for a set of messages, for aggregate quality reporting.
     *
     * <p>Counts only. Feedback comments are free text from users and are not aggregated into
     * anything an operator reads without a deliberate, audited step.
     */
    long countByOwnerAuthUserIdAndRating(UUID ownerAuthUserId,
                                         com.nexaai.chat.domain.FeedbackRating rating);

    /**
     * The rating distribution across every user's feedback.
     *
     * <p>Counts only. Implemented as two counts rather than a group-by so the shape of the result
     * is fixed and a caller cannot get a surprise key from the data.
     */
    @org.springframework.data.jpa.repository.Query("""
            select f.rating, count(f) from MessageFeedback f group by f.rating
            """)
    java.util.Map<com.nexaai.chat.domain.FeedbackRating, Long> countGroupedByRating();

    default Map<String, Long> countByRating() {
        java.util.Map<String, Long> totals = new java.util.LinkedHashMap<>();
        totals.put("UP", 0L);
        totals.put("DOWN", 0L);
        countGroupedByRating().forEach((rating, count) -> totals.put(rating.name(), count));
        return totals;
    }
}