package com.nexaai.document.dto;

import java.time.Instant;
import java.util.UUID;

public record DocumentFailureResponse(
        UUID documentId,
        String status,
        String failureReason,
        Instant failedAt
) {
}
