package com.nexaai.auth.dto.response;

import java.util.List;
import java.util.UUID;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserSummary user
) {
    public record UserSummary(
            UUID userId,
            String email,
            String displayName,
            List<String> roles
    ) {}

    public static AuthResponse of(String accessToken, String refreshToken, long expiresIn, UUID userId, String email, String displayName, List<String> roles) {
        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                expiresIn,
                new UserSummary(userId, email, displayName, roles)
        );
    }
}
