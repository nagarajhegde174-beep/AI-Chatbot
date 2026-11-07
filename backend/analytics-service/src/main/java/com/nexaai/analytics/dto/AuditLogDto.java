package com.nexaai.analytics.dto;

import com.nexaai.analytics.domain.AuditLog;
import java.time.Instant;
import java.util.UUID;

public record AuditLogDto(
        UUID id,
        String event,
        String actor,
        String target,
        String status,
        String ip,
        String details,
        Instant timestamp
) {
    public static AuditLogDto from(AuditLog a) {
        return new AuditLogDto(
                a.getId(),
                a.getAction(),
                a.getActor(),
                a.getTarget(),
                a.getResult(),
                a.getIpAddress(),
                a.getDetails(),
                a.getCreatedAt()
        );
    }
}
