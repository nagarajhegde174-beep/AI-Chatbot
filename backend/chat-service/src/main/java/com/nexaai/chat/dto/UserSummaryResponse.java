package com.nexaai.chat.dto;

import java.util.UUID;

public record UserSummaryResponse(
        UUID userId,
        String email,
        String displayName,
        String accountStatus
) {
}
