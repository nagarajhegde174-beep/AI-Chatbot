package com.nexaai.document.dto.event;

import java.util.UUID;

public record DocumentProcessingFailedPayload(
        UUID documentId,
        UUID userId,
        String reasonCode,
        String failureReason
) {
}
