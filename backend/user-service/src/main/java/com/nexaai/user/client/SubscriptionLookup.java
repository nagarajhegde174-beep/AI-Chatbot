package com.nexaai.user.client;

import java.util.UUID;

/**
 * User Service's view of subscription and usage data.
 *
 * <p><strong>An interface, not a call to the database.</strong> Subscription Service owns
 * billing; this service owns profiles. Reading another service's database directly would
 * violate the one-database-per-service rule in the same way reading Auth Service's database
 * would ({@code docs/RULES.md} §3), so the boundary is HTTP behind this interface.
 *
 * <p>Phase 9 implements Subscription Service. Until it exists, the bean bound to this
 * interface reports data as unavailable rather than as zero. That distinction matters: a zero
 * usage figure reads as "this user has used nothing", which is a different and wrong claim.
 */
public interface SubscriptionLookup {

    /**
     * Subscription and usage figures for one user.
     *
     * @param authUserId the auth-service user id, the identity the whole platform shares
     * @return the figures, or {@link Unavailable} when the owning service cannot be reached
     */
    UsageView usageFor(UUID authUserId);

    /**
     * The subscription and usage figures for one user.
     *
     * @param available {@code false} when the owning service is not reachable. Present as a
     *                  field rather than as an exception so one unreachable service degrades
     *                  the admin page instead of failing the whole request.
     * @param reason    why it is unavailable, safe to display
     */
    record UsageView(
            boolean available,
            String reason,
            String planId,
            String planName,
            String subscriptionStatus,
            Long monthlyMessageQuota,
            Long messagesUsedThisPeriod,
            Long tokenQuota,
            Long tokensUsedThisPeriod,
            java.time.Instant periodEndsAt) {

        /** Used when nothing can be fetched. Never reports zero usage. */
        public static UsageView unavailable(String reason) {
            return new UsageView(false, reason, null, null, null, null, null, null, null, null);
        }

        public static UsageView none(UUID authUserId) {
            return new UsageView(true, null, null, null, "NONE", null, null, null, null, null);
        }
    }
}