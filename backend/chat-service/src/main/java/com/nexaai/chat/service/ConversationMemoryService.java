package com.nexaai.chat.service;

import com.nexaai.chat.config.ChatProperties;
import com.nexaai.chat.domain.ChatMessage;
import com.nexaai.chat.domain.ConversationMemory;
import com.nexaai.chat.domain.MessageRole;
import com.nexaai.chat.domain.MessageStatus;
import com.nexaai.chat.repository.ChatMessageRepository;
import com.nexaai.chat.repository.ConversationMemoryRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Selects the context a conversation should carry, and maintains its compressed memory.
 *
 * <h2>The rule this exists to enforce</h2>
 *
 * <strong>History is never sent unbounded.</strong> Every generation picks a context that fits a
 * configured token budget, chosen newest-first, with the turns that are always kept. A conversation
 * that outgrows the budget is compressed; a conversation that still does not fit has its oldest
 * turns dropped, and says so. The failure mode this prevents is the obvious one: a user in a long
 * conversation gets an error about context length, or a provider truncates silently and the model
 * answers the wrong question.
 *
 * <h2>Where the memory lives, and why here</h2>
 *
 * In this service's database, keyed by conversation. Not in AI Service, which is stateless by
 * design (CI rule R5) and could not remember a conversation across a restart. Not on the client,
 * which would make it user-editable and unverifiable. A conversation's memory is derived from
 * messages this service already owns, so it belongs beside them.
 *
 * <h2>Isolation</h2>
 *
 * Every read and write takes the owner and filters on it. Memory is keyed by conversation as well,
 * so a query that forgot the owner could not reach another conversation's row — the predicate is
 * defence in depth, not the only defence.
 *
 * <h2>Summarisation is best-effort and never blocks a reply</h2>
 *
 * A failed summarisation is logged and the request proceeds with verbatim turns. The alternative —
 * refusing to answer because the conversation could not be compressed — would turn a memory
 * optimisation into an availability dependency on a second model call.
 */
@Service
public class ConversationMemoryService {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryService.class);

    private final ConversationMemoryRepository memory;
    private final ChatMessageRepository messages;
    private final ChatProperties properties;
    private final MemorySummarizer memorySummarizer;

    public ConversationMemoryService(ConversationMemoryRepository memory,
                                    ChatMessageRepository messages,
                                    ChatProperties properties,
                                    MemorySummarizer memorySummarizer) {
        this.memory = memory;
        this.messages = messages;
        this.properties = properties;
        this.memorySummarizer = memorySummarizer;
    }

    // ==================================================================
    // Retrieval
    // ==================================================================

    /**
     * The context for one generation.
     *
     * @param ownerAuthUserId  the caller's auth-service id. Filters every read
     * @param conversationId   the conversation. Must already be proven owned
     * @param pendingQuestion  the turn being answered, appended last
     */
    @Transactional(readOnly = true)
    public ConversationContext selectContext(UUID ownerAuthUserId, UUID conversationId,
                                             String pendingQuestion) {
        ConversationMemory existing = findMemory(conversationId, ownerAuthUserId);
        long coveredThrough = existing == null ? 0 : existing.getSummarisedThroughSequence();

        List<ChatMessage> uncovered = messages.findHistory(conversationId, ownerAuthUserId)
                .stream()
                .filter(m -> m.getSequenceNo() > coveredThrough)
                .filter(this::isSendable)
                .toList();

        return build(ownerAuthUserId, conversationId, existing, uncovered, pendingQuestion);
    }

    /**
     * The context for a generation, compressing first if the conversation has outgrown its budget.
     *
     * <p>The order is deliberate: select, then decide whether compression is needed, then select
     * again. Compressing first would cost a model call on every message; selecting first costs a
     * second, cheap read only when there is real pressure.
     *
     * <p><strong>Compression failure never fails the request.</strong> A null from the summariser
     * means "keep the verbatim turns", and the caller proceeds. Refusing to answer because a memory
     * optimisation could not run would make every reply depend on a second model call.
     */
    @Transactional
    public ConversationContext prepareForGeneration(UUID ownerAuthUserId, UUID conversationId,
                                                    String question) {
        ConversationContext first = selectContext(ownerAuthUserId, conversationId, question);

        ConversationMemory existing = findMemory(conversationId, ownerAuthUserId);
        long coveredThrough = existing == null ? 0 : existing.getSummarisedThroughSequence();
        List<ChatMessage> uncovered = messages.findHistory(conversationId, ownerAuthUserId)
                .stream()
                .filter(m -> m.getSequenceNo() > coveredThrough)
                .filter(this::isSendable)
                .toList();

        if (!shouldSummarise(uncovered)) {
            return first;
        }

        int compressed = summariseOldest(ownerAuthUserId, conversationId, uncovered, null);
        if (compressed == 0) {
            // Nothing was compressed — the summariser declined, returned nothing, or there was
            // nothing worth compressing. The first selection is still correct and still bounded.
            return first;
        }

        return selectContext(ownerAuthUserId, conversationId, question);
    }

    /**
     * Compresses now, whether or not the budget demands it.
     *
     * <p>Exists because "summarise when it overflows" is the right default and the wrong answer to
     * a user who has just been told the conversation lost its early turns. They can ask for the
     * compression to happen rather than wait for it.
     *
     * @return how many turns were compressed, or zero when there was nothing worth compressing
     */
    @Transactional
    public int refresh(UUID ownerAuthUserId, UUID conversationId) {
        ConversationMemory row = findMemory(conversationId, ownerAuthUserId);
        long coveredThrough = row == null ? 0 : row.getSummarisedThroughSequence();

        List<ChatMessage> uncovered = messages.findHistory(conversationId, ownerAuthUserId)
                .stream()
                .filter(m -> m.getSequenceNo() > coveredThrough)
                .filter(this::isSendable)
                .toList();

        // Compression is only worthwhile when it actually buys room. Summarising a short
        // conversation would replace a few hundred verbatim tokens with a paraphrase that is not
        // clearly better and costs a model call.
        if (!shouldSummarise(uncovered) && !wouldOverflow(uncovered)) {
            return 0;
        }
        return summariseOldest(ownerAuthUserId, conversationId, uncovered, null);
    }

    /** Whether the uncovered history alone exceeds the whole budget. */
    private boolean wouldOverflow(List<ChatMessage> uncovered) {
        int budget = Math.max(1, properties.getMemory().getContextTokenBudget());
        return TokenEstimate.ofAll(uncovered.stream().map(ChatMessage::getContent).toList())
                > budget;
    }

    /**
     * Whether a message can carry context.
     *
     * <p>Only {@code COMPLETE} messages. A {@code PENDING} placeholder has no content yet, and a
     * {@code FAILED} one holds an error message the user was shown — sending either as history asks
     * the model to treat a platform artefact as something that was said.
     */
    private boolean isSendable(ChatMessage message) {
        return message.getStatus() == MessageStatus.COMPLETE
                && message.getContent() != null
                && !message.getContent().isBlank();
    }

    /**
     * The selection itself, as a pure function of what is available.
     *
     * <p>Split out from {@link #selectContext} so the selection rules can be exercised directly,
     * without a database or a summarisation call, and so the ordering and budget logic is readable
     * in one place.
     */
    ConversationContext build(UUID ownerAuthUserId, UUID conversationId,
                              ConversationMemory existing, List<ChatMessage> uncovered,
                              String pendingQuestion) {

        ChatProperties.Memory config = properties.getMemory();
        int budget = Math.max(1, config.getContextTokenBudget());
        int alwaysKeep = Math.max(0, config.getAlwaysKeepRecentTurns());

        String summaryText = existing != null && existing.hasSummary()
                ? existing.getSummary() : null;
        int summaryTokens = summaryText == null ? 0 : TokenEstimate.of(summaryText);

        // The summary comes out of the budget first: it is the compressed form of everything
        // before the kept turns, so it is the cheapest way to keep the oldest context.
        int remaining = budget - summaryTokens;

        // Always keep at least the newest turn, whatever else is dropped. A generation with no
        // question in it is not a degraded answer, it is no answer at all.
        int keep = Math.min(uncovered.size(), Math.max(alwaysKeep, 1));
        List<ChatMessage> selected = new ArrayList<>(uncovered.subList(
                uncovered.size() - keep, uncovered.size()));

        int used = summaryTokens;
        for (ChatMessage message : selected) {
            used += TokenEstimate.of(message.getContent());
        }

        // Grow backwards through the remaining history while there is room. Stopping here is the
        // "never send unbounded history" guarantee, expressed as one loop with one budget.
        int dropped = 0;
        for (int i = uncovered.size() - keep - 1; i >= 0; i--) {
            ChatMessage candidate = uncovered.get(i);
            int cost = TokenEstimate.of(candidate.getContent());
            if (used + cost > budget) {
                dropped = i + 1;
                break;
            }
            selected.add(0, candidate);
            used += cost;
        }

        List<ConversationContext.ContextTurn> turns = new ArrayList<>(selected.size() + 2);
        if (summaryText != null) {
            // First, and labelled, so the model reads it as background rather than as the most
            // recent thing that happened.
            turns.add(new ConversationContext.ContextTurn(MessageRole.SYSTEM, summaryText, true));
        }
        for (ChatMessage message : selected) {
            turns.add(new ConversationContext.ContextTurn(
                    message.getRole(), message.getContent(), false));
        }
        turns.add(new ConversationContext.ContextTurn(MessageRole.USER, pendingQuestion, false));

        boolean truncated = dropped > 0;
        if (truncated) {
            log.warn("Conversation {} dropped {} older turn(s) that did not fit a budget of {} "
                    + "tokens. Consider summarisation, or a larger context budget.",
                    conversationId, dropped, budget);
        }

        return new ConversationContext(Collections.unmodifiableList(turns), selected.size(),
                dropped, summaryText != null, used, budget, truncated);
    }

    // ==================================================================
    // Summarisation
    // ==================================================================

    /**
     * Whether the uncovered history has grown past the point where compressing it is worthwhile.
     *
     * <p>Summarisation costs a model call, so it must not run on every message. Triggering at a
     * fraction of the budget means it runs when there is real pressure and not before.
     */
    public boolean shouldSummarise(List<ChatMessage> uncovered) {
        ChatProperties.Memory config = properties.getMemory();
        if (!config.isSummarisationEnabled() || uncovered.size() < 4) {
            return false;
        }
        int used = TokenEstimate.ofAll(uncovered.stream().map(ChatMessage::getContent).toList());
        return used > (int) (config.getContextTokenBudget() * config.getSummariseThreshold());
    }

    /**
     * Compresses the oldest uncovered turns and stores the result.
     *
     * <p>Compresses from the OLDEST end and stops as soon as the compressed remainder would fit
     * the budget. Compressing the newest turns instead would throw away exactly the context that
     * explains the question being answered.
     *
     * @return the number of turns now covered, or zero when nothing was compressed
     */
    @Transactional
    public int summariseOldest(UUID ownerAuthUserId, UUID conversationId,
                               List<ChatMessage> uncovered, String model) {
        ChatProperties.Memory config = properties.getMemory();
        if (!config.isSummarisationEnabled() || uncovered.size() < 4) {
            return 0;
        }

        List<ChatMessage> toSummarise = selectOldestThatOverflow(uncovered);

        ConversationMemory row = findOrCreate(conversationId, ownerAuthUserId);
        long newCoveredThrough = row.getSummarisedThroughSequence();

        // Fold the existing summary in, so the new one replaces it rather than losing it. Without
        // this, repeated summarisation would keep overwriting the earliest context each time and
        // the conversation would slowly forget its own beginning.
        List<ConversationContext.ContextTurn> forSummary = new ArrayList<>();
        if (row.hasSummary()) {
            forSummary.add(new ConversationContext.ContextTurn(
                    MessageRole.SYSTEM, row.getSummary(), true));
        }
        for (ChatMessage message : toSummarise) {
            forSummary.add(new ConversationContext.ContextTurn(
                    message.getRole(), message.getContent(), false));
        }

        MemorySummarizer.Result result;
        try {
            result = memorySummarizer.summarise(conversationId, forSummary, model, config);
        } catch (RuntimeException e) {
            // The seam documents "return null", but a summariser is a network call into another
            // service and this is a memory OPTIMISATION. Catching here is what stops a badly
            // behaved implementation from turning a failed optimisation into a failed reply.
            // Logged by type: the exception may carry the user's words.
            log.warn("Summariser threw for conversation {}: {}",
                    conversationId, e.getClass().getSimpleName());
            return 0;
        }

        if (result == null || result.summary() == null || result.summary().isBlank()) {
            // An empty summary is the worst outcome available: storing it would make the caller
            // believe the context was covered and stop sending the turns it just discarded.
            log.warn("Summarisation for conversation {} produced nothing; keeping verbatim history.",
                    conversationId);
            return 0;
        }

        for (ChatMessage message : toSummarise) {
            newCoveredThrough = Math.max(newCoveredThrough, message.getSequenceNo());
        }

        row.replaceSummary(result.summary(), newCoveredThrough,
                row.getSummarisedTurnCount() + toSummarise.size(),
                TokenEstimate.of(result.summary()));
        memory.save(row);

        log.info("Conversation {} compressed {} turn(s) into a summary of about {} tokens.",
                conversationId, toSummarise.size(), TokenEstimate.of(result.summary()));
        return toSummarise.size();
    }

    /** The oldest turns whose removal brings the remainder inside the budget. */
    private List<ChatMessage> selectOldestThatOverflow(List<ChatMessage> uncovered) {
        int budget = Math.max(1, properties.getMemory().getContextTokenBudget());
        int keep = Math.max(1, properties.getMemory().getAlwaysKeepRecentTurns());

        int total = TokenEstimate.ofAll(uncovered.stream().map(ChatMessage::getContent).toList());
        int allowed = (int) (budget * properties.getMemory().getSummariseThreshold());

        int fromOldest = 0;
        for (ChatMessage message : uncovered) {
            if (total - TokenEstimate.of(message.getContent()) <= allowed) {
                break;
            }
            total -= TokenEstimate.of(message.getContent());
            fromOldest++;
        }

        // Never compress the turns that are always kept, and never compress everything: with an
        // empty verbatim remainder the model would be answering from a summary of itself.
        int limit = Math.max(0, uncovered.size() - keep);
        fromOldest = Math.min(fromOldest, limit);
        return fromOldest == 0 ? List.of() : uncovered.subList(0, fromOldest);
    }

    // ==================================================================
    // Clearing
    // ==================================================================

    /**
     * Forgets a conversation's memory.
     *
     * <p><strong>Messages are not touched.</strong> "Forget what we discussed" and "delete the
     * discussion" are different requests. Clearing removes only the compressed form, and rewinds
     * the covered sequence so the next send rebuilds from messages that still exist.
     *
     * @return true when there was memory to clear
     */
    @Transactional
    public boolean clear(UUID ownerAuthUserId, UUID conversationId) {
        ConversationMemory row = findMemory(conversationId, ownerAuthUserId);
        if (row == null) {
            return false;
        }
        boolean hadMemory = row.hasSummary();
        row.clear();
        memory.save(row);
        log.info("Cleared memory for conversation {}. Messages are unchanged.", conversationId);
        return hadMemory;
    }

    /** What a conversation remembers, for the UI to show. Carries no message content. */
    @Transactional(readOnly = true)
    public MemoryStatus status(UUID ownerAuthUserId, UUID conversationId) {
        ConversationMemory row = findMemory(conversationId, ownerAuthUserId);
        return new MemoryStatus(
                row != null && row.hasSummary(),
                row == null ? 0 : row.getSummarisedTurnCount(),
                row == null || row.getSummaryEstimatedTokens() == null
                        ? 0 : row.getSummaryEstimatedTokens(),
                row == null ? null : row.getSummarisedAt(),
                properties.getMemory().getContextTokenBudget(),
                properties.getMemory().isSummarisationEnabled());
    }

    // ==================================================================
    // Persistence
    // ==================================================================

    ConversationMemory findMemory(UUID conversationId, UUID ownerAuthUserId) {
        return memory.findByConversationIdAndOwnerAuthUserId(conversationId, ownerAuthUserId)
                .orElse(null);
    }

    ConversationMemory findOrCreate(UUID conversationId, UUID ownerAuthUserId) {
        ConversationMemory existing = findMemory(conversationId, ownerAuthUserId);
        return existing != null
                ? existing
                : memory.save(ConversationMemory.empty(conversationId, ownerAuthUserId));
    }

    /**
     * What a conversation remembers.
     *
     * <p>Counts and timestamps only. A summary is a paraphrase of the user's own words, so this
     * deliberately reports that one exists without returning it — the frontend has no use for the
     * text and every copy is another place it could leak from.
     */
    public record MemoryStatus(
            boolean hasSummary,
            int summarisedTurns,
            int summaryEstimatedTokens,
            java.time.Instant summarisedAt,
            int contextTokenBudget,
            boolean summarisationEnabled) {
    }
}