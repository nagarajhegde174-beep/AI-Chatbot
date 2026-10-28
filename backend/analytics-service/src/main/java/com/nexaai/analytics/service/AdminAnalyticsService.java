package com.nexaai.analytics.service;

import com.nexaai.analytics.domain.*;
import com.nexaai.analytics.dto.*;
import com.nexaai.analytics.repository.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class AdminAnalyticsService {

    private final AiRequestMetricRepository metricRepository;
    private final UserActivityReadModelRepository userActivityRepository;
    private final DocumentReadModelRepository documentRepository;
    private final ConversationQueryLogRepository queryLogRepository;
    private final ModelRegistryStateRepository modelRepository;

    public AdminAnalyticsService(AiRequestMetricRepository metricRepository,
                                 UserActivityReadModelRepository userActivityRepository,
                                 DocumentReadModelRepository documentRepository,
                                 ConversationQueryLogRepository queryLogRepository,
                                 ModelRegistryStateRepository modelRepository) {
        this.metricRepository = metricRepository;
        this.userActivityRepository = userActivityRepository;
        this.documentRepository = documentRepository;
        this.queryLogRepository = queryLogRepository;
        this.modelRepository = modelRepository;
    }

    @PostConstruct
    public void initDefaultModels() {
        if (modelRepository.count() == 0) {
            modelRepository.save(new ModelRegistryState("gpt-4o-mini", "OPENAI", true, true, 0, 0, 0, Instant.now()));
            modelRepository.save(new ModelRegistryState("gpt-4o", "OPENAI", true, false, 0, 0, 0, Instant.now()));
            modelRepository.save(new ModelRegistryState("claude-3-5-sonnet", "ANTHROPIC", true, false, 0, 0, 0, Instant.now()));
            modelRepository.save(new ModelRegistryState("text-embedding-3-small", "OPENAI", true, false, 0, 0, 0, Instant.now()));
        }
    }

    // 1. Overview
    @Transactional(readOnly = true)
    public AdminOverviewDto getOverview() {
        long totalUsers = userActivityRepository.count();
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        long activeUsers = userActivityRepository.countActiveUsersSince(sevenDaysAgo);

        long totalAiRequests = metricRepository.count();
        long successfulRequests = metricRepository.countByStatus("SUCCESS");
        long failedRequests = metricRepository.countErrors();

        Long totalTokens = metricRepository.sumTotalTokens();
        long tokens = totalTokens != null ? totalTokens : 0L;

        double errorRate = totalAiRequests > 0 ? (failedRequests * 100.0 / totalAiRequests) : 0.0;
        double roundedErrorRate = Math.round(errorRate * 100.0) / 100.0;

        Map<String, Long> subStats = new LinkedHashMap<>();
        subStats.put("FREE", userActivityRepository.countBySubscriptionTier("FREE"));
        subStats.put("PRO", userActivityRepository.countBySubscriptionTier("PRO"));
        subStats.put("ENTERPRISE", userActivityRepository.countBySubscriptionTier("ENTERPRISE"));

        List<AiRequestMetric> all = metricRepository.findAll();
        BigDecimal totalCost = all.stream()
                .map(AiRequestMetric::getEstimatedCostUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new AdminOverviewDto(
                totalUsers,
                activeUsers,
                totalAiRequests,
                successfulRequests,
                failedRequests,
                tokens,
                failedRequests,
                roundedErrorRate,
                subStats,
                totalCost
        );
    }

    // 2. User Management
    @Transactional(readOnly = true)
    public List<AdminUserManagementDtos.AdminUserSummaryDto> listUsers(String query, String status) {
        List<UserActivityReadModel> users;
        if (query != null && !query.isBlank()) {
            users = userActivityRepository.findByEmailContainingIgnoreCase(query.trim());
        } else if (status != null && !status.isBlank()) {
            users = userActivityRepository.findByStatus(status.trim().toUpperCase());
        } else {
            users = userActivityRepository.findAll();
        }

        return users.stream()
                .map(u -> new AdminUserManagementDtos.AdminUserSummaryDto(
                        u.getUserId(),
                        u.getEmail(),
                        u.getStatus(),
                        u.getSubscriptionTier(),
                        u.getTokenQuota(),
                        u.getTokensUsed(),
                        u.getTotalRequests(),
                        u.getLastLoginAt(),
                        u.getLastLoginIp(),
                        u.getCreatedAt()
                ))
                .toList();
    }

    public AdminUserManagementDtos.AdminUserSummaryDto updateUserStatus(UUID userId, String status) {
        UserActivityReadModel user = userActivityRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setStatus(status.toUpperCase());
        UserActivityReadModel saved = userActivityRepository.save(user);

        return new AdminUserManagementDtos.AdminUserSummaryDto(
                saved.getUserId(),
                saved.getEmail(),
                saved.getStatus(),
                saved.getSubscriptionTier(),
                saved.getTokenQuota(),
                saved.getTokensUsed(),
                saved.getTotalRequests(),
                saved.getLastLoginAt(),
                saved.getLastLoginIp(),
                saved.getCreatedAt()
        );
    }

    // 3. Conversation Monitoring (respecting privacy: counts, metadata, never message content)
    @Transactional(readOnly = true)
    public ConversationMonitoringDtos.ConversationMonitoringDto getConversationMonitoring() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant todayStart = today.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant tomorrowStart = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant yesterdayStart = today.minusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        long todayQueries = queryLogRepository.countBetween(todayStart, tomorrowStart);
        long yesterdayQueries = queryLogRepository.countBetween(yesterdayStart, todayStart);
        long olderQueries = queryLogRepository.countBefore(yesterdayStart);
        long totalQueries = queryLogRepository.count();

        // User-wise queries (last 30 days)
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        List<ConversationQueryLog> logs = queryLogRepository.findByCreatedAtAfter(thirtyDaysAgo);

        Map<UUID, List<ConversationQueryLog>> byUser = logs.stream()
                .collect(Collectors.groupingBy(ConversationQueryLog::getUserId));

        List<ConversationMonitoringDtos.UserWiseQueryActivityDto> userActivity = byUser.entrySet().stream()
                .map(e -> {
                    UUID uid = e.getKey();
                    List<ConversationQueryLog> userLogs = e.getValue();
                    long count = userLogs.size();
                    long tokSum = userLogs.stream().mapToLong(ConversationQueryLog::getTokens).sum();
                    double avgLat = userLogs.stream().mapToInt(ConversationQueryLog::getLatencyMs).average().orElse(0.0);
                    Instant lastActive = userLogs.stream()
                            .map(ConversationQueryLog::getCreatedAt)
                            .max(Instant::compareTo)
                            .orElse(Instant.now());
                    return new ConversationMonitoringDtos.UserWiseQueryActivityDto(uid, count, tokSum, (long) avgLat, lastActive);
                })
                .sorted(Comparator.comparing(ConversationMonitoringDtos.UserWiseQueryActivityDto::queryCount).reversed())
                .limit(50)
                .toList();

        return new ConversationMonitoringDtos.ConversationMonitoringDto(
                todayQueries,
                yesterdayQueries,
                olderQueries,
                totalQueries,
                userActivity
        );
    }

    // 4. Model Management
    @Transactional(readOnly = true)
    public List<ModelManagementDtos.ModelItemDto> listModels() {
        return modelRepository.findAll().stream()
                .map(m -> {
                    String healthStatus;
                    if (!m.isEnabled()) {
                        healthStatus = "DISABLED";
                    } else if (m.getTotalErrors() > 0 && m.getTotalRequests() > 0 &&
                            ((double) m.getTotalErrors() / m.getTotalRequests() > 0.15)) {
                        healthStatus = "DEGRADED";
                    } else {
                        healthStatus = "HEALTHY";
                    }
                    return new ModelManagementDtos.ModelItemDto(
                            m.getModelName(),
                            m.getProvider(),
                            m.isEnabled(),
                            m.isDefault(),
                            m.getTotalRequests(),
                            m.getTotalTokens(),
                            m.getTotalErrors(),
                            healthStatus,
                            m.getLastUsedAt()
                    );
                })
                .toList();
    }

    public ModelManagementDtos.ModelItemDto toggleModel(String modelName, boolean enabled) {
        ModelRegistryState model = modelRepository.findById(modelName)
                .orElseThrow(() -> new IllegalArgumentException("Model not found: " + modelName));
        model.setEnabled(enabled);
        ModelRegistryState saved = modelRepository.save(model);
        return new ModelManagementDtos.ModelItemDto(
                saved.getModelName(),
                saved.getProvider(),
                saved.isEnabled(),
                saved.isDefault(),
                saved.getTotalRequests(),
                saved.getTotalTokens(),
                saved.getTotalErrors(),
                saved.isEnabled() ? "HEALTHY" : "DISABLED",
                saved.getLastUsedAt()
        );
    }

    public void setDefaultModel(String modelName) {
        List<ModelRegistryState> all = modelRepository.findAll();
        for (ModelRegistryState m : all) {
            m.setDefault(m.getModelName().equalsIgnoreCase(modelName));
        }
        modelRepository.saveAll(all);
    }

    // 5. RAG Monitoring
    @Transactional(readOnly = true)
    public RagMonitoringDtos.RagMonitoringDto getRagMonitoring() {
        long totalDocs = documentRepository.count();
        long activeDocs = documentRepository.countByStatus("INDEXED");
        Long totalChunks = documentRepository.sumTotalChunks();
        Long totalEmbeddings = documentRepository.sumTotalEmbeddings();
        long failureCount = documentRepository.countByStatus("FAILED");

        RagMonitoringDtos.RagSummaryDto summary = new RagMonitoringDtos.RagSummaryDto(
                totalDocs,
                activeDocs,
                totalChunks != null ? totalChunks : 0L,
                totalEmbeddings != null ? totalEmbeddings : 0L,
                failureCount
        );

        List<RagMonitoringDtos.RagDocumentItemDto> docs = documentRepository.findAll().stream()
                .sorted(Comparator.comparing(DocumentReadModel::getUploadedAt).reversed())
                .limit(100)
                .map(d -> new RagMonitoringDtos.RagDocumentItemDto(
                        d.getDocumentId(),
                        d.getUserId(),
                        d.getTitle(),
                        d.getFileSizeBytes(),
                        d.getStatus(),
                        d.getChunkCount(),
                        d.getEmbeddingCount(),
                        d.getErrorMessage(),
                        d.getUploadedAt(),
                        d.getUpdatedAt()
                ))
                .toList();

        return new RagMonitoringDtos.RagMonitoringDto(summary, docs);
    }

    public void deleteDocumentReadModel(UUID documentId) {
        documentRepository.deleteById(documentId);
    }

    public DocumentReadModel markReprocessing(UUID documentId) {
        DocumentReadModel doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
        doc.setStatus("REPROCESSING");
        doc.setUpdatedAt(Instant.now());
        return documentRepository.save(doc);
    }

    // 6. Analytics Funnel: Request -> Provider -> Model -> Tokens -> Latency -> Estimated AI Cost
    @Transactional(readOnly = true)
    public AnalyticsFunnelDtos.AnalyticsFunnelDto getAnalyticsFunnel() {
        long totalRequests = metricRepository.count();
        long successfulRequests = metricRepository.countByStatus("SUCCESS");
        long failedRequests = metricRepository.countErrors();
        Long totalTokens = metricRepository.sumTotalTokens();
        long tokens = totalTokens != null ? totalTokens : 0L;

        double errorRate = totalRequests > 0 ? (failedRequests * 100.0 / totalRequests) : 0.0;
        Double avgLat = metricRepository.averageLatencyMs();
        double latency = avgLat != null ? Math.round(avgLat * 10.0) / 10.0 : 0.0;

        List<AiRequestMetric> all = metricRepository.findAll();
        BigDecimal totalCost = all.stream()
                .map(AiRequestMetric::getEstimatedCostUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Long> providerUsage = all.stream()
                .collect(Collectors.groupingBy(AiRequestMetric::getProvider, Collectors.counting()));

        Map<String, Long> modelUsage = all.stream()
                .collect(Collectors.groupingBy(AiRequestMetric::getModel, Collectors.counting()));

        List<AnalyticsFunnelDtos.AiRequestTraceDto> recent = all.stream()
                .sorted(Comparator.comparing(AiRequestMetric::getCreatedAt).reversed())
                .limit(50)
                .map(m -> new AnalyticsFunnelDtos.AiRequestTraceDto(
                        m.getId(),
                        m.getUserId(),
                        m.getProvider(),
                        m.getModel(),
                        m.getPromptTokens(),
                        m.getCompletionTokens(),
                        m.getTotalTokens(),
                        m.getLatencyMs(),
                        m.getEstimatedCostUsd(),
                        m.getStatus(),
                        m.getErrorCode(),
                        m.getCreatedAt()
                ))
                .toList();

        return new AnalyticsFunnelDtos.AnalyticsFunnelDto(
                totalRequests,
                successfulRequests,
                failedRequests,
                tokens,
                Math.round(errorRate * 100.0) / 100.0,
                latency,
                totalCost,
                providerUsage,
                modelUsage,
                recent
        );
    }
}
