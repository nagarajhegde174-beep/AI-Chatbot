package com.nexaai.chat.web.dto;

import com.nexaai.chat.domain.ChatMessage;
import com.nexaai.chat.domain.MessageFeedback;
import com.nexaai.chat.domain.MessageRole;
import com.nexaai.chat.domain.MessageStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * A message, as the account holder sees it.
 *
 * @param feedback the caller's own feedback on this message, or null when they have not rated it
 */
public record MessageResponse(
        UUID id,
        UUID conversationId,
        long sequenceNo,
        MessageRole role,
        String content,
        MessageStatus status,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        boolean edited,
        boolean regenerated,
        String failureReason,
        Instant createdAt,
        FeedbackResponse feedback) {

    public static MessageResponse from(ChatMessage m, MessageFeedback feedback) {
        return new MessageResponse(
                m.getId(),
                m.getConversationId(),
                m.getSequenceNo(),
                m.getRole(),
                m.getContent(),
                m.getStatus(),
                m.getModel(),
                m.getInputTokens(),
                m.getOutputTokens(),
                m.getEditedFromId() != null,
                m.getRegeneratedFromId() != null,
                m.getFailureReason(),
                m.getCreatedAt(),
                feedback == null ? null : FeedbackResponse.from(feedback));
    }

    public static MessageResponse from(ChatMessage m) {
        return from(m, null);
    }

    /** Feedback attached to a message. */
    public record FeedbackResponse(
            UUID id,
            String rating,
            String comment,
            Instant updatedAt) {

        public static FeedbackResponse from(MessageFeedback f) {
            return new FeedbackResponse(f.getId(), f.getRating().name(), f.getComment(),
                    f.getUpdatedAt());
        }
    }

    /**
     * What a send produced: the stored user message and the assistant placeholder created for it.
     *
     * <p>Both are returned so the frontend can render the user's own message optimistically and
     * show the placeholder's PENDING state, without a second round trip.
     */
    public record SendResponse(MessageResponse userMessage, MessageResponse assistantMessage) {
    }

    /** What an edit produced, in the same shape as a send. */
    public record EditResponse(MessageResponse userMessage, MessageResponse assistantMessage) {
    }
}