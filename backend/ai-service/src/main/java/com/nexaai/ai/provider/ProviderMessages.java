package com.nexaai.ai.provider;

import com.nexaai.ai.model.ConversationTurn;
import com.nexaai.ai.model.ModelDescriptor;
import java.time.Duration;
import java.util.List;

/**
 * One turn's request to a provider, and one turn's result.
 *
 * <p>Deliberately not Spring AI types at this boundary. Spring AI's {@code Prompt} and
 * {@code ChatResponse} are the abstraction <em>inside</em> the provider client; letting them out
 * would tie the router, the controllers and the tests to a library version. These two records are
 * the service's own vocabulary, and the translation happens in exactly one class.
 *
 * <p><strong>Conversation turns, not one user string.</strong> A model asked to answer a question
 * in the context of a conversation is a different call from a model asked to answer a question, and
 * collapsing them at this boundary would make the distinction unrepresentable.
 */
public final class ProviderMessages {

    private ProviderMessages() {
    }

    /**
     * A generation request.
     *
     * @param systemMessage optional system instruction. Never sourced from the caller: a caller
     *                      who could set the system message could instruct the model directly,
     *                      which is prompt injection with an HTTP parameter
     * @param turns         the conversation, oldest first, with the turn being answered last
     * @param temperature   null means "use the client's default", which is not the same as a
     *                      guessed default — a null here must not become 0.7 somewhere downstream
     * @param maxOutputTokens clamped to the model's limit and the platform ceiling by the router
     */
    public record GenerationRequest(
            ModelDescriptor model,
            String systemMessage,
            List<ConversationTurn> turns,
            Double temperature,
            Integer maxOutputTokens) {

        public GenerationRequest {
            turns = turns == null ? List.of() : List.copyOf(turns);
        }
    }

    /**
     * A completed generation.
     *
     * @param inputTokens  from the provider's own usage metadata. Null when the provider did not
     *                     report it, which is different from zero and is never invented
     * @param outputTokens as above
     */
    public record GenerationResult(
            String content,
            ModelDescriptor model,
            Integer inputTokens,
            Integer outputTokens,
            Duration elapsed) {
    }
}