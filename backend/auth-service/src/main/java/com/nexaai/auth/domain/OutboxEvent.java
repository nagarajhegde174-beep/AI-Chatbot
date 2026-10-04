package com.nexaai.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * The transactional outbox row for an event this service owns.
 *
 * <p>{@code auth.user.registered.v1} is produced to Kafka. Writing the account row and the
 * event row in one transaction means the event cannot be lost by a crash between the two,
 * and cannot claim an account that was rolled back — the two failure modes of publishing
 * straight onto the broker inside a request.
 *
 * <p>{@link #publishedAt} is null until the broker accepts the event. A non-null value is
 * the only evidence a publish actually happened.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "topic", nullable = false, length = 128)
    private String topic;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private int eventVersion = 1;

    @Column(name = "aggregate_id")
    private UUID aggregateId;

    /**
     * The event body, stored as {@code jsonb}.
     *
     * <p>Mapped as {@code String} but with the {@code jsonb} JDBC type. Without the explicit
     * type Hibernate binds it as {@code varchar}, and PostgreSQL rejects the insert with
     * {@code column "payload" is of type jsonb but expression is of type character varying}.
     * That is found only by running the insert, which is why the integration tests exist.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "last_error", length = 512)
    private String lastError;

    protected OutboxEvent() {
        // for JPA
    }

    public OutboxEvent(String topic, String eventType, UUID aggregateId, String payload,
                       String correlationId) {
        this.topic = topic;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.correlationId = correlationId;
        this.createdAt = Instant.now();
    }

    /** Records that the broker accepted the event. */
    public void markPublished() {
        this.publishedAt = Instant.now();
        this.lastError = null;
    }

    /**
     * Records a failed publish attempt.
     *
     * <p>The error is truncated because it can originate from an external broker and is
     * therefore unbounded.
     */
    public void recordFailure(String error) {
        this.attempts++;
        this.lastError = error == null || error.length() <= 512 ? error : error.substring(0, 512);
    }

    /** Whether the event has exhausted its retries and needs operator attention. */
    public boolean isExhausted(int maxAttempts) {
        return attempts >= maxAttempts;
    }

    public UUID getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public String getEventType() {
        return eventType;
    }

    public int getEventVersion() {
        return eventVersion;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getPayload() {
        return payload;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }
}