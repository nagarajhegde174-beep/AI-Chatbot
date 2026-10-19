package com.nexaai.chat.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "message_citation")
public class MessageCitation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @Column(name = "document_id", nullable = false, length = 100)
    private String documentId;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "chunk_index")
    private Integer chunkIndex;

    @Column(name = "excerpt", columnDefinition = "TEXT")
    private String excerpt;

    public MessageCitation() {
    }

    public MessageCitation(UUID id, Message message, String documentId, String title, Integer chunkIndex, String excerpt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.message = message;
        this.documentId = documentId;
        this.title = title;
        this.chunkIndex = chunkIndex;
        this.excerpt = excerpt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public void setExcerpt(String excerpt) {
        this.excerpt = excerpt;
    }
}
