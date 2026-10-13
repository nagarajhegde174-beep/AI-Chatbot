package com.nexaai.chat.service;

import java.util.List;
import java.util.UUID;

/**
 * Compresses older conversation turns.
 *
 * <p>An interface because summarisation is optional and fallible, and the rest of the memory
 * machinery must not depend on a second model call succeeding. {@link ConversationMemoryService}
 * treats a null result as "carry on with verbatim turns" rather than as an error, and that policy
 * is only safe because the seam exists.
 *
 * <p>The implementation is {@link com.nexaai.chat.client.AiServiceSummarizer}, which calls AI
 * Service. It does <em>not</em> summarise locally: the whole value of a summary is that a model
 * wrote it, and string truncation that looks like a summary silently drops whatever was cut.
 */
public interface MemorySummarizer {

    /**
     * Compresses the supplied turns.
     *
     * @return the summary, or null when none could be produced. Null is a supported answer and
     *         means "keep the verbatim history", not "failed" — the two would otherwise be
     *         indistinguishable to the caller, and only one of them is worth retrying
     */
    Result summarise(UUID conversationId, List<ConversationContext.ContextTurn> turns, String model,
                     com.nexaai.chat.config.ChatProperties.Memory config);

    /**
     * A produced summary.
     *
     * @param summarisedTurnCount how many turns went in, echoed back so the caller does not have
     *                            to keep a second copy of the range it passed
     */
    record Result(String summary, int summarisedTurnCount) {

        public static Result of(String summary, int turns) {
            return new Result(summary, turns);
        }
    }

    /** The no-op implementation used when summarisation is switched off. */
    MemorySummarizer DISABLED = (conversationId, turns, model, config) -> null;
}