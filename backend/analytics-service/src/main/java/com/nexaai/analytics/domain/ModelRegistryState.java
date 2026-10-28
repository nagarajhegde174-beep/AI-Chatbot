package com.nexaai.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "model_registry_state")
public class ModelRegistryState {

    @Id
    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "total_requests", nullable = false)
    private long totalRequests;

    @Column(name = "total_tokens", nullable = false)
    private long totalTokens;

    @Column(name = "total_errors", nullable = false)
    private long totalErrors;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    public ModelRegistryState() {
    }

    public ModelRegistryState(String modelName, String provider, boolean enabled, boolean isDefault,
                              long totalRequests, long totalTokens, long totalErrors, Instant lastUsedAt) {
        this.modelName = modelName;
        this.provider = provider;
        this.enabled = enabled;
        this.isDefault = isDefault;
        this.totalRequests = totalRequests;
        this.totalTokens = totalTokens;
        this.totalErrors = totalErrors;
        this.lastUsedAt = lastUsedAt != null ? lastUsedAt : Instant.now();
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    public long getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(long totalRequests) {
        this.totalRequests = totalRequests;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(long totalTokens) {
        this.totalTokens = totalTokens;
    }

    public long getTotalErrors() {
        return totalErrors;
    }

    public void setTotalErrors(long totalErrors) {
        this.totalErrors = totalErrors;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }
}
