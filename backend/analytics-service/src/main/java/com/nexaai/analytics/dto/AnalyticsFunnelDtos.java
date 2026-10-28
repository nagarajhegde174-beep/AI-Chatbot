package com.nexaai.analytics.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AnalyticsFunnelDtos {

    public record AnalyticsFunnelDto(
            long totalRequests,
            long successfulRequests,
            long failedRequests,
            long totalTokens,
            double errorRatePercent,
            double avgLatencyMs,
            BigDecimal totalEstimatedAiCostUsd,
            Map<String, Long> providerUsage,
            Map<String, Long> modelUsage,
            List<AiRequestTraceDto> recentTraces
    ) {}

    public record AiRequestTraceDto(
            UUID requestId,
            UUID userId,
            String provider,
            String model,
            long promptTokens,
            long completionTokens,
            long totalTokens,
            long latencyMs,
            BigDecimal estimatedCostUsd,
            String status,
            String errorCode,
            Instant createdAt
    ) {}
}
