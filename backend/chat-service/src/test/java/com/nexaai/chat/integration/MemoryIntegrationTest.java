package com.nexaai.chat.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexaai.chat.domain.MessageRole;
import com.nexaai.chat.service.ConversationContext;
import com.nexaai.chat.service.ConversationMemoryService;
import com.nexaai.chat.support.TestTokens;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Conversation memory: retrieval, isolation, long conversations, summarisation, clearing.
 *
 * <p><strong>Every test here is about a limit or a boundary.</strong> The interesting failures in
 * memory are not "does it work" — they are "does it ever send too much", "does it ever send
 * someone else's", and "does it ever quietly pretend it remembered something it did not".
 *
 * <p>Runs against real PostgreSQL rather than mocks, because the property under test includes
 * ownership predicates and a unique constraint that a mocked repository would happily not have.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MemoryIntegrationTest extends PostgresIntegrationTest {

    /** The conversation owner. Every request below is made as this user unless stated otherwise. */
    private static final UUID OWNER = UUID.randomUUID();

    /** A second, unrelated user. Used only to prove isolation. */
    private static final UUID OTHER_USER = UUID.randomUUID();

    /**
     * A turn long enough that a hundred of them overflow the context budget.
     *
     * <p>Forty words, not one. The earlier version of this test sent short messages and asserted
     * truncation, and it did not truncate -- because short messages genuinely fit. The test was
     * wrong, not the code, and a test that fails for the right reason is still a test that has to
     * be understood before it is fixed.
     */
    private static final String LONG_TURN =
            "This is a deliberately long conversational turn used to push a conversation past its "
            + "context budget, containing enough ordinary English prose that the token estimate is "
            + "meaningful rather than rounding away to nothing across a hundred of them. ";

    @Autowired
    private ConversationMemoryService memory;

    @Autowired
    private MockMvc mvc;

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private String auth() {
        return bearer(TestTokens.userToken(OWNER));
    }

    private UUID createConversation() throws Exception {
        String body = mvc.perform(post("/api/v1/conversations")
                        .header(HttpHeaders.AUTHORIZATION, auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
    }

    private void send(UUID conversationId, String content) throws Exception {
        mvc.perform(post("/api/v1/conversations/" + conversationId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content.replace("\"", "\\\"") + "\"}"))
                .andExpect(status().isOk());
    }

    /**
     * The part of an error body that must be identical for two different conversations.
     *
     * <p>traceId and timestamp are excluded because they are different by design on every
     * response. Comparing them would assert nothing, and quietly deleting the assertion to make a
     * test pass is how an isolation guarantee stops being checked at all.
     */
    private static String stable(String errorBody) {
        return errorBody
                .replaceAll("\"timestamp\":\"[^\"]+\"", "\"timestamp\":\"\"")
                .replaceAll("\"traceId\":\"[^\"]+\"", "\"traceId\":\"\"");
    }

    private ConversationContext select(UUID conversationId, String question) {
        return memory.selectContext(OWNER, conversationId, question);
    }

    // ==================================================================
    // Retrieval
    // ==================================================================

    @Nested
    @DisplayName("context retrieval")
    class Retrieval {

        @Test
        @DisplayName("a new conversation has an empty, bounded context")
        void newConversationHasNoContext() throws Exception {
            UUID conversation = createConversation();
            send(conversation, "the first thing I ever said");

            ConversationContext context = select(conversation, "and now a follow-up");

            // The conversation is returned, not an error. A first question with no history is the
            // normal case and must not need special handling by the caller.
            assertThat(context.turns()).isNotEmpty();
            assertThat(context.summarised()).isFalse();
            assertThat(context.estimatedTokens()).isLessThanOrEqualTo(context.budget());
        }

        @Test
        @DisplayName("earlier turns are carried forward, oldest first, with the question last")
        void earlierTurnsAreCarried() throws Exception {
            UUID conversation = createConversation();
            send(conversation, "first question");
            send(conversation, "second question");

            ConversationContext context = select(conversation, "third question");

            List<String> contents = context.turns().stream().map(t -> t.content()).toList();

            // THREE turns, not four. Generation is disabled in this profile, so each assistant
            // placeholder is still PENDING with no content -- and a PENDING placeholder is
            // correctly EXCLUDED from context. Sending one would ask the model to treat a
            // platform artefact as something that was said.
            assertThat(contents).hasSize(3);
            assertThat(contents.get(0)).contains("first question");
            assertThat(contents.get(1)).contains("second question");
            assertThat(contents.get(2)).isEqualTo("third question");
        }

        @Test
        @DisplayName("the turn being answered is ALWAYS last, whatever else is dropped")
        void questionIsAlwaysLast() throws Exception {
            UUID conversation = createConversation();
            for (int i = 0; i < 40; i++) {
                send(conversation, "message number " + i);
            }

            ConversationContext context = select(conversation, "the actual question");

            // A context whose last turn is not the question produces a confident answer to the
            // wrong question, which is the worst outcome this feature could have.
            assertThat(context.turns().get(context.turns().size() - 1).content())
                    .isEqualTo("the actual question");
        }

        @Test
        @DisplayName("context is NEVER unbounded")
        void contextIsNeverUnbounded() throws Exception {
            UUID conversation = createConversation();
            for (int i = 0; i < 120; i++) {
                send(conversation, LONG_TURN + i);
            }

            ConversationContext context = select(conversation, "the actual question");

            // The whole point. A hundred turns of history that do not fit must not be sent.
            assertThat(context.estimatedTokens())
                    .as("context must always fit the configured budget")
                    .isLessThanOrEqualTo(context.budget());
        }

        @Test
        @DisplayName("when older turns are dropped, the count says so")
        void droppedTurnsAreReported() throws Exception {
            UUID conversation = createConversation();
            for (int i = 0; i < 120; i++) {
                send(conversation, LONG_TURN + i);
            }

            ConversationContext context = select(conversation, "the actual question");

            // A conversation that has quietly stopped remembering should be visible to the user,
            // not discovered weeks later when the model contradicts them.
            assertThat(context.truncated()).isTrue();
            assertThat(context.droppedTurns()).isPositive();
            assertThat(context.hasCompressedHistory()).isTrue();
        }

        @Test
        @DisplayName("a short conversation drops nothing")
        void shortConversationDropsNothing() throws Exception {
            UUID conversation = createConversation();
            send(conversation, "a short question");

            ConversationContext context = select(conversation, "a short follow-up");

            assertThat(context.truncated()).isFalse();
            assertThat(context.droppedTurns()).isZero();
        }

        @Test
        @DisplayName("a PENDING placeholder is never sent as context")
        void pendingMessagesAreNotContext() throws Exception {
            // A placeholder holds no content yet. Sending one asks the model to treat a platform
            // artefact as something that was said.
            UUID conversation = createConversation();
            send(conversation, "a real question");

            ConversationContext context = select(conversation, "another question");

            assertThat(context.turns()).allSatisfy(turn ->
                    assertThat(turn.content()).doesNotContain("PENDING"));
        }
    }

    // ==================================================================
    // Isolation
    // ==================================================================

    @Nested
    @DisplayName("context isolation")
    class Isolation {

        @Test
        @DisplayName("another user's conversation cannot be read")
        void cannotReadAnotherUsersMemory() throws Exception {
            UUID conversation = createConversation();
            send(conversation, "my private question");

            mvc.perform(get("/api/v1/conversations/{id}/memory", conversation)
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(OTHER_USER))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("another user's memory cannot be cleared")
        void cannotClearAnotherUsersMemory() throws Exception {
            UUID conversation = createConversation();
            send(conversation, "my private question");

            mvc.perform(delete("/api/v1/conversations/{id}/memory", conversation)
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(OTHER_USER)))
                            .with(csrf()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("another user's 404 is identical to one that does not exist")
        void noExistenceOracle() throws Exception {
            UUID conversation = createConversation();
            send(conversation, "my private question");

            String real = mvc.perform(get("/api/v1/conversations/{id}/memory", conversation)
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(OTHER_USER))))
                    .andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString();

            String fake = mvc.perform(
                            get("/api/v1/conversations/{id}/memory", UUID.randomUUID())
                                    .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(OTHER_USER))))
                    .andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString();

            // A different body would confirm the conversation is real, which is a reliable oracle
            // for discovering other people's ids.
            //
            // traceId and timestamp are stripped because they are DIFFERENT BY DESIGN on every
            // response -- comparing them would make this assertion pass for the wrong reason (a
            // test that always fails teaches nothing, and one that is quietly weakened to make
            // it pass is worse than either).
            assertThat(stable(real)).isEqualTo(stable(fake));
        }

        @Test
        @DisplayName("context from one conversation never appears in another")
        void noCrossConversationLeak() throws Exception {
            UUID first = createConversation();
            send(first, "a question about penguins");

            UUID second = createConversation();
            send(second, "a question about submarines");

            ConversationContext context = select(second, "and now something else");

            // Memory is keyed by conversation. A query that forgot the conversation id would
            // pass every ownership check and still leak, so this asserts the key, not the guard.
            assertThat(context.turns().stream().map(t -> t.content()))
                    .noneSatisfy(content -> assertThat(content).contains("penguins"));
        }

        @Test
        @DisplayName("memory routes require authentication")
        void memoryRoutesRequireAuthentication() throws Exception {
            UUID conversation = createConversation();

            // GET needs no CSRF token, so it fails on authentication: 401.
            mvc.perform(get("/api/v1/conversations/{id}/memory", conversation))
                    .andExpect(status().isUnauthorized());

            // DELETE is refused by CSRF at 403, BEFORE authentication is considered. That ordering
            // is correct rather than incidental -- a cross-site form post must not be able to
            // reach an endpoint at all, even when the browser happens to hold a session -- which
            // is why the two methods do not share a status here.
            mvc.perform(delete("/api/v1/conversations/{id}/memory", conversation))
                    .andExpect(status().isForbidden());

            // With a CSRF token but still no session, the DELETE gets as far as authentication
            // and is refused there. Asserted separately so the 403 above cannot be mistaken for
            // "authorisation is working" when it is really "the request never arrived".
            mvc.perform(delete("/api/v1/conversations/{id}/memory", conversation)
                            .with(csrf()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ==================================================================
    // Long conversations and summarisation
    // ==================================================================

    @Nested
    @DisplayName("long conversations")
    class LongConversations {

        @Test
        @DisplayName("a very long conversation still produces a bounded context")
        void veryLongConversationStaysBounded() throws Exception {
            UUID conversation = createConversation();
            for (int i = 0; i < 300; i++) {
                send(conversation, LONG_TURN + i);
            }

            ConversationContext context = select(conversation, "the question at the end");

            assertThat(context.estimatedTokens()).isLessThanOrEqualTo(context.budget());
            assertThat(context.turns().get(context.turns().size() - 1).content())
                    .isEqualTo("the question at the end");
        }

        @Test
        @DisplayName("the context size is bounded by the BUDGET, not by the conversation length")
        void contextSizeIsBoundedByTheBudget() throws Exception {
            UUID conversation = createConversation();
            for (int i = 0; i < 150; i++) {
                send(conversation, LONG_TURN + i);
            }
            int atOneFifty = select(conversation, "question one").turns().size();

            for (int i = 150; i < 600; i++) {
                send(conversation, LONG_TURN + i);
            }
            int atSixHundred = select(conversation, "question two").turns().size();

            // THE invariant, stated as a relationship rather than a magic number.
            //
            // The first version of this test asserted "fewer than 60 turns" and failed with 91.
            // The code was right: 91 turns genuinely fit a 6000-token budget. An arbitrary bound
            // tests nothing -- it passes or fails depending on how long the filler text is.
            //
            // What actually matters is that quadrupling the conversation does NOT quadruple the
            // context. If it did, history would be unbounded no matter how the budget was set,
            // because the budget would simply be ignored.
            assertThat(atSixHundred)
                    .as("a four-times-longer conversation must not produce a bigger context")
                    .isLessThanOrEqualTo(atOneFifty);

            assertThat(select(conversation, "question three").estimatedTokens())
                    .isLessThanOrEqualTo(select(conversation, "question four").budget());
        }
    }

    @Nested
    @DisplayName("summarisation")
    class Summarisation {

        @Test
        @DisplayName("the memory status endpoint reports what is remembered")
        void memoryStatusIsReported() throws Exception {
            UUID conversation = createConversation();
            send(conversation, "a question");

            mvc.perform(get("/api/v1/conversations/{id}/memory", conversation)
                            .header(HttpHeaders.AUTHORIZATION, auth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasSummary").value(false))
                    .andExpect(jsonPath("$.contextTokenBudget").isNumber());
        }

        @Test
        @DisplayName("memory status never returns summary TEXT")
        void memoryStatusNeverReturnsContent() throws Exception {
            UUID conversation = createConversation();
            send(conversation, "a very distinctive private phrase about wombats");

            String body = mvc.perform(get("/api/v1/conversations/{id}/memory", conversation)
                            .header(HttpHeaders.AUTHORIZATION, auth()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            // A summary is a paraphrase of the user's words. Returning it would be another copy
            // to protect, with no UI use for it.
            assertThat(body).doesNotContain("wombats").doesNotContain("summary\"");
        }

        @Test
        @DisplayName("a refresh on a short conversation compresses nothing")
        void shortConversationIsNotCompressed() throws Exception {
            UUID conversation = createConversation();
            send(conversation, "a short question");

            mvc.perform(post("/api/v1/conversations/{id}/memory/refresh", conversation)
                            .header(HttpHeaders.AUTHORIZATION, auth())
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.compressedTurns").value(0));
        }

        @Test
        @DisplayName("a summarisation failure does NOT stop the reply")
        void summarisationFailureDoesNotBlockTheReply() throws Exception {
            // The alternative — refusing to answer because a memory optimisation could not run —
            // would make every reply depend on a second model call succeeding.
            UUID conversation = createConversation();
            for (int i = 0; i < 60; i++) {
                send(conversation, LONG_TURN + i);
            }

            // No AI Service is reachable in this test, so any summarisation attempt fails. The
            // request must still succeed, carrying a bounded verbatim context.
            ConversationContext context = select(conversation, "the question");

            assertThat(context.estimatedTokens()).isLessThanOrEqualTo(context.budget());
            assertThat(context.turns()).isNotEmpty();
        }
    }

    // ==================================================================
    // Clearing
    // ==================================================================

    @Nested
    @DisplayName("clearing memory")
    class Clearing {

        @Test
        @DisplayName("clearing succeeds and reports what was forgotten")
        void clearingReportsOutcome() throws Exception {
            UUID conversation = createConversation();
            send(conversation, "a question");

            mvc.perform(delete("/api/v1/conversations/{id}/memory", conversation)
                            .header(HttpHeaders.AUTHORIZATION, auth())
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hadMemory").value(false))
                    .andExpect(jsonPath("$.memory.hasSummary").value(false));
        }

        @Test
        @DisplayName("clearing memory does NOT delete the conversation's messages")
        void clearingKeepsMessages() throws Exception {
            // "Forget what we discussed" and "delete the discussion" are different requests.
            // Conflating them would either lose the user's own words or fail to forget anything.
            UUID conversation = createConversation();
            send(conversation, "something I said");
            send(conversation, "something else I said");

            mvc.perform(delete("/api/v1/conversations/{id}/memory", conversation)
                            .header(HttpHeaders.AUTHORIZATION, auth())
                            .with(csrf()))
                    .andExpect(status().isOk());

            mvc.perform(get("/api/v1/conversations/{id}/messages", conversation)
                            .header(HttpHeaders.AUTHORIZATION, auth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)));
        }

        @Test
        @DisplayName("the conversation still works after clearing")
        void conversationStillWorksAfterClearing() throws Exception {
            UUID conversation = createConversation();
            send(conversation, "something I said");

            mvc.perform(delete("/api/v1/conversations/{id}/memory", conversation)
                            .header(HttpHeaders.AUTHORIZATION, auth())
                            .with(csrf()))
                    .andExpect(status().isOk());

            send(conversation, "and a question after forgetting");

            ConversationContext context = select(conversation, "another question");
            assertThat(context.turns()).isNotEmpty();
        }

        @Test
        @DisplayName("clearing twice is harmless")
        void clearingIsIdempotent() throws Exception {
            UUID conversation = createConversation();
            send(conversation, "a question");

            for (int i = 0; i < 2; i++) {
                mvc.perform(delete("/api/v1/conversations/{id}/memory", conversation)
                                .header(HttpHeaders.AUTHORIZATION, auth())
                                .with(csrf()))
                        .andExpect(status().isOk());
            }
        }

        @Test
        @DisplayName("clearing a conversation that does not exist is a 404")
        void clearingUnknownIsNotFound() throws Exception {
            mvc.perform(delete("/api/v1/conversations/{id}/memory", UUID.randomUUID())
                            .header(HttpHeaders.AUTHORIZATION, auth())
                            .with(csrf()))
                    .andExpect(status().isNotFound());
        }
    }

    // ==================================================================
    // Helpers
    // ==================================================================

}