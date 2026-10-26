package com.nexaai.document.dto;

import com.nexaai.document.domain.Document;
import com.nexaai.document.domain.DocumentStatus;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID documentId,
        String title,
        String originalFilename,
        String contentType,
        long sizeBytes,
        DocumentStatus status,
        String failureReason,
        int chunkCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static DocumentResponse from(Document doc) {
        return new DocumentResponse(
                doc.getId(),
                doc.getTitle(),
                doc.getOriginalFilename(),
                doc.getContentType(),
                doc.getSizeBytes(),
                doc.getStatus(),
                doc.getFailureReason(),
                doc.getChunkCount(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }
}
