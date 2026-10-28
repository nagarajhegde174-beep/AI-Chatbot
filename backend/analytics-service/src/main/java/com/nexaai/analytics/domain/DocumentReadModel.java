package com.nexaai.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_read_model")
public class DocumentReadModel {

    @Id
    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "status", nullable = false, length = 50)
    private String status; // UPLOADED, CHUNKED, INDEXED, FAILED, DELETED

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "embedding_count", nullable = false)
    private int embeddingCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public DocumentReadModel() {
    }

    public DocumentReadModel(UUID documentId, UUID userId, String title, long fileSizeBytes,
                             String status, int chunkCount, int embeddingCount, String errorMessage,
                             Instant uploadedAt, Instant updatedAt) {
        this.documentId = documentId;
        this.userId = userId;
        this.title = title;
        this.fileSizeBytes = fileSizeBytes;
        this.status = status != null ? status : "UPLOADED";
        this.chunkCount = chunkCount;
        this.embeddingCount = embeddingCount;
        this.errorMessage = errorMessage;
        this.uploadedAt = uploadedAt != null ? uploadedAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }

    public int getEmbeddingCount() {
        return embeddingCount;
    }

    public void setEmbeddingCount(int embeddingCount) {
        this.embeddingCount = embeddingCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
