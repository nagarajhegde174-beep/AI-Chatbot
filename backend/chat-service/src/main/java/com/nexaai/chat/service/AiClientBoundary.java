package com.nexaai.chat.service;

import com.nexaai.chat.domain.Conversation;
import com.nexaai.chat.domain.Message;

public interface AiClientBoundary {

    record AiResponseResult(
            String content,
            String model,
            int promptTokens,
            int completionTokens,
            int totalTokens
    ) {
    }

    /**
     * Generates an AI response for the conversation turn.
     * In Phase 3, this is the integration boundary returning placeholder responses
     * to prepare for Phase 4 (AI Service) integration.
     */
    AiResponseResult generateResponse(Conversation conversation, Message userMessage);
}
