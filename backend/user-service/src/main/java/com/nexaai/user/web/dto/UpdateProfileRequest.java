package com.nexaai.user.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A self-service profile update.
 *
 * <p><strong>Note what is not here: email, role, accountStatus, emailVerified.</strong> Those
 * are owned by auth-service or by administrative action. Accepting them on a self-service
 * endpoint would be a privilege escalation: a user could send {@code {"role": "ADMIN"}} and
 * become one. Their absence is the security control, not an oversight.
 */
public record UpdateProfileRequest(
        @Size(min = 1, max = 120, message = "displayName must be between 1 and 120 characters")
        String displayName,

        @Size(max = 1024, message = "avatarUrl must be at most 1024 characters")
        String avatarUrl) {
}