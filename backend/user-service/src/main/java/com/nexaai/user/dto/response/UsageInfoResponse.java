package com.nexaai.user.dto.response;

import java.time.Instant;
import java.util.Map;

/**
 * Usage summary DTO obtained via service boundary (REST / asynchronous).
 */
public record UsageInfoResponse(
        String userId,
        long totalTokensUsed,
        long messageCount,
        long documentCount,
        long storageBytesUsed,
        Instant billingCycleStart,
        Instant billingCycleEnd,
        Map<String, Long> tokensByModel
) {}
