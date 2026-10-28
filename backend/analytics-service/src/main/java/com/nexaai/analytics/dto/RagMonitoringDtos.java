package com.nexaai.analytics.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class RagMonitoringDtos {

    public record RagMonitoringDto(
            RagSummaryDto summary,
            List<RagDocumentItemDto> documents
    ) {}

    public record RagSummaryDto(
            long totalDocuments,
            long activeDocuments,
            long totalChunks,
            long totalEmbeddings,
            long failureCount
    ) {}

    public record RagDocumentItemDto(
            UUID documentId,
            UUID userId,
            String title,
            long fileSizeBytes,
            String status,
            int chunkCount,
            int embeddingCount,
            String errorMessage,
            Instant uploadedAt,
            Instant updatedAt
    ) {}
}
