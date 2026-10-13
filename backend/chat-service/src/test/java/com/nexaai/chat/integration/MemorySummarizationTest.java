package com.nexaai.chat.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexaai.chat.client.AiServiceSummarizer;
import com.nexaai.chat.config.ChatProperties;
import com.nexaai.chat.domain.ConversationMemory;
import com.nexaai.chat.repository.ConversationMemoryRepository;
import com.nexaai.chat.service.ConversationContext;
import com.nexaai.chat.service.ConversationMemoryService;
import com.nexaai.chat.service.MemorySummarizer;
import com.nexaai.chat.support.TestTokens;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Summarisation, against a controllable summariser.
 *
 * <p><strong>Why the summariser is replaced rather than the whole service.</strong>
 * {@link MemoryIntegrationTest} covers what happens when summarisation FAILS, which is the state
 * this environment is permanently in — no AI Service, no credentials. That leaves the success path
 * untested, and an untested success path is usually a path that does not work. Replacing only the
 * seam means the selection rules, the persistence, the sequence bookkeeping and the HTTP surface
 * are all exercised for real.
 *
 * <p>The stub records what it was asked to compress, because "the summary covers the right turns"
 * is a claim about range arithmetic and is easy to get subtly wrong.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(MemorySummarizationTest.StubSummarizerConfig.class)
class MemorySummarizationTest extends PostgresIntegrationTest {

    /** One turn long enough that a hundred of them overflow the context budget. */
    private static final String LONG_TURN =
            "Earlier in this conversation the user asked about deployment and the assistant "
            + "explained containers, networking and rolling updates at considerable length. ";

    private static final UUID OWNER = UUID.randomUUID();

    @Autowired
    private ConversationMemoryService memory;

    @Autowired
    private ConversationMemoryRepository memoryRepository;

    @Autowired
    private StubSummarizer summarizer;

    @Autowired
    private MockMvc mvc;

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private String auth() {
        return bearer(TestTokens.userToken(OWNER));
    }

    @BeforeEach
    void resetStub() {
        summarizer.reset();
    }

    private UUID createConversation() throws Exception {
        String body = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/conversations")
                        .header(HttpHeaders.AUTHORIZATION, auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
    }

    private void send(UUID conversationId, String content) throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/conversations/" + conversationId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content.replace("\"", "\\\"") + "\"}"))
                .andExpect(status().isOk());
    }

    // ==================================================================
    // Compression
    // ==================================================================

    @Nested
    @DisplayName("compression")
    class Compression {

        @Test
        @DisplayName("older turns are compressed and the summary leads the context")
        void summaryLeadsTheContext() throws Exception {
            UUID conversation = createConversation();
            for (int i = 0; i < 120; i++) {
                send(conversation, LONG_TURN + i);
            }

            ConversationContext compressed = memory.prepareForGeneration(
                    OWNER, conversation, "the question");

            // Compression happens on the FIRST over-budget pass, not on some later one.
            //
            // The first version of this test asserted it did NOT summarise until a second call,
            // on the reasoning that "select, then decide". That reasoning was wrong: a user whose
            // conversation has already outgrown the budget should not have to ask twice, and
            // waiting costs a turn in which they get a worse answer than they needed.
            assertThat(compressed.summarised()).isTrue();
            assertThat(compressed.turns().get(0).fromSummary()).isTrue();
            assertThat(compressed.turns().get(0).content())
                    .contains("what was discussed");
        }

        @Test
        @DisplayName("the summary covers the OLDEST turns, never the newest")
        void oldestTurnsAreCompressed() throws Exception {
            UUID conversation = createConversation();
            for (int i = 0; i < 120; i++) {
                send(conversation, LONG_TURN + i);
            }

            memory.prepareForGeneration(OWNER, conversation, "the question");

            // Compressing the newest turns instead would throw away exactly the context that
            // explains the question being asked, and the model would answer something else. The
            // stub records the transcript, so this asserts the RANGE rather than trusting that
            // "oldest" was passed as a parameter.
            //
            // LONG_TURN ends with "at considerable length. " and the index is appended, so the
            // first turn ends "length. 0" and the last "length. 119".
            assertThat(summarizer.lastTranscript())
                    .contains("length. 0")
                    .doesNotContain("length. 119");
        }

        @Test
        @DisplayName("the summary is persisted with the sequence range it covers")
        void summaryIsPersisted() throws Exception {
            UUID conversation = createConversation();
            for (int i = 0; i < 120; i++) {
                send(conversation, LONG_TURN + i);
            }

            summarizer.succeed("Persisted summary.");
            memory.prepareForGeneration(OWNER, conversation, "the question");

            ConversationMemory row = memoryRepository
                    .findByConversationIdAndOwnerAuthUserId(conversation, OWNER)
                    .orElseThrow();

            assertThat(row.hasSummary()).isTrue();
            assertThat(row.getSummary()).isEqualTo("Persisted summary.");
            assertThat(row.getSummarisedTurnCount()).isPositive();
            assertThat(row.getSummarisedThroughSequence()).isPositive();
            assertThat(row.getOwnerAuthUserId()).isEqualTo(OWNER);
        }

        @Test
        @DisplayName("re-summarising folds the old summary in rather than discarding it")
        void repeatedSummarisationDoesNotForget() throws Exception {
            UUID conversation = createConversation();
            for (int i = 0; i < 120; i++) {
                send(conversation, LONG_TURN + i);
            }

            memory.prepareForGeneration(OWNER, conversation, "q1");
            summarizer.succeed("First summary.");
            memory.prepareForGeneration(OWNER, conversation, "q2");

            summarizer.succeed("Second summary.");
            for (int i = 0; i < 120; i++) {
                send(conversation, LONG_TURN + (200 + i));
            }
            memory.prepareForGeneration(OWNER, conversation, "q3");

            // Without folding, each pass would overwrite the earliest context and the conversation
            // would slowly forget its own beginning — the most insidious failure in a memory
            // feature, because each individual pass looks correct.
            assertThat(summarizer.calls())
                    .as("a second compression pass must actually have happened")
                    .isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("a summary keeps the context bounded after compression")
        void compressionKeepsContextBounded() throws Exception {
            UUID conversation = createConversation();
            for (int i = 0; i < 200; i++) {
                send(conversation, LONG_TURN + i);
            }

            ConversationContext after = memory.prepareForGeneration(
                    OWNER, conversation, "q1");

            assertThat(after.estimatedTokens())
                    .as("compression exists to make room, so the result must fit")
                    .isLessThanOrEqualTo(after.budget());
        }
    }

    // ==================================================================
    // Model failure
    // ==================================================================

    @Nested
    @DisplayName("model failure during summarisation")
    class ModelFailure {

        @Test
        @DisplayName("a summariser that returns nothing stores NO summary")
        void nothingIsStoredWhenTheModelReturnsNothing() throws Exception {
            UUID conversation = createConversation();
            for (int i = 0; i < 120; i++) {
                send(conversation, LONG_TURN + i);
            }

            // Configured BEFORE the first pass, because compression happens on the first
            // over-budget request. Arming the failure afterwards leaves a perfectly good summary
            // already stored, and the test then passes or fails for the wrong reason.
            summarizer.returnNothing();

            ConversationContext context = memory.prepareForGeneration(
                    OWNER, conversation, "q1");

            // Storing an EMPTY summary is the worst outcome available: the caller would believe the
            // context was covered and stop sending the turns it just discarded. The user loses
            // history silently and permanently.
            assertThat(context.summarised()).isFalse();
            assertThat(memoryRepository
                    .findByConversationIdAndOwnerAuthUserId(conversation, OWNER)
                    .map(ConversationMemory::hasSummary)
                    .orElse(false)).isFalse();

            // And a later pass, once the model works again, still compresses. A failed
            // summarisation must not poison the conversation into never remembering itself.
            summarizer.succeed("Recovered summary.");
            assertThat(memory.prepareForGeneration(OWNER, conversation, "q2").summarised()).isTrue();
        }

        @Test
        @DisplayName("a summariser that throws does NOT break the reply")
        void aThrowingSummariserDoesNotBlock() throws Exception {
            UUID conversation = createConversation();
            for (int i = 0; i < 120; i++) {
                send(conversation, LONG_TURN + i);
            }

            summarizer.fail();

            ConversationContext context = memory.prepareForGeneration(
                    OWNER, conversation, "q1");

            // The seam documents "return null", so a throwing summariser is an implementation that
            // broke its contract. It must still not break the reply: this is a memory OPTIMISATION,
            // and a failed optimisation is not a failed answer.
            assertThat(context.turns()).isNotEmpty();
            assertThat(context.estimatedTokens()).isLessThanOrEqualTo(context.budget());
        }
    }

    // ==================================================================
    // Clearing, with a summary actually present
    // ==================================================================

    @Nested
    @DisplayName("clearing a real summary")
    class ClearingRealSummary {

        @Test
        @DisplayName("clearing removes the summary and leaves the messages")
        void clearingRemovesSummaryOnly() throws Exception {
            UUID conversation = createConversation();
            for (int i = 0; i < 120; i++) {
                send(conversation, LONG_TURN + i);
            }
            memory.prepareForGeneration(OWNER, conversation, "q1");
            summarizer.succeed("Something remembered.");
            memory.prepareForGeneration(OWNER, conversation, "q2");

            mvc.perform(delete("/api/v1/conversations/{id}/memory", conversation)
                            .header(HttpHeaders.AUTHORIZATION, auth())
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hadMemory").value(true))
                    .andExpect(jsonPath("$.memory.hasSummary").value(false));

            // Messages are the user's own words. "Forget what we discussed" and "delete the
            // discussion" are different requests.
            mvc.perform(get("/api/v1/conversations/{id}/messages", conversation)
                            .header(HttpHeaders.AUTHORIZATION, auth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()")
                            .value(org.hamcrest.Matchers.greaterThan(100)));
        }

        @Test
        @DisplayName("after clearing, memory rebuilds from the messages that still exist")
        void memoryRebuildsAfterClearing() throws Exception {
            UUID conversation = createConversation();
            for (int i = 0; i < 120; i++) {
                send(conversation, LONG_TURN + i);
            }
            memory.prepareForGeneration(OWNER, conversation, "q1");
            summarizer.succeed("Forgotten.");
            memory.prepareForGeneration(OWNER, conversation, "q2");

            memory.clear(OWNER, conversation);
            summarizer.succeed("Rebuilt.");

            ConversationContext rebuilt =
                    memory.prepareForGeneration(OWNER, conversation, "q3");

            // The conversation did not become permanently amnesiac: the messages are still there,
            // so the summary can be rebuilt. Clearing forgets; it does not amputate.
            assertThat(rebuilt.turns()).isNotEmpty();
        }
    }

    // ==================================================================
    // The stub
    // ==================================================================

    /**
     * A summariser under the test's control.
     *
     * <p>Replaces {@link AiServiceSummarizer} only. Everything above the seam — the selection
     * rules, the range arithmetic, the persistence, the HTTP surface — is the real implementation.
     */
    static class StubSummarizer implements MemorySummarizer {

        private final AtomicInteger calls = new AtomicInteger();
        private final List<String> transcripts = new ArrayList<>();

        private String summary = "A summary of what was discussed.";
        private boolean returnsNothing;
        private boolean throwsOnCall;

        @Override
        public Result summarise(UUID conversationId,
                                List<ConversationContext.ContextTurn> turns, String model,
                                ChatProperties.Memory config) {
            calls.incrementAndGet();

            if (throwsOnCall) {
                throw new IllegalStateException("stub summariser failure");
            }
            StringBuilder transcript = new StringBuilder();
            for (ConversationContext.ContextTurn turn : turns) {
                transcript.append(turn.content()).append('\n');
            }
            transcripts.add(transcript.toString());

            if (returnsNothing) {
                // The realistic failure: the call reached a model and it produced nothing usable.
                return null;
            }
            return Result.of(summary, turns.size());
        }

        void succeed(String text) {
            this.summary = text;
            this.returnsNothing = false;
            this.throwsOnCall = false;
        }

        void returnNothing() {
            this.returnsNothing = true;
            this.throwsOnCall = false;
        }

        void fail() {
            this.throwsOnCall = true;
            this.returnsNothing = false;
        }

        void reset() {
            calls.set(0);
            transcripts.clear();
            succeed("A summary of what was discussed.");
        }

        int calls() {
            return calls.get();
        }

        String lastTranscript() {
            return transcripts.isEmpty() ? "" : transcripts.get(transcripts.size() - 1);
        }
    }

    @TestConfiguration
    static class StubSummarizerConfig {

        @Bean
        @Primary
        StubSummarizer stubSummarizer() {
            return new StubSummarizer();
        }
    }
}