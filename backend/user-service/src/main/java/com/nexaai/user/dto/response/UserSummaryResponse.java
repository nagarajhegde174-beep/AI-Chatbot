package com.nexaai.user.dto.response;

import java.time.Instant;

/**
 * Paginated admin view — shows slightly less detail than full profile by design,
 * to keep list responses lean. Full detail is at GET /api/v1/admin/users/{id}.
 */
public record UserSummaryResponse(
        String userId,
        String email,
        String displayName,
        String accountStatus,
        Instant createdAt
) {}
