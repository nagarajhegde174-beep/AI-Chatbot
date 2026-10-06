package com.nexaai.ai.provider;

import com.nexaai.ai.config.AiProperties;
import com.nexaai.ai.model.ModelDescriptor;
import com.nexaai.ai.model.ProviderKind;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.ApplicationContext;
import reactor.core.publisher.Flux;

/**
 * The Spring AI-backed provider client.
 *
 * <p><strong>The only class in the service that touches Spring AI types.</strong> Everything above
 * it speaks {@link ProviderMessages}; everything below is the library. That containment is what
 * makes a Spring AI upgrade a change to one file rather than to the router, the controllers and
 * every test.
 *
 * <p><strong>How the Spring AI model is obtained.</strong> A provider's {@link ChatModel} bean is
 * looked up by the name configured for it. When the bean is absent — no credential, or the
 * provider is not enabled for this deployment — the provider reports itself unavailable rather
 * than failing at startup. A provider with no key is a supported configuration, not an error.
 *
 * <p><strong>Provider credentials never leave this class.</strong> The API key is read from
 * configuration to decide configured-or-not and is never logged, never included in an exception
 * message and never returned by an endpoint.
 *
 * <p><strong>Not a {@code @Component}, and the reason is worth keeping.</strong> The constructor
 * takes a {@link ProviderKind}, which component scanning cannot supply, so the annotation would
 * make the context fail to start with "no qualifying bean of type ProviderKind". It is
 * instantiated once per provider by {@code ProviderConfig}, which is also where the "one client
 * per known provider, configured or not" rule lives.
 */
public class SpringAiProviderClient implements ProviderClient {

    private static final Logger log = LoggerFactory.getLogger(SpringAiProviderClient.class);

    private final ApplicationContext context;
    private final Map<String, ChatModel> chatModels = new ConcurrentHashMap<>();
    private final Map<String, StreamingChatModel> streamingModels = new ConcurrentHashMap<>();

    /** Resolved once at construction: this provider's configuration, keyed by enum. */
    private final AiProperties.Provider configured;

    private final ProviderKind kind;

    public SpringAiProviderClient(ApplicationContext context, AiProperties properties,
                                  ProviderKind kind) {
        this.context = context;
        this.kind = kind;
        this.configured = properties.getProviders().entrySet().stream()
                .filter(entry -> ProviderKind.isKnown(entry.getKey()))
                .filter(entry -> ProviderKind.parse(entry.getKey()) == kind)
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);

        // The name and the credential are logged. Neither is a secret: a provider id and the
        // fact that a key is present are operational facts, and the key itself is not.
        log.info("Provider {} configured: enabled={}, credentialPresent={}, baseUrl={}",
                kind, configured != null && configured.isEnabled(),
                configured != null && configured.getApiKey() != null
                        && !configured.getApiKey().isBlank(),
                configured == null ? "<unset>" : configured.getBaseUrl());
    }

    /**
     * Builds one client per configured provider id.
     *
     * <p>Every provider gets a client whether or not it has a credential, so that
     * {@code /models} can say <em>why</em> a provider is unavailable rather than silently
     * omitting it. An absent provider and an unavailable one are different facts.
     */
    public static java.util.List<ProviderClient> forConfiguredProviders(
            ApplicationContext context, AiProperties properties) {
        java.util.List<ProviderClient> clients = new java.util.ArrayList<>();
        for (ProviderKind kind : ProviderKind.values()) {
            if (kind == ProviderKind.STUB) {
                continue;
            }
            clients.add(new SpringAiProviderClient(context, properties, kind));
        }
        return clients;
    }

    @Override
    public String id() {
        return kind.name();
    }

    @Override
    public boolean supports(ModelDescriptor model) {
        return model.providerKind() == kind;
    }

    @Override
    public boolean isAvailable() {
        return unavailabilityReason() == null;
    }

    @Override
    public String unavailabilityReason() {
        if (configured == null) {
            return "Provider " + kind.displayName() + " is not configured.";
        }
        AiProperties.Provider provider = configured;
        if (!provider.isEnabled()) {
            return "Provider " + kind.displayName() + " is disabled for this deployment.";
        }
        if (provider.getApiKey() == null || provider.getApiKey().isBlank()) {
            // Deliberately says a credential is absent without saying where it would come from.
            return "Provider " + kind.displayName() + " has no credential configured.";
        }
        if (resolveChatModel(provider) == null) {
            // The honest reason when the credential is present but no Spring AI bean exists:
            // the provider integration is not on this deployment's classpath or was not wired.
            return "Provider " + kind.displayName() + " has no Spring AI chat model available.";
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Completion
    // ------------------------------------------------------------------

    @Override
    public ProviderMessages.GenerationResult complete(
            ProviderMessages.GenerationRequest request) {

        Instant started = Instant.now();
        ChatModel model = requireChatModel(request.model());

        try {
            ChatResponse response = model.call(toSpringAiPrompt(request));
            ProviderMessages.GenerationResult result =
                    toResult(response, request.model(), started);

            if (result.content() == null || result.content().isBlank()) {
                // An empty completion is a provider fault, not an answer. Surfacing it as an
                // empty 200 would let it reach a user's screen as a blank bubble.
                throw ProviderException.unavailable(request.model(),
                        "Provider returned an empty completion.", null);
            }
            return result;

        } catch (ProviderException e) {
            throw e;
        } catch (Exception e) {
            throw classify(request.model(), e, Duration.between(started, Instant.now()));
        }
    }

    // ------------------------------------------------------------------
    // Streaming
    // ------------------------------------------------------------------

    @Override
    public Flux<String> stream(ProviderMessages.GenerationRequest request) {
        StreamingChatModel model = requireStreamingModel(request.model());

        return Flux.defer(() -> {
            Instant started = Instant.now();
            return model.stream(toSpringAiPrompt(request))
                    .map(SpringAiProviderClient::textOf)
                    .filter(chunk -> chunk != null && !chunk.isEmpty())
                    // doOnError classifies before the router sees it, so a stream failure is a
                    // retryable-or-not decision in exactly one place.
                    .onErrorMap(e -> e instanceof ProviderException ? e
                            : classify(request.model(), e, Duration.between(started, Instant.now())));
        });
    }

    // ------------------------------------------------------------------
    // Translation to and from Spring AI
    // ------------------------------------------------------------------

    /**
     * Builds the Spring AI prompt.
     *
     * <p>The system message is only ever included when this service supplied one. A caller
     * cannot set it: a caller who could would be instructing the model directly, which is prompt
     * injection with an HTTP parameter.
     */
    private Prompt toSpringAiPrompt(ProviderMessages.GenerationRequest request) {
        ChatOptions options = resolveOptions(request);
        java.util.List<Message> messages = messagesOf(request);
        // No Prompt.Builder.options(...): Spring AI's builder takes options at construction.
        return options == null ? new Prompt(messages) : new Prompt(messages, options);
    }

    private java.util.List<Message> messagesOf(ProviderMessages.GenerationRequest request) {
        java.util.List<Message> messages = new java.util.ArrayList<>(2);
        if (request.systemMessage() != null && !request.systemMessage().isBlank()) {
            messages.add(new SystemMessage(request.systemMessage()));
        }
        messages.add(new UserMessage(request.userContent()));
        return messages;
    }

    /**
     * Per-call options, including temperature.
     *
     * <p>Temperature is set only when the model supports it. Passing it to a model that ignores
     * it produces no error at the provider, so it would fail silently — which is why the
     * descriptor's flag is checked here and refused at the boundary if it is unsupported.
     */
    private ChatOptions resolveOptions(ProviderMessages.GenerationRequest request) {
        Double temperature = request.temperature();
        if (temperature != null && !request.model().supportsTemperature()) {
            throw ProviderException.rejected(request.model(),
                    "Model " + request.model().name() + " does not support temperature.", null);
        }
        Integer maxTokens = request.maxOutputTokens();
        // Null options means "use the client's default". Passing null here would override a
        // configured default with nothing.
        if (temperature == null && maxTokens == null) {
            return null;
        }
        org.springframework.ai.openai.OpenAiChatOptions.Builder options =
                org.springframework.ai.openai.OpenAiChatOptions.builder()
                        .model(request.model().providerModelId());
        if (temperature != null) {
            options.temperature(temperature);
        }
        if (maxTokens != null) {
            options.maxTokens(maxTokens);
        }
        return options.build();
    }

    private ProviderMessages.GenerationResult toResult(ChatResponse response,
                                                        ModelDescriptor model,
                                                        Instant started) {
        String content = response == null || response.getResult() == null
                ? null
                : response.getResult().getOutput().getText();

        Usage usage = usageOf(response);
        return new ProviderMessages.GenerationResult(
                content,
                model,
                usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens(),
                Duration.between(started, Instant.now()));
    }

    /** The text of one streamed chunk, or null when it carried metadata only. */
    private static String textOf(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return null;
        }
        AssistantMessage output = response.getResult().getOutput();
        return output == null ? null : output.getText();
    }

    /** Provider-reported usage. Never invented: an absent count stays null. */
    private static Usage usageOf(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        if (response.getMetadata().getUsage() == null) {
            return null;
        }
        Usage usage = response.getMetadata().getUsage();
        return usage.getTotalTokens() == 0 && usage.getPromptTokens() == 0
                && usage.getCompletionTokens() == 0 ? null : usage;
    }

    // ------------------------------------------------------------------
    // Failure classification
    // ------------------------------------------------------------------

    /**
     * Maps an arbitrary provider exception onto a {@link ProviderFailureKind}.
     *
     * <p>Matching on message text is a last resort, and this is where it lives: one method, with
     * a comment saying why, rather than scattered through the router where the same string
     * matching would appear three more times.
     */
    static ProviderException classify(ModelDescriptor model, Throwable cause, Duration elapsed) {
        String text = causeMessage(cause);
        String lower = text == null ? "" : text.toLowerCase(java.util.Locale.ROOT);

        ProviderFailureKind kind;
        if (contains(lower, "401", "unauthorized", "invalid api key", "authentication")) {
            kind = ProviderFailureKind.UNAUTHORISED;
        } else if (contains(lower, "429", "rate limit", "too many requests", "quota")) {
            kind = ProviderFailureKind.THROTTLED;
        } else if (contains(lower, "context length", "too long", "maximum context", "token limit",
                "413")) {
            kind = ProviderFailureKind.LIMIT_EXCEEDED;
        } else if (contains(lower, "400", "bad request", "invalid_request")) {
            kind = ProviderFailureKind.REJECTED;
        } else if (contains(lower, "timeout", "timed out", "connection", "unreachable",
                "unavailable", "503", "502", "500")) {
            kind = ProviderFailureKind.UNAVAILABLE;
        } else {
            kind = ProviderFailureKind.UNKNOWN;
        }

        return new ProviderException(kind, model,
                "Provider " + model.provider() + " call failed (" + kind + ").", cause, elapsed);
    }

    private static boolean contains(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String causeMessage(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current.getMessage() != null) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Bean lookup
    // ------------------------------------------------------------------

    private ChatModel resolveChatModel(AiProperties.Provider provider) {
        String beanName = provider.getChatModelBean();
        if (beanName == null || beanName.isBlank()) {
            return null;
        }
        if (!context.containsBean(beanName)) {
            return null;
        }
        return chatModels.computeIfAbsent(beanName,
                name -> context.getBean(name, ChatModel.class));
    }

    /**
     * The streaming model for a provider.
     *
     * <p>Named by configuration rather than derived from {@code chatModelBean} by appending a
     * suffix. A guessed bean name fails silently: the blocking path works, the provider reports
     * healthy, and streaming is simply absent — which is exactly the failure a test that only
     * exercised completion would never catch.
     */
    private StreamingChatModel resolveStreamingModel(AiProperties.Provider provider) {
        String beanName = provider.getStreamingModelBean();
        if (beanName == null || beanName.isBlank()) {
            return null;
        }
        if (!context.containsBean(beanName)) {
            return null;
        }
        return streamingModels.computeIfAbsent(beanName,
                name -> context.getBean(name, StreamingChatModel.class));
    }

    private ChatModel requireChatModel(ModelDescriptor model) {
        ChatModel client = configured == null ? null : resolveChatModel(configured);
        if (client == null) {
            throw ProviderException.notConfigured(model, unavailabilityReason());
        }
        return client;
    }

    private StreamingChatModel requireStreamingModel(ModelDescriptor model) {
        StreamingChatModel client = configured == null ? null : resolveStreamingModel(configured);
        if (client == null) {
            throw ProviderException.notConfigured(model,
                    unavailabilityReason() == null
                            ? "No streaming model is wired for this provider."
                            : unavailabilityReason());
        }
        return client;
    }
}