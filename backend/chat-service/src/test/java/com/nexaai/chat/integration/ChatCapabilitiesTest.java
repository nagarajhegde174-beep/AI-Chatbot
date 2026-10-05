package com.nexaai.chat.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexaai.chat.support.TestTokens;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * The fourteen required capabilities, each exercised over HTTP against a real database.
 *
 * <p>Grouped by capability so a failure names what broke rather than which test index failed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ChatCapabilitiesTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private UUID owner;

    @BeforeEach
    void setUp() {
        owner = UUID.randomUUID();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private String auth() {
        return bearer(TestTokens.userToken(owner));
    }

    private UUID createConversation(String title) throws Exception {
        String body = mvc.perform(post("/api/v1/conversations")
                        .header(HttpHeaders.AUTHORIZATION, auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(title == null ? "{}" : "{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(idFrom(body));
    }

    private UUID sendMessage(UUID conversationId, String content) throws Exception {
        String body = mvc.perform(post("/api/v1/conversations/" + conversationId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, auth())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // Non-greedy on purpose: the send response carries two ids, and a greedy
        // pattern would hand back the assistant placeholder's instead of the user's.
        return UUID.fromString(body.replaceAll(
                ".*\"userMessage\":\\{\"id\":\"([^\"]+)\".*", "$1"));
    }

    private static String idFrom(String json) {
        return json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    // ==================================================================
    // 1-3. create, list, get
    // ==================================================================

    @Nested
    @DisplayName("1-3. create, list, get")
    class Crud {

        @Test
        @DisplayName("creates a conversation and returns 201 with a Location header")
        void creates() throws Exception {
            mvc.perform(post("/api/v1/conversations")
                            .header(HttpHeaders.AUTHORIZATION, auth())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"First conversation\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists(HttpHeaders.LOCATION))
                    .andExpect(jsonPath("$.title").value("First conversation"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.messageCount").value(0));
        }

        @Test
        @DisplayName("accepts an empty body, because New chat must not need a title")
        void createsWithoutABody() throws Exception {
            mvc.perform(post("/api/v1/conversations")
                            .header(HttpHeaders.AUTHORIZATION, auth())
                            .with(csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.title").value("New conversation"));
        }

        @Test
        @DisplayName("rejects an over-long title")
        void rejectsOverlongTitle() throws Exception {
            mvc.perform(post("/api/v1/conversations")
                            .header(HttpHeaders.AUTHORIZATION, auth())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"" + "x".repeat(201) + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("lists the caller's conversations")
        void lists() throws Exception {
            createConversation("One");
            createConversation("Two");

            mvc.perform(get("/api/v1/conversations")
                            .header(HttpHeaders.AUTHORIZATION, auth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalItems").value(2));
        }

        @Test
        @DisplayName("caps the page size")
        void capsPageSize() throws Exception {
            mvc.perform(get("/api/v1/conversations").param("size", "5000")
                            .header(HttpHeaders.AUTHORIZATION, auth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.size").value(100));
        }

        @Test
        @DisplayName("gets one conversation")
        void gets() throws Exception {
            UUID id = createConversation("Readable");

            mvc.perform(get("/api/v1/conversations/" + id)
                            .header(HttpHeaders.AUTHORIZATION, auth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id.toString()))
                    .andExpect(jsonPath("$.title").value("Readable"));
        }

        @Test
        @DisplayName("404s for an unknown id")
        void unknownIsNotFound() throws Exception {
            mvc.perform(get("/api/v1/conversations/" + UUID.randomUUID())
                            .header(HttpHeaders.AUTHORIZATION, auth()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("404s for a malformed id rather than 500ing")
        void malformedIdIsBadRequest() throws Exception {
            mvc.perform(get("/api/v1/conversations/not-a-uuid")
                            .header(HttpHeaders.AUTHORIZATION, auth()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        }
    }

    // ==================================================================
    // 4. rename
    // ==================================================================

    @Nested
    @DisplayName("4. rename")
    class Rename {

        @Test
        @DisplayName("renames a conversation")
        void renames() throws Exception {
            UUID id = createConversation("Before");

            mvc.perform(patch("/api/v1/conversations/" + id)
                            .header(HttpHeaders.AUTHORIZATION, auth())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"After\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("After"));
        }

        @Test
        @DisplayName("refuses a blank title")
        void refusesBlankTitle() throws Exception {
            UUID id = createConversation("Original");

            mvc.perform(patch("/api/v1/conversations/" + id)
                            .header(HttpHeaders.AUTHORIZATION, auth())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"   \"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================================================================
    // 5. delete
    // ==================================================================

    @Nested
    @DisplayName("5. delete")
    class Delete {

        @Test
        @DisplayName("soft-deletes, so the conversation leaves the listing but the row survives")
        void softDeletes() throws Exception {
            UUID id = createConversation("Doomed");

            mvc.perform(delete("/api/v1/conversations/" + id)
                            .header(HttpHeaders.AUTHORIZATION, auth())
                            .with(csrf()))
                    .andExpect(status().isNoContent());

            mvc.perform(get("/api/v1/conversations")
                            .header(HttpHeaders.AUTHORIZATION, auth()))
                    .andExpect(jsonPath("$.totalItems").value(0));

            mvc.perform(get("/api/v1/conversations/" + id)
                            .header(HttpHeaders.AUTHORIZATION, auth()))
                    .andExpect(status().isNotFound());
        }
    }

    // ==================================================================
    // 6. archive
    // ==================================================================

    @Nested
    @DisplayName("6. archive")
    class Archive {

        @Test
        @DisplayName("archives and restores, which is why it is separate from delete")
        void archivesAndRestores() throws Exception {
            UUID id = createConversation("Archive me");

            mvc.perform(post("/api/v1/conversations/" + id + "/archive")
                            .header(HttpHeaders.AUTHORIZATION, auth())
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ARCHIVED"));

            // Out of the default listing, still readable, and still in the archived listing.
            mvc.perform(get("/api/v1/conversations")
                            .header(HttpHeaders.AUTHORIZATION, auth()))
                    .andExpect(jsonPath("$.totalItems").value(0));
            mvc.perform(get("/api/v1/conversations/archived")
                            .header(HttpHeaders.AUTHORIZATION, auth()))
                    .andExpect(jsonPath("$.totalItems").value(1));

            mvc.perform(post("/api/v1/conversations/" + id + "/restore")
                            .header(HttpHeaders.AUTHORIZATION, auth())
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("refuses to send a message to an archived conversation")
        void refusesSendWhileArchived() throws Exception {
            UUID id = createConversation("Archived");
            mvc.perform(post("/api/v1/conversations/" + id + "/archive")
                    .header(HttpHeaders.AUTHORIZATION, auth())
                    .with(csrf()))
                    .andExpect(status().isOk());

            mvc.perform(post("/api/v1/conversations/" + id + "/messages")
                            .header(HttpHeaders.AUTHORIZATION, auth())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"Hello\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONFLICT"));
        }
    }

    // ==================================================================
    // 7. search
    // ==================================================================

    @Nested
    @DisplayName("7. search")
    class Search {

        @Test
        @DisplayName("finds a conversation by title")
        void searchesByTitle() throws Exception {
            createConversation("Kubernetes networking notes");
            createConversation("Sourdough recipe");

            mvc.perform(get("/api/v1/conversations/search").param("q", "kubernetes")
                            .header(HttpHeaders.AUTHORIZATION, auth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalItems").value(1))
                    .andExpect(jsonPath("$.content[0].title")
                            .value("Kubernetes networking notes"));
        }

        @Test
        @DisplayName("finds a conversation by the text of a message, which the title does not contain")
        void searchesByMessageContent() throws Exception {
            UUID id = createConversation("Untitled chat");
            sendMessage(id, "The answer is forty two");

            mvc.perform(get("/api/v1/conversations/search").param("q", "forty two")
                            .header(HttpHeaders.AUTHORIZATION, auth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalItems").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(id.toString()));
        }

        @Test
        @DisplayName("a wildcard in the query is escaped, so it cannot match everything")
        void escapesWildcards() throws Exception {
            createConversation("Alpha");
            createConversation("Beta");

            // Without escaping, '%' would match every conversation and "search" would return
            // everything, which is how a filtered list silently becomes a full dump.
            mvc.perform(get("/api/v1/conversations/search").param("q", "%")
                            .header(HttpHeaders.AUTHORIZATION, auth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalItems").value(0));
        }
    }

    // ==================================================================
    // 8, 9, 10. send, store user message, store the AI placeholder
    // ==================================================================

    @Nested
    @DisplayName("8-10. send, store, placeholder")
    class Send {

        @Test
        @DisplayName("stores the user message and creates a PENDING assistant placeholder")
        void storesBothRows() throws Exception {
            UUID id = createConversation("Send test");

            mvc.perform(post("/api/v1/conversations/" + id + "/messages")
                            .header(HttpHeaders.AUTHORIZATION, auth())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"What is the capital of France?\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userMessage.role").value("USER"))
                    .andExpect(jsonPath("$.userMessage.status").value("COMPLETE"))
                    .andExpect(jsonPath("$.userMessage.content")
                            .value("What is the capital of France?"))
                    // The placeholder exists and is honestly PENDING: a reply is expected and
                    // has not arrived. Not an error, and not a fabricated answer.
                    .andExpect(jsonPath("$.assistantMessage.role").value("ASSISTANT"))
                    .andExpect(jsonPath("$.assistantMessage.status").value("PENDING"))
                    .andExpect(jsonPath("$.assistantMessage.content").value(""));
        }

        @Test
        @DisplayName("titles an untitled conversation from its first message")
        void derivesTitleFromFirstMessage() throws Exception {
            UUID id = createConversation(null);

            mvc.perform(post("/api/v1/conversations/" + id + "/messages")
                            .header(HttpHeaders.AUTHORIZATION, auth())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"How do I reverse a linked list?\"}"))
                    .andExpect(status().isOk());

            mvc.perform(get("/api/v1/conversations/" + id)
                            .header(HttpHeaders.AUTHORIZATION, auth()))
                    .andExpect(jsonPath("$.title").value("How do I reverse a linked list?"));
        }

        @Test
        @DisplayName("does NOT overwrite a title the user chose with a later message")
        void doesNotOverwriteAChosenTitle() throws Exception {
            UUID id = createConversation("My chosen title");

            sendMessage(id, "A completely different question");

            mvc.perform(get("/api/v1/conversations/" + id)
                            .header(HttpHeaders.AUTHORIZATION, auth()))
                    .andExpect(jsonPath("$.title").value("My chosen title"));
        }

        @Test
        @DisplayName("rejects a blank message")
        void rejectsBlankContent() throws Exception {
            UUID id = createConversation("Blank");

            mvc.perform(post("/api/v1/conversations/" + id + "/messages")
                            .header(HttpHeaders.AUTHORIZATION, auth())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"   \"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("history contains both messages in sequence order")
        void historyIsOrdered() throws Exception {
            UUID id = createConversation("Ordered");
            sendMessage(id, "First question");
            sendMessage(id, "Second question");

            mvc.perform(get("/api/v1/conversations/" + id + "/messages")
                            .header(HttpHeaders.AUTHORIZATION, auth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(4))
                    .andExpect(jsonPath("$[0].content").value("First question"))
                    .andExpect(jsonPath("$[1].role").value("ASSISTANT"))
                    .andExpect(jsonPath("$[2].content").value("Second question"))
                    .andExpect(jsonPath("$[3].role").value("ASSISTANT"));
        }

        @Test
        @DisplayName("the conversation counter tracks the messages actually stored")
        void counterTracksStoredMessages() throws Exception {
            UUID id = createConversation("Counted");
            sendMessage(id, "One");
            sendMessage(id, "Two");

            mvc.perform(get("/api/v1/conversations/" + id)
                            .header(HttpHeaders.AUTHORIZATION, auth()))
                    .andExpect(jsonPath("$.messageCount").value(4));
        }
    }

    // ==================================================================
    // 11. regenerate
    // ==================================================================

    @Nested
    @DisplayName("11. regenerate")
    class Regenerate {

        @Test
        @DisplayName("supersedes the previous attempt and keeps it out of history")
        void regenerates() throws Exception {
            UUID id = createConversation("Regenerate me");
            sendMessage(id, "Question");

            String assistantId = assistantIdOf(id);

            mvc.perform(post("/api/v1/messages/" + assistantId + "/regenerate")
                            .header(HttpHeaders.AUTHORIZATION, auth())
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("ASSISTANT"))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.regenerated").value(true));

            // History shows exactly one current answer to the question: the superseded attempt
            // is still in the table, but it is not part of the conversation as the user sees it.
            mvc.perform(get("/api/v1/conversations/" + id + "/messages")
                            .header(HttpHeaders.AUTHORIZATION, auth()))
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].role").value("USER"))
                    .andExpect(jsonPath("$[1].role").value("ASSISTANT"));
        }

        @Test
        @DisplayName("refuses to regenerate a user message")
        void refusesUserMessage() throws Exception {
            UUID id = createConversation("Regenerate a question");
            UUID userMessageId = sendMessage(id, "Question");

            mvc.perform(post("/api/v1/messages/" + userMessageId + "/regenerate")
                            .header(HttpHeaders.AUTHORIZATION, auth())
                            .with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Only an assistant message can be "
                            + "regenerated; this one is USER."));
        }

        private String assistantIdOf(UUID conversationId) throws Exception {
            return mvc.perform(get("/api/v1/conversations/" + conversationId + "/messages")
                            .header(HttpHeaders.AUTHORIZATION, auth()))
                    .andExpect(jsonPath("$[1].role").value("ASSISTANT"))
                    .andReturn().getResponse().getContentAsString()
                    .replaceAll("(?s).*\"id\":\"([^\"]+)\".*\"conversationId\":\"" + conversationId
                            + "\".*\"role\":\"ASSISTANT\".*", "$1");
        }
    }

    // ==================================================================
    // 12. edit and resend
    // ==================================================================

    @Nested
    @DisplayName("12. edit and resend")
    class Edit {

        @Test
        @DisplayName("supersedes the original, adds the edited message and a new placeholder")
        void editsAndResends() throws Exception {
            UUID id = createConversation("Edit me");
            UUID originalId = sendMessage(id, "Origional question");

            mvc.perform(put("/api/v1/messages/" + originalId)
                            .header(HttpHeaders.AUTHORIZATION, auth())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"Corrected question\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userMessage.content").value("Corrected question"))
                    .andExpect(jsonPath("$.userMessage.edited").value(true))
                    .andExpect(jsonPath("$.assistantMessage.role").value("ASSISTANT"))
                    .andExpect(jsonPath("$.assistantMessage.status").value("PENDING"));

            // The superseded original and the superseded placeholder leave the visible history;
            // what remains is the edited question and the new placeholder awaiting an answer.
            mvc.perform(get("/api/v1/conversations/" + id + "/messages")
                            .header(HttpHeaders.AUTHORIZATION, auth()))
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].content").value("Corrected question"))
                    .andExpect(jsonPath("$[0].edited").value(true))
                    .andExpect(jsonPath("$[1].role").value("ASSISTANT"));
        }

        @Test
        @DisplayName("refuses to edit an assistant message")
        void refusesAssistantMessage() throws Exception {
            UUID id = createConversation("Edit an answer");
            sendMessage(id, "Question");
            String assistantId = mvc.perform(
                            get("/api/v1/conversations/" + id + "/messages")
                                    .header(HttpHeaders.AUTHORIZATION, auth()))
                    .andExpect(jsonPath("$[1].role").value("ASSISTANT"))
                    .andReturn().getResponse().getContentAsString()
                    .replaceAll("(?s).*\"id\":\"([^\"]+)\".*\"role\":\"ASSISTANT\".*", "$1");

            mvc.perform(put("/api/v1/messages/" + assistantId)
                            .header(HttpHeaders.AUTHORIZATION, auth())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"content\":\"Rewritten answer\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================================================================
    // 13. feedback
    // ==================================================================

    @Nested
    @DisplayName("13. feedback")
    class Feedback {

        @Test
        @DisplayName("records a rating")
        void recordsRating() throws Exception {
            UUID id = createConversation("Rate me");
            UUID messageId = sendMessage(id, "Question");

            mvc.perform(put("/api/v1/messages/" + messageId + "/feedback")
                            .header(HttpHeaders.AUTHORIZATION, auth())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rating\":\"UP\",\"comment\":\"Clear\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rating").value("UP"))
                    .andExpect(jsonPath("$.comment").value("Clear"));
        }

        @Test
        @DisplayName("re-rating UPDATES the existing row rather than adding a second one")
        void reratingUpdates() throws Exception {
            UUID id = createConversation("Re-rate");
            UUID messageId = sendMessage(id, "Question");

            mvc.perform(put("/api/v1/messages/" + messageId + "/feedback")
                    .header(HttpHeaders.AUTHORIZATION, auth()).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"UP\"}"))
                    .andExpect(status().isOk());
            mvc.perform(put("/api/v1/messages/" + messageId + "/feedback")
                    .header(HttpHeaders.AUTHORIZATION, auth()).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"DOWN\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rating").value("DOWN"));

            // One row, and the message carries it.
            mvc.perform(get("/api/v1/conversations/" + id + "/messages")
                            .header(HttpHeaders.AUTHORIZATION, auth()))
                    .andExpect(jsonPath("$[0].feedback.rating").value("DOWN"));
        }

        @Test
        @DisplayName("rejects an unknown rating rather than defaulting")
        void rejectsUnknownRating() throws Exception {
            UUID id = createConversation("Bad rating");
            UUID messageId = sendMessage(id, "Question");

            // Defaulting an unrecognised rating to UP would corrupt the sentiment data with no
            // signal that anything went wrong.
            mvc.perform(put("/api/v1/messages/" + messageId + "/feedback")
                            .header(HttpHeaders.AUTHORIZATION, auth())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rating\":\"FIVE\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("clears a rating")
        void clearsRating() throws Exception {
            UUID id = createConversation("Unrate");
            UUID messageId = sendMessage(id, "Question");
            mvc.perform(put("/api/v1/messages/" + messageId + "/feedback")
                    .header(HttpHeaders.AUTHORIZATION, auth()).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"DOWN\"}"));

            mvc.perform(delete("/api/v1/messages/" + messageId + "/feedback")
                            .header(HttpHeaders.AUTHORIZATION, auth())
                            .with(csrf()))
                    .andExpect(status().isNoContent());

            mvc.perform(get("/api/v1/conversations/" + id + "/messages")
                            .header(HttpHeaders.AUTHORIZATION, auth()))
                    .andExpect(jsonPath("$[0].feedback").doesNotExist());
        }
    }

    // ==================================================================
    // 14. export
    // ==================================================================

    @Nested
    @DisplayName("14. export")
    class Export {

        @Test
        @DisplayName("exports Markdown with the conversation's content")
        void exportsMarkdown() throws Exception {
            UUID id = createConversation("Export me");
            sendMessage(id, "Remember this sentence");

            mvc.perform(get("/api/v1/conversations/" + id + "/export")
                            .header(HttpHeaders.AUTHORIZATION, auth())
                            // Explicit accept: the export path maps two producers, so the
                            // negotiated type depends on what the client asks for.
                            .accept(MediaType.TEXT_PLAIN))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                            org.hamcrest.Matchers.containsString("conversation-" + id)))
                    .andExpect(content().string(
                            org.hamcrest.Matchers.containsString("# Export me")))
                    .andExpect(content().string(
                            org.hamcrest.Matchers.containsString("Remember this sentence")));
        }

        @Test
        @DisplayName("exports JSON carrying the history")
        void exportsJson() throws Exception {
            UUID id = createConversation("Export JSON");
            sendMessage(id, "A question");

            mvc.perform(get("/api/v1/conversations/" + id + "/export")
                            .header(HttpHeaders.AUTHORIZATION, auth())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.formatVersion").value("1.0"))
                    .andExpect(jsonPath("$.conversationId").value(id.toString()))
                    .andExpect(jsonPath("$.messages.length()").value(2))
                    .andExpect(jsonPath("$.messages[0].content").value("A question"));
        }

        @Test
        @DisplayName("the filename contains only the id, never the title")
        void filenameIsNotUserControlled() throws Exception {
            // The title is user-supplied text. Putting it in Content-Disposition is how a
            // newline in a title becomes header injection, so the filename is derived from the
            // conversation id alone.
            UUID id = createConversation("Confidential merger plans");

            String disposition = mvc.perform(get("/api/v1/conversations/" + id + "/export")
                            .header(HttpHeaders.AUTHORIZATION, auth()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);

            assertThat(disposition)
                    .as("the filename must be the UUID and nothing the user typed")
                    .contains(id.toString())
                    .doesNotContain("Confidential")
                    .doesNotContain("\n")
                    .doesNotContain("\r");
        }
    }

    // ==================================================================
    // Model pinning
    // ==================================================================

    @Nested
    @DisplayName("model pinning")
    class Models {

        @Test
        @DisplayName("pins a model, and the placeholder records it")
        void pinsModel() throws Exception {
            UUID id = createConversation("Pinned");

            mvc.perform(patch("/api/v1/conversations/" + id + "/model")
                            .header(HttpHeaders.AUTHORIZATION, auth())
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"model\":\"gpt-x\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.model").value("gpt-x"));

            sendMessage(id, "Question");

            mvc.perform(get("/api/v1/conversations/" + id + "/messages")
                            .header(HttpHeaders.AUTHORIZATION, auth()))
                    .andExpect(jsonPath("$[1].model").value("gpt-x"));
        }

        @Test
        @DisplayName("falls back to the configured default when nothing is pinned")
        void fallsBackToDefault() throws Exception {
            UUID id = createConversation("Unpinned");
            sendMessage(id, "Question");

            mvc.perform(get("/api/v1/conversations/" + id + "/messages")
                            .header(HttpHeaders.AUTHORIZATION, auth()))
                    .andExpect(jsonPath("$[1].model").value("nexa-test-model"));
        }
    }
}