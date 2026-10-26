package com.nexaai.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexaai.user.domain.entity.ProcessedEvent;
import com.nexaai.user.domain.entity.UserPreferences;
import com.nexaai.user.domain.entity.UserProfile;
import com.nexaai.user.domain.model.AccountStatus;
import com.nexaai.user.domain.repository.ProcessedEventRepository;
import com.nexaai.user.domain.repository.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Consumes events emitted by auth-service per SERVICE_CONTRACTS.md §6.3.
 * Populates and updates UserProfile and UserPreferences.
 * Enforces idempotency via processed_event table per §12.5.
 * Features automated retry with exponential backoff and routing to DLT on failure.
 */
@Service
public class UserEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserEventConsumer.class);

    private final UserProfileRepository userProfileRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;
    private final UserProfileCacheService cacheService;

    public UserEventConsumer(UserProfileRepository userProfileRepository,
                             ProcessedEventRepository processedEventRepository,
                             ObjectMapper objectMapper,
                             @org.springframework.beans.factory.annotation.Autowired(required = false) UserProfileCacheService cacheService) {
        this.userProfileRepository = userProfileRepository;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
        this.cacheService = cacheService;
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            dltTopicSuffix = ".DLT"
    )
    @KafkaListener(topics = "auth.user.registered.v1", groupId = "${spring.kafka.consumer.group-id:nexa.user-service.events}")
    @Transactional
    public void handleUserRegistered(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventId = root.path("eventId").asText();
            if (eventId == null || eventId.isBlank()) {
                log.warn("Received auth.user.registered.v1 without eventId: {}", message);
                return;
            }

            if (processedEventRepository.existsById(eventId)) {
                log.info("Duplicate event ignored: {}", eventId);
                return;
            }

            JsonNode payload = root.path("payload");
            String userIdStr = payload.path("userId").asText();
            String email = payload.path("email").asText();
            String displayName = payload.path("displayName").asText(null);

            UUID userId = UUID.fromString(userIdStr);

            if (!userProfileRepository.existsById(userId)) {
                UserProfile profile = new UserProfile(userId, email, displayName, AccountStatus.ACTIVE);
                UserPreferences prefs = new UserPreferences(profile);
                profile.setPreferences(prefs);
                userProfileRepository.save(profile);
                log.info("Created user profile for userId: {}", userId);
            }

            processedEventRepository.save(new ProcessedEvent(eventId, "auth.user.registered.v1", Instant.now()));
        } catch (Exception e) {
            log.error("Failed to process auth.user.registered.v1: {}", message, e);
            throw new RuntimeException("Error processing Kafka message", e);
        }
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            dltTopicSuffix = ".DLT"
    )
    @KafkaListener(topics = "auth.user.email_verified.v1", groupId = "${spring.kafka.consumer.group-id:nexa.user-service.events}")
    @Transactional
    public void handleUserEmailVerified(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventId = root.path("eventId").asText();
            if (eventId == null || eventId.isBlank()) {
                log.warn("Received auth.user.email_verified.v1 without eventId: {}", message);
                return;
            }

            if (processedEventRepository.existsById(eventId)) {
                log.info("Duplicate event ignored: {}", eventId);
                return;
            }

            JsonNode payload = root.path("payload");
            String userIdStr = payload.path("userId").asText();
            UUID userId = UUID.fromString(userIdStr);

            userProfileRepository.findById(userId).ifPresent(user -> {
                if (user.getAccountStatus() == AccountStatus.PENDING_VERIFICATION) {
                    user.setAccountStatus(AccountStatus.ACTIVE);
                    user.setUpdatedAt(Instant.now());
                    userProfileRepository.save(user);
                    if (cacheService != null) {
                        cacheService.evict(userId);
                    }
                    log.info("Activated account for email-verified user: {}", userId);
                }
            });

            processedEventRepository.save(new ProcessedEvent(eventId, "auth.user.email_verified.v1", Instant.now()));
        } catch (Exception e) {
            log.error("Failed to process auth.user.email_verified.v1: {}", message, e);
            throw new RuntimeException("Error processing Kafka message", e);
        }
    }

    @DltHandler
    public void handleDlt(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Dead Letter Queue: routing message to operator alert triage for topic {}: {}", topic, message);
    }
}
