package com.nexaai.user.web.dto;

import jakarta.validation.constraints.Size;

/**
 * An administrative status change.
 *
 * <p>{@code reason} is required when suspending. A suspension the account holder cannot
 * explain is not an administrative tool, it is a support ticket waiting to happen, so the
 * service rejects a blank reason for SUSPENDED at the boundary rather than storing one.
 */
public record StatusChangeRequest(
        @Size(min = 1, max = 32, message = "status must be a valid account status")
        String status,

        @Size(max = 255, message = "reason must be at most 255 characters")
        String reason) {
}