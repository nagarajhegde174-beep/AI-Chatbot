package com.nexaai.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A revoked access-token id, retained until the token would have expired anyway.
 *
 * <p>Redis holds this set in production for speed. This table is the durable record, so a
 * Redis flush cannot silently un-revoke a token
 * ({@code docs/ARCHITECTURE.md} decision 005, {@code docs/SECURITY.md} section 2.2).
 */
@Entity
@Table(name = "token_denylist")
public class RevokedToken {

    @Id
    @Column(name = "jti", nullable = false, length = 64)
    private String jti;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at", nullable = false)
    private Instant revokedAt = Instant.now();

    @Column(name = "reason", nullable = false, length = 64)
    private String reason;

    protected RevokedToken() {
        // for JPA
    }

    public RevokedToken(String jti, UUID userId, Instant expiresAt, String reason) {
        this.jti = jti;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.reason = reason;
    }

    /** Whether this entry may be pruned because the token has expired naturally. */
    public boolean isPurgeable() {
        return expiresAt.isBefore(Instant.now());
    }

    public String getJti() {
        return jti;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getReason() {
        return reason;
    }
}