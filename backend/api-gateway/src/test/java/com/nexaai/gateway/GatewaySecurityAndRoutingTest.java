package com.nexaai.gateway;

import com.nexaai.gateway.security.JwtVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "rate-limit.enabled=false"
})
@AutoConfigureWebTestClient
public class GatewaySecurityAndRoutingTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtVerifier jwtVerifier;

    private TestJwtHelper testJwtHelper;

    @BeforeEach
    void setUp() {
        testJwtHelper = new TestJwtHelper();
        jwtVerifier.setPublicKey(testJwtHelper.getPublicKey());
    }

    @Test
    void healthzEndpoint_returnsOk() {
        webTestClient.get().uri("/healthz")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void gatewayInfoEndpoint_returnsServiceMetadata() {
        webTestClient.get().uri("/internal/v1/gateway/info")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("api-gateway")
                .jsonPath("$.version").isEqualTo("0.1.0")
                .jsonPath("$.port").isEqualTo(8080)
                .jsonPath("$.database").isEqualTo(null)
                .jsonPath("$.listensKafka").isEqualTo(false);
    }

    @Test
    void correlationId_generatedWhenMissing() {
        webTestClient.get().uri("/healthz")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value("X-Correlation-Id", header -> {
                    assertThat(header).isNotBlank();
                });
    }

    @Test
    void correlationId_preservedWhenProvided() {
        String testCorrelationId = "test-corr-12345";
        webTestClient.get().uri("/healthz")
                .header("X-Correlation-Id", testCorrelationId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Correlation-Id", testCorrelationId);
    }

    @Test
    void blockInternalPaths_blocksInternalEndpoint() {
        webTestClient.get().uri("/internal/v1/auth/info")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("GATEWAY_FORBIDDEN")
                .jsonPath("$.error.message").isEqualTo("Access to internal or administrative endpoints is denied.");
    }

    @Test
    void blockInternalPaths_blocksActuatorBeans() {
        webTestClient.get().uri("/actuator/beans")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("GATEWAY_FORBIDDEN");
    }

    @Test
    void actuatorHealth_isPermitted() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void protectedRoute_withoutToken_returns401Unauthorized() {
        webTestClient.get().uri("/api/v1/users/me")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("GATEWAY_UNAUTHORIZED");
    }

    @Test
    void protectedRoute_withSuspendedAccountToken_returns403Forbidden() {
        String token = testJwtHelper.generateToken(
                UUID.randomUUID(), "suspended@example.com", List.of("USER"), "SUSPENDED", 600000);

        webTestClient.get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("GATEWAY_ACCOUNT_SUSPENDED");
    }

    @Test
    void protectedRoute_withExpiredToken_returns401InvalidToken() {
        String expiredToken = testJwtHelper.generateToken(
                UUID.randomUUID(), "expired@example.com", List.of("USER"), "ACTIVE", -60000);

        webTestClient.get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("GATEWAY_INVALID_TOKEN");
    }

    @Test
    void protectedRoute_withTamperedToken_returns401() {
        webTestClient.get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("GATEWAY_INVALID_TOKEN");
    }

    @Test
    void protectedRoute_withAlgNone_returns401() {
        // Unsigned JWT with alg: none
        String unsignedJwt = "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.";
        webTestClient.get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unsignedJwt)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error.code").isEqualTo("GATEWAY_INVALID_TOKEN");
    }
}
