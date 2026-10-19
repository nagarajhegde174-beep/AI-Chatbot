package com.nexaai.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexaai.chat.domain.FeedbackType;
import com.nexaai.chat.dto.CreateConversationRequest;
import com.nexaai.chat.dto.EditMessageRequest;
import com.nexaai.chat.dto.MessageFeedbackRequest;
import com.nexaai.chat.dto.SendMessageRequest;
import com.nexaai.chat.dto.UpdateConversationRequest;
import com.nexaai.chat.repository.ConversationRepository;
import com.nexaai.chat.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private TestJwtHelper jwtHelper;

    private UUID userId;
    private String userToken;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        conversationRepository.deleteAll();

        userId = UUID.randomUUID();
        userToken = jwtHelper.generateToken(userId, "ada@example.com", List.of("USER"));
    }

    @Test
    void testFullConversationAndMessageLifecycle() throws Exception {
        // 1. Create conversation
        CreateConversationRequest createReq = new CreateConversationRequest("Project Discussion", "gpt-4o-mini", "You are a helpful assistant.", true);
        String createRes = mockMvc.perform(post("/api/v1/chat/conversations")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("Project Discussion"))
                .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.ragEnabled").value(true))
                .andExpect(jsonPath("$.isArchived").value(false))
                .andReturn().getResponse().getContentAsString();

        String convIdStr = objectMapper.readTree(createRes).get("id").asText();
        UUID conversationId = UUID.fromString(convIdStr);

        // 2. List conversations
        mockMvc.perform(get("/api/v1/chat/conversations")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Project Discussion"));

        // 3. Get conversation
        mockMvc.perform(get("/api/v1/chat/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(conversationId.toString()))
                .andExpect(jsonPath("$.title").value("Project Discussion"));

        // 4. Rename / update conversation
        UpdateConversationRequest updateReq = new UpdateConversationRequest("Architecture Discussion", "gpt-4o", null, null, null);
        mockMvc.perform(patch("/api/v1/chat/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Architecture Discussion"))
                .andExpect(jsonPath("$.model").value("gpt-4o"));

        // 5. Send message (stores user message + returns AI response placeholder)
        SendMessageRequest msgReq = new SendMessageRequest("Hello, can you help with system design?", null, null);
        String sendRes = mockMvc.perform(post("/api/v1/chat/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(msgReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.content", containsString("This is a placeholder response")))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalTokens").isNumber())
                .andReturn().getResponse().getContentAsString();

        String assistantMsgIdStr = objectMapper.readTree(sendRes).get("id").asText();

        // 6. List messages for conversation (should have 2 messages: USER + ASSISTANT)
        String messagesRes = mockMvc.perform(get("/api/v1/chat/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].role").value("USER"))
                .andExpect(jsonPath("$[0].content").value("Hello, can you help with system design?"))
                .andExpect(jsonPath("$[1].role").value("ASSISTANT"))
                .andReturn().getResponse().getContentAsString();

        String userMsgIdStr = objectMapper.readTree(messagesRes).get(0).get("id").asText();

        // 7. Regenerate foundation
        mockMvc.perform(post("/api/v1/chat/conversations/" + conversationId + "/messages/" + assistantMsgIdStr + "/regenerate")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ASSISTANT"))
                .andExpect(jsonPath("$.isRegenerated").value(true));

        // 8. Edit / resend foundation
        EditMessageRequest editReq = new EditMessageRequest("Hello, can you help with database design?");
        mockMvc.perform(post("/api/v1/chat/conversations/" + conversationId + "/messages/" + userMsgIdStr + "/edit")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ASSISTANT"));

        // 9. Feedback on message
        MessageFeedbackRequest feedbackReq = new MessageFeedbackRequest(FeedbackType.THUMBS_UP, "Great placeholder explanation!");
        mockMvc.perform(post("/api/v1/chat/conversations/" + conversationId + "/messages/" + assistantMsgIdStr + "/feedback")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(feedbackReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback").value("THUMBS_UP"))
                .andExpect(jsonPath("$.feedbackComment").value("Great placeholder explanation!"));

        // 10. Archive and unarchive conversation
        mockMvc.perform(post("/api/v1/chat/conversations/" + conversationId + "/archive")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isArchived").value(true));

        mockMvc.perform(get("/api/v1/chat/conversations?archived=true")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(post("/api/v1/chat/conversations/" + conversationId + "/unarchive")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isArchived").value(false));

        // 11. Search conversation
        mockMvc.perform(get("/api/v1/chat/conversations/search?q=database")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(conversationId.toString()));

        // 12. Export foundation (json and markdown)
        mockMvc.perform(get("/api/v1/chat/conversations/" + conversationId + "/export?format=markdown")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("markdown"))
                .andExpect(jsonPath("$.content", containsString("# Architecture Discussion")));

        // 13. Delete conversation
        mockMvc.perform(delete("/api/v1/chat/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNoContent());

        // Verify conversation is no longer listed
        mockMvc.perform(get("/api/v1/chat/conversations")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void testBoundaryAndHealthEndpoints() throws Exception {
        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("chat-service"));

        mockMvc.perform(get("/internal/v1/chat/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("chat-service"))
                .andExpect(jsonPath("$.port").value(8083))
                .andExpect(jsonPath("$.database").value("nexa_chat"))
                .andExpect(jsonPath("$.phase").value(3));
    }
}
