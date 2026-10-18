package com.nexaai.user.dto.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound Kafka event envelope — per SERVICE_CONTRACTS.md §12.2.
 * Used for all produced events: user.profile.updated.v1, user.status.changed.v1, user.account.deleted.v1.
 */
public record OutboundEvent(
        String eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String correlationId,
        String actorId,
        String subjectId,
        Object payload
) {
    public static OutboundEvent of(String eventType, String actorId, String subjectId, Object payload) {
        return new OutboundEvent(
                UUID.randomUUID().toString(),
                eventType,
                1,
                Instant.now(),
                UUID.randomUUID().toString(),
                actorId,
                subjectId,
                payload
        );
    }

    public static OutboundEvent of(String eventType, String actorId, String subjectId,
                                    String correlationId, Object payload) {
        return new OutboundEvent(
                UUID.randomUUID().toString(),
                eventType,
                1,
                Instant.now(),
                correlationId,
                actorId,
                subjectId,
                payload
        );
    }
}
