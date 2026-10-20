package com.nexaai.ai;

import com.nexaai.ai.config.AiProperties;
import com.nexaai.ai.domain.AiProvider;
import com.nexaai.ai.domain.FinishReason;
import com.nexaai.ai.dto.CompletionRequest;
import com.nexaai.ai.service.AiInferenceService;
import com.nexaai.ai.service.InferenceEventPublisher;
import com.nexaai.ai.service.ModelRouter;
import com.nexaai.ai.exception.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AiInferenceService.
 * Uses doReturn(...).when(...) to avoid Mockito's ambiguous overload resolution
 * with OpenAiChatModel.call(Prompt) vs call(Message...).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AiInferenceService — unit tests")
class AiInferenceServiceTest {

    @Mock OpenAiChatModel openAiModel;
    @Mock OpenAiChatModel groqModel;
    @Mock OpenAiChatModel geminiModel;
    @Mock InferenceEventPublisher eventPublisher;

    AiInferenceService service;
    ModelRouter router;
    AiProperties props;

    @BeforeEach
    void setUp() {
        props = buildTestProperties();
        router = new ModelRouter(props);
        service = new AiInferenceService(openAiModel, groqModel, geminiModel, router, eventPublisher, props);
    }

    // ------------------------------------------------------------------ complete()

    @Test
    @DisplayName("complete() returns a valid CompletionResponse for OpenAI")
    void complete_openai_success() {
        ChatResponse fakeResponse = fakeChatResponse("Hello from GPT!");
        // Use doReturn to sidestep Mockito's ambiguous overload resolution
        doReturn(fakeResponse).when(openAiModel).call(any(Prompt.class));

        var req = buildRequest("OPENAI", "gpt-4o-mini", "Say hi");

        StepVerifier.create(service.complete(req))
                .assertNext(r -> {
                    assertThat(r.provider()).isEqualTo("OPENAI");
                    assertThat(r.model()).isEqualTo("gpt-4o-mini");
                    assertThat(r.content()).isEqualTo("Hello from GPT!");
                    assertThat(r.finishReason()).isEqualTo(FinishReason.STOP);
                    assertThat(r.requestId()).isEqualTo(req.requestId());
                })
                .verifyComplete();

        verify(eventPublisher).publish(eq(req.requestId()), eq(AiProvider.OPENAI),
                eq("gpt-4o-mini"), anyInt(), anyInt(), anyLong(), eq(true));
    }

    @Test
    @DisplayName("complete() throws AiNotConfiguredException for unknown provider")
    void complete_unknown_provider() {
        var req = buildRequest("UNKNOWN_PROVIDER", "gpt-4o-mini", "hi");
        StepVerifier.create(service.complete(req))
                .expectErrorMatches(ex -> ex instanceof AiNotConfiguredException)
                .verify();
    }

    @Test
    @DisplayName("complete() throws AiModelNotFoundException for unlisted model")
    void complete_unknown_model() {
        var req = buildRequest("OPENAI", "gpt-99-ultra", "hi");
        StepVerifier.create(service.complete(req))
                .expectErrorMatches(ex -> ex instanceof AiModelNotFoundException)
                .verify();
    }

    @Test
    @DisplayName("complete() classifies provider RuntimeException as AiProviderException")
    void complete_provider_error_classified() {
        doThrow(new RuntimeException("Internal server error"))
                .when(openAiModel).call(any(Prompt.class));

        var req = buildRequest("OPENAI", "gpt-4o-mini", "hi");
        StepVerifier.create(service.complete(req))
                .expectErrorMatches(ex -> ex instanceof AiProviderException)
                .verify();
    }

    @Test
    @DisplayName("complete() classifies timeout message as AiTimeoutException")
    void complete_timeout_classified() {
        doThrow(new RuntimeException("timeout after 30s"))
                .when(openAiModel).call(any(Prompt.class));

        var req = buildRequest("OPENAI", "gpt-4o-mini", "hi");
        StepVerifier.create(service.complete(req))
                .expectErrorMatches(ex -> ex instanceof AiTimeoutException)
                .verify();
    }

    @Test
    @DisplayName("complete() classifies rate limit error as AiRateLimitedException")
    void complete_rate_limit_classified() {
        doThrow(new RuntimeException("rate limit exceeded: 429 too many requests"))
                .when(openAiModel).call(any(Prompt.class));

        var req = buildRequest("OPENAI", "gpt-4o-mini", "hi");
        StepVerifier.create(service.complete(req))
                .expectErrorMatches(ex -> ex instanceof AiRateLimitedException)
                .verify();
    }

    // ------------------------------------------------------------------ stream()

    @Test
    @DisplayName("stream() emits token chunks and terminates with done=true")
    void stream_emits_tokens_then_done() {
        Flux<ChatResponse> chunks = Flux.just(
                fakeChatResponse("Hello"),
                fakeChatResponse(" world"),
                fakeChatResponse("!")
        );
        doReturn(chunks).when(openAiModel).stream(any(Prompt.class));

        var req = buildRequest("OPENAI", "gpt-4o-mini", "Greet me");

        StepVerifier.create(service.stream(req))
                .assertNext(chunk -> {
                    assertThat(chunk.done()).isFalse();
                    assertThat(chunk.token()).isEqualTo("Hello");
                })
                .assertNext(chunk -> {
                    assertThat(chunk.done()).isFalse();
                    assertThat(chunk.token()).isEqualTo(" world");
                })
                .assertNext(chunk -> {
                    assertThat(chunk.done()).isFalse();
                    assertThat(chunk.token()).isEqualTo("!");
                })
                .assertNext(chunk -> {
                    assertThat(chunk.done()).isTrue();
                    assertThat(chunk.summary()).isNotNull();
                    assertThat(chunk.summary().content()).isEqualTo("Hello world!");
                })
                .verifyComplete();
    }

    // ------------------------------------------------------------------ helpers

    private CompletionRequest buildRequest(String provider, String model, String userMsg) {
        return new CompletionRequest(
                provider, model, null,
                List.of(new CompletionRequest.CompletionMessage("USER", userMsg)),
                0.7, 2048,
                UUID.randomUUID().toString()
        );
    }

    private ChatResponse fakeChatResponse(String text) {
        var output = new AssistantMessage(text);
        var generation = new Generation(output);
        return new ChatResponse(List.of(generation));
    }

    private AiProperties buildTestProperties() {
        var openaiModel = new AiProperties.ModelDefinition(
                "gpt-4o-mini", "GPT-4o Mini", 128000, 16384, true);
        var groqModel = new AiProperties.ModelDefinition(
                "llama-3.3-70b-versatile", "LLaMA 3.3 70B", 128000, 32768, true);
        var geminiModel = new AiProperties.ModelDefinition(
                "gemini-2.0-flash", "Gemini 2.0 Flash", 1048576, 8192, true);

        return new AiProperties(
                new AiProperties.ProviderConfig(true, "test-openai-key", null, List.of(openaiModel)),
                new AiProperties.ProviderConfig(false, "test-gemini-key", null, List.of(geminiModel)),
                new AiProperties.ProviderConfig(false, "test-groq-key", null, List.of(groqModel)),
                "OPENAI", "gpt-4o-mini",
                AiProperties.RetryConfig.defaults(),
                AiProperties.CircuitBreakerConfig.defaults()
        );
    }
}
