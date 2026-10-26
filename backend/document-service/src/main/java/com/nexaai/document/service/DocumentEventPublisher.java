package com.nexaai.document.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexaai.document.dto.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DocumentEventPublisher.class);

    public static final String TOPIC_DOCUMENT_UPLOADED = "document.uploaded.v1";
    public static final String TOPIC_DOCUMENT_CHUNKED = "document.chunked.v1";
    public static final String TOPIC_DOCUMENT_DELETED = "document.deleted.v1";
    public static final String TOPIC_DOCUMENT_FAILED = "document.processing.failed.v1";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public DocumentEventPublisher(
            @Autowired(required = false) KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishDocumentUploaded(UUID documentId, UUID userId, String filename, String contentType, long byteSize, String storageUri, String correlationId) {
        DocumentUploadedPayload payload = new DocumentUploadedPayload(
                documentId,
                userId,
                filename,
                contentType,
                byteSize,
                storageUri,
                Instant.now()
        );
        send(TOPIC_DOCUMENT_UPLOADED, documentId.toString(), "document.uploaded.v1", userId, documentId, correlationId, payload);
    }

    public void publishDocumentChunked(UUID documentId, UUID userId, String title, List<DocumentChunkedPayload.ChunkItem> chunks, String correlationId) {
        DocumentChunkedPayload payload = new DocumentChunkedPayload(
                documentId,
                userId,
                title,
                chunks.size(),
                chunks
        );
        send(TOPIC_DOCUMENT_CHUNKED, documentId.toString(), "document.chunked.v1", userId, documentId, correlationId, payload);
    }

    public void publishDocumentDeleted(UUID documentId, UUID userId, String correlationId) {
        DocumentDeletedPayload payload = new DocumentDeletedPayload(documentId, userId);
        send(TOPIC_DOCUMENT_DELETED, documentId.toString(), "document.deleted.v1", userId, documentId, correlationId, payload);
    }

    public void publishDocumentFailed(UUID documentId, UUID userId, String reasonCode, String failureReason, String correlationId) {
        DocumentProcessingFailedPayload payload = new DocumentProcessingFailedPayload(
                documentId,
                userId,
                reasonCode,
                failureReason
        );
        send(TOPIC_DOCUMENT_FAILED, documentId.toString(), "document.processing.failed.v1", userId, documentId, correlationId, payload);
    }

    private <T> void send(String topic, String key, String eventType, UUID actorId, UUID subjectId, String correlationId, T payload) {
        if (kafkaTemplate == null) {
            log.debug("KafkaTemplate is not configured (e.g. running in test profile). Skipping event publication: {}", eventType);
            return;
        }

        DocumentEventEnvelope<T> envelope = DocumentEventEnvelope.create(eventType, actorId, subjectId, correlationId, payload);
        try {
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(topic, key, json).whenComplete((res, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish event {} to topic {}: {}", eventType, topic, ex.getMessage());
                } else {
                    log.info("Successfully published event {} to topic {} with key {}", eventType, topic, key);
                }
            });
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize Kafka event envelope {}: {}", eventType, e.getMessage(), e);
        }
    }
}
