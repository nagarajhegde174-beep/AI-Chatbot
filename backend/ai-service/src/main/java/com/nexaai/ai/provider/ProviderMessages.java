package com.nexaai.ai.provider;

import com.nexaai.ai.model.ModelDescriptor;
import java.time.Duration;

/**
 * One turn's request to a provider, and one turn's result.
 *
 * <p>Deliberately not Spring AI types at this boundary. Spring AI's {@code Prompt} and
 * {@code ChatResponse} are the abstraction <em>inside</em> the provider client; letting them out
 * would tie the router, the controllers and the tests to a library version. These two records are
 * the service's own vocabulary, and the translation happens in exactly one class.
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
     */
    public record GenerationRequest(
            ModelDescriptor model,
            String systemMessage,
            String userContent,
            Double temperature,
            Integer maxOutputTokens) {
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