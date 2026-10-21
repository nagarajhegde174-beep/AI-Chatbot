package com.nexaai.chat.service;

import com.nexaai.chat.domain.Conversation;
import com.nexaai.chat.domain.Message;
import com.nexaai.chat.domain.MessageRole;
import com.nexaai.chat.repository.ConversationRepository;
import com.nexaai.chat.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Phase 5 — Conversation Memory / Context Window Management.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Estimate token cost of messages (chars ÷ 4 approximation).</li>
 *   <li>Build the context window sent to the AI Service for every turn:
 *       <ol>
 *         <li>Always include a rolling {@code contextSummary} (if one exists)
 *             so older turns are not lost.</li>
 *         <li>Include the most-recent {@code recentWindowSize} message
 *             pairs verbatim, bounded by {@code maxContextTokens}.</li>
 *       </ol>
 *   </li>
 *   <li>Trigger asynchronous (or inline) summarization when the un-summarized
 *       tail exceeds {@code summarizationThreshold} messages.</li>
 *   <li>Guarantee that context is conversation-scoped: one conversation never
 *       leaks into another.</li>
 * </ul>
 *
 * <p>Token counting is a chars-÷-4 heuristic; this avoids a hard tokenizer
 * dependency and is conservative enough for safety-margin purposes.
 */
@Service
@Transactional
public class ContextWindowService {

    private static final Logger log = LoggerFactory.getLogger(ContextWindowService.class);

    /** Approximate chars per token (heuristic; real value is ~3.5–4). */
    private static final int CHARS_PER_TOKEN = 4;

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final AiSummarizationClient summarizationClient;

    /**
     * Hard cap on tokens passed to the model as context (excluding the new
     * user turn). Configurable; default aligns with gpt-4o-mini's 128 k
     * context but we keep a conservative working budget.
     */
    @Value("${nexa.memory.max-context-tokens:6000}")
    private int maxContextTokens;

    /**
     * Number of the most-recent message turns (user+assistant pairs) to
     * always keep verbatim in the window regardless of token budget.
     */
    @Value("${nexa.memory.recent-window-size:10}")
    private int recentWindowSize;

    /**
     * When the number of un-summarized messages (messages with
     * sequenceNumber > summaryMessageSeq) reaches this threshold, a
     * summarization pass is triggered before the current completion.
     */
    @Value("${nexa.memory.summarization-threshold:20}")
    private int summarizationThreshold;

    public ContextWindowService(
            MessageRepository messageRepository,
            ConversationRepository conversationRepository,
            AiSummarizationClient summarizationClient) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.summarizationClient = summarizationClient;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Builds the ordered list of context messages to send to AI Service for
     * the given conversation. The list does NOT include the new user turn —
     * the caller appends it before calling AI Service.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Check whether un-summarized history exceeds the threshold; if so,
     *       run a summarization pass and persist the new summary.</li>
     *   <li>Select the recent window (last {@code recentWindowSize} message
     *       pairs) verbatim.</li>
     *   <li>Prepend the persisted {@code contextSummary} if one exists.</li>
     *   <li>Enforce {@code maxContextTokens}; if the recent window alone
     *       overflows, trim the oldest messages from the window.</li>
     * </ol>
     *
     * @param conversation the conversation (must not be null)
     * @return ordered context messages ready to pass to AI Service
     */
    public List<ContextMessage> buildContext(Conversation conversation) {
        // Step 1: maybe summarize
        maybeSummarize(conversation);

        // Step 2: pick recent verbatim messages
        List<Message> recent = fetchRecentMessages(conversation.getId(), recentWindowSize * 2);

        // Step 3: build the assembled context list
        List<ContextMessage> context = new ArrayList<>();

        String summary = conversation.getContextSummary();
        if (summary != null && !summary.isBlank()) {
            context.add(new ContextMessage("SYSTEM",
                    "[Conversation summary so far]\n" + summary.trim()));
        }

        for (Message m : recent) {
            context.add(new ContextMessage(m.getRole().name(), m.getContent()));
        }

        // Step 4: trim to token budget
        return trimToTokenBudget(context, maxContextTokens);
    }

    /**
     * Clears the conversation memory: deletes all messages and resets the
     * rolling summary. The conversation metadata (title, model, etc.) is kept.
     *
     * @param conversation the conversation whose memory to clear
     */
    public void clearMemory(Conversation conversation) {
        log.info("Clearing memory for conversation {}", conversation.getId());
        messageRepository.deleteByConversationId(conversation.getId());
        conversation.setContextSummary(null);
        conversation.setSummaryMessageSeq(0);
        conversationRepository.save(conversation);
        log.info("Memory cleared for conversation {}", conversation.getId());
    }

    /**
     * Estimates the token count for a piece of text using the chars-÷-4
     * heuristic.
     */
    public int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        return Math.max(1, text.length() / CHARS_PER_TOKEN);
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Fetches the N most-recent messages in chronological (ascending) order.
     */
    private List<Message> fetchRecentMessages(java.util.UUID conversationId, int limit) {
        List<Message> desc = messageRepository.findRecentByConversationId(
                conversationId, PageRequest.of(0, limit));
        // desc → asc (chronological)
        List<Message> asc = new ArrayList<>(desc);
        Collections.reverse(asc);
        return asc;
    }

    /**
     * Triggers a summarization pass when the unsummarized tail is large.
     * Updates the conversation in the database when a new summary is produced.
     */
    private void maybeSummarize(Conversation conversation) {
        List<Message> unsummarized = messageRepository.findByConversationIdAndSequenceNumberAfter(
                conversation.getId(), conversation.getSummaryMessageSeq());

        if (unsummarized.size() < summarizationThreshold) {
            return; // not enough to justify summarization
        }

        // Keep the most-recent recentWindowSize*2 messages verbatim; summarize the rest
        int keepVerbatim = recentWindowSize * 2;
        if (unsummarized.size() <= keepVerbatim) {
            return; // nothing old enough to summarize
        }

        List<Message> toSummarize = unsummarized.subList(0, unsummarized.size() - keepVerbatim);
        int newWatermark = toSummarize.get(toSummarize.size() - 1).getSequenceNumber();

        log.info("Summarizing {} messages for conversation {} (watermark → {})",
                toSummarize.size(), conversation.getId(), newWatermark);

        String newSummary = summarizationClient.summarize(
                conversation.getContextSummary(),
                toSummarize,
                conversation.getModel());

        if (newSummary != null && !newSummary.isBlank()) {
            conversation.setContextSummary(newSummary.trim());
            conversation.setSummaryMessageSeq(newWatermark);
            conversationRepository.save(conversation);
            log.info("Summary updated for conversation {} (watermark={})",
                    conversation.getId(), newWatermark);
        }
    }

    /**
     * Trims the context list from the front (oldest messages) until the total
     * estimated token count is within the budget. The summary SYSTEM message
     * (if present at index 0) is never trimmed.
     */
    private List<ContextMessage> trimToTokenBudget(List<ContextMessage> context, int tokenBudget) {
        int total = context.stream().mapToInt(m -> estimateTokens(m.content())).sum();
        if (total <= tokenBudget) return context;

        // Determine if the first message is the summary sentinel
        int protectedCount = (!context.isEmpty()
                && "SYSTEM".equals(context.get(0).role())
                && context.get(0).content().startsWith("[Conversation summary")) ? 1 : 0;

        List<ContextMessage> result = new ArrayList<>(context);
        int idx = protectedCount; // start trimming after protected messages

        while (total > tokenBudget && idx < result.size()) {
            total -= estimateTokens(result.get(idx).content());
            result.remove(idx);
            // Don't increment idx — the next element shifted into this position
        }

        log.debug("Context trimmed to ~{} tokens ({} messages)", total, result.size());
        return result;
    }

    // -----------------------------------------------------------------------
    // Value types
    // -----------------------------------------------------------------------

    /**
     * A resolved context message ready to be sent to AI Service.
     *
     * @param role    "USER", "ASSISTANT", or "SYSTEM"
     * @param content the message text
     */
    public record ContextMessage(String role, String content) {}
}
