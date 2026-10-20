package com.nexaai.ai;

import com.nexaai.ai.service.InferenceEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Verifies that InferenceEventPublisher publishes the correct fields and
 * — critically — NEVER publishes prompt content or completion content.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InferenceEventPublisher — no-content-leak tests")
class InferenceEventPublisherTest {

    @Mock KafkaTemplate<String, Object> kafkaTemplate;
    @Captor ArgumentCaptor<Object> payloadCaptor;

    @Test
    @DisplayName("Published event contains token counts and requestId — no content fields")
    void event_contains_counts_not_content() {
        var publisher = new InferenceEventPublisher(kafkaTemplate);
        publisher.publish("req-abc", com.nexaai.ai.domain.AiProvider.OPENAI,
                "gpt-4o-mini", 100, 50, 1200L, true);

        verify(kafkaTemplate).send(eq("ai.inference.completed.v1"), eq("req-abc"), payloadCaptor.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> event = (Map<String, Object>) payloadCaptor.getValue();

        // Required fields present
        assertThat(event).containsKey("requestId");
        assertThat(event).containsKey("provider");
        assertThat(event).containsKey("model");
        assertThat(event).containsKey("promptTokens");
        assertThat(event).containsKey("completionTokens");
        assertThat(event).containsKey("totalTokens");
        assertThat(event).containsKey("latencyMs");
        assertThat(event).containsKey("success");
        assertThat(event).containsKey("occurredAt");

        // Content fields MUST NOT appear
        assertThat(event).doesNotContainKey("promptContent");
        assertThat(event).doesNotContainKey("completionContent");
        assertThat(event).doesNotContainKey("content");
        assertThat(event).doesNotContainKey("prompt");
        assertThat(event).doesNotContainKey("message");
        assertThat(event).doesNotContainKey("apiKey");

        // Values are correct
        assertThat(event.get("requestId")).isEqualTo("req-abc");
        assertThat(event.get("provider")).isEqualTo("OPENAI");
        assertThat(event.get("promptTokens")).isEqualTo(100);
        assertThat(event.get("completionTokens")).isEqualTo(50);
        assertThat(event.get("totalTokens")).isEqualTo(150);
        assertThat(event.get("success")).isEqualTo(true);
    }

    @Test
    @DisplayName("Kafka failure does not propagate as an exception")
    void kafka_failure_is_swallowed() {
        var publisher = new InferenceEventPublisher(kafkaTemplate);
        when(kafkaTemplate.send(any(), any(), any())).thenThrow(new RuntimeException("Kafka down"));

        // Must not throw
        publisher.publish("req-fail", com.nexaai.ai.domain.AiProvider.OPENAI,
                "gpt-4o-mini", 0, 0, 0L, false);
    }
}
