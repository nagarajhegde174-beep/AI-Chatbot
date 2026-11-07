package com.nexaai.analytics.service;

import com.nexaai.analytics.domain.AuditLog;
import com.nexaai.analytics.dto.AuditLogDto;
import com.nexaai.analytics.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(password|secret|token|bearer|authorization|key)=[^&\\s,\"]+",
            Pattern.CASE_INSENSITIVE
    );

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public AuditLog logEvent(String actor, String action, String target, String result, String ipAddress, String rawDetails) {
        String sanitizedDetails = sanitize(rawDetails);
        AuditLog auditLog = new AuditLog(
                UUID.randomUUID(),
                actor != null ? actor : "anonymous",
                action,
                target,
                result != null ? result : "SUCCESS",
                ipAddress,
                sanitizedDetails,
                Instant.now()
        );

        AuditLog saved = auditLogRepository.save(auditLog);
        log.info("Audit logged: action={}, actor={}, target={}, result={}", action, actor, target, result);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<AuditLogDto> getAuditLogs(String filter) {
        List<AuditLog> list;
        if (filter != null && !filter.isBlank()) {
            list = auditLogRepository.findTop100ByOrderByCreatedAtDesc().stream()
                    .filter(a -> a.getAction().toLowerCase().contains(filter.toLowerCase())
                            || a.getActor().toLowerCase().contains(filter.toLowerCase())
                            || (a.getTarget() != null && a.getTarget().toLowerCase().contains(filter.toLowerCase())))
                    .collect(Collectors.toList());
        } else {
            list = auditLogRepository.findTop100ByOrderByCreatedAtDesc();
        }

        return list.stream().map(AuditLogDto::from).collect(Collectors.toList());
    }

    private String sanitize(String input) {
        if (input == null) return null;
        return SENSITIVE_PATTERN.matcher(input).replaceAll("$1=[REDACTED]");
    }
}
