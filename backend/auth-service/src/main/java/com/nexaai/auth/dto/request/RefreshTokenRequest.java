package com.nexaai.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record RefreshTokenRequest(
        @Schema(description = "Refresh token string (optional if sent via HTTP-only cookie)", example = "d91f...")
        String refreshToken
) {}
