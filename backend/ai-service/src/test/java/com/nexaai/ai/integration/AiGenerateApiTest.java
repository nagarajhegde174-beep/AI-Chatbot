package com.nexaai.ai.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexaai.ai.support.ProviderStubs;
import com.nexaai.ai.support.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * The completion endpoint, routing and model selection, over HTTP.
 *
 * <p>Routing, selection and fallback are already covered directly against the router. What only
 * an HTTP test can show is that the wiring survives the trip: that the configured provider is the
 * one consulted, that the answer comes back in the shape Chat Service parses, and that an
 * unconfigured provider is reported rather than silently ignored.
 */
class AiGenerateApiTest extends AiIntegrationTestBase {

    private String generate(String body) throws Exception {
        return mockMvc.perform(post("/internal/v1/ai/generate")
                        .header("Authorization", bearer(TestTokens.userToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    // ==================================================================
    // Completion
    // ==================================================================

    @Nested
    @DisplayName("completion")
    class Completion {

        @Test
        @DisplayName("returns the provider's content, and names the model that answered")
        void returnsContent() throws Exception {
            ProviderStubs.chat().returning("a considered answer");

            String body = generate("{\"message\":\"a question\"}");

            assertThat(body).contains("\"content\":\"a considered answer\"")
                    .contains("\"model\":\"nexa-default\"")
                    .contains("\"provider\":\"OPENAI\"")
                    .contains("\"streamed\":false")
                    .contains("\"fallbackUsed\":false");
        }

        @Test
        @DisplayName("token counts come from the provider and are never invented")
        void carriesProviderUsage() throws Exception {
            String body = generate("{\"message\":\"a question\"}");

            assertThat(body).contains("\"inputTokens\":11").contains("\"outputTokens\":7");
        }

        @Test
        @DisplayName("a named model is routed to its own provider")
        void routesNamedModel() throws Exception {
            // nexa-gemini-flash is on GEMINI, which the test profile does not configure. So a
            // request for it must fail rather than quietly being answered by the only provider
            // that happens to be wired.
            mockMvc.perform(post("/internal/v1/ai/generate")
                            .header("Authorization", bearer(TestTokens.userToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"model\":\"nexa-gemini-flash\",\"message\":\"hi\"}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("NO_PROVIDER_AVAILABLE"));
        }

        @Test
        @DisplayName("an absent model name uses the configured default")
        void absentModelUsesDefault() throws Exception {
            String body = generate("{\"message\":\"hi\"}");

            assertThat(body).contains("\"model\":\"nexa-default\"");
        }

        @Test
        @DisplayName("the default model is reported by its own endpoint")
        void defaultModelEndpoint() throws Exception {
            mockMvc.perform(get("/internal/v1/ai/default-model")
                            .header("Authorization", bearer(TestTokens.userToken())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.model").value("nexa-default"));
        }
    }

    // ==================================================================
    // Limits
    // ==================================================================

    @Nested
    @DisplayName("limits")
    class Limits {

        @Test
        @DisplayName("a temperature the model does not support is refused, not ignored")
        void refusesUnsupportedTemperature() throws Exception {
            // nexa-fixed declares supports-temperature: false. A provider that ignores an
            // unsupported parameter succeeds and looks fine, so the request would appear to
            // have honoured a setting that did nothing.
            mockMvc.perform(post("/internal/v1/ai/generate")
                            .header("Authorization", bearer(TestTokens.userToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"model\":\"nexa-fixed\",\"message\":\"hi\","
                                    + "\"temperature\":0.5}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value(org.hamcrest.Matchers.containsString(
                                    "does not support a temperature")));
        }

        @Test
        @DisplayName("maxOutputTokens above the model's limit is refused")
        void refusesTooManyOutputTokens() throws Exception {
            mockMvc.perform(post("/internal/v1/ai/generate")
                            .header("Authorization", bearer(TestTokens.userToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"hi\",\"maxOutputTokens\":99999}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("an over-long message is refused before the provider is called")
        void refusesOverLongMessage() throws Exception {
            ProviderStubs.reset();

            mockMvc.perform(post("/internal/v1/ai/generate")
                            .header("Authorization", bearer(TestTokens.userToken()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"message\":\"" + "x".repeat(400_000) + "\"}"))
                    .andExpect(status().isBadRequest());

            // Before, not after: a request that is certainly too long should never be billed.
            assertThat(ProviderStubs.chat().callCount()).isZero();
        }
    }

    // ==================================================================
    // Catalog, availability and health
    // ==================================================================

    @Nested
    @DisplayName("catalog and observability")
    class Catalog {

        @Test
        @DisplayName("every model is listed, available or not, with a reason when not")
        void modelsEndpointExplainsAvailability() throws Exception {
            String body = mockMvc.perform(get("/internal/v1/ai/models")
                            .header("Authorization", bearer(TestTokens.userToken())))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            // Listing only what works would hide a misconfigured deployment from the very
            // person trying to fix it.
            assertThat(body).contains("nexa-default")
                    .contains("nexa-gemini-flash")
                    .contains("\"available\":true")
                    .contains("\"available\":false")
                    .contains("unavailableReason");
        }

        @Test
        @DisplayName("an unavailable model names the provider and why")
        void unavailableReasonIsSpecific() throws Exception {
            String body = mockMvc.perform(get("/internal/v1/ai/models")
                            .header("Authorization", bearer(TestTokens.userToken())))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            // "Unavailable" alone tells an operator nothing. Naming the provider and the reason
            // tells them exactly which configuration line to change.
            assertThat(body).contains("Provider Google Gemini is disabled for this deployment.");
        }

        @Test
        @DisplayName("health reports every provider, including ones never called")
        void healthEndpoint() throws Exception {
            mockMvc.perform(get("/internal/v1/ai/health")
                            .header("Authorization", bearer(TestTokens.userToken())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.providers.OPENAI").exists())
                    .andExpect(jsonPath("$.providers.GEMINI").exists())
                    .andExpect(jsonPath("$.providers.GROQ").exists());
        }

        @Test
        @DisplayName("a failed call is counted against its provider")
        void healthReflectsFailures() throws Exception {
            ProviderStubs.chat().failingWith(
                    new IllegalStateException("429 rate limit exceeded"));

            mockMvc.perform(post("/internal/v1/ai/generate")
                    .header("Authorization", bearer(TestTokens.userToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"message\":\"hi\"}"));

            mockMvc.perform(get("/internal/v1/ai/health")
                            .header("Authorization", bearer(TestTokens.userToken())))
                    .andExpect(jsonPath("$.providers.OPENAI.failures")
                            .value(org.hamcrest.Matchers.greaterThan(0)))
                    .andExpect(jsonPath("$.providers.OPENAI.lastFailureKind")
                            .value(org.hamcrest.Matchers.anyOf(
                                    org.hamcrest.Matchers.is("THROTTLED"),
                                    org.hamcrest.Matchers.is("UNKNOWN"),
                                    org.hamcrest.Matchers.is("REJECTED"))));
        }

        @Test
        @DisplayName("usage reports totals and never any prompt text")
        void usageEndpoint() throws Exception {
            generate("{\"message\":\"a private question the user typed\"}");

            String body = mockMvc.perform(get("/internal/v1/ai/usage")
                            .header("Authorization", bearer(TestTokens.userToken())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totals.requests").value(1))
                    .andReturn().getResponse().getContentAsString();

            // Usage records get exported and retained. Carrying the prompt through them would
            // turn metering into a data-retention problem nobody asked for.
            assertThat(body).doesNotContain("private question");
            assertThat(body).contains("\"inputTokens\"");
        }

        @Test
        @DisplayName("a usage record is not attributed to any user, because this service has none")
        void usageCarriesNoUserIdentity() throws Exception {
            generate("{\"message\":\"hi\"}");

            String body = mockMvc.perform(get("/internal/v1/ai/usage")
                            .header("Authorization", bearer(TestTokens.userToken())))
                    .andReturn().getResponse().getContentAsString();

            // This service holds no user data at all. A user id in a usage record would be the
            // first place one appeared, in a place designed to be exported.
            assertThat(body).doesNotContain(TestTokens.userSubject());
        }
    }
}