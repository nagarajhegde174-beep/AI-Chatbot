package com.nexaai.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexaai.rag.domain.ProcessedEvent;
import com.nexaai.rag.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RagKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(RagKafkaConsumer.class);

    private final RagIngestionService ingestionService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    public RagKafkaConsumer(
            RagIngestionService ingestionService,
            ProcessedEventRepository processedEventRepository,
            ObjectMapper objectMapper
    ) {
        this.ingestionService = ingestionService;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "document.chunked.v1", groupId = "${spring.kafka.consumer.group-id:nexa.rag-service.events}")
    @Transactional
    public void onDocumentChunked(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            UUID eventId = UUID.fromString(root.path("eventId").asText());

            if (processedEventRepository.existsByEventId(eventId)) {
                log.info("Duplicate event {} ignored (already processed).", eventId);
                return;
            }

            JsonNode payload = root.path("payload");
            UUID documentId = UUID.fromString(payload.path("documentId").asText());
            UUID userId = UUID.fromString(payload.path("userId").asText());
            String title = payload.path("title").asText("document");
            String correlationId = root.path("correlationId").asText(null);

            JsonNode chunksNode = payload.path("chunks");
            List<String> chunks = new ArrayList<>();
            if (chunksNode.isArray()) {
                for (JsonNode chunkNode : chunksNode) {
                    chunks.add(chunkNode.path("text").asText(""));
                }
            }

            ingestionService.ingestChunks(userId, documentId, title, chunks, correlationId);
            processedEventRepository.save(new ProcessedEvent(eventId, "document.chunked.v1"));
            log.info("Successfully processed document.chunked.v1 event for document {}", documentId);

        } catch (Exception e) {
            log.error("Failed to process document.chunked.v1 event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "document.deleted.v1", groupId = "${spring.kafka.consumer.group-id:nexa.rag-service.events}")
    @Transactional
    public void onDocumentDeleted(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            UUID eventId = UUID.fromString(root.path("eventId").asText());

            if (processedEventRepository.existsByEventId(eventId)) {
                return;
            }

            JsonNode payload = root.path("payload");
            UUID documentId = UUID.fromString(payload.path("documentId").asText());
            UUID userId = UUID.fromString(payload.path("userId").asText());

            ingestionService.deleteDocumentVectors(userId, documentId);
            processedEventRepository.save(new ProcessedEvent(eventId, "document.deleted.v1"));
            log.info("Successfully processed document.deleted.v1 event for document {}", documentId);

        } catch (Exception e) {
            log.error("Failed to process document.deleted.v1 event: {}", e.getMessage(), e);
        }
    }
}
