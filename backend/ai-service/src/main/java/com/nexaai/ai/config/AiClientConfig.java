package com.nexaai.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Creates one ChatModel bean per enabled provider. Groq re-uses the OpenAI
 * client with a different base URL — this is the canonical Spring AI pattern
 * for OpenAI-compatible endpoints.
 *
 * Startup validation: if a provider is enabled but has no key, the application
 * context fails to build (SERVICE_CONTRACTS.md §8.4 AI_NOT_CONFIGURED).
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiClientConfig {

    private static final Logger log = LoggerFactory.getLogger(AiClientConfig.class);

    private static final String DEFAULT_OPENAI_BASE_URL = "https://api.openai.com";

    private final AiProperties props;

    public AiClientConfig(AiProperties props) {
        this.props = props;
    }

    // ------------------------------------------------------------------ OpenAI

    /**
     * Primary ChatModel — OpenAI.
     * Also used as the default when the caller omits a provider.
     */
    @Bean(name = "openAiChatModel")
    @Primary
    public OpenAiChatModel openAiChatModel() {
        AiProperties.ProviderConfig cfg = props.openai();
        if (!cfg.enabled()) {
            log.info("OpenAI provider disabled — bean registered but will refuse requests");
            return createOpenAiModel("disabled-key", resolveOpenAiBaseUrl(cfg));
        }
        requireKey("OPENAI", cfg.apiKey());
        log.info("OpenAI provider configured");
        return createOpenAiModel(cfg.apiKey(), resolveOpenAiBaseUrl(cfg));
    }

    // ------------------------------------------------------------------ Groq

    /**
     * Groq ChatModel. Groq exposes an OpenAI-compatible API; we override the
     * base URL and supply a Groq API key.
     */
    @Bean(name = "groqChatModel")
    public OpenAiChatModel groqChatModel() {
        AiProperties.ProviderConfig cfg = props.groq();
        if (!cfg.enabled()) {
            log.info("Groq provider disabled — bean registered but will refuse requests");
            return createOpenAiModel("disabled-key", resolveGroqBaseUrl(cfg));
        }
        requireKey("GROQ", cfg.apiKey());
        log.info("Groq provider configured (base-url={})", resolveGroqBaseUrl(cfg));
        return createOpenAiModel(cfg.apiKey(), resolveGroqBaseUrl(cfg));
    }

    // ------------------------------------------------------------------ Gemini

    /**
     * Google Gemini via the OpenAI-compatible endpoint.
     * Spring AI 1.0.0-M6 does not ship a dedicated Gemini starter on the
     * milestone repo. The OpenAI-compatible REST API lets us use a single
     * client for all three providers.
     */
    @Bean(name = "geminiChatModel")
    public OpenAiChatModel geminiChatModel() {
        AiProperties.ProviderConfig cfg = props.gemini();
        if (!cfg.enabled()) {
            log.info("Gemini provider disabled — bean registered but will refuse requests");
            return createOpenAiModel("disabled-key", resolveGeminiBaseUrl(cfg));
        }
        requireKey("GEMINI", cfg.apiKey());
        log.info("Gemini provider configured (OpenAI-compat, base-url={})", resolveGeminiBaseUrl(cfg));
        return createOpenAiModel(cfg.apiKey(), resolveGeminiBaseUrl(cfg));
    }

    // ------------------------------------------------------------------ helpers

    private OpenAiChatModel createOpenAiModel(String apiKey, String baseUrl) {
        OpenAiApi api = new OpenAiApi(baseUrl, apiKey);
        return new OpenAiChatModel(api, OpenAiChatOptions.builder().build());
    }

    private static String resolveOpenAiBaseUrl(AiProperties.ProviderConfig cfg) {
        return cfg != null && cfg.baseUrl() != null && !cfg.baseUrl().isBlank()
                ? cfg.baseUrl()
                : DEFAULT_OPENAI_BASE_URL;
    }

    private static String resolveGroqBaseUrl(AiProperties.ProviderConfig cfg) {
        return cfg.baseUrl() != null && !cfg.baseUrl().isBlank()
                ? cfg.baseUrl()
                : "https://api.groq.com/openai/v1";
    }

    private static String resolveGeminiBaseUrl(AiProperties.ProviderConfig cfg) {
        return cfg.baseUrl() != null && !cfg.baseUrl().isBlank()
                ? cfg.baseUrl()
                : "https://generativelanguage.googleapis.com/v1beta/openai";
    }

    /**
     * Throws at startup if a required API key is absent or blank.
     * Logged at ERROR level, but the exception is not swallowed.
     */
    private static void requireKey(String provider, String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "AI Service startup failure: provider " + provider
                            + " is enabled but has no API key configured. "
                            + "Set the corresponding environment variable and restart.");
        }
    }
}
