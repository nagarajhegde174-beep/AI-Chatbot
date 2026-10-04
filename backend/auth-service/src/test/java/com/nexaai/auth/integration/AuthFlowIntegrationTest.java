package com.nexaai.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf; import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexaai.auth.repository.AuthEventRepository; import com.nexaai.auth.repository.AuthUserRepository; import com.nexaai.auth.repository.OneTimeTokenRepository; import com.nexaai.auth.repository.OutboxEventRepository; import com.nexaai.auth.repository.RefreshTokenRepository; import com.nexaai.auth.repository.RevokedTokenRepository;
import com.nexaai.auth.service.AuthService;
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
 * End-to-end tests of the account flows against a real PostgreSQL and the real filter chain.
 *
 * <p>Each flow runs through HTTP, so the security configuration, the cookie handling, the
 * exception handler and the persistence mapping are all exercised together. Testing the
 * service class alone would miss every wiring mistake, which is the class of bug these tests
 * exist to catch.
 *
 * <p><strong>Deliberately not {@code @Transactional}.</strong> An earlier version wrapped each
 * test in one transaction, and that made every failure path fail for the wrong reason: a
 * rejected sign-in marks the transaction rollback-only, so the next call in the same test blew
 * up with {@code UnexpectedRollbackException} instead of the expected status. Letting each
 * request commit or roll back on its own is also what production does, so the test now
 * exercises the real commit boundaries rather than one artificial transaction around them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery-staple";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

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
        assertThat(databaseAvailable())
                .as("A real PostgreSQL must be available; set NEXA_TEST_PG_URL or run with Docker")
                .isTrue();
        TestEmailLinkCapture.clear();
        // Tests share one database now that they share nothing transactional. Clearing
        // explicitly is what keeps each test's assertions about its own data.
        refreshTokenRepository.deleteAllInBatch();
        oneTimeTokenRepository.deleteAllInBatch();
        authEventRepository.deleteAllInBatch();
        revokedTokenRepository.deleteAllInBatch();
        outboxEventRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    // ==================================================================
    // Registration
    // ==================================================================

    @Test
    @DisplayName("registration creates a pending account and issues no tokens")
    void registersAccount() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "ada@example.com",
                                "password", PASSWORD,
                                "displayName", "Ada"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isNotEmpty())
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
                // Returning no tokens is what keeps the verification path real.
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());

        var stored = userRepository.findByEmail("ada@example.com").orElseThrow();
        assertThat(stored.getPasswordHash()).isNotBlank();
        assertThat(stored.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(stored.isEmailVerified()).isFalse();
    }

    @Test
    @DisplayName("a duplicate email is a conflict, whatever its casing")
    void rejectsDuplicateEmail() throws Exception {
        register("ada@example.com", "Ada");

        // Normalising on write is what makes this a real uniqueness guarantee rather than a
        // case-sensitivity accident.
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "ADA@Example.com",
                                "password", PASSWORD,
                                "displayName", "Other"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AUTH_EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    @DisplayName("a short password is rejected with a field-level message")
    void rejectsShortPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "ada@example.com",
                                "password", "short",
                                "displayName", "Ada"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details.password").exists())
                // The details object must never echo the submitted value.
                .andExpect(jsonPath("$.error.details.password").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("short"))));
    }

    @Test
    @DisplayName("an invalid email is rejected")
    void rejectsInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "not-an-email",
                                "password", PASSWORD,
                                "displayName", "Ada"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    // ==================================================================
    // Sign-in
    // ==================================================================

    @Test
    @DisplayName("an unverified account cannot sign in")
    void unverifiedCannotSignIn() throws Exception {
        register("ada@example.com", "Ada");

        mockMvc.perform(login("ada@example.com", PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_ACCOUNT_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("a wrong password and an unknown email are indistinguishable")
    void noUserEnumeration() throws Exception {
        // The single most important assertion in this file. Any difference here is a reliable
        // oracle for which addresses are registered.
        String unknownBody = mockMvc.perform(login("nobody@example.com", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        register("ada@example.com", "Ada");
        verifyEmail("ada@example.com");

        String wrongPasswordBody = mockMvc.perform(login("ada@example.com", "the-wrong-password"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // Same code, same message. The correlation id and timestamp legitimately differ.
        assertThat(codeOf(unknownBody)).isEqualTo(codeOf(wrongPasswordBody));
        assertThat(messageOf(unknownBody)).isEqualTo(messageOf(wrongPasswordBody));
    }

    @Test
    @DisplayName("a correct password signs in and sets HTTP-only cookies")
    void signsInAndSetsCookies() throws Exception {
        registerAndVerify("ada@example.com", "Ada");

        var response = mockMvc.perform(login("ada@example.com", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value("ada@example.com"))
                .andReturn();

        var cookies = response.getResponse().getCookies();
        assertThat(cookies).isNotEmpty();

        var access = java.util.Arrays.stream(cookies)
                .filter(c -> c.getName().equals("nexa_access_token")).findFirst().orElseThrow();
        assertThat(access.isHttpOnly())
                .as("an HTTP-only cookie is what makes XSS a nuisance rather than a credential theft")
                .isTrue();
        assertThat(access.getAttribute("SameSite")).isEqualTo("Strict");

        var refresh = java.util.Arrays.stream(cookies)
                .filter(c -> c.getName().equals("nexa_refresh_token")).findFirst().orElseThrow();
        assertThat(refresh.isHttpOnly()).isTrue();
        // Scoped to the refresh endpoint, so it is not attached to every request.
        assertThat(refresh.getPath()).isEqualTo("/api/v1/auth/refresh");

        // No token in the body: it would be readable by any script on the page.
        assertThat(response.getResponse().getContentAsString()).doesNotContain("eyJ");
    }

    @Test
    @DisplayName("repeated failures lock the account")
    void locksAfterRepeatedFailures() throws Exception {
        registerAndVerify("ada@example.com", "Ada");

        // Failures below the threshold are ordinary rejections.
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(login("ada@example.com", "wrong")).andExpect(status().isUnauthorized());
        }

        // The fifth trips the threshold, so that attempt is itself reported as locked.
        mockMvc.perform(login("ada@example.com", "wrong"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error.code").value("AUTH_ACCOUNT_LOCKED"));

        // And the correct password is refused too, while the lock stands. This is the assertion
        // that matters: without the failure counter committing independently of the rejection,
        // the account would never lock at all.
        mockMvc.perform(login("ada@example.com", PASSWORD))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.error.code").value("AUTH_ACCOUNT_LOCKED"));
    }

    // ==================================================================
    // Refresh
    // ==================================================================

    @Test
    @DisplayName("refresh rotates both tokens")
    void refreshRotatesTokens() throws Exception {
        registerAndVerify("ada@example.com", "Ada");

        var first = mockMvc.perform(login("ada@example.com", PASSWORD))
                .andExpect(status().isOk()).andReturn();
        String firstRefresh = refreshCookieOf(first);

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(
                        new jakarta.servlet.http.Cookie("nexa_refresh_token", firstRefresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("an already-rotated refresh token revokes the whole family")
    void detectsTokenReuse() throws Exception {
        registerAndVerify("ada@example.com", "Ada");

        var first = mockMvc.perform(login("ada@example.com", PASSWORD)).andReturn();
        String original = refreshCookieOf(first);

        // Legitimate rotation.
        var second = mockMvc.perform(post("/api/v1/auth/refresh").cookie(
                        new jakarta.servlet.http.Cookie("nexa_refresh_token", original)))
                .andExpect(status().isOk()).andReturn();
        String rotated = refreshCookieOf(second);

        // Presenting the old token again means it was copied: the real holder always has the
        // newest. The whole family is revoked.
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(
                        new jakarta.servlet.http.Cookie("nexa_refresh_token", original)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_REUSE_DETECTED"));

        // And the legitimate session is now dead too, which is the intended blast radius.
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(
                        new jakarta.servlet.http.Cookie("nexa_refresh_token", rotated)))
                .andExpect(status().isUnauthorized());
    }

    // ==================================================================
    // Sign-out
    // ==================================================================

    @Test
    @DisplayName("sign-out clears the cookies and revokes the session")
    void logoutClearsCookies() throws Exception {
        registerAndVerify("ada@example.com", "Ada");
        var login = mockMvc.perform(login("ada@example.com", PASSWORD)).andReturn();

        var response = mockMvc.perform(post("/api/v1/auth/logout").with(csrf())
                        .cookie(new jakarta.servlet.http.Cookie("nexa_refresh_token",
                                refreshCookieOf(login)))
                        .cookie(new jakarta.servlet.http.Cookie("nexa_access_token",
                                accessCookieOf(login))))
                .andExpect(status().isNoContent())
                .andReturn();

        // Max-age 0 on both: the browser drops them.
        for (var cookie : response.getResponse().getCookies()) {
            assertThat(cookie.getMaxAge()).isZero();
        }
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private String register(String email, String displayName) throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email, "password", PASSWORD, "displayName", displayName))))
                .andExpect(status().isCreated())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString()).get("userId").asText();
    }

    private void registerAndVerify(String email, String displayName) throws Exception {
        register(email, displayName);
        verifyEmail(email);
    }

    /**
     * Reads the verification token the service logged, then verifies.
     *
     * <p>Phase 1 has no mail transport, so the local profile logs the link. Reaching for the
     * logged token keeps the test exercising the real token path rather than writing a token
     * into the database by hand.
     */
    private void verifyEmail(String email) {
        String link = TestEmailLinkCapture.lastVerificationLink();
        assertThat(link).as("a verification link must have been produced for " + email).isNotNull();
        String token = extractToken(link);
        assertThat(token).isNotBlank();

        try {
            mockMvc.perform(post("/api/v1/auth/email/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("token", token))))
                    .andExpect(status().isOk());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String extractToken(String url) {
        int index = url.indexOf("token=");
        return index < 0 ? "" : url.substring(index + "token=".length()).trim();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String email, String password) throws Exception {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", password)));
    }

    private static String refreshCookieOf(org.springframework.test.web.servlet.MvcResult result) {
        return java.util.Arrays.stream(result.getResponse().getCookies())
                .filter(c -> c.getName().equals("nexa_refresh_token"))
                .map(jakarta.servlet.http.Cookie::getValue)
                .findFirst().orElseThrow();
    }

    private static String accessCookieOf(org.springframework.test.web.servlet.MvcResult result) {
        return java.util.Arrays.stream(result.getResponse().getCookies())
                .filter(c -> c.getName().equals("nexa_access_token"))
                .map(jakarta.servlet.http.Cookie::getValue)
                .findFirst().orElseThrow();
    }

    private static String json(Object value) throws Exception {
        return JSON.writeValueAsString(value);
    }

    private static String codeOf(String body) throws Exception {
        return JSON.readTree(body).path("error").path("code").asText();
    }

    private static String messageOf(String body) throws Exception {
        return JSON.readTree(body).path("error").path("message").asText();
    }
}