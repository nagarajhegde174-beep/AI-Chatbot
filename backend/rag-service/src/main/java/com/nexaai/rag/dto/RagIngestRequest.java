package com.nexaai.rag.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record RagIngestRequest(
        @NotNull UUID userId,
        @NotNull UUID documentId,
        String documentTitle,
        String text,
        List<String> chunks
) {
}
