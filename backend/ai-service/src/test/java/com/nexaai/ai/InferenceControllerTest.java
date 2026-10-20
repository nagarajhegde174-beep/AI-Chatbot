package com.nexaai.ai;

import com.nexaai.ai.config.AiProperties;
import com.nexaai.ai.config.AiClientConfig;
import com.nexaai.ai.config.KafkaConfig;
import com.nexaai.ai.config.SecurityConfig;
import com.nexaai.ai.controller.InferenceController;
import com.nexaai.ai.controller.ModelController;
import com.nexaai.ai.dto.CompletionRequest;
import com.nexaai.ai.dto.CompletionResponse;
import com.nexaai.ai.domain.FinishReason;
import com.nexaai.ai.service.AiInferenceService;
import com.nexaai.ai.service.InferenceEventPublisher;
import com.nexaai.ai.service.ModelCatalogService;
import com.nexaai.ai.service.ModelRouter;
import com.nexaai.ai.service.ProviderHealthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Slice test for the controller layer. Uses WebFluxTest to load only the web
 * layer; services are mocked with @MockBean.
 *
 * Also verifies:
 * - /internal/v1/ai/** paths return 200 (permitted)
 * - Validation failures return 400
 * - Unknown routes return 403 (denyAll)
 */
@WebFluxTest(controllers = {InferenceController.class, ModelController.class})
@Import({SecurityConfig.class})
@ActiveProfiles("test")
@DisplayName("InferenceController — WebFlux slice tests")
class InferenceControllerTest {

    @Autowired
    WebTestClient client;

    @MockBean AiInferenceService inferenceService;
    @MockBean ModelCatalogService catalogService;
    @MockBean ProviderHealthService healthService;

    // ------------------------------------------------------------------ /completions

    @Test
    @DisplayName("POST /internal/v1/ai/completions — valid request returns 200")
    void completions_valid_returns_200() {
        var response = new CompletionResponse(
                "req-1", "OPENAI", "gpt-4o-mini", "Hello!", FinishReason.STOP,
                new CompletionResponse.TokenUsage(10, 5, 15), 800L);

        when(inferenceService.complete(any())).thenReturn(Mono.just(response));

        client.post().uri("/internal/v1/ai/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validRequest())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.provider").isEqualTo("OPENAI")
                .jsonPath("$.content").isEqualTo("Hello!")
                .jsonPath("$.usage.totalTokens").isEqualTo(15);
    }

    @Test
    @DisplayName("POST /internal/v1/ai/completions — missing maxOutputTokens returns 400")
    void completions_missing_max_tokens_returns_400() {
        client.post().uri("/internal/v1/ai/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "provider": "OPENAI",
                          "model": "gpt-4o-mini",
                          "messages": [{"role":"USER","content":"hi"}],
                          "requestId": "abc-123"
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("POST /internal/v1/ai/completions — empty messages list returns 400")
    void completions_empty_messages_returns_400() {
        client.post().uri("/internal/v1/ai/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "provider": "OPENAI",
                          "model": "gpt-4o-mini",
                          "messages": [],
                          "maxOutputTokens": 2048,
                          "requestId": "abc-123"
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ------------------------------------------------------------------ /stream

    @Test
    @DisplayName("POST /internal/v1/ai/stream — valid request returns SSE stream")
    void stream_valid_request() {
        when(inferenceService.stream(any())).thenReturn(Flux.empty());

        client.post().uri("/internal/v1/ai/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(validRequest())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);
    }

    // ------------------------------------------------------------------ /models

    @Test
    @DisplayName("GET /internal/v1/ai/models — returns model catalogue")
    void models_returns_catalogue() {
        when(catalogService.catalogue()).thenReturn(
                new com.nexaai.ai.dto.ModelCatalogResponse(List.of()));

        client.get().uri("/internal/v1/ai/models")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.providers").isArray();
    }

    // ------------------------------------------------------------------ security

    @Test
    @DisplayName("GET /api/v1/anything — returns 401 (unauthenticated; denyAll triggers 401 before 403)")
    void public_routes_denied() {
        // Spring Security WebFlux returns 401 for unauthenticated requests when
        // http-basic is not disabled. Our SecurityConfig uses denyAll() which
        // triggers the default 401 bearer challenge for unauthenticated callers.
        client.get().uri("/api/v1/anything")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("GET /actuator/health — is not forbidden by the security filter")
    void actuator_health_not_forbidden() {
        // In a WebFluxTest slice the actuator endpoint may not be present,
        // but the request must NOT be blocked by the security filter (403).
        // Expect anything except 403.
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().value(status -> {
                    if (status == 403) {
                        throw new AssertionError("Expected /actuator/health not to be blocked by security (403)");
                    }
                });
    }

    // ------------------------------------------------------------------ helpers

    private String validRequest() {
        return """
                {
                  "provider": "OPENAI",
                  "model": "gpt-4o-mini",
                  "messages": [{"role":"USER","content":"Hello"}],
                  "maxOutputTokens": 2048,
                  "requestId": "%s"
                }
                """.formatted(UUID.randomUUID());
    }
}
