package com.nexaai.chat.service;

import com.nexaai.chat.domain.MessageRole;
import java.util.List;

/**
 * The context selected for one generation, and how it was chosen.
 *
 * <p><strong>Selection is reported, not silent.</strong * The caller is told how many turns went
 * verbatim, how many were dropped, and whether the conversation has been compressed. A user whose
 * conversation has quietly stopped remembering anything should be able to see that, and an operator
 * diagnosing a context bug should not have to add logging to find out.
 *
 * @param turns            oldest first, with the turn being answered last
 * @param verbatimTurns    how many of those were sent unaltered
 * @param droppedTurns     how many older turns did not fit and were not summarised either
 * @param summarised       whether a compressed summary is leading the context
 * @param estimatedTokens  what the whole context was judged to cost
 * @param budget           the budget it was measured against
 * @param truncated        true when older turns were dropped to fit
 */
public record ConversationContext(
        List<ContextTurn> turns,
        int verbatimTurns,
        int droppedTurns,
        boolean summarised,
        int estimatedTokens,
        int budget,
        boolean truncated) {

    /** Whether anything was left behind, which the UI shows as a memory indicator. */
    public boolean hasCompressedHistory() {
        return summarised || droppedTurns > 0;
    }

    /** One turn of selected context, with the role the model will see. */
    public record ContextTurn(MessageRole role, String content, boolean fromSummary) {
    }
}