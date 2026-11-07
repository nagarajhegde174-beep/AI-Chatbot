package com.nexaai.analytics.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "actor", nullable = false)
    private String actor;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "target")
    private String target;

    @Column(name = "result", nullable = false, length = 50)
    private String result;

    @Column(name = "ip_address", length = 100)
    private String ipAddress;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public AuditLog() {
    }

    public AuditLog(UUID id, String actor, String action, String target, String result, String ipAddress, String details, Instant createdAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.actor = actor != null ? actor : "system";
        this.action = action;
        this.target = target;
        this.result = result != null ? result : "SUCCESS";
        this.ipAddress = ipAddress;
        this.details = details;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
