package com.nexaai.ai.service;

import com.nexaai.ai.domain.AiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Publishes the ai.inference.completed.v1 Kafka event.
 *
 * IMPORTANT: the event carries counts and latency only.
 * It MUST NOT carry prompt content, completion content, or API keys.
 * This is enforced here by construction — no String content field exists in
 * the payload.
 */
@Component
public class InferenceEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InferenceEventPublisher.class);
    private static final String TOPIC = "ai.inference.completed.v1";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public InferenceEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publish a metering event. Failures are logged but never re-thrown —
     * the inference already completed; a metering miss does not justify
     * failing the caller.
     */
    public void publish(
            String requestId,
            AiProvider provider,
            String model,
            int promptTokens,
            int completionTokens,
            long latencyMs,
            boolean success
    ) {
        try {
            Map<String, Object> event = Map.of(
                    "requestId",         requestId,
                    "provider",          provider.name(),
                    "model",             model,
                    "promptTokens",      promptTokens,
                    "completionTokens",  completionTokens,
                    "totalTokens",       promptTokens + completionTokens,
                    "latencyMs",         latencyMs,
                    "success",           success,
                    "occurredAt",        Instant.now().toString()
            );
            kafkaTemplate.send(TOPIC, requestId, event);
            log.debug("Published {} event for requestId={}", TOPIC, requestId);
        } catch (Exception e) {
            log.error("Failed to publish {} event for requestId={}: {}", TOPIC, requestId, e.getMessage());
        }
    }
}
