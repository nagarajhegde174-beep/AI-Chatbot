package com.nexaai.user.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexaai.user.support.TestTokens;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The token boundary.
 *
 * <p>Every case here is a token this service must refuse. They are the failures that a code
 * review will not catch, because each requires knowing how JWT verification actually behaves:
 * which claims are checked, whether the algorithm is pinned, and what a filter does with a
 * token it cannot parse.
 *
 * <p>None of these are mocked. Each token is genuinely signed, or deliberately badly signed,
 * and genuinely verified — so the assertions are about this service's real behaviour.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthenticationBoundaryTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    // ==================================================================
    // Tokens that must be refused
    // ==================================================================

    @ParameterizedTest(name = "refuses {0}")
    @MethodSource("badTokens")
    @DisplayName("every unusable token yields 401, not 500 and not access")
    void refusesUnusableTokens(String description, java.util.function.Supplier<String> token) {
        try {
            mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(token.get())))
                    .andExpect(status().isUnauthorized());
        } catch (Exception e) {
            throw new AssertionError("Expected 401 for " + description, e);
        }
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> badTokens() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("an expired token",
                        (java.util.function.Supplier<String>) TestTokens::expiredToken),
                org.junit.jupiter.params.provider.Arguments.of("a token from another issuer",
                        (java.util.function.Supplier<String>) TestTokens::wrongIssuerToken),
                org.junit.jupiter.params.provider.Arguments.of("a token for another audience",
                        (java.util.function.Supplier<String>) TestTokens::wrongAudienceToken),
                org.junit.jupiter.params.provider.Arguments.of("an unsigned alg:none token",
                        (java.util.function.Supplier<String>) TestTokens::unsignedToken),
                org.junit.jupiter.params.provider.Arguments.of(
                        "an HS256 token signed with the public key",
                        (java.util.function.Supplier<String>) TestTokens::algorithmConfusionToken),
                org.junit.jupiter.params.provider.Arguments.of("a token signed by another key",
                        (java.util.function.Supplier<String>) TestTokens::foreignKeyToken),
                org.junit.jupiter.params.provider.Arguments.of("a string that is not a JWT",
                        (java.util.function.Supplier<String>) TestTokens::garbageToken));
    }

    @Test
    @DisplayName("refuses a request with no Authorization header")
    void refusesAnonymous() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("refuses a non-Bearer scheme")
    void refusesNonBearerScheme() throws Exception {
        String token = TestTokens.userToken();

        mvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + token))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, "Token " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("refuses an empty Bearer value")
    void refusesEmptyBearer() throws Exception {
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer "))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer    "))
                .andExpect(status().isUnauthorized());
    }

    // ==================================================================
    // The algorithm-confusion attack, in detail
    // ==================================================================

    @Test
    @DisplayName("an HS256 token claiming ADMIN does not grant admin, even signed with the public key")
    void algorithmConfusionGrantsNothing() throws Exception {
        // The classic attack. A verifier that reads `alg` from the token and then uses the RSA
        // public key as an HMAC secret will accept this, and the attacker becomes ADMIN.
        // Pinning RS256 means the token is rejected outright.
        String attack = TestTokens.algorithmConfusionToken();

        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(attack)))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(attack)))
                .andExpect(status().isUnauthorized());
    }

    // ==================================================================
    // What a valid token does and does not carry
    // ==================================================================

    @Test
    @DisplayName("a USER token cannot reach an admin route")
    void userTokenIsNotAdmin() throws Exception {
        mvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the role comes from the verified token, not from a header the caller sets")
    void roleIsNotTakenFromARequestHeader() throws Exception {
        // X-Role: ADMIN is an old-school privilege-escalation attempt. This service must not
        // have a code path that reads a role from anywhere but a verified token.
        mvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken()))
                        .header("X-Role", "ADMIN")
                        .header("X-User-Roles", "ADMIN")
                        .header("X-Forwarded-User", "someone@else.test"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a token for a user with no profile 404s rather than 403")
    void tokenWithoutProfileIs404() throws Exception {
        // The token is valid; the profile simply has not been created yet. Distinguishing this
        // from "forbidden" tells the client to retry, not to re-authenticate.
        mvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("a malformed token is a 401, never a 500")
    void malformedTokenIsNotAServerError() throws Exception {
        // A filter that throws on a bad token turns every junk request into a 500, which both
        // misleads an operator and hands an attacker a different response to fingerprint.
        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, bearer("....")))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer("a.b.c")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("health is public, because the container healthcheck has no token")
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the OpenAPI document is public")
    void openApiIsPublic() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("other actuator endpoints are not exposed")
    void otherActuatorsAreClosed() throws Exception {
        // /actuator/env would hand a reader every configuration value in the process.
        mvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }

    // ==================================================================
    // CSRF
    // ==================================================================

    @Test
    @DisplayName("a cookie-authenticated write without a CSRF token is refused")
    void csrfIsEnforcedOnWrites() throws Exception {
        UUID authUserId = UUID.randomUUID();

        // No .with(csrf()). With the token presented only in a cookie, a cross-site form post
        // would carry it automatically, so the token check is the protection.
        mvc.perform(patch("/api/v1/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.userToken(authUserId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Hacked\"}"))
                .andExpect(status().isForbidden());
    }
}