package com.nexaai.analytics.dto;

import java.math.BigDecimal;
import java.util.Map;

public record AdminOverviewDto(
        long totalUsers,
        long activeUsers,
        long totalAiRequests,
        long successfulRequests,
        long failedRequests,
        long totalTokens,
        long totalErrors,
        double errorRatePercent,
        Map<String, Long> subscriptionStatistics,
        BigDecimal totalEstimatedAiCostUsd
) {}
