package com.nexaai.ai.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;

/**
 * Strongly-typed configuration for all three AI providers.
 *
 * Bound from "nexaai.ai.*" in application.yml.
 * Validated at startup: if a provider is enabled, its key must be non-blank.
 * The service refuses to start rather than fail silently at request time.
 */
@ConfigurationProperties(prefix = "nexaai.ai")
@Validated
public record AiProperties(

        @NotNull
        ProviderConfig openai,

        @NotNull
        ProviderConfig gemini,

        @NotNull
        ProviderConfig groq,

        /** The provider used when the caller does not specify one. */
        @NotBlank
        String defaultProvider,

        /** The model within the default provider. */
        @NotBlank
        String defaultModel,

        /**
         * Per-provider retry settings. Key is the lowercase provider name.
         * Falls back to the global retry defaults when absent.
         */
        RetryConfig retry,

        /** Circuit breaker configuration. */
        CircuitBreakerConfig circuitBreaker
) {

    public record ProviderConfig(
            /** Whether this provider is considered active at all. */
            boolean enabled,

            /**
             * The API key. Required when enabled == true.
             * Injected from the environment — never a literal value in YAML.
             */
            String apiKey,

            /**
             * Override the base URL. Used to point the OpenAI client at Groq
             * or any other OpenAI-compatible endpoint.
             */
            String baseUrl,

            /** Models available for this provider, by ascending tier. */
            List<ModelDefinition> models
    ) {}

    public record ModelDefinition(
            String id,
            String displayName,
            int maxContextTokens,
            int maxOutputTokens,
            boolean supportsTemperature
    ) {}

    public record RetryConfig(
            int maxAttempts,
            long initialBackoffMs,
            long maxBackoffMs,
            double multiplier
    ) {
        public static RetryConfig defaults() {
            return new RetryConfig(3, 500, 5000, 2.0);
        }
    }

    public record CircuitBreakerConfig(
            int failureThreshold,
            long waitDurationMs,
            int successThreshold
    ) {
        public static CircuitBreakerConfig defaults() {
            return new CircuitBreakerConfig(5, 30_000, 2);
        }
    }
}
