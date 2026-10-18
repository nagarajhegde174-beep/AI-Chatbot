package com.nexaai.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexaai.user.domain.entity.UserPreferences;
import com.nexaai.user.domain.entity.UserProfile;
import com.nexaai.user.domain.model.AccountStatus;
import com.nexaai.user.domain.repository.UserProfileRepository;
import com.nexaai.user.dto.request.UpdatePreferencesRequest;
import com.nexaai.user.dto.request.UpdateProfileRequest;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private TestJwtHelper jwtHelper;

    private UUID userId;
    private String token;

    @BeforeEach
    void setUp() {
        userProfileRepository.deleteAll();

        userId = UUID.randomUUID();
        UserProfile user = new UserProfile(userId, "ada@example.com", "Ada Lovelace", AccountStatus.ACTIVE);
        UserPreferences prefs = new UserPreferences(user);
        prefs.setTheme("dark");
        prefs.setDefaultModel("gpt-4o");
        user.setPreferences(prefs);
        userProfileRepository.save(user);

        token = jwtHelper.generateToken(userId, "ada@example.com", List.of("USER"));
    }

    @Test
    void getOwnProfile_withValidToken_returnsProfileAndPreferences() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.displayName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.preferences.theme").value("dark"))
                .andExpect(jsonPath("$.preferences.defaultModel").value("gpt-4o"))
                .andExpect(jsonPath("$.preferences.ragEnabledByDefault").value(true))
                .andExpect(jsonPath("$.preferences.streamResponses").value(true));
    }

    @Test
    void getOwnProfile_unauthorized_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("USER_UNAUTHORIZED"));
    }

    @Test
    void updateOwnProfile_updatesDisplayNameAndAvatar() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("Ada Byron", "https://example.com/avatar.png");

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Ada Byron"))
                .andExpect(jsonPath("$.avatarUrl").value("https://example.com/avatar.png"));
    }

    @Test
    void updatePreferences_updatesPreferences() throws Exception {
        UpdatePreferencesRequest request = new UpdatePreferencesRequest("light", "claude-3-opus", false, true);

        mockMvc.perform(put("/api/v1/users/me/preferences")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferences.theme").value("light"))
                .andExpect(jsonPath("$.preferences.defaultModel").value("claude-3-opus"))
                .andExpect(jsonPath("$.preferences.ragEnabledByDefault").value(false))
                .andExpect(jsonPath("$.preferences.streamResponses").value(true));
    }

    @Test
    void getOwnStatus_returnsAccountStatus() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"));
    }

    @Test
    void deleteOwnAccount_deletesUser() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Account successfully deleted."));

        assertFalse(userProfileRepository.existsById(userId));
    }

    @Test
    void profileResponse_neverContainsPasswordOrHash() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist());
    }
}
