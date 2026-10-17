package com.nexaai.auth.dto.response;

import java.util.List;
import java.util.UUID;

public record UserInfoResponse(
        UUID userId,
        String email,
        String displayName,
        List<String> roles,
        String status
) {}
