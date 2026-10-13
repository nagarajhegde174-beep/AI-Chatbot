package com.nexaai.ai.web;

import com.nexaai.ai.model.ConversationTurn;
import com.nexaai.ai.model.ModelDescriptor;
import com.nexaai.ai.provider.ProviderClient;
import com.nexaai.ai.provider.ProviderException;
import com.nexaai.ai.provider.ProviderFailureKind;
import com.nexaai.ai.routing.ModelRouter;
import com.nexaai.ai.usage.UsageRecord;
import com.nexaai.ai.usage.UsageSink;
import com.nexaai.ai.web.dto.GenerateRequest;
import com.nexaai.ai.web.dto.GenerateResponse;
import com.nexaai.ai.web.dto.SummarizeRequest;
import com.nexaai.ai.web.dto.SummarizeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * The AI Service API.
 *
 * <p>Under {@code /internal/v1/ai} because this is a service-to-service surface. It is not the
 * public API: the browser talks to Chat Service, and Chat Service talks to this. Keeping the
 * prefix honest is what stops someone later adding a gateway route to it and publishing every
 * model in the platform.
 *
 * <p><strong>Three endpoints, and the third exists.</strong> A router that can only be inspected
 * when something is broken is a router nobody maintains. Availability and health are first-class
 * reads.
 */
@RestController
@RequestMapping("/internal/v1/ai")
@Tag(name = "AI", description = "Stateless multi-model inference. Service-to-service only.")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final ModelRouter router;
    private final UsageSink usage;

    public AiController(ModelRouter router, UsageSink usage) {
        this.router = router;
        this.usage = usage;
    }

    // ------------------------------------------------------------------
    // Completion
    // ------------------------------------------------------------------

    @PostMapping(value = "/generate", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Generate a completion",
            description = "Answers `message` in the context of `messages`. The server always "
                    + "appends `message` as the final turn, so a caller cannot reorder history or "
                    + "send a conversation whose last turn is not the question. Selects a model, "
                    + "retries transient provider failures, and falls back to another model on "
                    + "the same provider when the first cannot serve the request.")
    public GenerateResponse generate(@Valid @RequestBody GenerateRequest request) {
        String requestId = UUID.randomUUID().toString();

        ModelDescriptor model = router.select(request.modelOrNull());
        List<ConversationTurn> turns = ConversationTurn.from(request.contextWithMessage());

        ModelRouter.RoutedResult routed = router.complete(requestId, model.name(), null, turns,
                request.temperature(), request.maxOutputTokens());

        return GenerateResponse.from(requestId, routed.result().content(), routed.model(),
                routed.fallbackUsed(), false, routed.result().inputTokens(),
                routed.result().outputTokens(), routed.result().elapsed().toMillis());
    }

    // ------------------------------------------------------------------
    // Summarisation
    // ------------------------------------------------------------------

    /**
     * Compresses a run of older turns into a summary.
     *
     * <p><strong>A model call, not string manipulation.</strong> The whole value of a summary is
     * that a model wrote it; a caller who could truncate or concatenate would produce something
     * that looks like a summary and silently loses whatever the truncation dropped.
     *
     * <p><strong>Nothing is persisted here.</strong> This service is stateless and holds no
     * conversation, so it cannot remember that it produced a summary, and a summary it did not ask
     * for is one it cannot clean up. Chat Service owns the memory and decides when to refresh it.
     */
    @PostMapping(value = "/summarize", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Compress older conversation turns",
            description = "Returns a summary of the supplied turns. The caller stores it; this "
                    + "service holds no conversation state.")
    public SummarizeResponse summarize(@Valid @RequestBody SummarizeRequest request) {
        String requestId = UUID.randomUUID().toString();
        List<ConversationTurn> turns = ConversationTurn.from(request.messages());
        Instant started = Instant.now();

        ModelRouter.RoutedResult routed = router.summarize(requestId, request.model(), turns,
                request.targetTokensOrDefault(400), request.temperature());

        String summary = routed.result().content();
        if (summary == null || summary.isBlank()) {
            // Recorded as a failure rather than returned as an empty summary. An empty summary is
            // the worst outcome available: the caller stores it, believes the context is covered,
            // and stops sending the turns it just discarded.
            throw new ProviderException(ProviderFailureKind.UNKNOWN, routed.model(),
                    "Summarisation returned an empty result.", null, java.time.Duration.ZERO);
        }

        return new SummarizeResponse(
                requestId,
                summary.trim(),
                routed.model().name(),
                routed.model().provider(),
                turns.size(),
                summary.trim().length(),
                routed.result().inputTokens(),
                routed.result().outputTokens(),
                Duration.between(started, Instant.now()).toMillis(),
                Instant.now());
    }

    // ------------------------------------------------------------------
    // Streaming
    // ------------------------------------------------------------------

    /**
     * Streams a completion as Server-Sent Events.
     *
     * <p><strong>Chunked, never buffered.</strong> Each token is emitted as it arrives; the whole
     * response is not assembled first. Buffering would defeat the entire point — the user would
     * see the answer land all at once, exactly as if there were no stream.
     *
     * <p>The event shape is the one recorded in {@code docs/SERVICE_CONTRACTS.md} §8: {@code
     * meta}, then {@code token}s, then {@code usage} and {@code done}, or a single {@code
     * error}. A client can therefore render progressively and still finish correctly on a
     * truncated stream, because {@code done} is explicit rather than inferred from the socket
     * closing.
     */
    @PostMapping(value = "/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream a completion",
            description = "Emits meta, then one event per text chunk, then usage and done. "
                    + "Progressive rendering depends on chunks arriving as they are produced.")
    public Flux<org.springframework.http.codec.ServerSentEvent<String>> stream(
            @Valid @RequestBody GenerateRequest request) {

        String requestId = UUID.randomUUID().toString();
        ModelDescriptor model = router.select(request.modelOrNull());
        List<ConversationTurn> turns = ConversationTurn.from(request.contextWithMessage());

        return Flux.defer(() -> {
            Instant started = Instant.now();

            // Availability is checked before a stream opens. Opening a stream that can only
            // fail immediately leaves the client holding an open connection to nothing.
            String unavailable = router.resolveAvailability(model);
            if (unavailable != null) {
                return Flux.just(errorEvent("PROVIDER_UNAVAILABLE", unavailable));
            }

            try {
                // Measured before the stream opens, over the whole conversation rather than the
                // new message. Refusing here means the caller gets a clean 400 and nothing has
                // been billed; refusing after would mean a half-open stream and a provider error.
                router.enforceInputLimit(model, turns);
            } catch (IllegalArgumentException e) {
                return Flux.just(errorEvent("CONTEXT_TOO_LARGE", e.getMessage()));
            }

            ProviderClient client = router.providerFor(model).orElse(null);
            if (client == null) {
                return Flux.just(errorEvent("PROVIDER_UNAVAILABLE",
                        "No provider client is registered for " + model.provider() + "."));
            }

            Flux<String> tokens = client.stream(
                    new com.nexaai.ai.provider.ProviderMessages.GenerationRequest(
                            model, null, turns, request.temperature(),
                            request.maxOutputTokens()));

            // Built in full before formatting. No trailing comma: the client parses this, and a
            // truncated frame would be the first thing to fail rather than the last.
            String meta = ("{\"requestId\":\"%s\",\"model\":\"%s\",\"provider\":\"%s\"}")
                    .formatted(requestId, model.name(), model.provider());

            return Flux.concat(
                            Flux.just(event("meta", meta)),
                            tokens.map(chunk -> event("token",
                                    "{\"text\":" + jsonString(chunk) + "}")),
                            Flux.defer(() -> {
                                long millis = Duration.between(started, Instant.now()).toMillis();
                                usage.record(UsageRecord.success(requestId, model, null, null,
                                        false, true, millis));
                                router.health().recordSuccess(model.provider());
                                // The parentheses matter and are not decoration. `.formatted`
                                // binds to the single literal it follows, so concatenating then
                                // formatting gives the format call one argument for two
                                // placeholders -- which throws, and the stream ends in an error
                                // event instead of `done`. Every client would see a failed
                                // generation after receiving the whole answer.
                                String done = ("{\"requestId\":\"%s\",\"status\":\"COMPLETE\","
                                        + "\"durationMillis\":%d}")
                                        .formatted(requestId, millis);
                                return Flux.just(event("done", done));
                            }))
                    // A mid-stream failure becomes an explicit error event and then a closed
                    // stream, never a truncated body the client has to guess about.
                    .onErrorResume(ProviderException.class, e -> {
                        router.health().recordFailure(model.provider(), e.kind());
                        usage.record(UsageRecord.failure(requestId, model, false, true,
                                Duration.between(started, Instant.now()).toMillis(), e.kind()));
                        log.warn("Stream {} failed on {} ({}): {}", requestId, model.name(),
                                e.kind(), e.getMessage());
                        return Flux.just(errorEvent(e.kind().name(),
                                "The provider could not complete the stream."));
                    })
                    .onErrorResume(Exception.class, e -> {
                        log.warn("Stream {} failed unexpectedly: {}", requestId,
                                e.getClass().getSimpleName());
                        return Flux.just(errorEvent("STREAM_FAILED",
                                "The stream could not be completed."));
                    });
        });
    }

    // ------------------------------------------------------------------
    // Models, availability and health
    // ------------------------------------------------------------------

    @GetMapping("/models")
    @Operation(summary = "List models with their availability",
            description = "Every configured model, whether or not it can be served right now, and "
                    + "for each an unavailable model the reason it cannot be.")
    public List<ModelView> models() {
        return router.availability().stream()
                .map(availability -> new ModelView(
                        availability.model().name(),
                        availability.model().provider(),
                        availability.model().priority(),
                        availability.model().supportsTemperature(),
                        availability.model().maxInputTokens(),
                        availability.model().maxOutputTokens(),
                        availability.available(),
                        availability.unavailableReason(),
                        availability.model().allowFallback()))
                .toList();
    }

    @GetMapping("/default-model")
    @Operation(summary = "The model used when a request names none")
    public DefaultModelView defaultModel() {
        return new DefaultModelView(router.catalog().defaultModelName());
    }

    @GetMapping("/health")
    @Operation(summary = "Provider health",
            description = "Success and failure counts per provider, plus whether each is "
                    + "currently considered usable. Contains no credential and no prompt content.")
    public HealthView health() {
        return new HealthView(router.providerHealth());
    }

    @GetMapping("/usage")
    @Operation(summary = "Recent usage",
            description = "Token counts and outcomes, in memory only: this service owns no "
                    + "database. Prompt and completion text are never recorded.")
    public UsageView usage() {
        UsageRecord.Totals totals = usage.totals();
        return new UsageView(totals, usage.recent());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static org.springframework.http.codec.ServerSentEvent<String> event(String name,
                                                                                String data) {
        return org.springframework.http.codec.ServerSentEvent.<String>builder()
                .event(name)
                .data(data)
                .build();
    }

    private static org.springframework.http.codec.ServerSentEvent<String> errorEvent(
            String code, String message) {
        return event("error", "{\"code\":\"" + code + "\",\"message\":\"" + message
                + "\",\"retryable\":" + !"PROVIDER_UNAVAILABLE".equals(code) + "}");
    }

    /**
     * Escapes a string for a JSON string literal.
     *
     * <p>Hand-rolled because the streaming path builds each frame by hand, and a model that
     * emits a quote or a newline must not be able to break the event framing. Control characters
     * are escaped rather than dropped: silently removing them would corrupt the answer the user
     * sees.
     */
    static String jsonString(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    /** One model as reported. */
    public record ModelView(
            String name,
            String provider,
            int priority,
            boolean supportsTemperature,
            int maxInputTokens,
            int maxOutputTokens,
            boolean available,
            String unavailableReason,
            boolean allowFallback) {
    }

    /** The default model name. */
    public record DefaultModelView(String model) {
    }

    /** Provider health as reported. */
    public record HealthView(Map<String, com.nexaai.ai.provider.ProviderHealth.HealthSnapshot> providers) {
    }

    /** Usage as reported. */
    public record UsageView(UsageRecord.Totals totals, List<UsageRecord> recent) {
    }
}
