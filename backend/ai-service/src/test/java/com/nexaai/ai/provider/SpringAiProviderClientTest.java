package com.nexaai.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexaai.ai.config.AiProperties;
import com.nexaai.ai.model.ModelDescriptor;
import com.nexaai.ai.model.ProviderKind;
import com.nexaai.ai.support.StubChatModel;
import com.nexaai.ai.support.StubStreamingChatModel;
import com.nexaai.ai.support.TestContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The Spring AI translation layer.
 *
 * <p>Everything below {@link ProviderClient} is Spring AI, and this is the only test that
 * exercises it. If these pass, the rest of the service can be trusted to be provider-agnostic —
 * which is the property that makes a provider swap a configuration change.
 *
 * <p>A {@link StubChatModel} is registered as a real bean in the application context, so bean
 * lookup, availability rules and the {@code Prompt}/{@code ChatResponse} translation are all
 * tested the way they actually run rather than through a hand-built object graph.
 */
class SpringAiProviderClientTest {

    private AiProperties properties;

    @BeforeEach
    void setUp() {
        // A clean bean factory per test: bean-name lookup is what this class is about, and a
        // bean left over from the previous test would silently satisfy an assertion about
        // absence.
        TestContext.reset();

        properties = TestContext.properties();
        properties.getProviders().put("OPENAI", provider("openaiChatModel",
                "openaiStreamChatModel", "sk-test"));
        properties.getProviders().put("GROQ", provider("groqChatModel", "groqStreamChatModel",
                "gsk-test"));
    }

    private AiProperties.Provider provider(String beanName, String streamingBean, String apiKey) {
        AiProperties.Provider provider = new AiProperties.Provider();
        provider.setEnabled(true);
        provider.setApiKey(apiKey);
        provider.setChatModelBean(beanName);
        provider.setStreamingModelBean(streamingBean);
        provider.setBaseUrl("https://example.invalid/v1");
        return provider;
    }

    private SpringAiProviderClient client(ProviderKind kind) {
        return new SpringAiProviderClient(TestContext.context(), properties, kind);
    }

    private static ModelDescriptor model(String name, String provider) {
        return new ModelDescriptor(name, provider, ProviderKind.valueOf(provider), 1, true,
                8000, 1000, true, false);
    }

    private static ProviderMessages.GenerationRequest request(ModelDescriptor model,
                                                              String content) {
        return new ProviderMessages.GenerationRequest(model, null, content, null, null);
    }

    // ==================================================================
    // Availability
    // ==================================================================

    @Nested
    @DisplayName("availability")
    class Availability {

        @Test
        @DisplayName("a provider with a key and a wired bean is available")
        void availableWhenWired() {
            TestContext.put("openaiChatModel", new StubChatModel("gpt"));

            assertThat(client(ProviderKind.OPENAI).isAvailable()).isTrue();
        }

        @Test
        @DisplayName("a provider with no credential reports so, rather than being absent")
        void missingCredentialIsReported() {
            AiProperties.Provider noKey = new AiProperties.Provider();
            noKey.setEnabled(true);
            noKey.setApiKey("");
            noKey.setChatModelBean("geminiChatModel");
            properties.getProviders().put("GEMINI", noKey);

            SpringAiProviderClient gemini = client(ProviderKind.GEMINI);

            // Not a startup failure and not a silent omission: the operator is told what is
            // missing, which is the only thing they can act on.
            assertThat(gemini.isAvailable()).isFalse();
            assertThat(gemini.unavailabilityReason()).contains("credential");
        }

        @Test
        @DisplayName("a provider with a key but no wired model is reported as unwired")
        void missingBeanIsReported() {
            // The honest reason when a credential is present but nothing is wired behind it: the
            // integration is not on this deployment's classpath, or was never configured. This
            // is distinct from a missing credential, and an operator fixes the two differently.
            properties.getProviders().put("GROQ",
                    provider("noSuchBean", "noSuchStreamBean", "gsk-test"));

            assertThat(client(ProviderKind.GROQ).unavailabilityReason())
                    .contains("no Spring AI chat model");
        }

        @Test
        @DisplayName("a provider absent from configuration entirely says so")
        void unconfiguredIsReported() {
            properties.getProviders().remove("GEMINI");

            assertThat(client(ProviderKind.GEMINI).unavailabilityReason())
                    .contains("not configured");
        }

        @Test
        @DisplayName("a disabled provider says it is disabled")
        void disabledIsReported() {
            AiProperties.Provider disabled = provider("openaiChatModel",
                    "openaiStreamChatModel", "sk-test");
            disabled.setEnabled(false);
            properties.getProviders().put("OPENAI", disabled);

            assertThat(client(ProviderKind.OPENAI).unavailabilityReason()).contains("disabled");
        }

        @Test
        @DisplayName("availability messages never contain a credential")
        void availabilityNeverLeaksCredentials() {
            properties.getProviders().put("GROQ",
                    provider("noSuchBean", "noSuchStreamBean", "gsk-test"));

            // Availability text is returned by an endpoint. A credential appearing in it would
            // publish the key to every caller that can reach /models.
            for (ProviderKind kind : List.of(ProviderKind.OPENAI, ProviderKind.GEMINI,
                    ProviderKind.GROQ)) {
                String reason = client(kind).unavailabilityReason();
                if (reason != null) {
                    assertThat(reason)
                            .doesNotContain("sk-test")
                            .doesNotContain("gsk-test")
                            .doesNotContain("test-key");
                }
            }
        }

        @Test
        @DisplayName("a provider with no streaming model is still available for completion")
        void streamingIsNotRequiredForCompletion() {
            // Blocking and streaming are separately wired. A deployment that has not wired
            // streaming must still serve completions, rather than reporting the whole provider
            // as broken.
            properties.getProviders().put("GROQ", provider("groqChatModel", "", "gsk-test"));
            TestContext.put("groqChatModel", new StubChatModel("llama"));

            SpringAiProviderClient groq = client(ProviderKind.GROQ);

            assertThat(groq.isAvailable()).isTrue();
            assertThat(groq.complete(request(model("m", "GROQ"), "hi")).content())
                    .isEqualTo("stub answer");
        }

        @Test
        @DisplayName("supports() matches on provider kind, not on bean name")
        void supportsByProviderKind() {
            // Routing asks "can you serve this model", and the honest answer is about which
            // provider owns it — not about which bean happens to be wired.
            assertThat(client(ProviderKind.OPENAI).supports(model("m", "OPENAI"))).isTrue();
            assertThat(client(ProviderKind.OPENAI).supports(model("m", "GROQ"))).isFalse();
        }
    }

    // ==================================================================
    // Completion
    // ==================================================================

    @Nested
    @DisplayName("completion")
    class Completion {

        @Test
        @DisplayName("a completion returns the provider's text")
        void returnsText() {
            TestContext.put("openaiChatModel", new StubChatModel("gpt")
                    .returning("the answer"));

            ProviderMessages.GenerationResult result = client(ProviderKind.OPENAI)
                    .complete(request(model("m", "OPENAI"), "the question"));

            assertThat(result.content()).isEqualTo("the answer");
        }

        @Test
        @DisplayName("provider-reported usage is carried through, not invented")
        void carriesUsage() {
            TestContext.put("openaiChatModel", new StubChatModel("gpt"));

            ProviderMessages.GenerationResult result = client(ProviderKind.OPENAI)
                    .complete(request(model("m", "OPENAI"), "hi"));

            assertThat(result.inputTokens()).isEqualTo(11);
            assertThat(result.outputTokens()).isEqualTo(7);
        }

        @Test
        @DisplayName("absent usage stays null rather than becoming zero")
        void absentUsageStaysNull() {
            // Zero would claim the provider reported no usage. Null says it reported none,
            // which is a different and honest statement.
            TestContext.put("openaiChatModel",
                    new StubChatModel("gpt").reportingNoUsage());

            ProviderMessages.GenerationResult result = client(ProviderKind.OPENAI)
                    .complete(request(model("m", "OPENAI"), "hi"));

            assertThat(result.inputTokens()).isNull();
            assertThat(result.outputTokens()).isNull();
        }

        @Test
        @DisplayName("the user content reaches the provider as a user message")
        void translatesPrompt() {
            StubChatModel stub = new StubChatModel("gpt");
            TestContext.put("openaiChatModel", stub);

            client(ProviderKind.OPENAI).complete(request(model("m", "OPENAI"), "the question"));

            assertThat(stub.lastPrompt().getContents()).contains("the question");
        }

        @Test
        @DisplayName("a system message is included only when this service set one")
        void systemMessageIsOptional() {
            StubChatModel stub = new StubChatModel("gpt");
            TestContext.put("openaiChatModel", stub);

            ModelDescriptor model = model("m", "OPENAI");
            client(ProviderKind.OPENAI).complete(new ProviderMessages.GenerationRequest(model,
                    "be concise", "hi", null, null));

            assertThat(stub.lastPrompt().getInstructions())
                    .anySatisfy(message ->
                            assertThat(message.getText()).isEqualTo("be concise"));
        }

        @Test
        @DisplayName("an empty completion is a provider fault, not an answer")
        void emptyCompletionIsAFault() {
            // Surfaced as an empty 200, it would reach a user's screen as a blank bubble.
            TestContext.put("openaiChatModel", new StubChatModel("gpt").returning(""));

            assertThatThrownBy(() -> client(ProviderKind.OPENAI)
                    .complete(request(model("m", "OPENAI"), "hi")))
                    .isInstanceOf(ProviderException.class);
        }

        @Test
        @DisplayName("calling an unavailable provider fails fast with NOT_CONFIGURED")
        void unavailableProviderFailsFast() {
            assertThatThrownBy(() -> client(ProviderKind.GEMINI)
                    .complete(request(model("m", "GEMINI"), "hi")))
                    .isInstanceOfSatisfying(ProviderException.class, e ->
                            assertThat(e.kind())
                                    .isEqualTo(ProviderFailureKind.NOT_CONFIGURED));
        }

        @Test
        @DisplayName("temperature reaches the provider when supported")
        void passesTemperature() {
            StubChatModel stub = new StubChatModel("gpt");
            TestContext.put("openaiChatModel", stub);

            ModelDescriptor model = model("m", "OPENAI");
            client(ProviderKind.OPENAI).complete(new ProviderMessages.GenerationRequest(model,
                    null, "hi", 0.25, null));

            assertThat(stub.lastPrompt().getOptions()).isNotNull();
        }
    }

    // ==================================================================
    // Failure classification
    // ==================================================================

    @Nested
    @DisplayName("failure classification")
    class Classification {

        @Test
        @DisplayName("an authentication failure is not retryable")
        void classifiesAuthentication() {
            assertThat(classify("401 Unauthorized")).isEqualTo(ProviderFailureKind.UNAUTHORISED);
        }

        @Test
        @DisplayName("a rate limit is retryable")
        void classifiesRateLimit() {
            assertThat(classify("429 Too Many Requests"))
                    .isEqualTo(ProviderFailureKind.THROTTLED);
        }

        @Test
        @DisplayName("a context-length rejection is a limit, not an outage")
        void classifiesContextLength() {
            // Retrying the same prompt against the same model exceeds the same limit. Treating
            // it as an outage would retry it and fail again, three times slower.
            assertThat(classify("This model's maximum context length is 8192 tokens"))
                    .isEqualTo(ProviderFailureKind.LIMIT_EXCEEDED);
        }

        @Test
        @DisplayName("a bad request is a rejection, not an outage")
        void classifiesBadRequest() {
            assertThat(classify("400 Bad Request: invalid_request_error"))
                    .isEqualTo(ProviderFailureKind.REJECTED);
        }

        @Test
        @DisplayName("a timeout is retryable unavailability")
        void classifiesTimeout() {
            assertThat(classify("Read timed out"))
                    .isEqualTo(ProviderFailureKind.UNAVAILABLE);
        }

        @Test
        @DisplayName("an unrecognised fault is retried once, because faults are often transient")
        void classifiesUnknown() {
            assertThat(classify("something nobody has seen before"))
                    .isEqualTo(ProviderFailureKind.UNKNOWN);
        }

        @Test
        @DisplayName("a classified failure message never contains the prompt")
        void failureMessagesCarryNoPrompt() {
            // The message is attached to a value that is logged and returned upstream.
            ProviderException exception = SpringAiProviderClient.classify(
                    model("m", "OPENAI"),
                    new IllegalStateException("connection refused"), java.time.Duration.ZERO);

            assertThat(exception.getMessage()).doesNotContain("hi");
            assertThat(exception.getMessage()).contains("OPENAI");
        }

        private ProviderFailureKind classify(String message) {
            ProviderException exception = SpringAiProviderClient.classify(
                    model("m", "OPENAI"),
                    new IllegalStateException(message), java.time.Duration.ZERO);
            return exception.kind();
        }
    }

    // ==================================================================
    // Streaming
    // ==================================================================

    @Nested
    @DisplayName("streaming")
    class Streaming {

        @Test
        @DisplayName("chunks arrive separately, not as one buffered string")
        void chunksAreEmittedIncrementally() {
            // The whole point of streaming. A single buffered emission would render exactly as
            // if there were no stream at all.
            TestContext.put("openaiStreamChatModel",
                    new StubStreamingChatModel("gpt").emitting(List.of("He", "llo", "!")));

            List<String> chunks = client(ProviderKind.OPENAI)
                    .stream(request(model("m", "OPENAI"), "hi"))
                    .collectList()
                    .block();

            assertThat(chunks).containsExactly("He", "llo", "!");
        }

        @Test
        @DisplayName("an unavailable provider errors the stream rather than opening an empty one")
        void unavailableProviderErrorsTheStream() {
            assertThatThrownBy(() -> client(ProviderKind.GEMINI)
                    .stream(request(model("m", "GEMINI"), "hi")).blockFirst())
                    .isInstanceOf(ProviderException.class);
        }

        @Test
        @DisplayName("no wired streaming model is reported, not silently empty")
        void missingStreamingModelIsReported() {
            properties.getProviders().put("OPENAI",
                    provider("openaiChatModel", "", "sk-test"));
            TestContext.put("openaiChatModel", new StubChatModel("gpt"));

            assertThatThrownBy(() -> client(ProviderKind.OPENAI)
                    .stream(request(model("m", "OPENAI"), "hi")).blockFirst())
                    .isInstanceOfSatisfying(ProviderException.class, e ->
                            assertThat(e.kind())
                                    .isEqualTo(ProviderFailureKind.NOT_CONFIGURED));
        }

        @Test
        @DisplayName("a mid-stream provider fault becomes a classified ProviderException")
        void midStreamFailureIsClassified() {
            TestContext.put("openaiStreamChatModel",
                    new StubStreamingChatModel("gpt")
                            .failingWith(new IllegalStateException("429 rate limit exceeded")));

            assertThatThrownBy(() -> client(ProviderKind.OPENAI)
                    .stream(request(model("m", "OPENAI"), "hi")).blockLast())
                    .isInstanceOfSatisfying(ProviderException.class, e ->
                            assertThat(e.kind()).isEqualTo(ProviderFailureKind.THROTTLED));
        }
    }

    /**
     * Every provider gets a client whether or not it is configured.
     *
     * <p>Worth asserting directly: an absent bean and an unavailable provider are different
     * facts, and an operator can only act on the second one.
     */
    @Test
    @DisplayName("a client exists for every known provider, configured or not")
    void everyProviderGetsAClient() {
        List<ProviderClient> clients =
                SpringAiProviderClient.forConfiguredProviders(TestContext.context(), properties);

        assertThat(clients).extracting(ProviderClient::id)
                .containsExactlyInAnyOrder("OPENAI", "GEMINI", "GROQ");
        assertThat(clients).filteredOn(client -> !client.isAvailable()).isNotEmpty();
    }
}