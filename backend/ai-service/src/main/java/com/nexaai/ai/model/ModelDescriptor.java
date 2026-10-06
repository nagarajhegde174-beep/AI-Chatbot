package com.nexaai.ai.model;

import com.nexaai.ai.config.AiProperties;
import java.util.Locale;

/**
 * One model, as the router sees it.
 *
 * <p>An immutable record rather than the mutable configuration object, so nothing downstream can
 * change a limit the catalog already reasoned about.
 *
 * @param supportsTemperature      not every model accepts a temperature. Sending one to a model
 *                                 that ignores it is not an error at the provider, which means it
 *                                 fails silently — so it is refused here instead
 * @param allowCrossProviderFallback whether another <em>provider</em> may answer when this one
 *                                 cannot. Off unless an operator opted in per model
 */
public record ModelDescriptor(
        String name,
        String provider,
        ProviderKind providerKind,
        int priority,
        boolean supportsTemperature,
        int maxInputTokens,
        int maxOutputTokens,
        boolean allowFallback,
        boolean allowCrossProviderFallback) {

    public static ModelDescriptor from(AiProperties.Model configured) {
        return new ModelDescriptor(
                configured.getName(),
                configured.getProvider(),
                ProviderKind.parse(configured.getProvider()),
                configured.getPriority(),
                configured.isSupportsTemperature(),
                configured.getMaxInputTokens(),
                configured.getMaxOutputTokens(),
                configured.isAllowFallback(),
                configured.isAllowCrossProviderFallback());
    }

    /**
     * The name Spring AI expects, which is not always the name callers use.
     *
     * <p>A model is addressed by our own name so that a provider's model identifier can change
     * without breaking Chat Service, which stores the name in a conversation.
     */
    public String providerModelId() {
        return name;
    }

    public String lowerName() {
        return name.toLowerCase(Locale.ROOT);
    }

    /** Whether a prompt of roughly this many tokens could plausibly be within the limit. */
    public boolean canAcceptInput(int estimatedInputTokens) {
        return estimatedInputTokens <= maxInputTokens;
    }
}