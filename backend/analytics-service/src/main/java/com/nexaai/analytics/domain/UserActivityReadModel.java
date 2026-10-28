package com.nexaai.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_activity_read_model")
public class UserActivityReadModel {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "email")
    private String email;

    @Column(name = "status", nullable = false, length = 50)
    private String status; // ACTIVE, SUSPENDED, DEACTIVATED

    @Column(name = "subscription_tier", nullable = false, length = 50)
    private String subscriptionTier; // FREE, PRO, ENTERPRISE

    @Column(name = "token_quota", nullable = false)
    private long tokenQuota;

    @Column(name = "tokens_used", nullable = false)
    private long tokensUsed;

    @Column(name = "total_requests", nullable = false)
    private long totalRequests;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "last_login_ip", length = 100)
    private String lastLoginIp;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UserActivityReadModel() {
    }

    public UserActivityReadModel(UUID userId, String email, String status, String subscriptionTier,
                                 long tokenQuota, long tokensUsed, long totalRequests,
                                 Instant lastLoginAt, String lastLoginIp, Instant createdAt) {
        this.userId = userId;
        this.email = email;
        this.status = status != null ? status : "ACTIVE";
        this.subscriptionTier = subscriptionTier != null ? subscriptionTier : "FREE";
        this.tokenQuota = tokenQuota > 0 ? tokenQuota : 100000L;
        this.tokensUsed = tokensUsed;
        this.totalRequests = totalRequests;
        this.lastLoginAt = lastLoginAt;
        this.lastLoginIp = lastLoginIp;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSubscriptionTier() {
        return subscriptionTier;
    }

    public void setSubscriptionTier(String subscriptionTier) {
        this.subscriptionTier = subscriptionTier;
    }

    public long getTokenQuota() {
        return tokenQuota;
    }

    public void setTokenQuota(long tokenQuota) {
        this.tokenQuota = tokenQuota;
    }

    public long getTokensUsed() {
        return tokensUsed;
    }

    public void setTokensUsed(long tokensUsed) {
        this.tokensUsed = tokensUsed;
    }

    public long getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(long totalRequests) {
        this.totalRequests = totalRequests;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public String getLastLoginIp() {
        return lastLoginIp;
    }

    public void setLastLoginIp(String lastLoginIp) {
        this.lastLoginIp = lastLoginIp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
