package com.nexaai.chat.service;

/**
 * Approximates token counts.
 *
 * <h2>Why not a real tokenizer</h2>
 *
 * A real tokenizer is per-model, tens of megabytes, and a per-request cost for a number that only
 * ever needs to be an <strong>upper bound</strong>. Using one would make this service depend on
 * the model catalogue to count tokens — and the catalogue belongs to AI Service, not here.
 *
 * <p>At roughly four characters per token, the estimate is deliberately generous for English prose.
 * Overestimating wastes a little context; underestimating produces a provider-side rejection, which
 * is the more expensive of the two errors. The real limit is enforced twice more: AI Service
 * refuses anything that does not fit the model it actually chose, and the provider itself is the
 * final authority.
 *
 * <h2>Why the cost is not just content length</h2>
 *
 * Every turn also costs role and framing tokens at the provider, and a conversation of very short
 * messages pays that per-message overhead many times over. Ignoring it makes a hundred one-word
 * messages look free when they are not — which is exactly the shape of a long conversation that
 * overflows a context window.
 */
public final class TokenEstimate {

    /** Characters per token. Four is the usual English approximation. */
    private static final int CHARS_PER_TOKEN = 4;

    /** Flat cost per turn for role, delimiters and provider framing. */
    private static final int PER_TURN_OVERHEAD_TOKENS = 4;

    private TokenEstimate() {
    }

    /** Approximate tokens for one piece of text, including its per-turn overhead. */
    public static int of(String text) {
        if (text == null || text.isEmpty()) {
            return PER_TURN_OVERHEAD_TOKENS;
        }
        return PER_TURN_OVERHEAD_TOKENS + (int) Math.ceil(text.length() / (double) CHARS_PER_TOKEN);
    }

    /** Approximate tokens for a whole run of turns, oldest first. */
    public static int ofAll(Iterable<String> texts) {
        int total = 0;
        for (String text : texts) {
            total += of(text);
        }
        return total;
    }
}