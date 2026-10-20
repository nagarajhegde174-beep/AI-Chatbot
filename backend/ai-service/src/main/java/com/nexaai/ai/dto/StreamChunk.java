package com.nexaai.ai.dto;

/**
 * Single-token chunk pushed over the SSE stream.
 * The event type on the SSE wire is "token" for partial chunks and "done" for
 * the final summary.
 */
public record StreamChunk(
        String requestId,
        String token,      // null on the final "done" chunk
        boolean done,
        CompletionResponse summary  // non-null only when done == true
) {

    /** Factory for a partial token chunk. */
    public static StreamChunk token(String requestId, String token) {
        return new StreamChunk(requestId, token, false, null);
    }

    /** Factory for the terminal "done" chunk that carries usage metadata. */
    public static StreamChunk done(String requestId, CompletionResponse summary) {
        return new StreamChunk(requestId, null, true, summary);
    }
}
