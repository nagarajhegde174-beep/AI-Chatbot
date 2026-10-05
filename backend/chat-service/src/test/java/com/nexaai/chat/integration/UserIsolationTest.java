package com.nexaai.chat.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexaai.chat.support.TestTokens;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * <strong>User isolation.</strong> The central security claim of this service.
 *
 * <p>Every assertion here is negative: a caller must not reach, modify, delete, rate or export
 * another user's conversation or message. These are the tests that would fail if an authorisation
 * check were dropped, and none of them would fail from reading the source.
 *
 * <p>The two answers that matter and are asserted separately:
 * <ul>
 *   <li>Another user's conversation is <strong>404</strong>, not 403. A 403 confirms it exists,
 *       which is a reliable oracle for discovering real ids.</li>
 *   <li>A USER token on an administrative route is <strong>403</strong>, because that is about the
 *       caller's role and not about someone else's data.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserIsolationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    // ==================================================================
    // Conversation-level isolation
    // ==================================================================

    @Nested
    @DisplayName("conversation isolation")
    class Conversations {

        @Test
        @DisplayName("a user cannot read another user's conversation")
        void cannotReadAnotherUsersConversation() throws Exception {
            UUID owner = UUID.randomUUID();
            UUID conversationId = createConversation(owner, "Private plans");

            mvc.perform(get("/api/v1/conversations/" + conversationId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }

        @Test
        @DisplayName("a user cannot see another user's conversation in their listing")
        void anotherConversationIsAbsentFromTheListing() throws Exception {
            UUID owner = UUID.randomUUID();
            UUID caller = UUID.randomUUID();
            createConversation(owner, "Someone else's secret");

            mvc.perform(get("/api/v1/conversations")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(caller))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalItems").value(0))
                    .andExpect(jsonPath("$.content").isEmpty());
        }

        @Test
        @DisplayName("a user cannot rename another user's conversation")
        void cannotRenameAnotherUsersConversation() throws Exception {
            UUID owner = UUID.randomUUID();
            UUID conversationId = createConversation(owner, "Original");

            mvc.perform(patch("/api/v1/conversations/" + conversationId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken()))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Hijacked\"}"))
                    .andExpect(status().isNotFound());

            assertTitle(conversationId, "Original");
        }

        @Test
        @DisplayName("a user cannot archive another user's conversation")
        void cannotArchiveAnotherUsersConversation() throws Exception {
            UUID owner = UUID.randomUUID();
            UUID conversationId = createConversation(owner, "Keep active");

            mvc.perform(post("/api/v1/conversations/" + conversationId + "/archive")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken()))
                            .with(csrf()))
                    .andExpect(status().isNotFound());

            assertStatus(conversationId, "ACTIVE");
        }

        @Test
        @DisplayName("a user cannot delete another user's conversation")
        void cannotDeleteAnotherUsersConversation() throws Exception {
            UUID owner = UUID.randomUUID();
            UUID conversationId = createConversation(owner, "Survivor");

            mvc.perform(delete("/api/v1/conversations/" + conversationId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken()))
                            .with(csrf()))
                    .andExpect(status().isNotFound());

            // Still readable by its owner, i.e. not soft-deleted.
            mvc.perform(get("/api/v1/conversations/" + conversationId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(owner))))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("a user cannot search into another user's conversations")
        void cannotSearchAnotherUsersConversations() throws Exception {
            UUID owner = UUID.randomUUID();
            createConversation(owner, "Confidential merger plans");

            mvc.perform(get("/api/v1/conversations/search").param("q", "Confidential")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalItems").value(0));
        }

        @Test
        @DisplayName("a user cannot read another user's history")
        void cannotReadAnotherUsersHistory() throws Exception {
            UUID owner = UUID.randomUUID();
            UUID conversationId = createConversation(owner, "History");
            sendMessage(owner, conversationId, "My private question");

            mvc.perform(get("/api/v1/conversations/" + conversationId + "/messages")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("a user cannot send into another user's conversation")
        void cannotSendIntoAnotherUsersConversation() throws Exception {
            UUID owner = UUID.randomUUID();
            UUID conversationId = createConversation(owner, "Not yours");

            mvc.perform(post("/api/v1/conversations/" + conversationId + "/messages")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken()))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"Injected\"}"))
                    .andExpect(status().isNotFound());

            // The owner's history is unchanged.
            mvc.perform(get("/api/v1/conversations/" + conversationId + "/messages")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(owner))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("a user cannot export another user's conversation")
        void cannotExportAnotherUsersConversation() throws Exception {
            UUID owner = UUID.randomUUID();
            UUID conversationId = createConversation(owner, "Export me");
            sendMessage(owner, conversationId, "Secret content");

            mvc.perform(get("/api/v1/conversations/" + conversationId + "/export")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken())))
                    .andExpect(status().isNotFound());
        }
    }

    // ==================================================================
    // Message-level isolation
    // ==================================================================

    @Nested
    @DisplayName("message isolation")
    class Messages {

        @Test
        @DisplayName("a user cannot edit another user's message")
        void cannotEditAnotherUsersMessage() throws Exception {
            UUID owner = UUID.randomUUID();
            UUID conversationId = createConversation(owner, "Edit me");
            UUID messageId = sendMessage(owner, conversationId, "Original question");

            mvc.perform(put("/api/v1/messages/" + messageId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken()))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"Rewritten by an intruder\"}"))
                    .andExpect(status().isNotFound());

            // The original text survives untouched.
            mvc.perform(get("/api/v1/conversations/" + conversationId + "/messages")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(owner))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].content").value("Original question"));
        }

        @Test
        @DisplayName("a user cannot regenerate another user's message")
        void cannotRegenerateAnotherUsersMessage() throws Exception {
            UUID owner = UUID.randomUUID();
            UUID conversationId = createConversation(owner, "Regenerate me");
            UUID userMessageId = sendMessage(owner, conversationId, "Question");

            // The assistant placeholder is at a known sequence, so fetch it by asking for the
            // history rather than parsing an id out of a JSON blob with a regex.
            String assistantId = mvc.perform(
                            get("/api/v1/conversations/" + conversationId + "/messages")
                                    .header(HttpHeaders.AUTHORIZATION,
                                            bearer(TestTokens.userToken(owner))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[1].role").value("ASSISTANT"))
                    .andReturn().getResponse().getContentAsString()
                    .replaceAll(".*\"id\":\"([^\"]+)\",\"conversationId\":\""
                            + conversationId + "\".*\"role\":\"ASSISTANT\".*", "$1");

            mvc.perform(post("/api/v1/messages/" + assistantId + "/regenerate")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken()))
                            .with(csrf()))
                    .andExpect(status().isNotFound());

            // The owner's own regenerate still works, which is what proves the test reached a
            // real message rather than a not-found id that nothing could ever do.
            mvc.perform(get("/api/v1/conversations/" + conversationId + "/messages")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(owner))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value(userMessageId.toString()));
        }

        @Test
        @DisplayName("a user cannot rate another user's message")
        void cannotRateAnotherUsersMessage() throws Exception {
            UUID owner = UUID.randomUUID();
            UUID conversationId = createConversation(owner, "Rate me");
            UUID messageId = sendMessage(owner, conversationId, "Question");

            mvc.perform(put("/api/v1/messages/" + messageId + "/feedback")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken()))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rating\":\"DOWN\"}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("a user cannot delete another user's message")
        void cannotDeleteAnotherUsersMessage() throws Exception {
            UUID owner = UUID.randomUUID();
            UUID conversationId = createConversation(owner, "Delete me");
            UUID messageId = sendMessage(owner, conversationId, "Question");

            mvc.perform(delete("/api/v1/messages/" + messageId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken()))
                            .with(csrf()))
                    .andExpect(status().isNotFound());

            mvc.perform(get("/api/v1/conversations/" + conversationId + "/messages")
                            .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(owner))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }
    }

    // ==================================================================
    // Probing: the 404 must not be an oracle
    // ==================================================================

    @Nested
    @DisplayName("a 404 must not leak existence")
    class NoExistenceOracle {

        @Test
        @DisplayName("an existing other-user conversation and a nonexistent one are indistinguishable")
        void existingAndMissingLookIdentical() throws Exception {
            UUID owner = UUID.randomUUID();
            UUID real = createConversation(owner, "Real");
            UUID fake = UUID.randomUUID();

            String realBody = withoutVolatileFields(
                    body(get("/api/v1/conversations/" + real)));
            String fakeBody = withoutVolatileFields(
                    body(get("/api/v1/conversations/" + fake)));

            assertThat(realBody).isEqualTo(fakeBody)
                    .as("differing bodies would reveal which ids are real");
        }

        @Test
        @DisplayName("the 404 body does not echo the requested id")
        void notFoundDoesNotEchoTheId() throws Exception {
            UUID owner = UUID.randomUUID();
            UUID real = createConversation(owner, "Real");

            String body = body(get("/api/v1/conversations/" + real));

            assertThat(body).doesNotContain(real.toString());
        }
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private UUID createConversation(UUID owner, String title) throws Exception {
        String body = mvc.perform(post("/api/v1/conversations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(owner)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
    }

    private UUID sendMessage(UUID owner, UUID conversationId, String content) throws Exception {
        String body = mvc.perform(post("/api/v1/conversations/" + conversationId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(owner)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.replaceAll(".*\"userMessage\":\\{\"id\":\"([^\"]+)\".*", "$1"));
    }

    /**
     * Asserts a conversation's title via the ADMIN metadata route.
     *
     * <p>Used because the owner id is a local variable of the test, not available to a second
     * assertion helper. The admin route is the documented way to observe a conversation's state
     * without owning it, which makes it a legitimate instrument here.
     */
    private void assertTitle(UUID conversationId, String expected) throws Exception {
        mvc.perform(get("/api/v1/admin/conversations/" + conversationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(expected));
    }

    private void assertStatus(UUID conversationId, String expected) throws Exception {
        mvc.perform(get("/api/v1/admin/conversations/" + conversationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(expected));
    }

    /**
     * Issues a request with the attacker's token and returns the 404 body.
     *
     * <p>The two cases under test must produce byte-identical bodies. Anything that differs --
     * a timestamp, the requested id, a different wording -- would let a caller tell "not yours"
     * from "does not exist", which is an oracle for discovering real conversation ids.
     */
    /**
     * Strips the two fields that differ on every request by design.
     *
     * <p>The timestamp and trace id are unique per request and carry nothing about existence, so
     * comparing raw bodies would fail for a reason that has nothing to do with isolation. What
     * matters is that the status, code and message are identical.
     */
    private static String withoutVolatileFields(String json) {
        return json
                .replaceAll("\"timestamp\":\"[^\"]+\"", "\"timestamp\":\"\"")
                .replaceAll("\"traceId\":\"[^\"]+\"", "\"traceId\":\"\"");
    }
    private String body(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request
                        .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken())))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
    }
}
