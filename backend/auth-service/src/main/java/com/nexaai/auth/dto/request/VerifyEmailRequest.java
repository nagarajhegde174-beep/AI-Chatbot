package com.nexaai.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(
        @Schema(description = "Verification token received via email", example = "a1b2c3d4-e5f6...")
        @NotBlank(message = "Verification token is required.")
        String token
) {}
