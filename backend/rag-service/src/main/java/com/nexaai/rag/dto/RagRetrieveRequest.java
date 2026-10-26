package com.nexaai.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record RagRetrieveRequest(
        @NotNull(message = "userId is required for tenant isolation")
        UUID userId,

        @NotBlank(message = "query must not be blank")
        String query,

        Integer topK,

        List<UUID> documentIds,

        Double minScore
) {
}
