package com.nexaai.auth.dto.event;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String correlationId,
        String actorId,
        String subjectId,
        T payload
) {
    public static <T> EventEnvelope<T> create(String eventType, int eventVersion, String correlationId, String actorId, String subjectId, T payload) {
        return new EventEnvelope<>(
                UUID.randomUUID(),
                eventType,
                eventVersion,
                Instant.now(),
                correlationId != null ? correlationId : UUID.randomUUID().toString(),
                actorId,
                subjectId,
                payload
        );
    }
}
