package com.nexaai.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A refresh token, stored as a SHA-256 hash.
 *
 * <p>The plaintext token exists only in the HTTP response. A database dump must not yield a
 * usable refresh token, so the stored form is not the token
 * ({@code docs/SECURITY.md} section 2.2).
 *
 * <p>Tokens rotate: every use issues a new one and revokes the old. {@link #familyId} groups
 * a rotation chain, so presenting an already-rotated token can be detected as theft and the
 * whole family revoked at once.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private TokenSource source = TokenSource.PASSWORD;

    protected RefreshToken() {
        // for JPA
    }

    public RefreshToken(UUID familyId, String tokenHash, UUID userId, Instant expiresAt,
                        TokenSource source) {
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.source = source;
        this.issuedAt = Instant.now();
    }

    /** Whether the token may still be exchanged. */
    public boolean isUsable() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }

    /** Whether the token was already exchanged, which indicates theft rather than a retry. */
    public boolean isAlreadyUsed() {
        return revokedAt != null;
    }

    public void revoke() {
        if (this.revokedAt == null) {
            this.revokedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public TokenSource getSource() {
        return source;
    }

    /** Deliberately omits the token hash. */
    @Override
    public String toString() {
        return "RefreshToken{id=%s, familyId=%s, userId=%s, expiresAt=%s, revoked=%s}"
                .formatted(id, familyId, userId, expiresAt, revokedAt != null);
    }
}