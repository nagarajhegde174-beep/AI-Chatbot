package com.nexaai.user.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.springframework.data.domain.Persistable;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_preferences")
public class UserPreferences implements Persistable<UUID> {

    @Transient
    private boolean isNew = true;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private UserProfile user;

    @Column(name = "theme", nullable = false)
    private String theme = "system";

    @Column(name = "default_model")
    private String defaultModel;

    @Column(name = "rag_enabled_default", nullable = false)
    private boolean ragEnabledByDefault = true;

    @Column(name = "stream_responses", nullable = false)
    private boolean streamResponses = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UserPreferences() {}

    public UserPreferences(UserProfile user) {
        this.user = user;
        this.userId = user.getId();
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() { return userId; }

    public UserProfile getUser() { return user; }
    public void setUser(UserProfile user) { this.user = user; }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public String getDefaultModel() { return defaultModel; }
    public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }

    public boolean isRagEnabledByDefault() { return ragEnabledByDefault; }
    public void setRagEnabledByDefault(boolean ragEnabledByDefault) { this.ragEnabledByDefault = ragEnabledByDefault; }

    public boolean isStreamResponses() { return streamResponses; }
    public void setStreamResponses(boolean streamResponses) { this.streamResponses = streamResponses; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public UUID getId() {
        return this.userId;
    }

    @Override
    public boolean isNew() {
        return this.isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}
