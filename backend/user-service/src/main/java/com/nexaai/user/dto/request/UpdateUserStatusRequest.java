package com.nexaai.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * ADMIN: change a user's account status.
 * Per SERVICE_CONTRACTS.md §3.3 — AccountStatus values: ACTIVE, SUSPENDED, DEACTIVATED.
 */
public record UpdateUserStatusRequest(
        @NotBlank(message = "Status is required")
        @Pattern(regexp = "^(ACTIVE|SUSPENDED|DEACTIVATED)$",
                message = "Status must be ACTIVE, SUSPENDED, or DEACTIVATED")
        String status
) {}
