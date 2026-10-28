package com.nexaai.analytics.dto;

import java.math.BigDecimal;
import java.util.List;

public record UserUsageSummaryDto(
        long aiRequests,
        TokenUsageBreakdown tokenUsage,
        long documentCount,
        long storageUsageBytes,
        String storageUsageFormatted,
        List<DailyUsagePointDto> dailyUsage,
        MonthlyUsageSummaryDto monthlyUsage,
        List<ModelUsageSummaryDto> modelUsage,
        long remainingQuota,
        UserSubscriptionStatusDto subscriptionStatus
) {
    public record TokenUsageBreakdown(long promptTokens, long completionTokens, long totalTokens) {}
    public record DailyUsagePointDto(String date, long requests, long tokens, BigDecimal estimatedCostUsd) {}
    public record MonthlyUsageSummaryDto(long currentMonthTokens, long currentMonthRequests, BigDecimal currentMonthCostUsd) {}
    public record ModelUsageSummaryDto(String model, String provider, long requests, long tokens, BigDecimal estimatedCostUsd) {}
    public record UserSubscriptionStatusDto(String tier, long tokenQuota, long tokensUsed, String status) {}
}
