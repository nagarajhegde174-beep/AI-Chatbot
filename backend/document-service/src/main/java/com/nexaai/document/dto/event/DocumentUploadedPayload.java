package com.nexaai.document.dto.event;

import java.time.Instant;
import java.util.UUID;

public record DocumentUploadedPayload(
        UUID documentId,
        UUID userId,
        String filename,
        String contentType,
        long byteSize,
        String storageUri,
        Instant uploadedAt
) {
}
