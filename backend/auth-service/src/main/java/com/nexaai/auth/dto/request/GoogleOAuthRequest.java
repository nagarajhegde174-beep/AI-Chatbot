package com.nexaai.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record GoogleOAuthRequest(
        @Schema(description = "Google ID Token (JWT) from Google Sign-In button", example = "eyJhbGci...")
        String idToken,

        @Schema(description = "Optional direct Google ID for development / testing", example = "10987654321")
        String googleId,

        @Schema(description = "Optional direct Google email for development / testing", example = "googleuser@example.com")
        String email,

        @Schema(description = "Optional direct Google display name", example = "Google User")
        String displayName,

        @Schema(description = "Optional verified flag", example = "true")
        Boolean emailVerified
) {}
