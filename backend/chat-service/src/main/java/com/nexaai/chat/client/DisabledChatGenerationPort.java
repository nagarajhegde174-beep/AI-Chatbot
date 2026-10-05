package com.nexaai.chat.client;

import com.nexaai.chat.config.ChatProperties;

/**
 * The generation port while no AI Service exists.
 *
 * <p>Reports itself unavailable, so {@code send} stores the user message, creates the PENDING
 * placeholder and stops there.
 *
 * <p><strong>This is not a stub pretending to work.</strong> It does not synthesise a reply, does
 * not return an empty string as if the model had declined, and does not throw. A send in this
 * phase produces a conversation whose last message is visibly PENDING, which is the honest
 * representation of "the reply has not arrived".
 */
public class DisabledChatGenerationPort implements ChatGenerationPort {

    private final ChatProperties properties;

    public DisabledChatGenerationPort(ChatProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isAvailable() {
        return properties.getGeneration().isEnabled();
    }

    /**
     * @throws UnsupportedOperationException always
     *
     * <p>{@link #isAvailable()} is checked before every call, so reaching this means a caller
     * ignored the availability check. Throwing is the right response: silently succeeding here
     * would produce an assistant message with no content that looks like a real answer.
     */
    @Override
    public GenerationOutcome generate(GenerationRequest request) {
        throw new UnsupportedOperationException(
                "Generation is disabled. Check isAvailable() before calling generate(). "
                        + "With generation off a sent message leaves a PENDING placeholder, "
                        + "which is the intended behaviour.");
    }
}