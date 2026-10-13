package com.nexaai.ai.routing;

import com.nexaai.ai.config.AiProperties;
import com.nexaai.ai.model.ConversationTurn;
import com.nexaai.ai.model.ModelCatalog;
import com.nexaai.ai.model.ModelDescriptor;
import com.nexaai.ai.provider.ProviderClient;
import com.nexaai.ai.provider.ProviderException;
import com.nexaai.ai.provider.ProviderFailureKind;
import com.nexaai.ai.provider.ProviderHealth;
import com.nexaai.ai.provider.ProviderMessages;
import com.nexaai.ai.retry.RetryExecutor;
import com.nexaai.ai.usage.UsageRecord;
import com.nexaai.ai.usage.UsageSink;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Selects a model, calls it, retries it, and falls back when it cannot be used.
 *
 * <p>This is the Model Router. Everything provider-specific stops here: the controller asks for a
 * completion, and gets one, without knowing or caring which of three providers produced it.
 *
 * <p><strong>Selection order.</strong> A named model is tried first, then same-provider siblings
 * by priority. Falling back stays within a provider on purpose: a request for Gemini answered
 * by Llama is a different product, not a graceful degradation, and the caller did not agree to
 * it.
 *
 * <p><strong>Retry and fallback are different decisions.</strong> Retry repeats the <em>same</em>
 * request after a transient fault. Fallback tries a <em>different</em> model after the first has
 * genuinely failed. Interleaving them incorrectly — treating every error as retryable — is how a
 * rejected prompt becomes five provider calls and a slower failure.
 */
@Service
public class ModelRouter {

    private static final Logger log = LoggerFactory.getLogger(ModelRouter.class);

    private final ModelCatalog catalog;
    private final Map<String, ProviderClient> providersById;
    private final ProviderHealth health;
    private final RetryExecutor retry;
    private final UsageSink usage;
    private final AiProperties properties;

    public ModelRouter(ModelCatalog catalog, List<ProviderClient> providerClients,
                       ProviderHealth health, RetryExecutor retry, UsageSink usage,
                       AiProperties properties) {
        this.catalog = catalog;
        this.health = health;
        this.retry = retry;
        this.usage = usage;
        this.properties = properties;

        Map<String, ProviderClient> byId = new LinkedHashMap<>();
        for (ProviderClient client : providerClients) {
            byId.put(client.id(), client);
        }
        this.providersById = Map.copyOf(byId);

        log.info("Model router ready with provider(s): {}", providersById.keySet());
    }

    // ==================================================================
    // Model selection
    // ==================================================================

    /**
     * Resolves a caller-supplied model name to a descriptor.
     *
     * @throws UnknownModelException when the name is not in the catalog, which is a client error
     *         and must not silently become "the default model", because a caller who asked for a
     *         model that does not exist should be told so
     */
    public ModelDescriptor select(String requestedModel) {
        if (requestedModel == null || requestedModel.isBlank()) {
            return catalog.defaultModel();
        }
        return catalog.find(requestedModel).orElseThrow(() -> new UnknownModelException(
                requestedModel, catalog.all()));
    }

    // ==================================================================
    // Availability and health
    // ==================================================================

    /** Every model, with whether it can be served right now and why not when it cannot. */
    public List<ModelAvailability> availability() {
        List<ModelAvailability> availability = new ArrayList<>();
        for (ModelDescriptor model : catalog.all()) {
            availability.add(new ModelAvailability(
                    model,
                    resolveAvailability(model),
                    health.snapshot(model.provider())));
        }
        return availability;
    }

    /**
     * Whether a model can be served, and the reason when it cannot.
     *
     * <p>The reason is the point. "This model is unavailable" tells an operator nothing; "Groq
     * has no credential configured" tells them exactly what to do.
     */
    public String resolveAvailability(ModelDescriptor model) {
        ProviderClient client = providersById.get(model.provider());
        if (client == null) {
            return "No provider client is registered for " + model.provider() + ".";
        }
        if (!client.supports(model)) {
            return "Provider " + model.provider() + " does not serve model " + model.name() + ".";
        }
        if (!client.isAvailable()) {
            return client.unavailabilityReason();
        }
        if (!health.isHealthy(model.provider())) {
            return "Provider " + model.provider() + " is temporarily unhealthy after recent "
                    + "failures.";
        }
        return null;
    }

    public Map<String, ProviderHealth.HealthSnapshot> providerHealth() {
        return health.snapshotAll(providersById.keySet());
    }

    // ==================================================================
    // Completion with retry and fallback
    // ==================================================================

    /**
     * A single-turn completion, with no conversation context.
     *
     * <p>A convenience for callers with nothing to remember. It is a genuine shape rather than a
     * test-only one — the summariser uses it, and so would a first question in a new conversation —
     * and it routes through exactly the same path as the multi-turn overload, so there is only one
     * implementation of "answer this".
     */
    public RoutedResult complete(String requestId, String requestedModel, String systemMessage,
                                 String userContent, Double temperature, Integer maxOutputTokens) {
        return complete(requestId, requestedModel, systemMessage,
                List.of(new ConversationTurn(ConversationTurn.Kind.USER, userContent)),
                temperature, maxOutputTokens);
    }

    /**
     * Runs one turn, falling back across the model's provider chain when it cannot be completed.
     *
     * <p><strong>The whole assembled prompt is measured, not just the new message.</strong> A
     * caller that checked only its own turn would send a 200-turn conversation to a model with a
     * 4k context and discover the problem as a provider error, having already paid for the
     * request. Context that does not fit is refused here, before any provider is contacted.
     *
     * @param systemMessage set by this service only, never by the caller
     */
    public RoutedResult complete(String requestId, String requestedModel, String systemMessage,
                                 List<ConversationTurn> turns, Double temperature,
                                 Integer maxOutputTokens) {

        ModelDescriptor primary = select(requestedModel);
        if (turns == null || turns.isEmpty()) {
            // Refused rather than sent. An empty prompt produces either a provider error or a
            // confident answer to nothing, and the caller cannot tell which happened from the
            // response. Reaching here means the caller forgot to append the turn being answered,
            // which is a bug worth surfacing rather than papering over with a placeholder.
            throw new IllegalArgumentException(
                    "A generation request must contain at least the turn being answered.");
        }
        enforceInputLimit(primary, turns);
        ProviderMessages.GenerationRequest request = new ProviderMessages.GenerationRequest(
                primary, systemMessage, turns, validatedTemperature(primary, temperature),
                effectiveMaxOutputTokens(primary, maxOutputTokens));

        Instant started = Instant.now();
        ProviderException firstFailure = null;

        for (ModelDescriptor candidate : catalog.fallbackChainFor(primary.name())) {
            ProviderClient client = providersById.get(candidate.provider());
            String reason = resolveAvailability(candidate);

            // Re-checked per candidate: a fallback model may have a smaller context window than
            // the one the caller sized against, and sending a prompt that fits neither is a
            // provider-side rejection we could have avoided.
            try {
                enforceInputLimit(candidate, turns);
            } catch (IllegalArgumentException e) {
                log.info("Skipping model {} for request {}: {}", candidate.name(), requestId,
                        e.getMessage());
                continue;
            }

            if (reason != null) {
                log.info("Skipping model {} for request {}: {}", candidate.name(), requestId,
                        reason);
                continue;
            }

            try {
                ProviderMessages.GenerationResult result = retry.execute(
                        "provider " + candidate.provider() + " model " + candidate.name(),
                        () -> client.complete(withModel(request, candidate)));

                health.recordSuccess(candidate.provider());
                boolean fellBack = !candidate.name().equals(primary.name());
                usage.record(UsageRecord.success(requestId, candidate, result.inputTokens(),
                        result.outputTokens(), fellBack, false,
                        Duration.between(started, Instant.now()).toMillis()));

                log.info("Request {} served by {} ({}){}", requestId, candidate.provider(),
                        candidate.name(), fellBack ? " after fallback" : "");

                return new RoutedResult(result, candidate, fellBack, null);

            } catch (ProviderException e) {
                health.recordFailure(candidate.provider(), e.kind());
                if (firstFailure == null) {
                    firstFailure = e;
                }
                log.warn("Request {} failed on {} ({}): {}", requestId, candidate.name(),
                        e.kind(), e.getMessage());

                if (!e.kind().shouldFallBack()) {
                    usage.record(UsageRecord.failure(requestId, candidate,
                            !candidate.name().equals(primary.name()), false,
                            Duration.between(started, Instant.now()).toMillis(), e.kind()));
                    throw e;
                }
            }
        }

        if (firstFailure != null) {
            usage.record(UsageRecord.failure(requestId, primary, false, false,
                    Duration.between(started, Instant.now()).toMillis(), firstFailure.kind()));
            throw firstFailure;
        }

        throw new NoProviderAvailableException(requestId, primary,
                describeUnavailableChain(primary));
    }

    /**
     * The request re-pointed at a fallback candidate.
     *
     * <p>Needed because fallback may change the model, and the request carries the descriptor
     * for its limits and its temperature support. Falling back without re-validating would apply
     * the primary's limits to a model with different ones.
     */
    private ProviderMessages.GenerationRequest withModel(ProviderMessages.GenerationRequest request,
                                                        ModelDescriptor candidate) {
        return new ProviderMessages.GenerationRequest(
                candidate,
                request.systemMessage(),
                request.turns(),
                validatedTemperature(candidate, request.temperature()),
                effectiveMaxOutputTokens(candidate, request.maxOutputTokens()));
    }

    // ==================================================================
    // Validation applied before any provider is contacted
    // ==================================================================

    /**
     * Temperature, refused when the model does not support it.
     *
     * <p>Checked here rather than passed through because a provider that ignores an unsupported
     * parameter fails silently: the request succeeds, the answer looks fine, and the caller
     * believes they controlled the output when they did not.
     */
    Double validatedTemperature(ModelDescriptor model, Double requested) {
        if (requested == null) {
            return null;
        }
        if (requested < 0 || requested > 2) {
            throw new IllegalArgumentException("temperature must be between 0 and 2, was "
                    + requested + ".");
        }
        if (!model.supportsTemperature()) {
            throw new IllegalArgumentException("Model " + model.name()
                    + " does not support a temperature parameter.");
        }
        return requested;
    }

    /**
     * Output tokens, clamped to the model's limit and the platform ceiling.
     *
     * <p>Two limits, and both apply: the model's own limit, and a platform ceiling that the
     * caller cannot raise. The caller is not the only party with an interest in the bill.
     */
    Integer effectiveMaxOutputTokens(ModelDescriptor model, Integer requested) {
        int ceiling = Math.min(model.maxOutputTokens(),
                properties.getDefaults().getMaxOutputTokensCeiling());

        if (requested == null) {
            int configured = properties.getDefaults().getMaxOutputTokens();
            return Math.min(configured, ceiling);
        }
        if (requested <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be positive, was "
                    + requested + ".");
        }
        if (requested > ceiling) {
            throw new IllegalArgumentException("maxOutputTokens of " + requested
                    + " exceeds the limit of " + ceiling + " for model " + model.name() + ".");
        }
        return requested;
    }

    /** Rough input-size check against the model's context limit, over the whole prompt. */
    public void enforceInputLimit(ModelDescriptor model, List<ConversationTurn> turns) {
        int estimated = ConversationTurn.estimatedTokens(turns);
        if (!model.canAcceptInput(estimated)) {
            // Names the actual numbers rather than just "too long". A caller that cannot see how
            // far over it is cannot decide between shortening the question, dropping history, or
            // picking a larger model — and which of those is right depends on what they meant.
            throw new IllegalArgumentException(
                    "The conversation context is too large for model " + model.name() + ": about "
                            + estimated + " tokens estimated against a limit of "
                            + model.maxInputTokens() + ". Send less history, or use a model with "
                            + "a larger context window.");
        }
    }

    /**
     * The summarised result of compressing a run of turns.
     *
     * <p>Summarisation is a model call, so it goes through {@link #complete} — the same router,
     * the same retry policy, the same provider classification. A caller that could summarise
     * without a model would be summarising with string manipulation, which is not summarisation.
     *
     * <p><strong>Low temperature by default.</strong> A summary is an act of compression; the
     * model's creativity is not a virtue here, and a summariser that embellishes invents things
     * the user did not say. A summary that invents is worse than no summary at all, because the
     * user cannot tell it apart from their own words.
     */
    public RoutedResult summarize(String requestId, String requestedModel,
                                  List<ConversationTurn> turns, Integer targetTokens,
                                  Double temperature) {
        int target = targetTokens == null ? 400 : targetTokens;
        String instruction = "Summarise the following conversation, preserving the user's goals, "
                + "decisions, stated facts, and any unresolved questions. Write roughly " + target
                + " tokens. Do not add information that is not present.\n\n"
                + renderTranscript(turns);

        return complete(requestId, requestedModel, null,
                List.of(new ConversationTurn(ConversationTurn.Kind.USER, instruction)),
                temperature == null ? 0.2 : temperature,
                target);
    }

    /** The turns as a role-prefixed transcript, for the summarisation instruction. */
    private static String renderTranscript(List<ConversationTurn> turns) {
        StringBuilder out = new StringBuilder();
        for (ConversationTurn turn : turns) {
            out.append(turn.kind().name()).append(": ").append(turn.content()).append("\n\n");
        }
        return out.toString().trim();
    }

    /**
     * How many estimated tokens of room remain for a run of turns.
     *
     * <p>Exposed so a caller sizing its window can ask before sending rather than after being
     * refused. Negative means it does not fit.
     */
    public int headroomAfter(ModelDescriptor model, List<ConversationTurn> turns) {
        return model.maxInputTokens() - ConversationTurn.estimatedTokens(turns);
    }

    private String describeUnavailableChain(ModelDescriptor primary) {
        StringBuilder description = new StringBuilder();
        for (ModelDescriptor candidate : catalog.fallbackChainFor(primary.name())) {
            String reason = resolveAvailability(candidate);
            if (reason != null) {
                if (description.length() > 0) {
                    description.append("; ");
                }
                description.append(candidate.name()).append(": ").append(reason);
            }
        }
        return description.length() == 0
                ? "No candidate model is available."
                : description.toString();
    }

    /** The provider client for an id, for the streaming path. */
    public Optional<ProviderClient> providerFor(ModelDescriptor model) {
        return Optional.ofNullable(providersById.get(model.provider()));
    }

    /** The catalog, for the streaming path. */
    public ModelCatalog catalog() {
        return catalog;
    }

    /** Health, for the streaming path. */
    public ProviderHealth health() {
        return health;
    }

    /** Usage, for the streaming path. */
    public UsageSink usage() {
        return usage;
    }

    /** Retry, for the streaming path. */
    public RetryExecutor retry() {
        return retry;
    }

    /** A model's availability, with the reason when it is unavailable. */
    public record ModelAvailability(
            ModelDescriptor model,
            String unavailableReason,
            ProviderHealth.HealthSnapshot providerHealth) {

        public boolean available() {
            return unavailableReason == null;
        }
    }

    /**
     * A completed turn, and how it got there.
     *
     * @param fallbackUsed true when the model that answered is not the one that was asked for,
     *                     which the caller must be able to see rather than infer
     */
    public record RoutedResult(
            ProviderMessages.GenerationResult result,
            ModelDescriptor model,
            boolean fallbackUsed,
            String fallbackReason) {
    }

    /** A model name that is not in the catalog. */
    public static class UnknownModelException extends RuntimeException {
        private final String requested;
        private final List<String> available;

        public UnknownModelException(String requested, List<ModelDescriptor> catalog) {
            super("Unknown model '" + requested + "'.");
            this.requested = requested;
            this.available = catalog.stream().map(ModelDescriptor::name).toList();
        }

        public String requested() {
            return requested;
        }

        /** The known names, so a client can correct itself rather than guess. */
        public List<String> available() {
            return available;
        }
    }

    /** Every candidate model for a request was unavailable. */
    public static class NoProviderAvailableException extends RuntimeException {
        private final String requestId;
        private final ModelDescriptor model;
        private final String detail;

        public NoProviderAvailableException(String requestId, ModelDescriptor model,
                                            String detail) {
            super("No provider is available for model " + model.name() + ".");
            this.requestId = requestId;
            this.model = model;
            this.detail = detail;
        }

        public String requestId() {
            return requestId;
        }

        public ModelDescriptor model() {
            return model;
        }

        /** Per-candidate reasons, so the response explains what to configure. */
        public String detail() {
            return detail;
        }
    }
}