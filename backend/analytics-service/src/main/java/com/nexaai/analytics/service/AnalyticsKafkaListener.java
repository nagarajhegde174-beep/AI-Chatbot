package com.nexaai.analytics.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "nexa.analytics.kafka.enabled", havingValue = "true")
public class AnalyticsKafkaListener {

    private final AnalyticsKafkaConsumer consumer;

    public AnalyticsKafkaListener(AnalyticsKafkaConsumer consumer) {
        this.consumer = consumer;
    }

    @KafkaListener(
            topics = {
                    "auth.user.login.v1",
                    "auth.user.registered.v1",
                    "user.status.changed.v1",
                    "chat.message.sent.v1",
                    "ai.inference.completed.v1",
                    "document.uploaded.v1",
                    "document.chunked.v1",
                    "document.indexed.v1",
                    "document.processing.failed.v1",
                    "document.deleted.v1",
                    "subscription.changed.v1"
            },
            groupId = "${spring.kafka.consumer.group-id:analytics-service-group}",
            autoStartup = "${nexa.analytics.kafka.enabled:false}"
    )
    public void onEvent(String message) {
        consumer.processEventJson(message);
    }
}
