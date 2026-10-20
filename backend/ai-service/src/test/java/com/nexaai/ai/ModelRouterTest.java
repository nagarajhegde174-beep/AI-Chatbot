package com.nexaai.ai;

import com.nexaai.ai.config.AiProperties;
import com.nexaai.ai.domain.AiProvider;
import com.nexaai.ai.exception.AiCircuitOpenException;
import com.nexaai.ai.exception.AiModelNotFoundException;
import com.nexaai.ai.exception.AiNotConfiguredException;
import com.nexaai.ai.service.ModelRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ModelRouter — circuit breaker state machine, model
 * validation, and availability checks.
 */
@DisplayName("ModelRouter — circuit breaker and model validation")
class ModelRouterTest {

    ModelRouter router;
    AiProperties props;

    @BeforeEach
    void setUp() {
        props = buildProps(3, 50L); // threshold=3, wait=50ms
        router = new ModelRouter(props);
    }

    // ------------------------------------------------------------------ validation

    @Test
    @DisplayName("validate() succeeds for a known enabled provider + model")
    void validate_success() {
        // should not throw
        router.validate(AiProvider.OPENAI, "gpt-4o-mini");
    }

    @Test
    @DisplayName("validate() throws AiNotConfiguredException for a disabled provider")
    void validate_disabled_provider() {
        assertThatThrownBy(() -> router.validate(AiProvider.GEMINI, "gemini-2.0-flash"))
                .isInstanceOf(AiNotConfiguredException.class);
    }

    @Test
    @DisplayName("validate() throws AiModelNotFoundException for unknown model")
    void validate_unknown_model() {
        assertThatThrownBy(() -> router.validate(AiProvider.OPENAI, "gpt-99-ultra"))
                .isInstanceOf(AiModelNotFoundException.class);
    }

    // ------------------------------------------------------------------ circuit breaker

    @Test
    @DisplayName("Circuit is CLOSED initially — isAvailable returns true")
    void circuit_starts_closed() {
        assertThat(router.isAvailable(AiProvider.OPENAI)).isTrue();
    }

    @Test
    @DisplayName("Circuit opens after threshold failures")
    void circuit_opens_after_threshold() {
        for (int i = 0; i < 3; i++) {
            router.recordFailure(AiProvider.OPENAI);
        }
        assertThat(router.isAvailable(AiProvider.OPENAI)).isFalse();
    }

    @Test
    @DisplayName("validate() throws AiCircuitOpenException when circuit is open")
    void validate_throws_when_circuit_open() {
        for (int i = 0; i < 3; i++) {
            router.recordFailure(AiProvider.OPENAI);
        }
        assertThatThrownBy(() -> router.validate(AiProvider.OPENAI, "gpt-4o-mini"))
                .isInstanceOf(AiCircuitOpenException.class);
    }

    @Test
    @DisplayName("Circuit closes (becomes available) after wait duration")
    void circuit_half_opens_after_wait() throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            router.recordFailure(AiProvider.OPENAI);
        }
        assertThat(router.isAvailable(AiProvider.OPENAI)).isFalse();

        Thread.sleep(60); // wait > 50ms configured wait

        // After wait, circuit should allow a probe (HALF_OPEN)
        assertThat(router.isAvailable(AiProvider.OPENAI)).isTrue();
    }

    @Test
    @DisplayName("recordSuccess() resets failure count and closes the circuit")
    void record_success_closes_circuit() {
        for (int i = 0; i < 3; i++) {
            router.recordFailure(AiProvider.OPENAI);
        }
        // manually let wait expire
        router.recordSuccess(AiProvider.OPENAI);
        // After success, circuit should be CLOSED
        assertThat(router.isAvailable(AiProvider.OPENAI)).isTrue();
    }

    @Test
    @DisplayName("findModel() returns the correct ModelDefinition")
    void find_model_returns_definition() {
        var def = router.findModel(AiProvider.OPENAI, "gpt-4o-mini");
        assertThat(def).isPresent();
        assertThat(def.get().displayName()).isEqualTo("GPT-4o Mini");
        assertThat(def.get().maxOutputTokens()).isEqualTo(16384);
    }

    @Test
    @DisplayName("findModel() returns empty for an unknown model")
    void find_model_unknown() {
        var def = router.findModel(AiProvider.OPENAI, "gpt-unknown");
        assertThat(def).isEmpty();
    }

    // ------------------------------------------------------------------ helpers

    private AiProperties buildProps(int threshold, long waitMs) {
        var openaiModel = new AiProperties.ModelDefinition(
                "gpt-4o-mini", "GPT-4o Mini", 128000, 16384, true);
        var geminiModel = new AiProperties.ModelDefinition(
                "gemini-2.0-flash", "Gemini 2.0 Flash", 1048576, 8192, true);
        var groqModel = new AiProperties.ModelDefinition(
                "llama-3.3-70b-versatile", "LLaMA 3.3 70B", 128000, 32768, true);

        return new AiProperties(
                new AiProperties.ProviderConfig(true, "key", null, List.of(openaiModel)),
                new AiProperties.ProviderConfig(false, "key", null, List.of(geminiModel)),
                new AiProperties.ProviderConfig(false, "key", null, List.of(groqModel)),
                "OPENAI", "gpt-4o-mini",
                AiProperties.RetryConfig.defaults(),
                new AiProperties.CircuitBreakerConfig(threshold, waitMs, 2)
        );
    }
}
