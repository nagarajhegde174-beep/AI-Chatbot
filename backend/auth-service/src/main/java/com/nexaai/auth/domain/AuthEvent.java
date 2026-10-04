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

/**
 * An authentication event: what happened, to whom, and whether it succeeded.
 *
 * <p>This is the {@code auth_event} table, which serves lockout decisions and the audit
 * trail. Per {@code docs/SECURITY.md} section 11.2 it records <em>that</em> an event
 * occurred, never a credential and never a password.
 */
@Entity
@Table(name = "auth_event")
public class AuthEvent {

    /**
     * Surrogate key.
     *
     * <p>Deliberately a bigserial, not a UUID. This table grows without bound and is never
     * referenced by id from outside the service, so there is nothing to gain from a random
     * key and a real cost: a bigserial index is compact and ordered, which keeps inserts
     * appending rather than scattering across the index.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /** Null for an event about an email that matches no account, such as a failed login. */
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private AuthEventType eventType;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    protected AuthEvent() {
        // for JPA
    }

    public AuthEvent(UUID userId, AuthEventType eventType, boolean success,
                     String ipAddress, String userAgent) {
        this.userId = userId;
        this.eventType = eventType;
        this.success = success;
        this.ipAddress = ipAddress;
        this.userAgent = truncate(userAgent);
        this.occurredAt = Instant.now();
    }

    /**
     * Truncates the user agent to the column width.
     *
     * <p>A user agent is attacker-controlled and unbounded. Without truncation an oversized
     * header is either a hard database error or, worse, a way to push a large blob into the
     * audit table.
     */
    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 512 ? value : value.substring(0, 512);
    }

    public Long getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public AuthEventType getEventType() {
        return eventType;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
