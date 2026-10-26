package com.nexaai.document.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DocumentContentResponse(
        UUID documentId,
        String title,
        int chunkCount,
        List<DocumentChunkResponse> chunks,
        Instant retrievedAt
) {
}
