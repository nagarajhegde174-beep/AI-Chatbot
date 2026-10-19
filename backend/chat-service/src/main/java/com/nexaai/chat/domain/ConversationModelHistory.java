package com.nexaai.chat.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversation_model_history")
public class ConversationModelHistory {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(name = "previous_model", length = 100)
    private String previousModel;

    @Column(name = "new_model", nullable = false, length = 100)
    private String newModel;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @Column(name = "changed_by", nullable = false, updatable = false)
    private UUID changedBy;

    public ConversationModelHistory() {
    }

    public ConversationModelHistory(UUID id, Conversation conversation, String previousModel, String newModel, UUID changedBy) {
        this.id = id != null ? id : UUID.randomUUID();
        this.conversation = conversation;
        this.previousModel = previousModel;
        this.newModel = newModel;
        this.changedBy = changedBy;
        this.changedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
    }

    public String getPreviousModel() {
        return previousModel;
    }

    public void setPreviousModel(String previousModel) {
        this.previousModel = previousModel;
    }

    public String getNewModel() {
        return newModel;
    }

    public void setNewModel(String newModel) {
        this.newModel = newModel;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Instant changedAt) {
        this.changedAt = changedAt;
    }

    public UUID getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(UUID changedBy) {
        this.changedBy = changedBy;
    }
}
