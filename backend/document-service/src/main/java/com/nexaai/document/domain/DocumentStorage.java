package com.nexaai.document.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_storage")
public class DocumentStorage {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false, unique = true)
    private Document document;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "stored_bytes", nullable = false)
    private long storedBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public DocumentStorage() {
    }

    public DocumentStorage(UUID id, Document document, String storagePath, String checksumSha256, long storedBytes) {
        this.id = id != null ? id : UUID.randomUUID();
        this.document = document;
        this.storagePath = storagePath;
        this.checksumSha256 = checksumSha256;
        this.storedBytes = storedBytes;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public void setChecksumSha256(String checksumSha256) {
        this.checksumSha256 = checksumSha256;
    }

    public long getStoredBytes() {
        return storedBytes;
    }

    public void setStoredBytes(long storedBytes) {
        this.storedBytes = storedBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
