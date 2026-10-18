package com.nexaai.user.dto.response;

import java.time.Instant;

/**
 * Subscription summary DTO obtained via service boundary (REST / asynchronous).
 */
public record SubscriptionInfoResponse(
        String userId,
        String plan,
        String status,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd
) {}
