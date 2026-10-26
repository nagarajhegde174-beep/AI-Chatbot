package com.nexaai.rag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RagEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RagEventPublisher.class);

    public static final String TOPIC_DOCUMENT_INDEXED = "document.indexed.v1";
    public static final String TOPIC_DOCUMENT_PROCESSED = "document.processed.v1";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public RagEventPublisher(
            @Autowired(required = false) KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishDocumentIndexed(UUID documentId, UUID userId, int chunkCount, String embeddingModel, String correlationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("documentId", documentId);
        payload.put("userId", userId);
        payload.put("chunkCount", chunkCount);
        payload.put("vectorCount", chunkCount);
        payload.put("embeddingModel", embeddingModel);
        payload.put("status", "COMPLETED");
        payload.put("processedAt", Instant.now().toString());

        send(TOPIC_DOCUMENT_INDEXED, documentId.toString(), "document.indexed.v1", userId, documentId, correlationId, payload);
        send(TOPIC_DOCUMENT_PROCESSED, documentId.toString(), "document.processed.v1", userId, documentId, correlationId, payload);
    }

    private void send(String topic, String key, String eventType, UUID actorId, UUID subjectId, String correlationId, Object payload) {
        if (kafkaTemplate == null) {
            log.debug("KafkaTemplate not available, skipping RAG event: {}", eventType);
            return;
        }

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", 1);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("correlationId", correlationId != null ? correlationId : UUID.randomUUID().toString());
        envelope.put("actorId", actorId != null ? actorId.toString() : null);
        envelope.put("subjectId", subjectId != null ? subjectId.toString() : null);
        envelope.put("payload", payload);

        try {
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(topic, key, json);
            log.info("Published RAG event {} to topic {}", eventType, topic);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize RAG event envelope: {}", e.getMessage());
        }
    }
}
