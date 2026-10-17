package com.nexaai.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @Schema(description = "Account email address", example = "user@example.com")
        @NotBlank(message = "Email is required.")
        @Email(message = "Email must be a valid email address.")
        String email
) {}
