package com.nexaai.analytics;

import com.nexaai.analytics.domain.AiRequestMetric;
import com.nexaai.analytics.domain.DocumentReadModel;
import com.nexaai.analytics.domain.UserActivityReadModel;
import com.nexaai.analytics.repository.AiRequestMetricRepository;
import com.nexaai.analytics.repository.DocumentReadModelRepository;
import com.nexaai.analytics.repository.UserActivityReadModelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"nexa.analytics.kafka.enabled=false", "spring.kafka.listener.auto-startup=false"})
class AdminSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AiRequestMetricRepository metricRepository;

    @Autowired
    private UserActivityReadModelRepository userRepository;

    @Autowired
    private DocumentReadModelRepository documentRepository;

    private final UUID adminId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID standardUserId = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        metricRepository.deleteAll();
        documentRepository.deleteAll();
        userRepository.deleteAll();

        // Seed real persisted test records
        userRepository.save(new UserActivityReadModel(
                standardUserId, "alice@example.com", "ACTIVE", "FREE", 100000L, 1250L, 5L,
                Instant.now(), "127.0.0.1", Instant.now().minusSeconds(86400)));

        metricRepository.save(new AiRequestMetric(
                UUID.randomUUID(), standardUserId, UUID.randomUUID(), UUID.randomUUID(),
                "OPENAI", "gpt-4o-mini", 800L, 200L, 1000L, 450L,
                BigDecimal.valueOf(0.000240), "SUCCESS", null, Instant.now()));

        documentRepository.save(new DocumentReadModel(
                UUID.randomUUID(), standardUserId, "project-spec.pdf", 204800L, "INDEXED",
                4, 4, null, Instant.now(), Instant.now()));
    }

    @Test
    @DisplayName("USER cannot access /api/v1/admin/overview (403 Forbidden)")
    void userCannotAccessAdminOverview() throws Exception {
        mockMvc.perform(get("/api/v1/admin/overview")
                        .header("X-User-Id", standardUserId.toString())
                        .header("X-User-Roles", "USER")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("USER cannot access /api/v1/admin/users (403 Forbidden)")
    void userCannotAccessAdminUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Id", standardUserId.toString())
                        .header("X-User-Roles", "USER")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("USER cannot access /api/v1/admin/models (403 Forbidden)")
    void userCannotAccessAdminModels() throws Exception {
        mockMvc.perform(get("/api/v1/admin/models")
                        .header("X-User-Id", standardUserId.toString())
                        .header("X-User-Roles", "USER")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("USER cannot access /api/v1/admin/rag/monitoring (403 Forbidden)")
    void userCannotAccessAdminRagMonitoring() throws Exception {
        mockMvc.perform(get("/api/v1/admin/rag/monitoring")
                        .header("X-User-Id", standardUserId.toString())
                        .header("X-User-Roles", "USER")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("USER cannot access /api/v1/admin/analytics (403 Forbidden)")
    void userCannotAccessAdminAnalytics() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics")
                        .header("X-User-Id", standardUserId.toString())
                        .header("X-User-Roles", "USER")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Unauthenticated request to admin API returns 401 Unauthorized")
    void unauthenticatedCannotAccessAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/overview")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ADMIN can access /api/v1/admin/overview with real persisted statistics")
    void adminCanAccessOverview() throws Exception {
        mockMvc.perform(get("/api/v1/admin/overview")
                        .header("X-User-Id", adminId.toString())
                        .header("X-User-Roles", "ADMIN")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(1))
                .andExpect(jsonPath("$.totalAiRequests").value(1))
                .andExpect(jsonPath("$.totalTokens").value(1000))
                .andExpect(jsonPath("$.successfulRequests").value(1))
                .andExpect(jsonPath("$.failedRequests").value(0))
                .andExpect(jsonPath("$.subscriptionStatistics.FREE").value(1));
    }

    @Test
    @DisplayName("ADMIN can access /api/v1/admin/users and inspect user management list")
    void adminCanAccessUserManagement() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-User-Id", adminId.toString())
                        .header("X-User-Roles", "ADMIN")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("alice@example.com"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].subscriptionTier").value("FREE"));
    }

    @Test
    @DisplayName("ADMIN can access /api/v1/admin/conversations/monitoring")
    void adminCanAccessConversationMonitoring() throws Exception {
        mockMvc.perform(get("/api/v1/admin/conversations/monitoring")
                        .header("X-User-Id", adminId.toString())
                        .header("X-User-Roles", "ADMIN")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQueries").isNumber());
    }

    @Test
    @DisplayName("ADMIN can access /api/v1/admin/models and toggle model status")
    void adminCanManageModels() throws Exception {
        mockMvc.perform(get("/api/v1/admin/models")
                        .header("X-User-Id", adminId.toString())
                        .header("X-User-Roles", "ADMIN")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].modelName").exists());

        mockMvc.perform(post("/api/v1/admin/models/gpt-4o-mini/toggle")
                        .header("X-User-Id", adminId.toString())
                        .header("X-User-Roles", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    @DisplayName("ADMIN can access /api/v1/admin/rag/monitoring")
    void adminCanAccessRagMonitoring() throws Exception {
        mockMvc.perform(get("/api/v1/admin/rag/monitoring")
                        .header("X-User-Id", adminId.toString())
                        .header("X-User-Roles", "ADMIN")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalDocuments").value(1))
                .andExpect(jsonPath("$.documents[0].title").value("project-spec.pdf"));
    }

    @Test
    @DisplayName("ADMIN can access /api/v1/admin/analytics funnel tracking (request -> provider -> model -> tokens -> latency -> cost)")
    void adminCanAccessAnalyticsFunnel() throws Exception {
        mockMvc.perform(get("/api/v1/admin/analytics")
                        .header("X-User-Id", adminId.toString())
                        .header("X-User-Roles", "ADMIN")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequests").value(1))
                .andExpect(jsonPath("$.totalTokens").value(1000))
                .andExpect(jsonPath("$.providerUsage.OPENAI").value(1))
                .andExpect(jsonPath("$.modelUsage.gpt-4o-mini").value(1))
                .andExpect(jsonPath("$.totalEstimatedAiCostUsd").isNumber());
    }

    @Test
    @DisplayName("Standard USER can access own real persisted usage (/api/v1/usage/summary)")
    void userCanAccessOwnUsageSummary() throws Exception {
        mockMvc.perform(get("/api/v1/usage/summary")
                        .header("X-User-Id", standardUserId.toString())
                        .header("X-User-Roles", "USER")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiRequests").value(1))
                .andExpect(jsonPath("$.tokenUsage.totalTokens").value(1000))
                .andExpect(jsonPath("$.documentCount").value(1))
                .andExpect(jsonPath("$.storageUsageBytes").value(204800))
                .andExpect(jsonPath("$.remainingQuota").value(99000))
                .andExpect(jsonPath("$.subscriptionStatus.tier").value("FREE"));
    }
}
