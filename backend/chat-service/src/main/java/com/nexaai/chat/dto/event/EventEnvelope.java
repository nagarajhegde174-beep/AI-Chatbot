package com.nexaai.chat.dto.event;

import java.time.Instant;
import java.util.UUID;

public record EventEnvelope<T>(
        String eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String correlationId,
        String actorId,
        String subjectId,
        T payload
) {
    public static <T> EventEnvelope<T> create(
            String eventType,
            int version,
            String correlationId,
            String actorId,
            String subjectId,
            T payload
    ) {
        return new EventEnvelope<>(
                UUID.randomUUID().toString(),
                eventType,
                version,
                Instant.now(),
                correlationId != null ? correlationId : UUID.randomUUID().toString(),
                actorId,
                subjectId,
                payload
        );
    }
}
