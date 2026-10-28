package com.nexaai.analytics.service;

import com.nexaai.analytics.domain.AiRequestMetric;
import com.nexaai.analytics.domain.UserActivityReadModel;
import com.nexaai.analytics.dto.UserUsageSummaryDto;
import com.nexaai.analytics.repository.AiRequestMetricRepository;
import com.nexaai.analytics.repository.DocumentReadModelRepository;
import com.nexaai.analytics.repository.UserActivityReadModelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class UserUsageService {

    private final AiRequestMetricRepository metricRepository;
    private final DocumentReadModelRepository documentRepository;
    private final UserActivityReadModelRepository userActivityRepository;

    public UserUsageService(AiRequestMetricRepository metricRepository,
                            DocumentReadModelRepository documentRepository,
                            UserActivityReadModelRepository userActivityRepository) {
        this.metricRepository = metricRepository;
        this.documentRepository = documentRepository;
        this.userActivityRepository = userActivityRepository;
    }

    public UserUsageSummaryDto getUserUsageSummary(UUID userId) {
        // 1. User activity profile / quota
        UserActivityReadModel userProfile = userActivityRepository.findById(userId)
                .orElse(new UserActivityReadModel(userId, null, "ACTIVE", "FREE", 100000L, 0L, 0L, null, null, Instant.now()));

        // 2. Token counts
        Long promptTokens = metricRepository.sumPromptTokensByUserId(userId);
        Long completionTokens = metricRepository.sumCompletionTokensByUserId(userId);
        Long totalTokens = metricRepository.sumTotalTokensByUserId(userId);

        long pTokens = promptTokens != null ? promptTokens : 0L;
        long cTokens = completionTokens != null ? completionTokens : 0L;
        long tTokens = totalTokens != null ? totalTokens : 0L;

        long aiRequests = metricRepository.countByUserId(userId);

        // 3. Document and storage usage
        long docCount = documentRepository.countByUserIdAndStatusNot(userId, "DELETED");
        Long storageBytes = documentRepository.sumStorageBytesByUserId(userId);
        long sBytes = storageBytes != null ? storageBytes : 0L;

        // 4. Daily usage points (last 14 days)
        Instant fourteenDaysAgo = Instant.now().minus(14, ChronoUnit.DAYS);
        List<AiRequestMetric> recentMetrics = metricRepository.findByUserIdAndCreatedAtAfter(userId, fourteenDaysAgo);

        Map<String, List<AiRequestMetric>> byDate = recentMetrics.stream()
                .collect(Collectors.groupingBy(m ->
                        LocalDate.ofInstant(m.getCreatedAt(), ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE)));

        List<UserUsageSummaryDto.DailyUsagePointDto> dailyUsage = new ArrayList<>();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int i = 13; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            String dateKey = d.format(DateTimeFormatter.ISO_LOCAL_DATE);
            List<AiRequestMetric> metricsForDay = byDate.getOrDefault(dateKey, List.of());
            long dayReqs = metricsForDay.size();
            long dayTokens = metricsForDay.stream().mapToLong(AiRequestMetric::getTotalTokens).sum();
            BigDecimal dayCost = metricsForDay.stream()
                    .map(AiRequestMetric::getEstimatedCostUsd)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            dailyUsage.add(new UserUsageSummaryDto.DailyUsagePointDto(dateKey, dayReqs, dayTokens, dayCost));
        }

        // 5. Monthly usage
        Instant startOfMonth = today.withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<AiRequestMetric> monthMetrics = metricRepository.findByUserIdAndCreatedAtAfter(userId, startOfMonth);
        long monthTokens = monthMetrics.stream().mapToLong(AiRequestMetric::getTotalTokens).sum();
        long monthRequests = monthMetrics.size();
        BigDecimal monthCost = monthMetrics.stream()
                .map(AiRequestMetric::getEstimatedCostUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        UserUsageSummaryDto.MonthlyUsageSummaryDto monthlyUsage =
                new UserUsageSummaryDto.MonthlyUsageSummaryDto(monthTokens, monthRequests, monthCost);

        // 6. Model usage breakdown
        List<AiRequestMetric> allUserMetrics = metricRepository.findByUserId(userId);
        Map<String, List<AiRequestMetric>> byModel = allUserMetrics.stream()
                .collect(Collectors.groupingBy(AiRequestMetric::getModel));

        List<UserUsageSummaryDto.ModelUsageSummaryDto> modelUsage = byModel.entrySet().stream()
                .map(entry -> {
                    String modelName = entry.getKey();
                    List<AiRequestMetric> ms = entry.getValue();
                    String provider = ms.isEmpty() ? "OPENAI" : ms.get(0).getProvider();
                    long reqs = ms.size();
                    long toks = ms.stream().mapToLong(AiRequestMetric::getTotalTokens).sum();
                    BigDecimal cost = ms.stream()
                            .map(AiRequestMetric::getEstimatedCostUsd)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new UserUsageSummaryDto.ModelUsageSummaryDto(modelName, provider, reqs, toks, cost);
                })
                .sorted(Comparator.comparing(UserUsageSummaryDto.ModelUsageSummaryDto::requests).reversed())
                .toList();

        // 7. Remaining quota
        long remainingQuota = Math.max(0L, userProfile.getTokenQuota() - tTokens);

        return new UserUsageSummaryDto(
                aiRequests,
                new UserUsageSummaryDto.TokenUsageBreakdown(pTokens, cTokens, tTokens),
                docCount,
                sBytes,
                formatBytes(sBytes),
                dailyUsage,
                monthlyUsage,
                modelUsage,
                remainingQuota,
                new UserUsageSummaryDto.UserSubscriptionStatusDto(
                        userProfile.getSubscriptionTier(),
                        userProfile.getTokenQuota(),
                        tTokens,
                        userProfile.getStatus()
                )
        );
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
