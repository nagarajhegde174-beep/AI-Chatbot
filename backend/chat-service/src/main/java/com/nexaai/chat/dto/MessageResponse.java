package com.nexaai.chat.dto;

import com.nexaai.chat.domain.FeedbackType;
import com.nexaai.chat.domain.MessageRole;
import com.nexaai.chat.domain.MessageStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        UUID conversationId,
        UUID userId,
        MessageRole role,
        String content,
        String model,
        MessageStatus status,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        int sequenceNumber,
        FeedbackType feedback,
        String feedbackComment,
        boolean isRegenerated,
        UUID originalMessageId,
        Instant createdAt,
        Instant updatedAt,
        List<MessageCitationResponse> citations
) {
}
