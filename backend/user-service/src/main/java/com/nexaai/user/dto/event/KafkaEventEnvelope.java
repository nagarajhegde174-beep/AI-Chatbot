package com.nexaai.user.dto.event;

import java.time.Instant;

/**
 * Inbound event envelope — per SERVICE_CONTRACTS.md §12.2.
 * Generic wrapper for all consumed events.
 */
public record KafkaEventEnvelope<T>(
        String eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String correlationId,
        String actorId,
        String subjectId,
        T payload
) {}
