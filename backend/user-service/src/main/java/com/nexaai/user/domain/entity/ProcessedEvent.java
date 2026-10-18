package com.nexaai.user.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Tracks consumed Kafka event IDs for idempotent processing.
 * Per SERVICE_CONTRACTS.md §12.5: at-least-once delivery; every consumer is idempotent.
 */
@Entity
@Table(name = "processed_event")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ProcessedEvent() {}

    public ProcessedEvent(String eventId, String topic) {
        this(eventId, topic, Instant.now());
    }

    public ProcessedEvent(String eventId, String topic, Instant processedAt) {
        this.eventId = eventId;
        this.topic = topic;
        this.processedAt = processedAt != null ? processedAt : Instant.now();
    }

    public String getEventId() { return eventId; }
    public String getTopic() { return topic; }
    public Instant getProcessedAt() { return processedAt; }
}
