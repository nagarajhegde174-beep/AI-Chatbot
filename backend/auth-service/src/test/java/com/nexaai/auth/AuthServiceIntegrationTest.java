package com.nexaai.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexaai.auth.domain.entity.AuthUser;
import com.nexaai.auth.domain.entity.EmailVerificationToken;
import com.nexaai.auth.domain.entity.PasswordResetToken;
import com.nexaai.auth.domain.model.AccountStatus;
import com.nexaai.auth.domain.repository.AuthUserRepository;
import com.nexaai.auth.domain.repository.EmailVerificationTokenRepository;
import com.nexaai.auth.domain.repository.PasswordResetTokenRepository;
import com.nexaai.auth.domain.repository.RefreshTokenRepository;
import com.nexaai.auth.domain.repository.TokenDenylistRepository;
import com.nexaai.auth.dto.request.ChangePasswordRequest;
import com.nexaai.auth.dto.request.ForgotPasswordRequest;
import com.nexaai.auth.dto.request.GoogleOAuthRequest;
import com.nexaai.auth.dto.request.LoginRequest;
import com.nexaai.auth.dto.request.RefreshTokenRequest;
import com.nexaai.auth.dto.request.RegisterRequest;
import com.nexaai.auth.dto.request.ResetPasswordRequest;
import com.nexaai.auth.dto.request.VerifyEmailRequest;
import com.nexaai.auth.dto.response.AuthResponse;
import com.nexaai.auth.dto.response.RegisterResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthUserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private TokenDenylistRepository tokenDenylistRepository;

    @BeforeEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        emailVerificationTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        tokenDenylistRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void testCompleteAuthLifecycle() throws Exception {
        String email = "ada.lovelace@example.com";
        String initialPassword = "InitialPassword123!";
        String newPassword = "UpdatedPassword456!";

        // 1. REGISTRATION
        RegisterRequest registerReq = new RegisterRequest(email, initialPassword, "Ada Lovelace");
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
                .andReturn();

        RegisterResponse registerResponse = objectMapper.readValue(
                registerResult.getResponse().getContentAsString(),
                RegisterResponse.class
        );
        assertNotNull(registerResponse.userId());

        // Verify user in DB
        AuthUser userInDb = userRepository.findByEmail(email).orElseThrow();
        assertEquals(AccountStatus.PENDING_VERIFICATION, userInDb.getStatus());
        assertNotNull(userInDb.getPasswordHash());
        assertFalse(userInDb.getPasswordHash().contains(initialPassword)); // password hashed!

        // 2. LOGIN BEFORE VERIFICATION MUST BE REJECTED
        LoginRequest loginReq = new LoginRequest(email, initialPassword);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_ACCOUNT_NOT_VERIFIED"));

        // 3. EMAIL VERIFICATION
        EmailVerificationToken verificationToken = emailVerificationTokenRepository
                .findTopByUserOrderByCreatedAtDesc(userInDb)
                .orElseThrow();

        mockMvc.perform(post("/api/v1/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyEmailRequest(verificationToken.getToken()))))
                .andExpect(status().isOk());

        AuthUser verifiedUser = userRepository.findByEmail(email).orElseThrow();
        assertEquals(AccountStatus.ACTIVE, verifiedUser.getStatus());

        // 4. LOGIN AFTER VERIFICATION
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(),
                AuthResponse.class
        );
        String accessToken = authResponse.accessToken();
        String refreshToken = authResponse.refreshToken();

        // 5. ACCESS AUTHENTICATED ROUTE /api/v1/auth/me
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles[0]").value("USER"));

        // 6. TOKEN REFRESH
        RefreshTokenRequest refreshReq = new RefreshTokenRequest(refreshToken);
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andReturn();

        AuthResponse refreshedResponse = objectMapper.readValue(
                refreshResult.getResponse().getContentAsString(),
                AuthResponse.class
        );
        String newRefreshToken = refreshedResponse.refreshToken();
        String newAccessToken = refreshedResponse.accessToken();

        // 7. THEFT DETECTION: Present old revoked refresh token
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_REVOKED"));

        // New token family must now also be revoked due to theft detection
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(newRefreshToken))))
                .andExpect(status().isUnauthorized());

        // 8. CHANGE PASSWORD
        // Obtain fresh access token
        AuthResponse freshAuth = objectMapper.readValue(
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginReq)))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(),
                AuthResponse.class
        );

        ChangePasswordRequest changePasswordReq = new ChangePasswordRequest(initialPassword, newPassword);
        mockMvc.perform(post("/api/v1/auth/password/change")
                        .header("Authorization", "Bearer " + freshAuth.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changePasswordReq)))
                .andExpect(status().isOk());

        // Login with old password fails
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, initialPassword))))
                .andExpect(status().isUnauthorized());

        // Login with new password succeeds
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, newPassword))))
                .andExpect(status().isOk());

        // 9. FORGOT PASSWORD AND RESET PASSWORD
        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(email))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        // User reset token in DB
        PasswordResetToken resetToken = passwordResetTokenRepository.findAll().getFirst();
        assertNull(resetToken.getUsedAt());

        // 10. GOOGLE OAUTH ACCOUNT LINKING
        GoogleOAuthRequest googleOAuthReq = new GoogleOAuthRequest(
                null,
                "google-sub-12345",
                email, // matches existing account
                "Ada L.",
                true // verified
        );

        mockMvc.perform(post("/api/v1/auth/oauth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(googleOAuthReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());

        AuthUser linkedUser = userRepository.findByEmail(email).orElseThrow();
        assertEquals("google-sub-12345", linkedUser.getGoogleId());

        // 11. GOOGLE OAUTH CREATE NEW ACCOUNT
        String googleOnlyEmail = "new.google.user@example.com";
        GoogleOAuthRequest newGoogleReq = new GoogleOAuthRequest(
                null,
                "google-sub-99999",
                googleOnlyEmail,
                "New Google User",
                true
        );

        mockMvc.perform(post("/api/v1/auth/oauth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newGoogleReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.user.email").value(googleOnlyEmail));

        AuthUser createdGoogleUser = userRepository.findByEmail(googleOnlyEmail).orElseThrow();
        assertNull(createdGoogleUser.getPasswordHash()); // Never store Google passwords!
        assertEquals("google-sub-99999", createdGoogleUser.getGoogleId());
        assertEquals(AccountStatus.ACTIVE, createdGoogleUser.getStatus());
    }
}
