package com.nexaai.chat.service;

import com.nexaai.chat.domain.Message;

import java.util.List;

/**
 * Port for AI Service summarization calls (Phase 5).
 *
 * <p>Chat Service sends a list of conversation turns that should be
 * compressed into a rolling context summary. The AI Service owns the
 * actual provider call; Chat Service only sees the resulting string.
 *
 * <p>This interface makes the dependency easy to stub in tests.
 */
public interface AiSummarizationClient {

    /**
     * Summarizes a set of conversation turns into a compact context blob.
     *
     * @param existingSummary the current rolling summary (may be null/blank)
     * @param messages        the ordered message turns to compress
     * @param preferredModel  hint for the model to use; may be ignored by the impl
     * @return the new summary text, or null/blank if summarization failed
     */
    String summarize(String existingSummary, List<Message> messages, String preferredModel);
}
