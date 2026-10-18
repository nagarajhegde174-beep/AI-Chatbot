package com.nexaai.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexaai.user.domain.entity.UserProfile;
import com.nexaai.user.domain.model.AccountStatus;
import com.nexaai.user.domain.repository.UserProfileRepository;
import com.nexaai.user.dto.request.UpdateUserStatusRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserServiceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private TestJwtHelper jwtHelper;

    private UUID adminId;
    private String adminToken;
    private String regularUserToken;
    private UUID targetUserId;

    @BeforeEach
    void setUp() {
        userProfileRepository.deleteAll();

        adminId = UUID.randomUUID();
        adminToken = jwtHelper.generateToken(adminId, "admin@example.com", List.of("ADMIN", "USER"));

        UUID normalUserId = UUID.randomUUID();
        regularUserToken = jwtHelper.generateToken(normalUserId, "normal@example.com", List.of("USER"));

        targetUserId = UUID.randomUUID();
        UserProfile targetUser = new UserProfile(targetUserId, "target@example.com", "Target User", AccountStatus.ACTIVE);
        userProfileRepository.save(targetUser);

        UserProfile secondUser = new UserProfile(UUID.randomUUID(), "bob@example.com", "Bob Builder", AccountStatus.SUSPENDED);
        userProfileRepository.save(secondUser);
    }

    @Test
    void listUsers_asAdmin_returnsPaginatedUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void listUsers_asRegularUser_returns403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + regularUserToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("USER_FORBIDDEN"));
    }

    @Test
    void searchUsers_withQuery_returnsMatchingOnly() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("query", "Target")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].email").value("target@example.com"));
    }

    @Test
    void filterUsers_byStatus_returnsMatchingStatusOnly() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("status", "SUSPENDED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].email").value("bob@example.com"));
    }

    @Test
    void getUserDetails_asAdmin_returnsUser() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/" + targetUserId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(targetUserId.toString()))
                .andExpect(jsonPath("$.email").value("target@example.com"));
    }

    @Test
    void updateUserStatus_toSuspended_updatesStatus() throws Exception {
        UpdateUserStatusRequest request = new UpdateUserStatusRequest("SUSPENDED");

        mockMvc.perform(patch("/api/v1/admin/users/" + targetUserId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("SUSPENDED"));
    }

    @Test
    void getUserSubscription_acrossBoundary_returnsSubscriptionInfo() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/" + targetUserId + "/subscription")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(targetUserId.toString()))
                .andExpect(jsonPath("$.plan").exists())
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void getUserUsage_acrossBoundary_returnsUsageInfo() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/" + targetUserId + "/usage")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(targetUserId.toString()))
                .andExpect(jsonPath("$.totalTokensUsed").exists())
                .andExpect(jsonPath("$.messageCount").exists());
    }
}
