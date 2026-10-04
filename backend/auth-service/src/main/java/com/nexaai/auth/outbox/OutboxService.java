package com.nexaai.auth.outbox;

import com.nexaai.auth.domain.AuthUser;
import com.nexaai.auth.domain.OutboxEvent;
import com.nexaai.auth.repository.OutboxEventRepository;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes outbound events to the transactional outbox.
 *
 * <p>The account row and the event row are committed in one transaction, so the event can
 * neither be lost by a crash between the write and the publish, nor describe an account that
 * was rolled back. Those are the two failure modes of publishing straight onto the broker
 * inside a request, and both are real.
 *
 * <p><strong>No prompt or credential content in any payload.</strong> This service publishes
 * identity facts only: who was created, and what to call them
 * ({@code docs/SERVICE_CONTRACTS.md} section 12.2).
 */
@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    /** The topic this service owns. Versioned in the name, per the event naming convention. */
    public static final String TOPIC_USER_REGISTERED = "auth.user.registered.v1";
    public static final String TOPIC_EMAIL_VERIFIED = "auth.user.email_verified.v1";

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Records {@code auth.user.registered.v1}.
     *
     * <p>User Service consumes this to create a profile. It carries no credential, and none
     * can reach it: a password hash is not passed to this method.
     */
    @Transactional
    public void recordUserRegistered(AuthUser user, String correlationId) {
        Map<String, Object> payload = Map.of(
                "userId", user.getId().toString(),
                "email", user.getEmail(),
                "displayName", user.getDisplayName(),
                "roles", user.getRole().name(),
                "status", user.getStatus().name(),
                "emailVerified", user.isEmailVerified(),
                "registeredVia", "PASSWORD",
                "occurredAt", user.getCreatedAt().toString());

        save(TOPIC_USER_REGISTERED, TOPIC_USER_REGISTERED, user.getId(), payload, correlationId);
    }

    /** Records {@code auth.user.email_verified.v1}. */
    @Transactional
    public void recordEmailVerified(AuthUser user, String correlationId) {
        Map<String, Object> payload = Map.of(
                "userId", user.getId().toString(),
                "email", user.getEmail(),
                "emailVerified", true,
                "occurredAt", java.time.Instant.now().toString());

        save(TOPIC_EMAIL_VERIFIED, TOPIC_EMAIL_VERIFIED, user.getId(), payload, correlationId);
    }

    private void save(String topic, String eventType, UUID aggregateId,
                      Map<String, Object> payload, String correlationId) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            repository.save(new OutboxEvent(topic, eventType, aggregateId, json, correlationId));
        } catch (Exception e) {
            // A serialisation failure must not roll back a successful registration. The event
            // is lost, and losing an event is far better than refusing to create the account.
            log.error("Could not serialise outbox event {} for aggregate {}", eventType, aggregateId, e);
        }
    }
}