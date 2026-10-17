package com.nexaai.auth.dto.response;

import java.util.List;
import java.util.UUID;

public record UserStatusResponse(
        UUID userId,
        String status,
        List<String> roles
) {}
