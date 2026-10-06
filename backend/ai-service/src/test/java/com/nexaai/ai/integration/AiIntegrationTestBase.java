package com.nexaai.ai.integration;

import com.nexaai.ai.provider.ProviderHealth;
import com.nexaai.ai.support.ProviderStubs;
import com.nexaai.ai.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base for the HTTP-level tests.
 *
 * <p><strong>The real application context, not a sliced one.</strong> A {@code @WebMvcTest} would
 * stand in a controller and mock away the router, the provider client and the filter chain — which
 * is precisely the wiring under test. Authorization here is a property of the whole stack, and a
 * slice that replaces it proves nothing about the deployment.
 *
 * <p><strong>The shipped configuration is used.</strong> Only {@code application-test.yml} is
 * layered on top, so a regression in {@code application.yml} fails these tests instead of being
 * masked by a test-only copy that ships to nobody.
 *
 * <p>The JWT public key is injected dynamically because the test key pair is generated at
 * runtime. It could not live in a properties file, and hard-coding one would mean shipping a key
 * in source.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ProviderStubs.class)
abstract class AiIntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;

    /**
     * Injected only to clear it.
     *
     * <p>{@code ProviderHealth} is a process-wide singleton, so a test that makes a provider
     * fail three times leaves it marked unhealthy for the next thirty seconds. Without this, a
     * stream-failure test silently turns every later test in the JVM into a 503 — and the
     * resulting failures look like routing bugs rather than test pollution. Each test starts
     * healthy, exactly as each real deployment does.
     */
    @Autowired
    protected ProviderHealth providerHealth;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("nexa.ai.jwt.public-key", TestTokens::publicKeyPem);
    }

    @BeforeEach
    void resetSharedState() {
        ProviderStubs.reset();
        providerHealth.reset();
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}