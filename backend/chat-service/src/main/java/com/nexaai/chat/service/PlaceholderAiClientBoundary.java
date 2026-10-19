package com.nexaai.chat.service;

import com.nexaai.chat.domain.Conversation;
import com.nexaai.chat.domain.Message;
import org.springframework.stereotype.Service;

@Service
public class PlaceholderAiClientBoundary implements AiClientBoundary {

    @Override
    public AiResponseResult generateResponse(Conversation conversation, Message userMessage) {
        String model = conversation.getModel() != null ? conversation.getModel() : "gpt-4o-mini";
        String prompt = userMessage.getContent();

        // Estimated placeholder tokens
        int promptTokens = Math.max(1, prompt.length() / 4);
        String responseContent = "This is a placeholder response for: \"" + 
                (prompt.length() > 50 ? prompt.substring(0, 47) + "..." : prompt) + 
                "\". Phase 4 will connect real streaming inference.";
        int completionTokens = Math.max(1, responseContent.length() / 4);
        int totalTokens = promptTokens + completionTokens;

        return new AiResponseResult(responseContent, model, promptTokens, completionTokens, totalTokens);
    }
}
