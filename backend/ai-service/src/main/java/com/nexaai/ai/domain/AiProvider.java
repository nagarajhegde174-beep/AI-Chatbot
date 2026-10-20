package com.nexaai.ai.domain;

/**
 * Supported AI providers. Each maps to a Spring AI ChatClient configuration.
 * Groq uses OpenAI-compatible API via a base-url override.
 */
public enum AiProvider {
    OPENAI,
    GEMINI,
    GROQ
}
