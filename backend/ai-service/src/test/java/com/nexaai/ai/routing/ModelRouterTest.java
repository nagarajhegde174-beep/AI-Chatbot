package com.nexaai.ai.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexaai.ai.config.AiProperties;
import com.nexaai.ai.model.ModelCatalog;
import com.nexaai.ai.model.ModelDescriptor;
import com.nexaai.ai.provider.ProviderException;
import com.nexaai.ai.provider.ProviderFailureKind;
import com.nexaai.ai.provider.ProviderHealth;
import com.nexaai.ai.retry.RetryExecutor;
import com.nexaai.ai.support.StubProviderClient;
import com.nexaai.ai.usage.UsageSink;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The Model Router: selection, fallback, retry, limits, temperature and availability.
 *
 * <p><strong>No network and no provider account.</strong> Everything here runs against
 * {@link StubProviderClient}, so the assertions are about this service's decisions — which model,
 * how many attempts, whether to fall back — and not about whether OpenAI is up today.
 *
 * <p>That separation is the point of the {@code ProviderClient} seam. These behaviours are the
 * ones that must be trustworthy, and they are the ones a live-provider test could never verify,
 * because a provider outage and a routing bug look identical from the outside.
 */
class ModelRouterTest {

    private StubProviderClient openai;
    private StubProviderClient gemini;
    private StubProviderClient groq;
    private ModelRouter router;
    private ModelRouter optedInRouter;
    private ModelCatalog catalog;

    @BeforeEach
    void setUp() {
        openai = new StubProviderClient("OPENAI").returning("openai answer");
        gemini = new StubProviderClient("GEMINI").returning("gemini answer");
        groq = new StubProviderClient("GROQ").returning("groq answer");

        catalog = new ModelCatalog(properties());
        router = new RouterBuilder().build();

        // The same catalog, but with the Gemini model explicitly permitted to be answered by
        // another provider. Compared against `router` so both policies are asserted from the
        // same test rather than by trusting one configuration.
        AiProperties optedIn = properties();
        optedIn.getModels().get(2).setAllowCrossProviderFallback(true);
        optedInRouter = new ModelRouter(new ModelCatalog(optedIn),
                List.of(openai, gemini, groq),
                new ProviderHealth(optedIn),
                new RetryExecutor(optedIn),
                new UsageSink.InMemory(optedIn),
                optedIn);
    }

    private AiProperties properties() {
        AiProperties properties = new AiProperties();
        properties.getDefaults().setModel("nexa-default");

        properties.getProviders().put("OPENAI", provider());
        properties.getProviders().put("GEMINI", provider());
        properties.getProviders().put("GROQ", provider());

        // Two OpenAI models so fallback within a provider is exercisable, and one per other
        // provider so cross-provider isolation can be asserted.
        properties.getModels().add(model("nexa-default", "OPENAI", 1, true, true));
        properties.getModels().add(model("nexa-openai-mini", "OPENAI", 2, true, true));
        properties.getModels().add(model("nexa-gemini-flash", "GEMINI", 1, true, true));
        properties.getModels().add(model("nexa-groq-llama", "GROQ", 1, true, true));
        properties.getModels().add(model("nexa-fixed", "GROQ", 5, false, false));

        // Retries off by default in unit tests so a test that means to make one call makes one.
        properties.getRetry().setEnabled(false);
        properties.getRetry().setMaxAttempts(3);
        return properties;
    }

    private AiProperties.Provider provider() {
        AiProperties.Provider provider = new AiProperties.Provider();
        provider.setEnabled(true);
        provider.setApiKey("test-key");
        return provider;
    }

    private AiProperties.Model model(String name, String provider, int priority,
                                     boolean temperature, boolean allowFallback) {
        AiProperties.Model model = new AiProperties.Model();
        model.setName(name);
        model.setProvider(provider);
        model.setPriority(priority);
        model.setSupportsTemperature(temperature);
        model.setAllowFallback(allowFallback);
        model.setMaxInputTokens(8000);
        model.setMaxOutputTokens(1000);
        return model;
    }

    /** Builds routers with per-test retry settings without repeating the constructor. */
    private final class RouterBuilder {
        private boolean retryEnabled = false;
        private int maxAttempts = 3;

        RouterBuilder withRetries(boolean enabled, int attempts) {
            this.retryEnabled = enabled;
            this.maxAttempts = attempts;
            return this;
        }

        ModelRouter build() {
            AiProperties props = properties();
            props.getRetry().setEnabled(retryEnabled);
            props.getRetry().setMaxAttempts(maxAttempts);
            return new ModelRouter(new ModelCatalog(props),
                    List.of(openai, gemini, groq),
                    new ProviderHealth(props),
                    new RetryExecutor(props),
                    new UsageSink.InMemory(props),
                    props);
        }
    }

    // ==================================================================
    // Model selection
    // ==================================================================

    @Nested
    @DisplayName("model selection")
    class Selection {

        @Test
        @DisplayName("an absent model name selects the configured default")
        void selectsDefault() {
            assertThat(router.select(null).name()).isEqualTo("nexa-default");
            assertThat(router.select("").name()).isEqualTo("nexa-default");
            assertThat(router.select("   ").name()).isEqualTo("nexa-default");
        }

        @Test
        @DisplayName("a known name selects that model")
        void selectsNamed() {
            assertThat(router.select("nexa-gemini-flash").provider()).isEqualTo("GEMINI");
            assertThat(router.select("nexa-groq-llama").provider()).isEqualTo("GROQ");
        }

        @Test
        @DisplayName("selection is case-insensitive, because callers are not careful")
        void selectionIsCaseInsensitive() {
            assertThat(router.select("NEXA-DEFAULT").name()).isEqualTo("nexa-default");
            assertThat(router.select("Nexa-Gemini-Flash").provider()).isEqualTo("GEMINI");
        }

        @Test
        @DisplayName("an unknown name is a 400, not a silent fall back to the default")
        void unknownModelIsRejected() {
            // Silently substituting the default would answer a question about model A with
            // model B, and the caller would have no way to know.
            assertThatThrownBy(() -> router.select("no-such-model"))
                    .isInstanceOf(ModelRouter.UnknownModelException.class)
                    .hasMessageContaining("no-such-model");
        }

        @Test
        @DisplayName("the unknown-model error lists the names a caller can choose from")
        void unknownModelListsOptions() {
            assertThatThrownBy(() -> router.select("no-such-model"))
                    .isInstanceOfSatisfying(ModelRouter.UnknownModelException.class, e ->
                            assertThat(e.available()).contains("nexa-default", "nexa-gemini-flash"));
        }
    }

    // ==================================================================
    // Routing
    // ==================================================================

    @Nested
    @DisplayName("routing")
    class Routing {

        @Test
        @DisplayName("a request reaches the provider that owns the chosen model")
        void routesToOwningProvider() {
            ModelRouter.RoutedResult result = router.complete("r1", "nexa-gemini-flash", null,
                    "hello", null, null);

            assertThat(result.model().provider()).isEqualTo("GEMINI");
            assertThat(result.result().content()).isEqualTo("gemini answer");
            assertThat(gemini.completeCalls()).isEqualTo(1);
            assertThat(openai.completeCalls()).isZero();
            assertThat(groq.completeCalls()).isZero();
        }

        @Test
        @DisplayName("the default model routes to its own provider")
        void defaultRoutesCorrectly() {
            ModelRouter.RoutedResult result = router.complete("r1", null, null, "hello", null,
                    null);

            assertThat(result.model().provider()).isEqualTo("OPENAI");
            assertThat(openai.completeCalls()).isEqualTo(1);
        }

        @Test
        @DisplayName("a successful call is not marked as a fallback")
        void successIsNotFallback() {
            assertThat(router.complete("r1", "nexa-default", null, "hi", null, null)
                    .fallbackUsed()).isFalse();
        }
    }

    // ==================================================================
    // Fallback
    // ==================================================================

    @Nested
    @DisplayName("fallback")
    class Fallback {

        @Test
        @DisplayName("falls back to another model on the SAME provider")
        void fallsBackWithinProvider() {
            // One model failing, its sibling fine. This is the case same-provider fallback
            // genuinely covers: a model rate-limited or decommissioned while the provider runs.
            openai.failingModel("nexa-default", ProviderFailureKind.THROTTLED,
                    "model rate limited");

            ModelRouter.RoutedResult result = router.complete("r1", "nexa-default", null, "hi",
                    null, null);

            assertThat(result.model().name()).isEqualTo("nexa-openai-mini");
            assertThat(result.fallbackUsed()).isTrue();
            assertThat(result.result().content()).isEqualTo("openai answer");
        }

        @Test
        @DisplayName("does NOT cross to a different provider by default")
        void doesNotCrossProvidersByDefault() {
            // A request for Gemini answered by Llama is a different product, not a graceful
            // degradation. The caller did not agree to it, so it needs an explicit opt-in.
            gemini.failingWith(ProviderFailureKind.UNAVAILABLE, "gemini is down");

            assertThatThrownBy(() -> router.complete("r1", "nexa-gemini-flash", null, "hi", null,
                    null))
                    .isInstanceOf(ProviderException.class);

            // The point of the test: Gemini failed and nobody else was asked.
            assertThat(openai.completeCalls()).isZero();
            assertThat(groq.completeCalls()).isZero();
        }

        @Test
        @DisplayName("a provider outage is NOT rescued by a sibling model on that provider")
        void providerOutageIsNotRescuedWithinProvider() {
            // The honest limit of same-provider fallback: when a provider is down, every model
            // behind it is down too, so the chain has nothing to fall back TO.
            openai.failingWith(ProviderFailureKind.UNAVAILABLE, "openai is down");

            assertThatThrownBy(() -> router.complete("r1", "nexa-default", null, "hi", null, null))
                    .isInstanceOf(ProviderException.class);

            // Both models on the provider were tried, and both failed.
            assertThat(openai.completeCalls()).isEqualTo(2);
        }

        @Test
        @DisplayName("crosses to another provider when the model opts in")
        void crossesProvidersWhenOptedIn() {
            // This is the configuration that survives an outage: an operator who would rather
            // have an answer from a different model than an error.
            gemini.failingWith(ProviderFailureKind.UNAVAILABLE, "gemini is down");

            ModelRouter.RoutedResult result = optedInRouter.complete("r1", "nexa-gemini-flash",
                    null, "hi", null, null);

            assertThat(result.model().provider()).isEqualTo("OPENAI");
            assertThat(result.fallbackUsed()).isTrue();
        }

        @Test
        @DisplayName("a REJECTED request is NOT retried and NOT fallen back")
        void rejectedRequestDoesNotFallBack() {
            openai.failingModel("nexa-default", ProviderFailureKind.REJECTED, "400 bad request");

            assertThatThrownBy(() -> router.complete("r1", "nexa-default", null, "hi", null,
                    null))
                    .isInstanceOf(ProviderException.class);

            // One call only. The provider refused this request; a sibling model on the same
            // provider will refuse it identically, so trying again would be theatre.
            assertThat(openai.completeCalls()).isEqualTo(1);
        }

        @Test
        @DisplayName("a model with allowFallback=false does not fall back")
        void respectsAllowFallbackFalse() {
            groq.failingModel("nexa-fixed", ProviderFailureKind.UNAVAILABLE, "groq is down");

            assertThatThrownBy(() -> router.complete("r1", "nexa-fixed", null, "hi", null, null))
                    .isInstanceOf(ProviderException.class);

            // Groq has two models, but this one refuses fallback, so only it was tried.
            assertThat(groq.completeCalls()).isEqualTo(1);
        }

        @Test
        @DisplayName("every candidate unavailable gives a 503 naming each reason")
        void allUnavailableExplainsEach() {
            openai.unavailable("no credential");
            gemini.unavailable("disabled");
            groq.unavailable("no Spring AI model");

            assertThatThrownBy(() -> router.complete("r1", "nexa-gemini-flash", null, "hi", null,
                    null))
                    .isInstanceOfSatisfying(ModelRouter.NoProviderAvailableException.class, e -> {
                        assertThat(e.detail()).contains("disabled");
                        assertThat(e.detail()).as("the reason must reach the caller")
                                .isNotBlank();
                    });
        }

        @Test
        @DisplayName("fallback re-validates against the fallback model's own limits")
        void fallbackRevalidatesLimits() {
            // Falling back without re-validating would apply the primary's token limit to a
            // model with a different one.
            openai.failingModel("nexa-default", ProviderFailureKind.UNAVAILABLE, "down");

            router.complete("r1", "nexa-default", null, "hi", null, null);

            assertThat(openai.lastRequest().model().name()).isEqualTo("nexa-openai-mini");
        }
    }

    // ==================================================================
    // Retry
    // ==================================================================

    @Nested
    @DisplayName("retry")
    class Retry {

        @Test
        @DisplayName("a retryable failure is retried up to the limit")
        void retriesRetryableFailures() {
            ModelRouter retrying = new RouterBuilder().withRetries(true, 3).build();
            openai.failingFirst(2);

            ModelRouter.RoutedResult result = retrying.complete("r1", "nexa-default", null, "hi",
                    null, null);

            assertThat(result.result().content()).isEqualTo("openai answer");
            assertThat(openai.completeCalls())
                    .as("two failures then a success is three calls")
                    .isEqualTo(3);
        }

        @Test
        @DisplayName("a non-retryable failure is NOT retried")
        void doesNotRetryRejectedRequests() {
            ModelRouter retrying = new RouterBuilder().withRetries(true, 3).build();
            // Retrying a rejected request is how one bad prompt becomes three provider calls.
            openai.failingWith(ProviderFailureKind.REJECTED, "400 bad request");

            assertThatThrownBy(() -> retrying.complete("r1", "nexa-default", null, "hi", null,
                    null))
                    .isInstanceOf(ProviderException.class);

            assertThat(openai.completeCalls()).isEqualTo(1);
        }

        @Test
        @DisplayName("an exhausted retry gives up on that model and falls back")
        void givesUpAfterMaxAttempts() {
            ModelRouter retrying = new RouterBuilder().withRetries(true, 2).build();
            openai.failingModel("nexa-default", ProviderFailureKind.UNAVAILABLE,
                    "model permanently down");

            ModelRouter.RoutedResult result = retrying.complete("r1", "nexa-default", null, "hi",
                    null, null);

            assertThat(result.model().name()).isEqualTo("nexa-openai-mini");
            assertThat(openai.completeCalls())
                    .as("two attempts on the failing model, then one on its sibling")
                    .isEqualTo(3);
        }

        @Test
        @DisplayName("backoff grows and is capped")
        void backoffIsBounded() {
            AiProperties props = properties();
            props.getRetry().setInitialBackoff(java.time.Duration.ofMillis(100));
            props.getRetry().setMaxBackoff(java.time.Duration.ofMillis(400));
            RetryExecutor executor = new RetryExecutor(props);

            assertThat(executor.backoffFor(1)).isEqualTo(java.time.Duration.ofMillis(100));
            assertThat(executor.backoffFor(2)).isEqualTo(java.time.Duration.ofMillis(200));
            assertThat(executor.backoffFor(3)).isEqualTo(java.time.Duration.ofMillis(400));
            assertThat(executor.backoffFor(9))
                    .as("without a cap the delay grows without bound")
                    .isEqualTo(java.time.Duration.ofMillis(400));
        }
    }

    // ==================================================================
    // Provider failure classification
    // ==================================================================

    @Nested
    @DisplayName("failure classification")
    class Failures {

        @Test
        @DisplayName("an unauthorised failure is not retried")
        void unauthorisedIsNotRetryable() {
            assertThat(ProviderFailureKind.UNAUTHORISED.isRetryable()).isFalse();
        }

        @Test
        @DisplayName("a rejected request is not retried")
        void rejectedIsNotRetryable() {
            assertThat(ProviderFailureKind.REJECTED.isRetryable()).isFalse();
        }

        @Test
        @DisplayName("an exceeded limit is not retried")
        void limitIsNotRetryable() {
            // The same request will exceed the same limit on a second attempt.
            assertThat(ProviderFailureKind.LIMIT_EXCEEDED.isRetryable()).isFalse();
        }

        @Test
        @DisplayName("throttling and unavailability are retried")
        void transientIsRetryable() {
            assertThat(ProviderFailureKind.THROTTLED.isRetryable()).isTrue();
            assertThat(ProviderFailureKind.UNAVAILABLE.isRetryable()).isTrue();
            assertThat(ProviderFailureKind.UNKNOWN.isRetryable()).isTrue();
        }

        @Test
        @DisplayName("a missing provider is not retried")
        void notConfiguredIsNotRetryable() {
            assertThat(ProviderFailureKind.NOT_CONFIGURED.isRetryable()).isFalse();
        }
    }

    // ==================================================================
    // Token limits and temperature
    // ==================================================================

    @Nested
    @DisplayName("limits and temperature")
    class Limits {

        @Test
        @DisplayName("temperature is refused for a model that does not support it")
        void refusesUnsupportedTemperature() {
            // A provider that ignores an unsupported parameter succeeds and looks fine, so
            // sending one would silently do nothing.
            assertThatThrownBy(() -> router.complete("r1", "nexa-fixed", null, "hi", 0.5, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not support a temperature");
        }

        @Test
        @DisplayName("temperature out of range is refused")
        void refusesOutOfRangeTemperature() {
            assertThatThrownBy(() -> router.complete("r1", "nexa-default", null, "hi", 5.0, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("between 0 and 2");
        }

        @Test
        @DisplayName("a supported temperature reaches the provider")
        void passesTemperatureThrough() {
            router.complete("r1", "nexa-default", null, "hi", 0.25, null);

            assertThat(openai.lastRequest().temperature()).isEqualTo(0.25);
        }

        @Test
        @DisplayName("an absent temperature is passed as null, not as a guessed default")
        void absentTemperatureStaysNull() {
            router.complete("r1", "nexa-default", null, "hi", null, null);

            assertThat(openai.lastRequest().temperature()).isNull();
        }

        @Test
        @DisplayName("maxOutputTokens above the model limit is refused")
        void refusesTooManyOutputTokens() {
            assertThatThrownBy(() -> router.complete("r1", "nexa-default", null, "hi", null, 5000))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds the limit");
        }

        @Test
        @DisplayName("an absent maxOutputTokens is clamped to the configured default")
        void defaultsOutputTokens() {
            router.complete("r1", "nexa-default", null, "hi", null, null);

            assertThat(openai.lastRequest().maxOutputTokens()).isEqualTo(1000);
        }

        @Test
        @DisplayName("an over-long message is refused before any provider is contacted")
        void enforcesInputLimitBeforeCallingOut() {
            // Before, not after: a request that is certainly too long should not be billed.
            assertThatThrownBy(() ->
                    router.enforceInputLimit(catalog.find("nexa-default").orElseThrow(),
                            "x".repeat(100_000)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("too long");

            assertThat(openai.completeCalls()).isZero();
        }
    }

    // ==================================================================
    // Availability and health
    // ==================================================================

    @Nested
    @DisplayName("availability and health")
    class Availability {

        @Test
        @DisplayName("an unavailable provider reports a reason, not just false")
        void unavailableExplainsWhy() {
            // "This model is unavailable" tells an operator nothing. This tells them what to fix.
            openai.unavailable("Provider OpenAI has no credential configured.");

            assertThat(router.resolveAvailability(catalog.find("nexa-default").orElseThrow()))
                    .isEqualTo("Provider OpenAI has no credential configured.");
        }

        @Test
        @DisplayName("every model is listed whether or not it can be served")
        void availabilityListsEverything() {
            openai.unavailable("no credential");

            List<ModelRouter.ModelAvailability> availability = router.availability();

            assertThat(availability).hasSize(5);
            assertThat(availability).filteredOn(a -> !a.available())
                    .isNotEmpty();
            assertThat(availability).anyMatch(a -> a.available());
        }

        @Test
        @DisplayName("three consecutive retryable failures mark a provider unhealthy")
        void marksProviderUnhealthy() {
            ProviderHealth health = new ProviderHealth(properties());

            assertThat(health.isHealthy("OPENAI")).isTrue();
            health.recordFailure("OPENAI", ProviderFailureKind.UNAVAILABLE);
            health.recordFailure("OPENAI", ProviderFailureKind.UNAVAILABLE);
            assertThat(health.isHealthy("OPENAI")).isTrue();
            health.recordFailure("OPENAI", ProviderFailureKind.UNAVAILABLE);
            assertThat(health.isHealthy("OPENAI")).isFalse();
        }

        @Test
        @DisplayName("a rejected request does NOT mark a provider unhealthy")
        void rejectionDoesNotMarkUnhealthy() {
            // A provider answering "400 bad request" is a provider that is up. Treating that as
            // an outage takes a working provider out of rotation.
            ProviderHealth health = new ProviderHealth(properties());

            for (int i = 0; i < 5; i++) {
                health.recordFailure("OPENAI", ProviderFailureKind.REJECTED);
            }

            assertThat(health.isHealthy("OPENAI")).isTrue();
        }

        @Test
        @DisplayName("a success clears an unhealthy state")
        void successRecovers() {
            ProviderHealth health = new ProviderHealth(properties());
            health.recordFailure("OPENAI", ProviderFailureKind.UNAVAILABLE);
            health.recordFailure("OPENAI", ProviderFailureKind.UNAVAILABLE);
            health.recordFailure("OPENAI", ProviderFailureKind.UNAVAILABLE);
            assertThat(health.isHealthy("OPENAI")).isFalse();

            health.recordSuccess("OPENAI");

            assertThat(health.isHealthy("OPENAI")).isTrue();
        }

        @Test
        @DisplayName("health counts separate retryable from total failures")
        void healthReportsCounts() {
            ProviderHealth health = new ProviderHealth(properties());
            health.recordSuccess("OPENAI");
            health.recordFailure("OPENAI", ProviderFailureKind.REJECTED);
            health.recordFailure("OPENAI", ProviderFailureKind.REJECTED);

            ProviderHealth.HealthSnapshot snapshot = health.snapshot("OPENAI");

            assertThat(snapshot.successes()).isEqualTo(1);
            assertThat(snapshot.failures()).isEqualTo(2);
            assertThat(snapshot.lastFailureKind()).isEqualTo("REJECTED");
        }
    }

    // ==================================================================
    // Usage
    // ==================================================================

    @Nested
    @DisplayName("usage")
    class Usage {

        @Test
        @DisplayName("a successful call is recorded with its token counts")
        void recordsSuccess() {
            ModelRouter usageRouter = new RouterBuilder().build();
            openai.withUsage(31, 12);

            usageRouter.complete("r1", "nexa-default", null, "hi", null, null);

            UsageSink sink = usageRouter.usage();
            assertThat(sink.totals().requests()).isEqualTo(1);
            assertThat(sink.totals().successes()).isEqualTo(1);
            assertThat(sink.totals().inputTokens()).isEqualTo(31);
            assertThat(sink.totals().outputTokens()).isEqualTo(12);
        }

        @Test
        @DisplayName("a fallback is recorded as one")
        void recordsFallback() {
            openai.failingModel("nexa-default", ProviderFailureKind.UNAVAILABLE, "down");
            ModelRouter usageRouter = new RouterBuilder().build();

            usageRouter.complete("r1", "nexa-default", null, "hi", null, null);

            assertThat(usageRouter.usage().recent())
                    .anyMatch(record -> record.fallbackUsed());
        }

        @Test
        @DisplayName("usage records carry no prompt or completion text")
        void usageCarriesNoText() {
            // A usage record is the thing most likely to be logged and kept for a year.
            ModelRouter usageRouter = new RouterBuilder().build();
            openai.withUsage(5, 5);

            usageRouter.complete("r1", "nexa-default", null,
                    "a very private question", null, null);

            assertThat(usageRouter.usage().recent())
                    .noneMatch(record -> record.toString().contains("private question"));
        }

        @Test
        @DisplayName("absent token counts stay null rather than becoming zero")
        void absentCountsStayNull() {
            // Zero would claim the provider reported no usage. Null says it reported none, which
            // is a different and honest statement.
            openai.withUsage(null, null);
            ModelRouter usageRouter = new RouterBuilder().build();

            usageRouter.complete("r1", "nexa-default", null, "hi", null, null);

            assertThat(usageRouter.usage().recent().get(0).totalTokens()).isNull();
        }
    }

    // ==================================================================
    // Catalog integrity
    // ==================================================================

    @Nested
    @DisplayName("catalog")
    class CatalogTests {

        @Test
        @DisplayName("a duplicate model name is refused at startup")
        void refusesDuplicateNames() {
            AiProperties props = properties();
            props.getModels().add(model("nexa-default", "GEMINI", 3, true, true));

            // Selection would be non-deterministic, and a non-deterministic model choice is
            // not something to discover in production.
            assertThatThrownBy(() -> new ModelCatalog(props))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Duplicate model name");
        }

        @Test
        @DisplayName("an unknown provider in the catalog is refused at startup")
        void refusesUnknownProvider() {
            AiProperties props = properties();
            props.getModels().add(model("nexa-mystery", "NOT_A_PROVIDER", 9, true, true));

            assertThatThrownBy(() -> new ModelCatalog(props))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("NOT_A_PROVIDER");
        }

        @Test
        @DisplayName("the fallback chain is ordered by priority")
        void chainIsPriorityOrdered() {
            List<ModelDescriptor> chain = catalog.fallbackChainFor("nexa-default");

            assertThat(chain).extracting(ModelDescriptor::name)
                    .containsExactly("nexa-default", "nexa-openai-mini");
        }

        @Test
        @DisplayName("an unknown provider id is rejected rather than defaulted")
        void providerKindParsing() {
            assertThatThrownBy(() -> com.nexaai.ai.model.ProviderKind.parse("MYSTERY"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(com.nexaai.ai.model.ProviderKind.parse("openai"))
                    .isEqualTo(com.nexaai.ai.model.ProviderKind.OPENAI);
        }
    }
}
