package com.nexaai.chat.service;

import com.nexaai.chat.config.KafkaConfig;
import com.nexaai.chat.domain.Conversation;
import com.nexaai.chat.domain.Message;
import com.nexaai.chat.dto.event.AiRequestedPayload;
import com.nexaai.chat.dto.event.ConversationCreatedPayload;
import com.nexaai.chat.dto.event.EventEnvelope;
import com.nexaai.chat.dto.event.MessageSentPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes chat events asynchronously to Kafka (KRaft mode).
 * Emits CHAT_CREATED, MESSAGE_SENT, AI_REQUESTED per SERVICE_CONTRACTS §12.
 */
@Service
public class ChatEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ChatEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ChatEventPublisher(@Autowired(required = false) KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishConversationCreated(Conversation conversation, String correlationId) {
        ConversationCreatedPayload payload = new ConversationCreatedPayload(
                conversation.getId(),
                conversation.getUserId(),
                conversation.getTitle(),
                conversation.getModel(),
                conversation.isRagEnabled(),
                conversation.getCreatedAt()
        );

        EventEnvelope<ConversationCreatedPayload> envelope = EventEnvelope.create(
                KafkaConfig.TOPIC_CONVERSATION_CREATED,
                1,
                correlationId,
                conversation.getUserId().toString(),
                conversation.getId().toString(),
                payload
        );

        send(KafkaConfig.TOPIC_CONVERSATION_CREATED, conversation.getId().toString(), envelope);
    }

    public void publishMessageSent(Message message, String correlationId) {
        MessageSentPayload payload = new MessageSentPayload(
                message.getId(),
                message.getConversation().getId(),
                message.getUserId(),
                message.getRole().name(),
                message.getSequenceNumber(),
                message.getContent() != null ? message.getContent().length() : 0,
                message.getCreatedAt()
        );

        EventEnvelope<MessageSentPayload> envelope = EventEnvelope.create(
                KafkaConfig.TOPIC_MESSAGE_SENT,
                1,
                correlationId,
                message.getUserId() != null ? message.getUserId().toString() : "system",
                message.getConversation().getId().toString(),
                payload
        );

        send(KafkaConfig.TOPIC_MESSAGE_SENT, message.getConversation().getId().toString(), envelope);
    }

    public void publishAiRequested(UUID conversationId, UUID userId, String model, int estimatedTokens, String correlationId) {
        String requestId = "req-" + UUID.randomUUID().toString().substring(0, 8);
        AiRequestedPayload payload = new AiRequestedPayload(
                requestId,
                conversationId,
                userId,
                "SPRING_AI",
                model,
                estimatedTokens,
                Instant.now()
        );

        EventEnvelope<AiRequestedPayload> envelope = EventEnvelope.create(
                KafkaConfig.TOPIC_AI_REQUESTED,
                1,
                correlationId,
                userId != null ? userId.toString() : "anonymous",
                conversationId.toString(),
                payload
        );

        send(KafkaConfig.TOPIC_AI_REQUESTED, userId != null ? userId.toString() : conversationId.toString(), envelope);
    }

    private void send(String topic, String key, Object event) {
        if (kafkaTemplate == null) {
            log.debug("KafkaTemplate not configured; skipping Kafka event publish for topic: {}", topic);
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
