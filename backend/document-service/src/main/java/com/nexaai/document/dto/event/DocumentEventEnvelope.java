package com.nexaai.document.dto.event;

import java.time.Instant;
import java.util.UUID;

public record DocumentEventEnvelope<T>(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String correlationId,
        UUID actorId,
        UUID subjectId,
        T payload
) {
    public static <T> DocumentEventEnvelope<T> create(String eventType, UUID actorId, UUID subjectId, String correlationId, T payload) {
        return new DocumentEventEnvelope<>(
                UUID.randomUUID(),
                eventType,
                1,
                Instant.now(),
                correlationId != null ? correlationId : UUID.randomUUID().toString(),
                actorId,
                subjectId,
                payload
        );
    }
}
