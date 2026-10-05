package com.nexaai.chat.service;

import com.nexaai.chat.domain.ChatMessage;
import com.nexaai.chat.domain.FeedbackRating;
import com.nexaai.chat.domain.MessageFeedback;
import com.nexaai.chat.exception.ChatResourceNotFoundException;
import com.nexaai.chat.repository.MessageFeedbackRepository;
import com.nexaai.chat.web.dto.MessageResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Feedback on a message.
 *
 * <p>One row per message. Re-rating updates the existing row rather than appending, because "the
 * user's current opinion" is one fact and two contradictory rows make every read ambiguous.
 *
 * <p>Feedback is only accepted on a message that is still current. Rating a superseded answer
 * would attach sentiment to something the user no longer sees.
 */
@Service
@Transactional
public class FeedbackService {

    private final MessageFeedbackRepository feedback;
    private final MessageService messages;

    public FeedbackService(MessageFeedbackRepository feedback, MessageService messages) {
        this.feedback = feedback;
        this.messages = messages;
    }

    /** Records or updates the caller's opinion of one of their messages. */
    public MessageResponse.FeedbackResponse rate(UUID ownerAuthUserId, UUID messageId,
                                                 FeedbackRating rating, String comment) {
        ChatMessage message = messages.getOwnedMessage(ownerAuthUserId, messageId);

        if (!message.acceptsFeedback()) {
            throw new IllegalArgumentException(
                    "Feedback can only be given on a current, non-system message.");
        }

        MessageFeedback existing = feedback
                .findByMessageIdAndOwnerAuthUserId(messageId, ownerAuthUserId)
                .orElse(null);

        if (existing == null) {
            MessageFeedback created = MessageFeedback.of(messageId, ownerAuthUserId, rating,
                    comment);
            return MessageResponse.FeedbackResponse.from(feedback.save(created));
        }

        existing.update(rating, comment);
        return MessageResponse.FeedbackResponse.from(feedback.save(existing));
    }

    /** Removes the caller's feedback, so a message can be unrated. */
    public void clear(UUID ownerAuthUserId, UUID messageId) {
        MessageFeedback existing = feedback
                .findByMessageIdAndOwnerAuthUserId(messageId, ownerAuthUserId)
                .orElseThrow(() -> new ChatResourceNotFoundException(
                        ChatResourceNotFoundException.Kind.FEEDBACK, messageId));
        feedback.delete(existing);
    }

    /** The caller's positive-rating count, for a summary endpoint. */
    @Transactional(readOnly = true)
    public long countRating(UUID ownerAuthUserId, FeedbackRating rating) {
        return feedback.countByOwnerAuthUserIdAndRating(ownerAuthUserId, rating);
    }

    /**
     * The rating distribution across every user's feedback.
     *
     * <p>Counts only, never comments. This is the aggregate behind the administrative dashboard,
     * and it deliberately contains no text a user wrote.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> totalsByRating() {
        return feedback.countByRating();
    }
}