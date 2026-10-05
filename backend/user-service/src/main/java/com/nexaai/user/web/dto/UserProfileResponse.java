package com.nexaai.user.web.dto;

import com.nexaai.user.domain.AccountStatus;
import com.nexaai.user.domain.Role;
import com.nexaai.user.domain.UserProfile;
import java.time.Instant;
import java.util.UUID;

/**
 * A user's own profile.
 *
 * <p>Returned only to the account holder. Note what is absent: the internal profile id is
 * included because the frontend needs it, but no credential-adjacent field exists at all,
 * because none exists to expose.
 */
public record UserProfileResponse(
        UUID id,
        UUID authUserId,
        String email,
        String displayName,
        String avatarUrl,
        AccountStatus accountStatus,
        Role role,
        String statusReason,
        Instant statusChangedAt,
        String planId,
        String planName,
        boolean emailVerified,
        String registeredVia,
        Instant createdAt,
        Instant updatedAt) {

    public static UserProfileResponse from(UserProfile p) {
        return new UserProfileResponse(
                p.getId(),
                p.getAuthUserId(),
                p.getEmail(),
                p.getDisplayName(),
                p.getAvatarUrl(),
                p.getAccountStatus(),
                p.getRole(),
                p.getStatusReason(),
                p.getStatusChangedAt(),
                p.getPlanId(),
                p.getPlanName(),
                p.isEmailVerified(),
                p.getRegisteredVia().name(),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }
}