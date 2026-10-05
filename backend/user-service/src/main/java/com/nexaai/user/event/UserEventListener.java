package com.nexaai.user.event;

import com.nexaai.user.config.UserProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Consumes {@code auth.user.registered.v1}.
 *
 * <p><strong>Disabled unless a broker is configured.</strong> The listener bean is created only
 * when {@code nexa.user.event.enabled} is true, so User Service starts and serves traffic in an
 * environment with no Kafka, rather than failing to start on an unreachable broker.
 * Registration is delivered by replay in a fresh environment, so running without consuming is
 * a supported state, not a broken one.
 *
 * <p>The consumer group is this service's own. A group shared with another service would make
 * the two services compete for events, each getting a random subset of them.
 */
@Component
@ConditionalOnProperty(prefix = "nexa.user.event", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class UserEventListener {

    private static final Logger log = LoggerFactory.getLogger(UserEventListener.class);

    private final UserProperties properties;
    private final ObjectMapper objectMapper;
    private final UserRegistrationEventHandler handler;

    public UserEventListener(UserProperties properties, ObjectMapper objectMapper,
                             UserRegistrationEventHandler handler) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.handler = handler;
    }

    @KafkaListener(
            topics = UserRegisteredPayload.TOPIC,
            groupId = "${nexa.user.event.consumer-group}")
    public void onUserRegistered(String rawJson) {
        log.debug("Received {} bytes on {}", rawJson.length(), UserRegisteredPayload.TOPIC);
        try {
            DomainEvent<UserRegisteredPayload> event = objectMapper.readValue(
                    rawJson,
                    objectMapper.getTypeFactory().constructParametricType(
                            DomainEvent.class, UserRegisteredPayload.class));
            handler.onUserRegistered(event);
        } catch (Exception e) {
            // Rethrown so the container applies its retry and dead-letter policy. Swallowing it
            // here would commit the offset and silently lose the registration, leaving a real
            // account with no profile and no indication why.
            log.error("Failed to handle event on {}: {}", UserRegisteredPayload.TOPIC,
                    e.getClass().getSimpleName());
            throw new EventHandlingException("Could not handle "
                    + UserRegisteredPayload.TOPIC, e);
        }
    }

    /** Wraps a failure so the container's error handling sees a runtime exception. */
    public static class EventHandlingException extends RuntimeException {
        public EventHandlingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}