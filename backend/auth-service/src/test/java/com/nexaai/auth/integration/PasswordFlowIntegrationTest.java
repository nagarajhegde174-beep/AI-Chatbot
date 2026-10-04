package com.nexaai.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexaai.auth.domain.AccountStatus;
import com.nexaai.auth.repository.AuthUserRepository;
import com.nexaai.auth.repository.OneTimeTokenRepository;
import com.nexaai.auth.repository.RefreshTokenRepository;
import com.nexaai.auth.repository.RevokedTokenRepository;
import com.nexaai.auth.repository.AuthEventRepository;
import com.nexaai.auth.repository.OutboxEventRepository;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Email verification, password reset and password change.
 *
 * <p>Separate from {@link AuthFlowIntegrationTest} because these flows share nothing but the
 * database, and mixing them made one file the place every failure landed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordFlowIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery-staple";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthUserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private OneTimeTokenRepository oneTimeTokenRepository;

    @Autowired
    private AuthEventRepository authEventRepository;

    @Autowired
    private RevokedTokenRepository revokedTokenRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void resetState() {
        assertThat(databaseAvailable()).isTrue();
        TestEmailLinkCapture.clear();
        refreshTokenRepository.deleteAllInBatch();
        oneTimeTokenRepository.deleteAllInBatch();
        authEventRepository.deleteAllInBatch();
        revokedTokenRepository.deleteAllInBatch();
        outboxEventRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    // ==================================================================
    // Email verification
    // ==================================================================

    @Test
    @DisplayName("verifying activates the account and lets it sign in")
    void verificationEnablesSignIn() throws Exception {
        registerAndVerify("ada@example.com");

        var user = userRepository.findByEmail("ada@example.com").orElseThrow();
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);

        mockMvc.perform(login("ada@example.com", PASSWORD)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a verification token is single use")
    void verificationTokenIsSingleUse() throws Exception {
        register("ada@example.com");
        String token = tokenFrom(TestEmailLinkCapture.lastVerificationLink());

        mockMvc.perform(post("/api/v1/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token))))
                .andExpect(status().isOk());

        // Following the link twice is a 410 Gone, not a 200. The work is done and repeating it
        // would be meaningless.
        mockMvc.perform(post("/api/v1/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token))))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_ALREADY_CONSUMED"));
    }

    @Test
    @DisplayName("an unknown verification token is refused")
    void unknownVerificationTokenIsRefused() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", "not-a-real-token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"));
    }

    @Test
    @DisplayName("a verification token cannot be replayed as a password reset")
    void verificationTokenCannotResetPassword() throws Exception {
        // purpose separation: a leaked verification link must not be a reset link.
        register("ada@example.com");
        String token = tokenFrom(TestEmailLinkCapture.lastVerificationLink());

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "newPassword", "another-long-passphrase"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"));
    }

    @Test
    @DisplayName("a suspended account is not reactivated by clicking an old link")
    void oldLinkDoesNotResurrectSuspendedAccount() throws Exception {
        // A real bypass, caught by a unit test first: verifyEmail() used to ask
        // canTransitionTo(ACTIVE), which is true for SUSPENDED, so anyone holding a stale
        // verification link could lift an administrator's suspension.
        register("ada@example.com");
        String token = tokenFrom(TestEmailLinkCapture.lastVerificationLink());

        var user = userRepository.findByEmail("ada@example.com").orElseThrow();
        user.verifyEmail();
        user.transitionTo(AccountStatus.SUSPENDED);
        userRepository.saveAndFlush(user);

        mockMvc.perform(post("/api/v1/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token))))
                .andExpect(status().isOk());

        assertThat(userRepository.findByEmail("ada@example.com").orElseThrow().getStatus())
                .isEqualTo(AccountStatus.SUSPENDED);
    }

    // ==================================================================
    // Forgot password
    // ==================================================================

    @Test
    @DisplayName("forgot password gives the same answer for a known and an unknown email")
    void forgotPasswordDoesNotEnumerate() throws Exception {
        registerAndVerify("ada@example.com");

        String unknown = mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "nobody@example.com"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String known = mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "ada@example.com"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Identical, word for word. Reporting "no such account" would make this endpoint an
        // address-enumeration oracle.
        assertThat(JSON.readTree(unknown).path("error").path("message"))
                .isEqualTo(JSON.readTree(known).path("error").path("message"));
    }

    @Test
    @DisplayName("a reset token is stored hashed, never in plaintext")
    void resetTokenIsStoredHashed() throws Exception {
        registerAndVerify("ada@example.com");

        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "ada@example.com"))))
                .andExpect(status().isOk());

        String token = tokenFrom(TestEmailLinkCapture.lastResetLink());
        var stored = oneTimeTokenRepository.findLatestLive(
                userRepository.findByEmail("ada@example.com").orElseThrow().getId(),
                com.nexaai.auth.domain.OneTimeTokenPurpose.PASSWORD_RESET).orElseThrow();

        // Read access to this table must not be enough to reset anyone's password.
        assertThat(stored.getTokenHash()).isNotEqualTo(token).hasSize(64);
    }

    @Test
    @DisplayName("resetting the password lets the new one work and the old one fail")
    void resetChangesThePassword() throws Exception {
        registerAndVerify("ada@example.com");

        mockMvc.perform(post("/api/v1/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "ada@example.com")))).andExpect(status().isOk());

        String token = tokenFrom(TestEmailLinkCapture.lastResetLink());
        String newPassword = "a-brand-new-passphrase";

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "newPassword", newPassword))))
                .andExpect(status().isOk());

        mockMvc.perform(login("ada@example.com", newPassword)).andExpect(status().isOk());
        mockMvc.perform(login("ada@example.com", PASSWORD)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a reset token is single use")
    void resetTokenIsSingleUse() throws Exception {
        registerAndVerify("ada@example.com");
        mockMvc.perform(post("/api/v1/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "ada@example.com")))).andExpect(status().isOk());

        String token = tokenFrom(TestEmailLinkCapture.lastResetLink());

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "newPassword", "first-new-passphrase"))))
                .andExpect(status().isOk());

        // A second use fails. This is the property that limits the damage of an intercepted
        // reset link.
        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "newPassword", "second-new-passphrase"))))
                .andExpect(status().isGone());
    }

    @Test
    @DisplayName("a reset revokes every existing session")
    void resetRevokesAllSessions() throws Exception {
        registerAndVerify("ada@example.com");

        var beforeReset = mockMvc.perform(login("ada@example.com", PASSWORD)).andReturn();
        String refreshToken = java.util.Arrays.stream(beforeReset.getResponse().getCookies())
                .filter(c -> c.getName().equals("nexa_refresh_token"))
                .map(jakarta.servlet.http.Cookie::getValue).findFirst().orElseThrow();

        mockMvc.perform(post("/api/v1/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "ada@example.com")))).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", tokenFrom(TestEmailLinkCapture.lastResetLink()),
                                "newPassword", "a-new-passphrase"))))
                .andExpect(status().isOk());

        // Password recovery is how someone responds to a compromise. Leaving the attacker's
        // sessions alive would defeat the point entirely.
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(
                        new jakarta.servlet.http.Cookie("nexa_refresh_token", refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("resetting clears a lockout, since the user proved control of the mailbox")
    void resetClearsLockout() throws Exception {
        registerAndVerify("ada@example.com");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(login("ada@example.com", "wrong"));
        }
        mockMvc.perform(login("ada@example.com", PASSWORD)).andExpect(status().isLocked());

        mockMvc.perform(post("/api/v1/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "ada@example.com")))).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", tokenFrom(TestEmailLinkCapture.lastResetLink()),
                                "newPassword", "a-new-passphrase"))))
                .andExpect(status().isOk());

        // Locking a legitimate owner out of the account they just recovered would be perverse.
        mockMvc.perform(login("ada@example.com", "a-new-passphrase")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a weak new password is rejected on reset")
    void resetRejectsWeakPassword() throws Exception {
        registerAndVerify("ada@example.com");
        mockMvc.perform(post("/api/v1/auth/password/forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "ada@example.com")))).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", tokenFrom(TestEmailLinkCapture.lastResetLink()),
                                "newPassword", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    // ==================================================================
    // Change password
    // ==================================================================

    @Test
    @DisplayName("changing a password requires the current one")
    void changeRequiresCurrentPassword() throws Exception {
        registerAndVerify("ada@example.com");
        var session = mockMvc.perform(login("ada@example.com", PASSWORD)).andReturn();
        var accessCookie = accessCookieOf(session);

        // Without the current password, a stolen access token would be enough to lock the owner
        // out permanently, turning a token theft into a denial of service.
        mockMvc.perform(post("/api/v1/auth/password/change")
                        .cookie(new jakarta.servlet.http.Cookie("nexa_access_token", accessCookie))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "wrong", "newPassword", "a-new-passphrase"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_WRONG_PASSWORD"));
    }

    @Test
    @DisplayName("changing a password revokes other sessions")
    void changeRevokesOtherSessions() throws Exception {
        registerAndVerify("ada@example.com");

        var session = mockMvc.perform(login("ada@example.com", PASSWORD)).andReturn();
        String accessCookie = accessCookieOf(session);
        String refreshCookie = java.util.Arrays.stream(session.getResponse().getCookies())
                .filter(c -> c.getName().equals("nexa_refresh_token"))
                .map(jakarta.servlet.http.Cookie::getValue).findFirst().orElseThrow();

        mockMvc.perform(post("/api/v1/auth/password/change")
                        .cookie(new jakarta.servlet.http.Cookie("nexa_access_token", accessCookie))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", PASSWORD, "newPassword", "a-new-passphrase"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(
                        new jakarta.servlet.http.Cookie("nexa_refresh_token", refreshCookie)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(login("ada@example.com", "a-new-passphrase")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("changing a password requires authentication")
    void changeRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password/change")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", PASSWORD, "newPassword", "a-new-passphrase"))))
                .andExpect(status().isUnauthorized());
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private String registerAndVerify(String email) throws Exception {
        register(email);
        mockMvc.perform(post("/api/v1/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", tokenFrom(TestEmailLinkCapture.lastVerificationLink())))))
                .andExpect(status().isOk());
        return email;
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD, "displayName", "Ada"))))
                .andExpect(status().isCreated());
    }

    private static String tokenFrom(String url) {
        assertThat(url).as("a link must have been produced").isNotNull();
        int index = url.indexOf("token=");
        assertThat(index).isGreaterThanOrEqualTo(0);
        return url.substring(index + "token=".length()).trim();
    }

    private static String accessCookieOf(org.springframework.test.web.servlet.MvcResult result) {
        return java.util.Arrays.stream(result.getResponse().getCookies())
                .filter(c -> c.getName().equals("nexa_access_token"))
                .map(jakarta.servlet.http.Cookie::getValue).findFirst().orElseThrow();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String email, String password) throws Exception {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", password)));
    }

    private static String json(Object value) throws Exception {
        return JSON.writeValueAsString(value);
    }
}