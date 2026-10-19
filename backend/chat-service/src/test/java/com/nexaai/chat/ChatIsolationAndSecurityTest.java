package com.nexaai.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexaai.chat.dto.CreateConversationRequest;
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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatIsolationAndSecurityTest {

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

    private UUID userA;
    private String tokenA;

    private UUID userB;
    private String tokenB;

    private UUID adminUser;
    private String adminToken;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        conversationRepository.deleteAll();

        userA = UUID.randomUUID();
        tokenA = jwtHelper.generateToken(userA, "userA@example.com", List.of("USER"));

        userB = UUID.randomUUID();
        tokenB = jwtHelper.generateToken(userB, "userB@example.com", List.of("USER"));

        adminUser = UUID.randomUUID();
        adminToken = jwtHelper.generateToken(adminUser, "admin@example.com", List.of("ADMIN"));
    }

    @Test
    void testUserIsolationCannotAccessAnotherUsersConversation() throws Exception {
        // User A creates a conversation
        CreateConversationRequest req = new CreateConversationRequest("User A Private Chat", "gpt-4o-mini", null, true);
        String createRes = mockMvc.perform(post("/api/v1/chat/conversations")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String convIdStr = objectMapper.readTree(createRes).get("id").asText();
        UUID convId = UUID.fromString(convIdStr);

        // User B attempts to GET conversation of User A -> 403 Forbidden
        mockMvc.perform(get("/api/v1/chat/conversations/" + convId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        // User B attempts to list messages of User A -> 403 Forbidden
        mockMvc.perform(get("/api/v1/chat/conversations/" + convId + "/messages")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        // User B attempts to send message to User A's conversation -> 403 Forbidden
        SendMessageRequest sendReq = new SendMessageRequest("Infiltration attempt", null, null);
        mockMvc.perform(post("/api/v1/chat/conversations/" + convId + "/messages")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sendReq)))
                .andExpect(status().isForbidden());

        // User B attempts to update User A's conversation -> 403 Forbidden
        UpdateConversationRequest updateReq = new UpdateConversationRequest("Tampered Title", null, null, null, null);
        mockMvc.perform(patch("/api/v1/chat/conversations/" + convId)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isForbidden());

        // User B attempts to delete User A's conversation -> 403 Forbidden
        mockMvc.perform(delete("/api/v1/chat/conversations/" + convId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        // User B list conversations should NOT include User A's conversation
        mockMvc.perform(get("/api/v1/chat/conversations")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void testAdminAccessAllowedToReadConversations() throws Exception {
        // User A creates a conversation
        CreateConversationRequest req = new CreateConversationRequest("User A Conversation", "gpt-4o-mini", null, true);
        String createRes = mockMvc.perform(post("/api/v1/chat/conversations")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String convIdStr = objectMapper.readTree(createRes).get("id").asText();
        UUID convId = UUID.fromString(convIdStr);

        // Admin can view conversation via AdminChatController
        mockMvc.perform(get("/api/v1/admin/chat/conversations")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(convId.toString()));

        mockMvc.perform(get("/api/v1/admin/chat/conversations/" + convId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(convId.toString()));
    }

    @Test
    void testUserRoleCannotAccessAdminRoutes() throws Exception {
        mockMvc.perform(get("/api/v1/admin/chat/conversations")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());
    }

    @Test
    void testUnauthenticatedRequestsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/chat/conversations"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/chat/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
