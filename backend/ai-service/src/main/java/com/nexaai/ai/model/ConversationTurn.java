package com.nexaai.ai.model;

import com.nexaai.ai.web.dto.ContextMessage;
import java.util.ArrayList;
import java.util.List;

/**
 * One turn of conversation, as the router sees it.
 *
 * <p>Separate from {@link ContextMessage} because that one is a wire type with bean-validation
 * annotations and this one is the internal vocabulary. The two are translated once, in
 * {@link #from}, so validation annotations cannot leak into the routing logic and the routing logic
 * cannot accidentally become the contract.
 *
 * <p><strong>{@code SUMMARY} is a role, not a special field.</strong> A summary is context like any
 * other; the only thing that distinguishes it is where it sits (first) and that it is labelled, so
 * the model reads it as recollection rather than as the most recent thing said.
 */
public record ConversationTurn(Kind kind, String content) {

    public enum Kind {
        USER,
        ASSISTANT,
        SUMMARY;

        static Kind from(ContextMessage.Role role) {
            return switch (role) {
                case USER -> USER;
                case ASSISTANT -> ASSISTANT;
                case SUMMARY -> SUMMARY;
            };
        }
    }

    public ConversationTurn {
        // A blank turn is a formatting artefact of a partially-summarised history, not
        // information. Carrying it would spend context on nothing and produce a model response to
        // an empty message.
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("A conversation turn must not be blank.");
        }
    }

    public static ConversationTurn of(ContextMessage.Role role, String content) {
        return new ConversationTurn(Kind.from(role), content);
    }

    /** Translates validated wire turns, dropping nothing — validation has already run. */
    public static List<ConversationTurn> from(List<ContextMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<ConversationTurn> turns = new ArrayList<>(messages.size());
        for (ContextMessage message : messages) {
            turns.add(of(message.role(), message.content()));
        }
        return List.copyOf(turns);
    }

    /**
     * Approximate token cost, at roughly four characters per token.
     *
     * <p>Deliberately approximate and deliberately generous. A real tokenizer is a dependency and a
     * per-request cost for a number that only ever needs to be an upper bound: overestimating
     * wastes a little context, and underestimating sends a request the provider rejects. Over the
     * whole assembled prompt this ratio is well inside the margin the provider itself enforces.
     */
    public int estimatedTokens() {
        return content.length() / 4;
    }

    /**
     * Total approximate tokens of a run of turns.
     *
     * <p>Static so callers can account for a whole prompt without building a throwaway list.
     */
    public static int estimatedTokens(List<ConversationTurn> turns) {
        int total = 0;
        for (ConversationTurn turn : turns) {
            total += turn.estimatedTokens();
        }
        return total;
    }
}