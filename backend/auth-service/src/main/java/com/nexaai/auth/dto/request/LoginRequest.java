package com.nexaai.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(description = "User email address", example = "user@example.com")
        @NotBlank(message = "Email is required.")
        @Email(message = "Email must be a valid email address.")
        String email,

        @Schema(description = "Account password", example = "P@ssw0rd123!")
        @NotBlank(message = "Password is required.")
        String password
) {}
