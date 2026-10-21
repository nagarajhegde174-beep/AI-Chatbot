package com.nexaai.chat.service;

import com.nexaai.chat.domain.Conversation;
import com.nexaai.chat.domain.Message;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Placeholder implementation of {@link AiClientBoundary} and
 * {@link AiSummarizationClient}. Active only when
 * {@code nexa.ai-client.mode=placeholder}.
 *
 * <p>Use this for local development when AI Service is not running.
 */
@Service
@ConditionalOnProperty(
        name = "nexa.ai-client.mode",
        havingValue = "placeholder"
)
public class PlaceholderAiClientBoundary implements AiClientBoundary, AiSummarizationClient {

    @Override
    public AiResponseResult generateResponse(Conversation conversation, Message userMessage) {
        String model = conversation.getModel() != null ? conversation.getModel() : "gpt-4o-mini";
        String prompt = userMessage.getContent();

        // Estimated placeholder tokens
        int promptTokens = Math.max(1, prompt.length() / 4);
        String responseContent = "This is a placeholder response for: \""
                + (prompt.length() > 50 ? prompt.substring(0, 47) + "..." : prompt)
                + "\". Start AI Service and set nexa.ai-client.mode=live for real inference.";
        int completionTokens = Math.max(1, responseContent.length() / 4);
        int totalTokens = promptTokens + completionTokens;

        return new AiResponseResult(responseContent, model, promptTokens, completionTokens, totalTokens);
    }

    @Override
    public String summarize(String existingSummary, List<Message> messages, String preferredModel) {
        // No-op in placeholder mode; return existing summary unchanged
        return existingSummary;
    }
}
