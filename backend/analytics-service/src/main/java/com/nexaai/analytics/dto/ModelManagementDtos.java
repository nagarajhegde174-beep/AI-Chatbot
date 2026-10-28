package com.nexaai.analytics.dto;

import java.time.Instant;

public class ModelManagementDtos {

    public record ModelItemDto(
            String modelName,
            String provider,
            boolean enabled,
            boolean isDefault,
            long totalRequests,
            long totalTokens,
            long totalErrors,
            String status, // HEALTHY, DEGRADED, DISABLED
            Instant lastUsedAt
    ) {}

    public record ToggleModelRequest(
            boolean enabled
    ) {}
}
