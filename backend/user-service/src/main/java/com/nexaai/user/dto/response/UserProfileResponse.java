package com.nexaai.user.dto.response;

import java.time.Instant;

/**
 * Per SERVICE_CONTRACTS.md §6.2 — profile shape.
 * No password, hash, or token field exists in this shape.
 */
public record UserProfileResponse(
        String userId,
        String email,
        String displayName,
        String avatarUrl,
        String accountStatus,
        PreferencesDto preferences,
        Instant createdAt,
        Instant updatedAt
) {
    public record PreferencesDto(
            String theme,
            String defaultModel,
            boolean ragEnabledByDefault,
            boolean streamResponses
    ) {}
}
