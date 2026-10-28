package com.nexaai.analytics.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ConversationMonitoringDtos {

    public record ConversationMonitoringDto(
            long todayQueries,
            long yesterdayQueries,
            long olderQueries,
            long totalQueries,
            List<UserWiseQueryActivityDto> userWiseActivity
    ) {}

    /**
     * Preserves privacy: carries counts, message lengths, latency, token metrics,
     * NEVER stores or exposes sensitive prompt text in plain view.
     */
    public record UserWiseQueryActivityDto(
            UUID userId,
            long queryCount,
            long totalTokens,
            long avgLatencyMs,
            Instant lastQueryAt
    ) {}
}
