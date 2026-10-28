package com.nexaai.analytics;

import com.nexaai.analytics.domain.AiRequestMetric;
import com.nexaai.analytics.domain.DocumentReadModel;
import com.nexaai.analytics.domain.UserActivityReadModel;
import com.nexaai.analytics.repository.*;
import com.nexaai.analytics.service.AnalyticsKafkaConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {"nexa.analytics.kafka.enabled=false", "spring.kafka.listener.auto-startup=false"})
class AnalyticsConsumerTest {

    @Autowired
    private AnalyticsKafkaConsumer consumer;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private AiRequestMetricRepository metricRepository;

    @Autowired
    private UserActivityReadModelRepository userRepository;

    @Autowired
    private DocumentReadModelRepository documentRepository;

    @BeforeEach
    void setUp() {
        metricRepository.deleteAll();
        documentRepository.deleteAll();
        userRepository.deleteAll();
        processedEventRepository.deleteAll();
    }

    @Test
    @DisplayName("Idempotent event processing skips duplicate eventId")
    void deduplicatesOnEventId() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String json = """
                {
                  "eventId": "%s",
                  "eventType": "auth.user.registered.v1",
                  "occurredAt": "2026-10-28T12:00:00Z",
                  "actorId": "%s",
                  "payload": {
                    "email": "test@example.com",
                    "tier": "FREE",
                    "tokenQuota": 100000
                  }
                }
                """.formatted(eventId, userId);

        boolean first = consumer.processEventJson(json);
        assertTrue(first, "First event should be processed");
        assertEquals(1, userRepository.count());

        boolean second = consumer.processEventJson(json);
        assertFalse(second, "Duplicate event must be skipped");
        assertEquals(1, userRepository.count());
        assertEquals(1, processedEventRepository.count());
    }

    @Test
    @DisplayName("Inference completed event updates AiRequestMetric, model usage, and user tokens")
    void handlesAiInferenceCompleted() {
        UUID userId = UUID.randomUUID();
        // Register user first
        userRepository.save(new UserActivityReadModel(userId, "bob@example.com", "ACTIVE", "PRO", 500000L, 0L, 0L, null, null, null));

        String json = """
                {
                  "eventId": "%s",
                  "eventType": "ai.inference.completed.v1",
                  "occurredAt": "2026-10-28T12:05:00Z",
                  "actorId": "%s",
                  "payload": {
                    "conversationId": "%s",
                    "messageId": "%s",
                    "provider": "OPENAI",
                    "model": "gpt-4o-mini",
                    "promptTokens": 300,
                    "completionTokens": 100,
                    "totalTokens": 400,
                    "latencyMs": 350,
                    "status": "SUCCESS"
                  }
                }
                """.formatted(UUID.randomUUID(), userId, UUID.randomUUID(), UUID.randomUUID());

        boolean processed = consumer.processEventJson(json);
        assertTrue(processed);

        List<AiRequestMetric> metrics = metricRepository.findByUserId(userId);
        assertEquals(1, metrics.size());
        assertEquals(400, metrics.get(0).getTotalTokens());
        assertEquals(350, metrics.get(0).getLatencyMs());
        assertNotNull(metrics.get(0).getEstimatedCostUsd());

        UserActivityReadModel updatedUser = userRepository.findById(userId).orElseThrow();
        assertEquals(400, updatedUser.getTokensUsed());
        assertEquals(1, updatedUser.getTotalRequests());
    }

    @Test
    @DisplayName("Document uploaded, chunked, and indexed events update DocumentReadModel")
    void handlesDocumentLifecycleEvents() {
        UUID docId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // 1. Uploaded
        String uploadJson = """
                {
                  "eventId": "%s",
                  "eventType": "document.uploaded.v1",
                  "actorId": "%s",
                  "payload": {
                    "documentId": "%s",
                    "title": "guide.pdf",
                    "fileSizeBytes": 50000
                  }
                }
                """.formatted(UUID.randomUUID(), userId, docId);
        consumer.processEventJson(uploadJson);

        DocumentReadModel doc = documentRepository.findById(docId).orElseThrow();
        assertEquals("UPLOADED", doc.getStatus());
        assertEquals(50000, doc.getFileSizeBytes());

        // 2. Chunked
        String chunkedJson = """
                {
                  "eventId": "%s",
                  "eventType": "document.chunked.v1",
                  "payload": {
                    "documentId": "%s",
                    "chunkCount": 8
                  }
                }
                """.formatted(UUID.randomUUID(), docId);
        consumer.processEventJson(chunkedJson);

        doc = documentRepository.findById(docId).orElseThrow();
        assertEquals("CHUNKED", doc.getStatus());
        assertEquals(8, doc.getChunkCount());

        // 3. Indexed
        String indexedJson = """
                {
                  "eventId": "%s",
                  "eventType": "document.indexed.v1",
                  "payload": {
                    "documentId": "%s",
                    "vectorCount": 8
                  }
                }
                """.formatted(UUID.randomUUID(), docId);
        consumer.processEventJson(indexedJson);

        doc = documentRepository.findById(docId).orElseThrow();
        assertEquals("INDEXED", doc.getStatus());
        assertEquals(8, doc.getEmbeddingCount());
    }
}
