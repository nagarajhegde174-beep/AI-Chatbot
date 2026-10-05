package com.nexaai.user.web.dto;

import com.nexaai.user.domain.AccountStatus;
import com.nexaai.user.domain.Role;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of the administrative user listing.
 *
 * <p>Deliberately thinner than {@link UserProfileResponse}. An admin listing returns hundreds
 * of rows; carrying the full profile on each one is both wasteful and a wider blast radius if
 * the response is logged or cached somewhere it should not be.
 *
 * <p>The admin view does include the email. That is the point of an admin listing, and the
 * route requires the ADMIN role.
 */
public record AdminUserSummary(
        UUID id,
        UUID authUserId,
        String email,
        String displayName,
        AccountStatus accountStatus,
        Role role,
        String statusReason,
        boolean emailVerified,
        String planName,
        Instant createdAt) {
}