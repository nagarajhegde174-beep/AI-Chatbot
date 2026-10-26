package com.nexaai.auth.service;

import com.nexaai.auth.config.KafkaConfig;
import com.nexaai.auth.domain.entity.AuthUser;
import com.nexaai.auth.dto.event.EventEnvelope;
import com.nexaai.auth.dto.event.UserEmailVerifiedPayload;
import com.nexaai.auth.dto.event.UserRegisteredPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public EventPublisher(@Autowired(required = false) KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishUserRegistered(AuthUser user, String correlationId) {
        UserRegisteredPayload payload = new UserRegisteredPayload(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                List.of(user.getRole().name()),
                user.getStatus().name(),
                user.getCreatedAt()
        );

        EventEnvelope<UserRegisteredPayload> envelope = EventEnvelope.create(
                KafkaConfig.TOPIC_USER_REGISTERED,
                1,
                correlationId,
                user.getId().toString(),
                user.getId().toString(),
                payload
        );

        send(KafkaConfig.TOPIC_USER_REGISTERED, user.getId().toString(), envelope);
    }

    public void publishUserEmailVerified(AuthUser user, String correlationId) {
        UserEmailVerifiedPayload payload = new UserEmailVerifiedPayload(
                user.getId(),
                user.getEmail(),
                user.getStatus().name(),
                Instant.now()
        );

        EventEnvelope<UserEmailVerifiedPayload> envelope = EventEnvelope.create(
                KafkaConfig.TOPIC_USER_EMAIL_VERIFIED,
                1,
                correlationId,
                user.getId().toString(),
                user.getId().toString(),
                payload
        );

        send(KafkaConfig.TOPIC_USER_EMAIL_VERIFIED, user.getId().toString(), envelope);
    }

    public void publishUserLogin(AuthUser user, String correlationId, String ipAddress, String userAgent) {
        com.nexaai.auth.dto.event.UserLoginPayload payload = new com.nexaai.auth.dto.event.UserLoginPayload(
                user.getId(),
                user.getEmail(),
                Instant.now(),
                ipAddress,
                userAgent
        );

        EventEnvelope<com.nexaai.auth.dto.event.UserLoginPayload> envelope = EventEnvelope.create(
                KafkaConfig.TOPIC_USER_LOGIN,
                1,
                correlationId,
                user.getId().toString(),
                user.getId().toString(),
                payload
        );

        send(KafkaConfig.TOPIC_USER_LOGIN, user.getId().toString(), envelope);
    }

    private void send(String topic, String key, Object event) {
        if (kafkaTemplate == null) {
            log.warn("KafkaTemplate not configured; skipping Kafka event publish for topic: {}", topic);
            return;
        }

        try {
            kafkaTemplate.send(topic, key, event).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish event to topic {}: {}", topic, ex.getMessage());
                } else {
                    log.info("Published event to topic {} [key: {}]", topic, key);
                }
            });
        } catch (Exception ex) {
            log.warn("Could not dispatch event to Kafka for topic {}: {}", topic, ex.getMessage());
        }
    }
}
