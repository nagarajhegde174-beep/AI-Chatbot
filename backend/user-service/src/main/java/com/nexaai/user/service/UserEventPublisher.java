package com.nexaai.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexaai.user.dto.event.OutboundEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Publishes domain events to Kafka per SERVICE_CONTRACTS.md §6.4 & §12.
 * Non-blocking / graceful fallback if Kafka is not running (e.g. unit tests or local dev).
 */
@Service
public class UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public UserEventPublisher(@Autowired(required = false) KafkaTemplate<String, Object> kafkaTemplate,
                              ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishProfileUpdated(String userId, Object payload) {
        publish("user.profile.updated.v1", userId, userId, payload);
    }

    public void publishAccountDeleted(String userId) {
        publish("user.account.deleted.v1", userId, userId, Map.of("userId", userId));
    }

    public void publishStatusChanged(String actorId, String targetUserId, String oldStatus, String newStatus) {
        publish("user.status.changed.v1", actorId, targetUserId, Map.of(
                "userId", targetUserId,
                "oldStatus", oldStatus,
                "newStatus", newStatus
        ));
    }

    private void publish(String topic, String actorId, String subjectId, Object payload) {
        OutboundEvent event = OutboundEvent.of(topic, actorId, subjectId, payload);
        if (kafkaTemplate != null) {
            try {
                kafkaTemplate.send(topic, subjectId, event);
                log.info("Published event {} to topic {}", event.eventId(), topic);
            } catch (Exception e) {
                log.warn("Failed to publish event {} to topic {}: {}", event.eventId(), topic, e.getMessage());
            }
        } else {
            log.debug("KafkaTemplate not configured. Skipping event emission for topic {}", topic);
        }
    }
}
