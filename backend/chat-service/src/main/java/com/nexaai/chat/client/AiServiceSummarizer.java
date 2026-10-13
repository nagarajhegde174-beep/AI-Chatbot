package com.nexaai.chat.client;

import com.nexaai.chat.config.ChatProperties;
import com.nexaai.chat.domain.MessageRole;
import com.nexaai.chat.security.CallTokenHolder;
import com.nexaai.chat.service.ConversationContext;
import com.nexaai.chat.service.MemorySummarizer;
import com.nexaai.chat.service.TokenEstimate;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Compresses conversation turns by calling AI Service.
 *
 * <p><strong>No local summarisation.</strong> Truncating a conversation produces something that
 * looks like a summary and silently drops whatever was cut — including the question the summary was
 * meant to explain. The model call is the expensive part, and it is the part that earns the result.
 *
 * <p><strong>Never throws.</strong> Summarisation is an optimisation; refusing to answer a user
 * because their conversation could not be compressed would turn a memory feature into an
 * availability dependency on a second model call. Every failure returns null, which the caller
 * reads as "keep the verbatim turns".
 *
 * <p><strong>The caller's token is forwarded</strong> exactly as it is for a generation, so a
 * summary is attributable to the user whose conversation it compresses.
 */
@Component
public class AiServiceSummarizer implements MemorySummarizer {

    private static final Logger log = LoggerFactory.getLogger(AiServiceSummarizer.class);

    private final HttpClient httpClient;
    private final ChatProperties properties;
    private final CallTokenHolder callToken;

    public AiServiceSummarizer(ChatProperties properties, CallTokenHolder callToken) {
        this.properties = properties;
        this.callToken = callToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public Result summarise(UUID conversationId, List<ConversationContext.ContextTurn> turns,
                            String model, ChatProperties.Memory config) {

        String token = callToken.get();
        if (token == null) {
            // No request to attribute this to. Refusing is correct: an unattributed summary would
            // be content from one user attached to another user's conversation.
            log.warn("Cannot summarise conversation {} with no authenticated caller.", conversationId);
            return null;
        }

        String body = buildRequestBody(conversationId, turns, model, config);

        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .timeout(config.getSummariseTimeout())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                // Logged, not returned: the body may name a provider or an account, and the
                // caller only needs to know it did not work.
                log.warn("AI Service returned {} summarising conversation {}: {}",
                        response.statusCode(), conversationId, truncate(response.body()));
                return null;
            }

            String summary = extractSummary(response.body());
            if (summary == null || summary.isBlank()) {
                // Storing an empty summary is the worst outcome: the caller would believe the
                // context was covered and stop sending the turns it just discarded.
                log.warn("Summarisation for conversation {} returned nothing usable.", conversationId);
                return null;
            }
            return Result.of(summary, turns.size());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            // Named by TYPE only. The exception may carry the user's words.
            log.warn("Summarisation failed for conversation {}: {}",
                    conversationId, e.getClass().getSimpleName());
            return null;
        }
    }

    private URI endpoint() {
        String base = properties.getGeneration().getAiServiceUrl();
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return URI.create(trimmed + "/internal/v1/ai/summarize");
    }

    /**
     * The summarisation request.
     *
     * <p>A summary turn is sent with the {@code SUMMARY} role so AI Service labels it as
     * compressed context rather than as something the user said.
     */
    private String buildRequestBody(UUID conversationId,
                                    List<ConversationContext.ContextTurn> turns, String model,
                                    ChatProperties.Memory config) {
        StringBuilder out = new StringBuilder("{");
        if (model != null && !model.isBlank()) {
            out.append("\"model\":").append(jsonString(model)).append(',');
        }
        out.append("\"targetTokens\":").append(summaryTarget(config)).append(',');
        out.append("\"messages\":[");
        for (int i = 0; i < turns.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            ConversationContext.ContextTurn turn = turns.get(i);
            out.append("{\"role\":\"").append(aiRole(turn)).append("\",\"content\":")
                    .append(jsonString(turn.content())).append('}');
        }
        out.append("]}");
        return out.toString();
    }

    /** The role name AI Service expects for a given Chat Service role. */
    private static String aiRole(ConversationContext.ContextTurn turn) {
        // A compressed turn is already a summary, whether it came from this conversation's memory
        // or is about to be folded into one.
        if (turn.fromSummary()) {
            return "SUMMARY";
        }
        return turn.role() == MessageRole.ASSISTANT ? "ASSISTANT" : "USER";
    }

    /** Roughly a quarter of the budget, so the summary plus the kept turns fit with room to spare. */
    private static int summaryTarget(ChatProperties.Memory config) {
        int budget = Math.max(1, config.getContextTokenBudget());
        int target = budget / 4;
        return Math.max(50, Math.min(4000, target));
    }

    private static String extractSummary(String body) {
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
            com.fasterxml.jackson.databind.JsonNode summary = node.get("summary");
            return summary == null || summary.isNull() ? null : summary.asText();
        } catch (Exception e) {
            return null;
        }
    }

    /** Escapes a string for a JSON literal. Control characters are escaped, not dropped. */
    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder(value.length() + 8);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private static String truncate(String body) {
        if (body == null) {
            return "<empty body>";
        }
        String single = body.replaceAll("\\s+", " ").trim();
        return single.length() <= 300 ? single : single.substring(0, 300) + "...";
    }
}