package com.nexaai.ai.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A request to compress a run of older conversation turns.
 *
 * <p>Summarisation is a <em>model</em> operation, so it goes through the same router, the same
 * retry policy and the same provider classification as generation. A caller cannot summarise with
 * cheap string manipulation, because the whole value of a summary is that a model wrote it.
 *
 * @param targetTokens what to compress down to. A target rather than a ratio, because the thing
 *                     that actually matters is the size of the result relative to the model's
 *                     input limit, and a ratio cannot express that
 * @param messages     the turns to compress, oldest first. Order matters: a summary built from a
 *                     shuffled conversation is a summary of a conversation that never happened
 */
public record SummarizeRequest(

        @Size(max = 128)
        String model,

        @NotEmpty(message = "messages is required and must not be empty")
        @Size(max = 200, message = "messages must contain at most 200 entries")
        List<@Valid ContextMessage> messages,

        @Min(value = 50, message = "targetTokens must be at least 50")
        @Max(value = 4000, message = "targetTokens must be at most 4000")
        Integer targetTokens,

        @Min(0)
        @Max(2)
        Double temperature) {

    /** The token size to compress to, defaulting when the caller names none. */
    public int targetTokensOrDefault(int fallback) {
        return targetTokens == null ? fallback : targetTokens;
    }

    /**
     * The turns to compress, as text.
     *
     * <p>Prefixed by role so the model can tell who said what. Without that, "yes" in a summary
     * request is ambiguous, and a summary that attributes a question to the wrong speaker is worse
     * than no summary at all.
     */
    public String transcript() {
        StringBuilder out = new StringBuilder();
        for (ContextMessage message : messages) {
            out.append(message.role().name())
                    .append(": ")
                    .append(message.content())
                    .append("\n\n");
        }
        return out.toString().trim();
    }
}