package com.nexaai.ai;

import com.nexaai.ai.config.AiProperties;
import com.nexaai.ai.dto.ModelCatalogResponse;
import com.nexaai.ai.service.ModelCatalogService;
import com.nexaai.ai.service.ModelRouter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ModelCatalogService.
 * Verifies that the catalogue is built correctly from configuration and that
 * circuit breaker state is reflected in the "available" flag.
 */
@DisplayName("ModelCatalogService — catalogue building tests")
class ModelCatalogServiceTest {

    @Test
    @DisplayName("catalogue() includes all three providers")
    void catalogue_includes_all_providers() {
        var props = buildProps();
        var router = new ModelRouter(props);
        var service = new ModelCatalogService(props, router);

        ModelCatalogResponse cat = service.catalogue();

        assertThat(cat.providers()).hasSize(3);
        assertThat(cat.providers().stream().map(ModelCatalogResponse.ProviderModels::provider))
                .containsExactlyInAnyOrder("OPENAI", "GEMINI", "GROQ");
    }

    @Test
    @DisplayName("catalogue() marks disabled provider as not available")
    void catalogue_marks_disabled_provider_unavailable() {
        var props = buildProps(); // Gemini and Groq disabled
        var router = new ModelRouter(props);
        var service = new ModelCatalogService(props, router);

        ModelCatalogResponse cat = service.catalogue();

        var gemini = cat.providers().stream()
                .filter(p -> "GEMINI".equals(p.provider()))
                .findFirst().orElseThrow();

        assertThat(gemini.available()).isFalse();
    }

    @Test
    @DisplayName("catalogue() marks open circuit as not available")
    void catalogue_marks_open_circuit_unavailable() {
        var props = buildPropsAllEnabled();
        var router = new ModelRouter(props);

        // Trip the circuit for OPENAI
        for (int i = 0; i < 5; i++) {
            router.recordFailure(com.nexaai.ai.domain.AiProvider.OPENAI);
        }

        var service = new ModelCatalogService(props, router);
        ModelCatalogResponse cat = service.catalogue();

        var openai = cat.providers().stream()
                .filter(p -> "OPENAI".equals(p.provider()))
                .findFirst().orElseThrow();

        assertThat(openai.available()).isFalse();
    }

    @Test
    @DisplayName("catalogue() lists model ids for enabled provider")
    void catalogue_lists_model_ids() {
        var props = buildProps();
        var router = new ModelRouter(props);
        var service = new ModelCatalogService(props, router);

        ModelCatalogResponse cat = service.catalogue();

        var openai = cat.providers().stream()
                .filter(p -> "OPENAI".equals(p.provider()))
                .findFirst().orElseThrow();

        assertThat(openai.models()).hasSize(2);
        assertThat(openai.models().stream().map(ModelCatalogResponse.ModelInfo::id))
                .containsExactlyInAnyOrder("gpt-4o-mini", "gpt-4o");
    }

    // ------------------------------------------------------------------ helpers

    private AiProperties buildProps() {
        return new AiProperties(
                new AiProperties.ProviderConfig(true, "key", null, List.of(
                        new AiProperties.ModelDefinition("gpt-4o-mini", "GPT-4o Mini", 128000, 16384, true),
                        new AiProperties.ModelDefinition("gpt-4o", "GPT-4o", 128000, 16384, true)
                )),
                new AiProperties.ProviderConfig(false, "key", null, List.of(
                        new AiProperties.ModelDefinition("gemini-2.0-flash", "Gemini 2.0 Flash", 1048576, 8192, true)
                )),
                new AiProperties.ProviderConfig(false, "key", null, List.of(
                        new AiProperties.ModelDefinition("llama-3.3-70b-versatile", "LLaMA 3.3 70B", 128000, 32768, true)
                )),
                "OPENAI", "gpt-4o-mini",
                AiProperties.RetryConfig.defaults(),
                AiProperties.CircuitBreakerConfig.defaults()
        );
    }

    private AiProperties buildPropsAllEnabled() {
        return new AiProperties(
                new AiProperties.ProviderConfig(true, "key", null, List.of(
                        new AiProperties.ModelDefinition("gpt-4o-mini", "GPT-4o Mini", 128000, 16384, true)
                )),
                new AiProperties.ProviderConfig(true, "key", null, List.of(
                        new AiProperties.ModelDefinition("gemini-2.0-flash", "Gemini 2.0 Flash", 1048576, 8192, true)
                )),
                new AiProperties.ProviderConfig(true, "key", null, List.of(
                        new AiProperties.ModelDefinition("llama-3.3-70b-versatile", "LLaMA 3.3 70B", 128000, 32768, true)
                )),
                "OPENAI", "gpt-4o-mini",
                AiProperties.RetryConfig.defaults(),
                new AiProperties.CircuitBreakerConfig(5, 30000, 2)
        );
    }
}
