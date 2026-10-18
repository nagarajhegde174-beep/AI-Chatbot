package com.nexaai.user.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Partial update for own profile. All fields optional — only non-null values are applied.
 */
public record UpdateProfileRequest(
        @Size(min = 1, max = 100, message = "Display name must be between 1 and 100 characters")
        String displayName,

        @Size(max = 500, message = "Avatar URL must not exceed 500 characters")
        String avatarUrl
) {}
