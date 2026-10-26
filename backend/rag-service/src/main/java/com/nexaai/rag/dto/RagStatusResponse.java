package com.nexaai.rag.dto;

import java.util.UUID;

public record RagStatusResponse(
        UUID documentId,
        long vectorCount,
        String status
) {
}
