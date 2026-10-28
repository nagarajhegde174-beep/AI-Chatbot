package com.nexaai.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexaai.analytics.domain.*;
import com.nexaai.analytics.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class AnalyticsKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsKafkaConsumer.class);

    private final ProcessedEventRepository processedEventRepository;
    private final AiRequestMetricRepository metricRepository;
    private final UserActivityReadModelRepository userActivityRepository;
    private final DocumentReadModelRepository documentRepository;
    private final ConversationQueryLogRepository queryLogRepository;
    private final ModelRegistryStateRepository modelRepository;
    private final AiCostEstimator costEstimator;
    private final ObjectMapper objectMapper;

    public AnalyticsKafkaConsumer(ProcessedEventRepository processedEventRepository,
                                  AiRequestMetricRepository metricRepository,
                                  UserActivityReadModelRepository userActivityRepository,
                                  DocumentReadModelRepository documentRepository,
                                  ConversationQueryLogRepository queryLogRepository,
                                  ModelRegistryStateRepository modelRepository,
                                  AiCostEstimator costEstimator,
                                  ObjectMapper objectMapper) {
        this.processedEventRepository = processedEventRepository;
        this.metricRepository = metricRepository;
        this.userActivityRepository = userActivityRepository;
        this.documentRepository = documentRepository;
        this.queryLogRepository = queryLogRepository;
        this.modelRepository = modelRepository;
        this.costEstimator = costEstimator;
        this.objectMapper = objectMapper;
    }

    /**
     * Core idempotent processing logic callable from Kafka listener or test hooks.
     */
    public boolean processEventJson(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventIdStr = root.path("eventId").asText(null);
            if (eventIdStr == null || eventIdStr.isBlank()) {
                log.warn("Analytics event missing eventId, skipping");
                return false;
            }

            UUID eventId = UUID.fromString(eventIdStr);
            if (processedEventRepository.existsById(eventId)) {
                log.debug("Event {} already processed, skipping duplicate", eventId);
                return false;
            }

            String eventType = root.path("eventType").asText("");
            JsonNode payload = root.path("payload");
            String actorId = root.path("actorId").asText(null);
            String subjectId = root.path("subjectId").asText(null);

            dispatch(eventType, payload, actorId, subjectId);

            processedEventRepository.save(new ProcessedEvent(eventId, eventType, Instant.now()));
            return true;
        } catch (Exception e) {
            log.error("Failed to process analytics event message: {}", e.getMessage(), e);
            return false;
        }
    }

    private void dispatch(String eventType, JsonNode payload, String actorId, String subjectId) {
        switch (eventType) {
            case "auth.user.login.v1", "USER_LOGIN" -> handleUserLogin(payload, actorId);
            case "auth.user.registered.v1", "USER_REGISTERED" -> handleUserRegistered(payload, actorId);
            case "user.status.changed.v1", "USER_STATUS_CHANGED" -> handleUserStatusChanged(payload, subjectId);
            case "chat.message.sent.v1", "MESSAGE_SENT" -> handleChatMessageSent(payload, actorId);
            case "ai.inference.completed.v1", "AI_RESPONSE_GENERATED" -> handleAiInferenceCompleted(payload, actorId);
            case "document.uploaded.v1", "DOCUMENT_READY" -> handleDocumentUploaded(payload, actorId);
            case "document.chunked.v1", "DOCUMENT_CHUNKED" -> handleDocumentChunked(payload);
            case "document.indexed.v1", "DOCUMENT_INDEXED" -> handleDocumentIndexed(payload);
            case "document.processing.failed.v1", "DOCUMENT_PROCESSING_FAILED" -> handleDocumentFailed(payload);
            case "document.deleted.v1", "DOCUMENT_DELETED" -> handleDocumentDeleted(payload);
            case "subscription.changed.v1", "SUBSCRIPTION_CHANGED" -> handleSubscriptionChanged(payload, actorId);
            default -> log.debug("Unhandled event type for analytics read model: {}", eventType);
        }
    }

    private void handleUserLogin(JsonNode p, String actorId) {
        UUID userId = parseUUID(actorId != null ? actorId : p.path("userId").asText(null));
        if (userId == null) return;

        UserActivityReadModel user = userActivityRepository.findById(userId)
                .orElse(new UserActivityReadModel(userId, p.path("email").asText(null), "ACTIVE", "FREE", 100000L, 0L, 0L, null, null, Instant.now()));

        user.setLastLoginAt(Instant.now());
        if (p.hasNonNull("ip")) user.setLastLoginIp(p.get("ip").asText());
        userActivityRepository.save(user);
    }

    private void handleUserRegistered(JsonNode p, String actorId) {
        UUID userId = parseUUID(actorId != null ? actorId : p.path("userId").asText(null));
        if (userId == null) return;

        String email = p.path("email").asText(null);
        String tier = p.path("tier").asText("FREE");
        long quota = p.path("tokenQuota").asLong(100000L);

        UserActivityReadModel user = new UserActivityReadModel(userId, email, "ACTIVE", tier, quota, 0L, 0L, null, null, Instant.now());
        userActivityRepository.save(user);
    }

    private void handleUserStatusChanged(JsonNode p, String subjectId) {
        UUID userId = parseUUID(subjectId != null ? subjectId : p.path("userId").asText(null));
        if (userId == null) return;

        userActivityRepository.findById(userId).ifPresent(u -> {
            u.setStatus(p.path("status").asText(u.getStatus()).toUpperCase());
            userActivityRepository.save(u);
        });
    }

    private void handleChatMessageSent(JsonNode p, String actorId) {
        UUID userId = parseUUID(actorId != null ? actorId : p.path("userId").asText(null));
        UUID convId = parseUUID(p.path("conversationId").asText(null));
        if (userId == null || convId == null) return;

        String model = p.path("model").asText("gpt-4o-mini");
        int tokens = p.path("tokens").asInt(0);
        int latency = p.path("latencyMs").asInt(0);

        queryLogRepository.save(new ConversationQueryLog(
                UUID.randomUUID(), userId, convId, model, tokens, latency, "SUCCESS", Instant.now()));
    }

    private void handleAiInferenceCompleted(JsonNode p, String actorId) {
        UUID userId = parseUUID(actorId != null ? actorId : p.path("userId").asText(null));
        if (userId == null) return;

        UUID convId = parseUUID(p.path("conversationId").asText(null));
        UUID msgId = parseUUID(p.path("messageId").asText(null));
        String provider = p.path("provider").asText("OPENAI");
        String model = p.path("model").asText("gpt-4o-mini");
        long promptTokens = p.path("promptTokens").asLong(0L);
        long completionTokens = p.path("completionTokens").asLong(0L);
        long totalTokens = p.path("totalTokens").asLong(promptTokens + completionTokens);
        long latencyMs = p.path("latencyMs").asLong(0L);
        String status = p.path("status").asText("SUCCESS");
        String errorCode = p.path("errorCode").asText(null);

        BigDecimal cost = costEstimator.estimateCostUsd(model, promptTokens, completionTokens);

        AiRequestMetric metric = new AiRequestMetric(
                UUID.randomUUID(), userId, convId, msgId, provider, model,
                promptTokens, completionTokens, totalTokens, latencyMs, cost, status, errorCode, Instant.now());
        metricRepository.save(metric);

        // Update Model registry stats
        ModelRegistryState modelState = modelRepository.findById(model)
                .orElse(new ModelRegistryState(model, provider, true, false, 0, 0, 0, Instant.now()));
        modelState.setTotalRequests(modelState.getTotalRequests() + 1);
        modelState.setTotalTokens(modelState.getTotalTokens() + totalTokens);
        if (!"SUCCESS".equalsIgnoreCase(status)) {
            modelState.setTotalErrors(modelState.getTotalErrors() + 1);
        }
        modelState.setLastUsedAt(Instant.now());
        modelRepository.save(modelState);

        // Update User profile aggregated tokens & requests
        userActivityRepository.findById(userId).ifPresent(u -> {
            u.setTotalRequests(u.getTotalRequests() + 1);
            u.setTokensUsed(u.getTokensUsed() + totalTokens);
            userActivityRepository.save(u);
        });
    }

    private void handleDocumentUploaded(JsonNode p, String actorId) {
        UUID docId = parseUUID(p.path("documentId").asText(null));
        UUID userId = parseUUID(actorId != null ? actorId : p.path("userId").asText(null));
        if (docId == null || userId == null) return;

        String title = p.path("title").asText("Untitled Document");
        long size = p.path("fileSizeBytes").asLong(0L);

        DocumentReadModel doc = new DocumentReadModel(
                docId, userId, title, size, "UPLOADED", 0, 0, null, Instant.now(), Instant.now());
        documentRepository.save(doc);
    }

    private void handleDocumentChunked(JsonNode p) {
        UUID docId = parseUUID(p.path("documentId").asText(null));
        if (docId == null) return;

        documentRepository.findById(docId).ifPresent(d -> {
            d.setStatus("CHUNKED");
            d.setChunkCount(p.path("chunkCount").asInt(d.getChunkCount()));
            d.setUpdatedAt(Instant.now());
            documentRepository.save(d);
        });
    }

    private void handleDocumentIndexed(JsonNode p) {
        UUID docId = parseUUID(p.path("documentId").asText(null));
        if (docId == null) return;

        documentRepository.findById(docId).ifPresent(d -> {
            d.setStatus("INDEXED");
            d.setEmbeddingCount(p.path("vectorCount").asInt(p.path("chunkCount").asInt(d.getChunkCount())));
            d.setUpdatedAt(Instant.now());
            documentRepository.save(d);
        });
    }

    private void handleDocumentFailed(JsonNode p) {
        UUID docId = parseUUID(p.path("documentId").asText(null));
        if (docId == null) return;

        documentRepository.findById(docId).ifPresent(d -> {
            d.setStatus("FAILED");
            d.setErrorMessage(p.path("error").asText("Processing failed"));
            d.setUpdatedAt(Instant.now());
            documentRepository.save(d);
        });
    }

    private void handleDocumentDeleted(JsonNode p) {
        UUID docId = parseUUID(p.path("documentId").asText(null));
        if (docId == null) return;

        documentRepository.findById(docId).ifPresent(d -> {
            d.setStatus("DELETED");
            d.setUpdatedAt(Instant.now());
            documentRepository.save(d);
        });
    }

    private void handleSubscriptionChanged(JsonNode p, String actorId) {
        UUID userId = parseUUID(actorId != null ? actorId : p.path("userId").asText(null));
        if (userId == null) return;

        userActivityRepository.findById(userId).ifPresent(u -> {
            if (p.hasNonNull("plan")) u.setSubscriptionTier(p.get("plan").asText());
            if (p.hasNonNull("tokenQuota")) u.setTokenQuota(p.get("tokenQuota").asLong());
            userActivityRepository.save(u);
        });
    }

    private UUID parseUUID(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            return UUID.fromString(str.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
