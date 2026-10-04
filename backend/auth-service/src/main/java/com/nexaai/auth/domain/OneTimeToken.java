package com.nexaai.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A single-use, time-limited token, stored hashed.
 *
 * <p>Used for email verification and password reset. Stored as a SHA-256 hash so that read
 * access to this table is not sufficient to verify anyone's email or reset anyone's
 * password ({@code docs/SECURITY.md} section 2.1).
 *
 * <p>{@link #consume()} is deliberately not idempotent in its return value: the first call
 * succeeds and later calls report failure. The service layer treats a repeat as benign where
 * a user may reasonably double-click, but a reuse after the token is consumed and the
 * password changed is what the theft-detection path looks for.
 */
@Entity
@Table(name = "one_time_token")
public class OneTimeToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false)
    private OneTimeTokenPurpose purpose;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    protected OneTimeToken() {
        // for JPA
    }

    public OneTimeToken(UUID userId, String tokenHash, OneTimeTokenPurpose purpose,
                        Duration validity) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.purpose = purpose;
        this.issuedAt = Instant.now();
        this.expiresAt = this.issuedAt.plus(validity);
    }

    /** Whether the token is still redeemable. */
    public boolean isRedeemable() {
        return consumedAt == null && expiresAt.isAfter(Instant.now());
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    /** Marks the token used. Only valid while it is still redeemable. */
    public void consume() {
        this.consumedAt = Instant.now();
    }

    /**
     * Records a failed attempt.
     *
     * @return the new attempt count
     */
    public int recordFailedAttempt() {
        return ++this.attemptCount;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public OneTimeTokenPurpose getPurpose() {
        return purpose;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    /** Deliberately omits the token hash. */
    @Override
    public String toString() {
        return "OneTimeToken{id=%s, userId=%s, purpose=%s, expiresAt=%s, consumed=%s, attempts=%d}"
                .formatted(id, userId, purpose, expiresAt, consumedAt != null, attemptCount);
    }
}