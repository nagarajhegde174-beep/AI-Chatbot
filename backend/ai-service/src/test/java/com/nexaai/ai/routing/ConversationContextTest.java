package com.nexaai.ai.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexaai.ai.config.AiProperties;
import com.nexaai.ai.model.ConversationTurn;
import com.nexaai.ai.model.ModelDescriptor;
import com.nexaai.ai.provider.ProviderException;
import com.nexaai.ai.provider.ProviderFailureKind;
import com.nexaai.ai.provider.ProviderHealth;
import com.nexaai.ai.retry.RetryExecutor;
import com.nexaai.ai.support.StubProviderClient;
import com.nexaai.ai.usage.UsageSink;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Conversation context: selection, whole-prompt limits, and summarisation.
 *
 * <p>The property under test throughout is that <strong>context is measured, not assumed</strong>.
 * A conversation that is too long has to be refused before any provider is billed for it, and a
 * summary has to be a real model call rather than a truncation — because a truncation that drops
 * the wrong turn looks exactly like a working summary until the model contradicts the user.
 */
class ConversationContextTest {

    private StubProviderClient openai;
    private StubProviderClient gemini;
    private StubProviderClient groq;
    private ModelRouter router;
    private ModelDescriptor small;

    @BeforeEach
    void setUp() {
        openai = new StubProviderClient("OPENAI").returning("an answer");
        gemini = new StubProviderClient("GEMINI").returning("a gemini answer");
        groq = new StubProviderClient("GROQ").returning("a groq answer");

        AiProperties properties = properties();
        router = new ModelRouter(new com.nexaai.ai.model.ModelCatalog(properties),
                List.of(openai, gemini, groq),
                new ProviderHealth(properties),
                new RetryExecutor(properties),
                new UsageSink.InMemory(properties),
                properties);

        small = router.select("nexa-default");
    }

    private AiProperties properties() {
        AiProperties properties = new AiProperties();
        properties.getDefaults().setModel("nexa-default");
        properties.getProviders().put("OPENAI", provider());
        properties.getProviders().put("GEMINI", provider());
        properties.getProviders().put("GROQ", provider());

        // A deliberately small context window. The point is that the LIMIT is small, so a test can
        // overflow it with a handful of turns rather than a megabyte of filler text.
        properties.getModels().add(model("nexa-default", "OPENAI", 1, 400, 1000));
        properties.getModels().add(model("nexa-openai-mini", "OPENAI", 2, 400, 1000));
        properties.getModels().add(model("nexa-gemini-flash", "GEMINI", 1, 4000, 1000));
        properties.getModels().add(model("nexa-groq-llama", "GROQ", 1, 4000, 1000));

        properties.getRetry().setEnabled(false);
        return properties;
    }

    private AiProperties.Provider provider() {
        AiProperties.Provider provider = new AiProperties.Provider();
        provider.setEnabled(true);
        provider.setApiKey("test-key");
        return provider;
    }

    private AiProperties.Model model(String name, String provider, int priority, int maxInput,
                                     int maxOutput) {
        AiProperties.Model model = new AiProperties.Model();
        model.setName(name);
        model.setProvider(provider);
        model.setPriority(priority);
        model.setMaxInputTokens(maxInput);
        model.setMaxOutputTokens(maxOutput);
        return model;
    }

    private static ConversationTurn user(String content) {
        return new ConversationTurn(ConversationTurn.Kind.USER, content);
    }

    private static ConversationTurn assistant(String content) {
        return new ConversationTurn(ConversationTurn.Kind.ASSISTANT, content);
    }

    private static ConversationTurn summary(String content) {
        return new ConversationTurn(ConversationTurn.Kind.SUMMARY, content);
    }

    // ==================================================================
    // Context selection
    // ==================================================================

    @Nested
    @DisplayName("context selection")
    class Selection {

        @Test
        @DisplayName("a single turn is answered as a single turn")
        void singleTurn() {
            router.complete("r1", "nexa-default", null, List.of(user("hello")), null, null);

            assertThat(openai.lastRequest().turns()).hasSize(1);
            assertThat(openai.lastRequest().turns().get(0).content()).isEqualTo("hello");
        }

        @Test
        @DisplayName("the whole conversation reaches the provider, in order, unaltered")
        void wholeConversationIsSent() {
            // Not truncated, not reordered, not summarised by the service. Whatever Chat Service
            // selected is what the model sees.
            List<ConversationTurn> turns = List.of(
                    summary("They discussed deployment."),
                    user("what is a connection pool?"),
                    assistant("A pool of reusable connections."),
                    user("how do I size it?"));

            router.complete("r1", "nexa-default", null, turns, null, null);

            assertThat(openai.lastRequest().turns()).containsExactlyElementsOf(turns);
        }

        @Test
        @DisplayName("a summary is sent as its own kind, ahead of the verbatim turns")
        void summaryIsLabelled() {
            // Presented as ordinary prose it reads to the model as the most recent thing said,
            // which is exactly the failure a compressed conversation is prone to.
            List<ConversationTurn> turns = List.of(
                    summary("Earlier: they chose Postgres."),
                    user("now what?"));

            router.complete("r1", "nexa-default", null, turns, null, null);

            List<ConversationTurn> sent = openai.lastRequest().turns();
            assertThat(sent.get(0).kind()).isEqualTo(ConversationTurn.Kind.SUMMARY);
            assertThat(sent.get(1).kind()).isEqualTo(ConversationTurn.Kind.USER);
        }

        @Test
        @DisplayName("an empty conversation is refused, not sent to a provider as an empty prompt")
        void emptyContextIsRefused() {
            // Sent as-is, this produces either a provider error or a confident answer to nothing,
            // and the caller cannot tell which from the response. Reaching the router with no
            // turn means the caller forgot to append the question, which is a bug worth raising.
            assertThatThrownBy(() ->
                    router.complete("r1", "nexa-default", null, List.of(), null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least the turn being answered");

            assertThat(openai.completeCalls()).isZero();
        }

        @Test
        @DisplayName("a blank turn is refused, because it carries no information")
        void blankTurnIsRefused() {
            // A blank turn is a formatting artefact of a partially-summarised history. Sending it
            // spends context on nothing and invites the model to respond to nothing.
            assertThatThrownBy(() -> new ConversationTurn(ConversationTurn.Kind.USER, "   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blank");
        }

        @Test
        @DisplayName("fallback preserves the whole conversation, not just the last turn")
        void fallbackPreservesContext() {
            openai.failingModel("nexa-default", ProviderFailureKind.UNAVAILABLE, "down");
            List<ConversationTurn> turns = List.of(
                    user("older question"), assistant("older answer"), user("newest question"));

            ModelRouter.RoutedResult result =
                    router.complete("r1", "nexa-default", null, turns, null, null);

            // Losing history on fallback would be a silent, invisible quality regression: the
            // user gets a coherent answer to a question they did not ask.
            assertThat(result.model().name()).isEqualTo("nexa-openai-mini");
            assertThat(openai.lastRequest().turns()).containsExactlyElementsOf(turns);
        }
    }

    // ==================================================================
    // Token-aware limits
    // ==================================================================

    @Nested
    @DisplayName("token-aware limits")
    class Limits {

        @Test
        @DisplayName("the WHOLE prompt is measured, not just the newest turn")
        void measuresWholePrompt() {
            // Each turn is comfortably inside the 400-token window on its own. Together they are
            // not. A per-turn check would wave this through and the provider would reject it.
            String chunk = "x".repeat(700);
            List<ConversationTurn> turns = List.of(
                    user(chunk), assistant(chunk), user(chunk));

            assertThatThrownBy(() -> router.complete("r1", "nexa-default", null, turns, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("too large");
        }

        @Test
        @DisplayName("an oversized conversation is refused BEFORE any provider is called")
        void refusesBeforeCallingOut() {
            // Before, not after: a request that cannot fit should never be billed for.
            List<ConversationTurn> turns = List.of(user("x".repeat(4000)));

            assertThatThrownBy(() -> router.complete("r1", "nexa-default", null, turns, null, null))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThat(openai.completeCalls()).isZero();
        }

        @Test
        @DisplayName("the error names both numbers, so the caller can decide what to do")
        void errorIsActionable() {
            // A caller that cannot see how far over it is cannot choose between shortening the
            // question, dropping history, or picking a bigger model.
            assertThatThrownBy(() -> router.complete("r1", "nexa-default", null,
                    List.of(user("x".repeat(4000))), null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("too large")
                    .hasMessageContaining("400")
                    .hasMessageContaining("larger context window");
        }

        @Test
        @DisplayName("a conversation that fits is accepted")
        void acceptsWhatFits() {
            router.complete("r1", "nexa-default", null,
                    List.of(user("a"), assistant("b"), user("c")), null, null);

            assertThat(openai.completeCalls()).isEqualTo(1);
        }

        @Test
        @DisplayName("headroom is reported so a caller can size a window without guessing")
        void reportsHeadroom() {
            // 400 estimated tokens against a 400-token window leaves nothing; the point is that
            // the caller can ASK rather than discovering the answer by being refused.
            int headroom = router.headroomAfter(small, List.of(user("x".repeat(400))));

            assertThat(headroom).isEqualTo(300);
        }

        @Test
        @DisplayName("headroom goes negative when the conversation will not fit")
        void headroomGoesNegative() {
            // A caller sizing its window must be able to see that it will not fit, rather than
            // building a window that overflows and finding out at send time.
            int headroom = router.headroomAfter(small, List.of(user("x".repeat(4000))));

            assertThat(headroom).isNegative();
        }

        @Test
        @DisplayName("a fallback model whose window is too small is SKIPPED, not sent an oversized prompt")
        void fallbackWindowIsRespected() {
            // The primary has a 4000-token window, its same-provider fallback only 400. A prompt
            // that fits the primary does not fit the fallback, and sending it anyway turns a
            // recoverable outage into a hard failure.
            AiProperties properties = properties();
            properties.getModels().clear();
            properties.getModels().add(model("nexa-default", "OPENAI", 1, 4000, 1000));
            properties.getModels().add(model("nexa-openai-mini", "OPENAI", 2, 400, 1000));

            ModelRouter wideThenNarrow = new ModelRouter(
                    new com.nexaai.ai.model.ModelCatalog(properties),
                    List.of(openai, gemini, groq),
                    new ProviderHealth(properties),
                    new RetryExecutor(properties),
                    new UsageSink.InMemory(properties),
                    properties);

            openai.failingModel("nexa-default", ProviderFailureKind.UNAVAILABLE, "down");

            // ~500 estimated tokens: fits 4000, does not fit 400.
            List<ConversationTurn> oversized = List.of(user("x".repeat(2000)));
            assertThatThrownBy(() ->
                    wideThenNarrow.complete("r1", "nexa-default", null, oversized, null, null))
                    .isInstanceOf(ProviderException.class);

            // ONE call. The primary was tried and failed; the fallback was skipped on size, so it
            // was never contacted. Two calls would mean the oversized prompt was sent anyway.
            assertThat(openai.completeCalls()).isEqualTo(1);
        }
    }

    // ==================================================================
    // Summarisation
    // ==================================================================

    @Nested
    @DisplayName("summarisation")
    class Summarisation {

        @Test
        @DisplayName("a summary is produced by a model call, not by truncation")
        void summaryIsAModelCall() {
            openai.returning("The user chose Postgres and asked about pooling.");

            ModelRouter.RoutedResult result = router.summarize("r1", "nexa-default",
                    List.of(user("which database?"), assistant("Postgres."), user("why?")),
                    200, null);

            // If this were string manipulation the provider would never be called, and the test
            // would pass while the feature quietly did nothing.
            assertThat(openai.completeCalls()).isEqualTo(1);
            assertThat(result.result().content()).contains("Postgres");
        }

        @Test
        @DisplayName("the transcript is role-prefixed, so a summary cannot misattribute a turn")
        void transcriptIsAttributed() {
            router.summarize("r1", "nexa-default",
                    List.of(user("what did I choose?"), assistant("You chose Postgres.")),
                    200, null);

            String sent = openai.lastRequest().turns().get(0).content();

            // Without the prefixes, "You chose Postgres" in a summary request is ambiguous, and a
            // summary that swaps who said what is worse than no summary.
            assertThat(sent).contains("USER: what did I choose?")
                    .contains("ASSISTANT: You chose Postgres.");
        }

        @Test
        @DisplayName("summarisation uses a low temperature by default")
        void summarisationIsNotCreative() {
            router.summarize("r1", "nexa-default", List.of(user("hello")), 200, null);

            // A summary that embellishes invents things the user did not say, and the user cannot
            // tell it apart from their own words.
            assertThat(openai.lastRequest().temperature()).isEqualTo(0.2);
        }

        @Test
        @DisplayName("an explicit temperature is respected")
        void explicitTemperatureWins() {
            router.summarize("r1", "nexa-default", List.of(user("hello")), 200, 0.9);

            assertThat(openai.lastRequest().temperature()).isEqualTo(0.9);
        }

        @Test
        @DisplayName("summarisation goes through retry and fallback like any other call")
        void summarisationRetriesAndFallsBack() {
            openai.failingModel("nexa-default", ProviderFailureKind.THROTTLED, "rate limited");

            ModelRouter.RoutedResult result = router.summarize("r1", "nexa-default",
                    List.of(user("hello")), 200, null);

            assertThat(result.model().name()).isEqualTo("nexa-openai-mini");
            assertThat(result.result().content()).isEqualTo("an answer");
        }

        @Test
        @DisplayName("a model failure during summarisation surfaces as a failure, not an empty summary")
        void modelFailureSurfaces() {
            openai.failingModel("nexa-default", ProviderFailureKind.REJECTED, "400 refused");

            // The dangerous outcome is an EMPTY summary: the caller would store it, believe the
            // context was covered, and stop sending the turns it just discarded.
            assertThatThrownBy(() -> router.summarize("r1", "nexa-default",
                    List.of(user("hello")), 200, null))
                    .isInstanceOf(ProviderException.class);
        }

        @Test
        @DisplayName("summarising an oversized run is refused rather than silently truncated")
        void refusesOversizedRun() {
            assertThatThrownBy(() -> router.summarize("r1", "nexa-default",
                    List.of(user("x".repeat(4000))), 200, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("too large");
        }

        @Test
        @DisplayName("a summary is metered as usage, like any other model call")
        void summarisationIsMetered() {
            openai.withUsage(120, 30);

            router.summarize("r1", "nexa-default", List.of(user("hello")), 200, null);

            UsageRecordAssertions.assertMetered(router.usage(), 120, 30);
        }
    }

    /** Small helper so the metering assertion reads as a single statement. */
    private static final class UsageRecordAssertions {
        static void assertMetered(UsageSink usage, int input, int output) {
            assertThat(usage.totals().requests()).isEqualTo(1);
            assertThat(usage.totals().inputTokens()).isEqualTo(input);
            assertThat(usage.totals().outputTokens()).isEqualTo(output);
        }
    }

    // ==================================================================
    // Isolation of context between conversations
    // ==================================================================

    @Nested
    @DisplayName("context isolation")
    class Isolation {

        @Test
        @DisplayName("context is passed per request and never accumulated on the router")
        void contextIsNotAccumulated() {
            // A router that appended each request's turns to a field would leak conversation A
            // into conversation B. Two sequential calls must produce two independent prompts.
            router.complete("r1", "nexa-default", null,
                    List.of(user("conversation A")), null, null);
            List<ConversationTurn> first = new ArrayList<>(openai.lastRequest().turns());

            router.complete("r2", "nexa-default", null,
                    List.of(user("conversation B")), null, null);
            List<ConversationTurn> second = new ArrayList<>(openai.lastRequest().turns());

            assertThat(first).hasSize(1);
            assertThat(second).hasSize(1);
            assertThat(second.get(0).content()).isEqualTo("conversation B");
        }

        @Test
        @DisplayName("the router keeps no conversation content between calls")
        void routerHoldsNoContent() {
            router.complete("r1", "nexa-default", null,
                    List.of(user("a private question")), null, null);

            // Usage is the only thing that outlives a request, and it carries counts, never
            // content. A field holding the prompt would be a second copy of the user's words.
            assertThat(router.usage().recent().toString()).doesNotContain("a private question");
        }
    }
}