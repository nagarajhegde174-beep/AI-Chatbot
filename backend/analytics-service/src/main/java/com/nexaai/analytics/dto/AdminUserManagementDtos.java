package com.nexaai.analytics.dto;

import java.time.Instant;
import java.util.UUID;

public class AdminUserManagementDtos {

    public record AdminUserSummaryDto(
            UUID userId,
            String email,
            String status,
            String subscriptionTier,
            long tokenQuota,
            long tokensUsed,
            long totalRequests,
            Instant lastLoginAt,
            String lastLoginIp,
            Instant createdAt
    ) {}

    public record UpdateUserStatusRequest(
            String status
    ) {}
}
