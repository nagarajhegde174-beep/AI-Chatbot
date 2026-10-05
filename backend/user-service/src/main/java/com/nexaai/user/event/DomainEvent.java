package com.nexaai.user.event;

import java.time.Instant;
import java.util.UUID;

/**
 * The envelope every NexaAI event shares.
 *
 * <p>Duplicated rather than imported from a shared events module: a shared jar would be the
 * shared business module that {@code docs/RULES.md} §2 prohibits. A schema duplicated across
 * services is a real cost, and it is smaller than a shared dependency.
 *
 * <p>{@code eventId} is what makes consumption idempotent. Kafka is at-least-once, so the same
 * event will arrive twice, and dedup is keyed on this field.
 */
public record DomainEvent<T>(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        UUID correlationId,
        T payload) {
}