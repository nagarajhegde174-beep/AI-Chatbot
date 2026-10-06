package com.nexaai.ai.model;

import java.util.Locale;

/**
 * The three providers NexaAI talks to.
 *
 * <p>Groq speaks the OpenAI wire protocol, so it is the same integration with a different base
 * URL. That is why {@link #OPENAI_COMPATIBLE} exists as a property: it is the fact that decides
 * whether a provider needs its own credential wiring or shares OpenAI's.
 */
public enum ProviderKind {

    OPENAI("OpenAI", true),
    /** Google Gemini. A distinct protocol, so a distinct Spring AI integration. */
    GEMINI("Google Gemini", false),
    /** Groq: Llama and Mixtral models served over the OpenAI-compatible API. */
    GROQ("Groq", true),
    /** Used only in tests, where no real provider is reachable. */
    STUB("Stub", false);

    private final String displayName;
    private final boolean openAiCompatible;

    ProviderKind(String displayName, boolean openAiCompatible) {
        this.displayName = displayName;
        this.openAiCompatible = openAiCompatible;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isOpenAiCompatible() {
        return openAiCompatible;
    }

    /** Parses a configured provider id. Unknown ids are an error, never a silent default. */
    public static ProviderKind parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("provider is required");
        }
        for (ProviderKind kind : values()) {
            if (kind.name().equalsIgnoreCase(value.trim())) {
                return kind;
            }
        }
        throw new IllegalArgumentException(
                "Unknown provider '" + value + "'. Expected one of: OPENAI, GEMINI, GROQ.");
    }

    /** Whether a provider id is one this build knows about. */
    public static boolean isKnown(String value) {
        if (value == null) {
            return false;
        }
        String normalised = value.trim().toUpperCase(Locale.ROOT);
        for (ProviderKind kind : values()) {
            if (kind.name().equals(normalised)) {
                return true;
            }
        }
        return false;
    }
}