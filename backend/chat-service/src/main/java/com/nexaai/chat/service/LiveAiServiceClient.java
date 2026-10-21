package com.nexaai.chat.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexaai.chat.domain.Conversation;
import com.nexaai.chat.domain.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Live implementation of {@link AiClientBoundary} and
 * {@link AiSummarizationClient} that calls the AI Service over HTTP.
 *
 * <p>Active only when {@code nexa.ai-client.mode=live} (the default when
 * AI Service is running). Overridden by
 * {@link PlaceholderAiClientBoundary} when mode=placeholder.
 *
 * <p>Implements both ports so a single RestClient instance is shared and
 * configuration stays in one place.
 */
@Service
@ConditionalOnProperty(
        name = "nexa.ai-client.mode",
        havingValue = "live",
        matchIfMissing = true
)
public class LiveAiServiceClient implements AiClientBoundary, AiSummarizationClient {

    private static final Logger log = LoggerFactory.getLogger(LiveAiServiceClient.class);

    private static final int DEFAULT_MAX_TOKENS = 2048;
    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final String DEFAULT_PROVIDER = "OPENAI";

    private final RestClient restClient;
    private final ContextWindowService contextWindowService;

    public LiveAiServiceClient(
            @Value("${services.ai.base-url:http://localhost:8084}") String aiServiceUrl,
            ContextWindowService contextWindowService) {
        this.restClient = RestClient.builder()
                .baseUrl(aiServiceUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.contextWindowService = contextWindowService;
        log.info("LiveAiServiceClient configured with base URL: {}", aiServiceUrl);
    }

    // -----------------------------------------------------------------------
    // AiClientBoundary — non-streaming completion
    // -----------------------------------------------------------------------

    @Override
    public AiResponseResult generateResponse(Conversation conversation, Message userMessage) {
        // Build context window (Phase 5 memory)
        List<ContextWindowService.ContextMessage> contextMsgs =
                contextWindowService.buildContext(conversation);

        // Assemble full message list for AI Service
        List<MessageDto> messages = new ArrayList<>();
        for (ContextWindowService.ContextMessage ctx : contextMsgs) {
            messages.add(new MessageDto(ctx.role(), ctx.content()));
        }
        // Always append the new user turn last
        messages.add(new MessageDto("USER", userMessage.getContent()));

        String requestId = UUID.randomUUID().toString();
        String provider = resolveProvider(conversation.getModel());
        String model = conversation.getModel() != null ? conversation.getModel() : "gpt-4o-mini";

        CompletionRequestDto requestDto = new CompletionRequestDto(
                provider,
                model,
                conversation.getSystemPrompt(),
                messages,
                DEFAULT_TEMPERATURE,
                DEFAULT_MAX_TOKENS,
                requestId
        );

        try {
            CompletionResponseDto response = restClient.post()
                    .uri("/internal/v1/ai/completions")
                    .body(requestDto)
                    .retrieve()
                    .body(CompletionResponseDto.class);

            if (response == null) {
                log.warn("AI Service returned null response for requestId={}", requestId);
                return fallbackResult(model, userMessage.getContent());
            }

            int prompt = response.usage() != null ? response.usage().promptTokens() : 0;
            int completion = response.usage() != null ? response.usage().completionTokens() : 0;
            int total = response.usage() != null ? response.usage().totalTokens() : 0;

            return new AiResponseResult(
                    response.content() != null ? response.content() : "",
                    response.model() != null ? response.model() : model,
                    prompt, completion, total
            );

        } catch (RestClientException e) {
            log.error("AI Service call failed for conversation {}: {}", conversation.getId(), e.getMessage());
            return fallbackResult(model, userMessage.getContent());
        }
    }

    // -----------------------------------------------------------------------
    // AiSummarizationClient — context compression
    // -----------------------------------------------------------------------

    @Override
    public String summarize(String existingSummary, List<Message> messages, String preferredModel) {
        if (messages == null || messages.isEmpty()) return existingSummary;

        String requestId = UUID.randomUUID().toString();
        String provider = resolveProvider(preferredModel);
        String model = (preferredModel != null && !preferredModel.isBlank())
                ? preferredModel : "gpt-4o-mini";

        List<MessageDto> turns = messages.stream()
                .map(m -> new MessageDto(m.getRole().name(), m.getContent()))
                .toList();

        SummarizeRequestDto requestDto = new SummarizeRequestDto(
                provider, model, existingSummary, turns, 300, requestId
        );

        try {
            SummarizeResponseDto response = restClient.post()
                    .uri("/internal/v1/ai/summarize")
                    .body(requestDto)
                    .retrieve()
                    .body(SummarizeResponseDto.class);

            return (response != null && response.summary() != null) ? response.summary() : existingSummary;

        } catch (RestClientException e) {
            log.warn("Summarization call to AI Service failed: {}. Keeping existing summary.", e.getMessage());
            return existingSummary; // graceful degradation
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private String resolveProvider(String model) {
        if (model == null) return DEFAULT_PROVIDER;
        String m = model.toLowerCase();
        if (m.startsWith("gemini")) return "GEMINI";
        if (m.startsWith("llama") || m.startsWith("mixtral") || m.startsWith("groq")) return "GROQ";
        return "OPENAI";
    }

    private AiResponseResult fallbackResult(String model, String userContent) {
        String content = "I'm sorry, I'm unable to respond right now. Please try again in a moment.";
        int promptEst = Math.max(1, userContent.length() / 4);
        int completionEst = Math.max(1, content.length() / 4);
        return new AiResponseResult(content, model, promptEst, completionEst, promptEst + completionEst);
    }

    // -----------------------------------------------------------------------
    // Local DTOs mirroring AI Service's contract (no shared library)
    // -----------------------------------------------------------------------

    record MessageDto(String role, String content) {}

    record CompletionRequestDto(
            String provider,
            String model,
            String systemPrompt,
            List<MessageDto> messages,
            Double temperature,
            Integer maxOutputTokens,
            String requestId
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CompletionResponseDto(
            String requestId,
            String provider,
            String model,
            String content,
            String finishReason,
            UsageDto usage,
            Long latencyMs
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UsageDto(int promptTokens, int completionTokens, int totalTokens) {}

    record SummarizeRequestDto(
            String provider,
            String model,
            String existingSummary,
            List<MessageDto> messages,
            Integer maxOutputTokens,
            String requestId
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SummarizeResponseDto(
            String requestId,
            String provider,
            String model,
            String summary,
            Integer totalTokensUsed
    ) {}
}
