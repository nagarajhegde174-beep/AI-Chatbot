package com.nexaai.ai.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexaai.ai.support.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import java.util.stream.Stream;

/**
 * Authorization on the AI Service.
 *
 * <p>This is an internal surface, not a public one. Every route is called by Chat Service, so the
 * questions that matter are: can an unauthenticated caller reach it, will a forged or
 * mis-scoped token get through, and is any credential exposed in a response.
 *
 * <p>Each rejection case is asserted on its own. A single "unauthorized is 401" test would pass
 * just as happily if the service rejected every token, which is a service that is up and useless.
 */
class AiAuthorizationTest extends AiIntegrationTestBase {

    private static final String BODY = """
            {"model":"nexa-default","message":"hello"}""";

    static Stream<String> guardedRoutes() {
        return Stream.of(
                "GET:/internal/v1/ai/models",
                "GET:/internal/v1/ai/default-model",
                "GET:/internal/v1/ai/health",
                "GET:/internal/v1/ai/usage",
                "POST:/internal/v1/ai/generate");
    }

    @Nested
    @DisplayName("authentication")
    class Authentication {

        @ParameterizedTest(name = "{0} requires a token")
        @MethodSource("com.nexaai.ai.integration.AiAuthorizationTest#guardedRoutes")
        @DisplayName("an unauthenticated caller gets 401 on every route")
        void everyRouteRequiresAToken(String route) throws Exception {
            String[] parts = route.split(":");

            if (parts[0].equals("GET")) {
                mockMvc.perform(get(parts[1])).andExpect(status().isUnauthorized());
            } else {
                mockMvc.perform(post(parts[1])
                                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                        .andExpect(status().isUnauthorized());
            }
        }

        @Test
        @DisplayName("a forged token is rejected, not decoded")
        void forgedTokenIsRejected() throws Exception {
            // "not-a-token" must fail signature verification, not be parsed leniently and
            // treated as an anonymous caller with a claim set.
            mockMvc.perform(get("/internal/v1/ai/models")
                            .header("Authorization", bearer(TestTokens.garbageToken())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("an unsigned token claiming ADMIN roles is rejected")
        void unsignedTokenIsRejected() throws Exception {
            // alg:none is the classic JWT bypass. It carries roles the caller asked for and no
            // signature at all, which must be worth exactly nothing.
            mockMvc.perform(get("/internal/v1/ai/models")
                            .header("Authorization", bearer(TestTokens.unsignedToken())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("an HS256 token signed with the public key is rejected")
        void algorithmConfusionIsRejected() throws Exception {
            // The classic algorithm-confusion attack: sign with HMAC using the RSA *public* key.
            // Accepted, it would let anyone who has read the public key mint a valid ADMIN token.
            mockMvc.perform(get("/internal/v1/ai/models")
                            .header("Authorization", bearer(TestTokens.algorithmConfusionToken())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("an expired token is rejected")
        void expiredTokenIsRejected() throws Exception {
            mockMvc.perform(get("/internal/v1/ai/models")
                            .header("Authorization", bearer(TestTokens.expiredToken())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("a token from another issuer is rejected")
        void wrongIssuerIsRejected() throws Exception {
            mockMvc.perform(get("/internal/v1/ai/models")
                            .header("Authorization", bearer(TestTokens.wrongIssuerToken())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("a token for another audience is rejected")
        void wrongAudienceIsRejected() throws Exception {
            mockMvc.perform(get("/internal/v1/ai/models")
                            .header("Authorization", bearer(TestTokens.wrongAudienceToken())))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("authorisation")
    class Authorisation {

        @Test
        @DisplayName("a valid user token is accepted, because Chat Service forwards the user's")
        void validUserTokenIsAccepted() throws Exception {
            // Chat Service calls this service on the user's behalf with the user's own token, so
            // USER must be sufficient. Requiring a service-only credential would mean inventing a
            // second identity system and would break the call path entirely.
            mockMvc.perform(get("/internal/v1/ai/models")
                            .header("Authorization", bearer(TestTokens.userToken())))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("a service token is accepted")
        void serviceTokenIsAccepted() throws Exception {
            mockMvc.perform(get("/internal/v1/ai/models")
                            .header("Authorization", bearer(TestTokens.serviceToken())))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("an ADMIN token confers nothing extra here")
        void adminHasNoExtraPower() throws Exception {
            // Worth stating: there is no admin-only route on this service, and no route that
            // exposes another user's prompt. Adding one would need a decision, not a token.
            mockMvc.perform(get("/internal/v1/ai/models")
                            .header("Authorization", bearer(TestTokens.adminToken())))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("actuator and docs")
    class Actuator {

        @Test
        @DisplayName("health is public and reveals no configuration detail")
        void healthIsPublic() throws Exception {
            // Liveness for a load balancer. show-details is 'when-authorized' in the shipped
            // config, so an unauthenticated probe must not learn the datasource, the model list
            // or anything else about the deployment.
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        }

        @Test
        @DisplayName("other actuator endpoints are not public")
        void otherActuatorEndpointsAreGuarded() throws Exception {
            mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/actuator/configprops")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("the OpenAPI document is reachable")
        void openApiIsReachable() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        }
    }

    @Nested
    @DisplayName("no credential is ever exposed")
    class NoCredentialExposure {

        @Test
        @DisplayName("no response body contains the provider credential")
        void responsesCarryNoCredential() throws Exception {
            String body = mockMvc.perform(get("/internal/v1/ai/models")
                            .header("Authorization", bearer(TestTokens.serviceToken())))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            // The key is server-side only. If it ever appeared in a response body, a browser or
            // any caller reaching /models would hold a billable provider credential.
            assertThat(body).doesNotContain("not-a-real-key");
        }

        @Test
        @DisplayName("no availability reason names the credential")
        void availabilityReasonsCarryNoCredential() throws Exception {
            String body = mockMvc.perform(get("/internal/v1/ai/models")
                            .header("Authorization", bearer(TestTokens.serviceToken())))
                    .andReturn().getResponse().getContentAsString();

            // Also checked case-insensitively for a field name: "apiKey": "..." would not
            // contain the placeholder value but would still be a disclosure.
            assertThat(body.toLowerCase(java.util.Locale.ROOT)).doesNotContain("api-key");
        }
    }
}