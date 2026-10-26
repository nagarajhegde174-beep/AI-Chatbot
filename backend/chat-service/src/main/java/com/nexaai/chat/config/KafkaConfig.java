package com.nexaai.chat.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@Profile("!test")
public class KafkaConfig {

    public static final String TOPIC_CONVERSATION_CREATED = "chat.conversation.created.v1";
    public static final String TOPIC_MESSAGE_SENT = "chat.message.sent.v1";
    public static final String TOPIC_MESSAGE_SENT_DLT = "chat.message.sent.v1.DLT";
    public static final String TOPIC_AI_REQUESTED = "ai.inference.requested.v1";

    @Bean
    public NewTopic chatConversationCreatedTopic() {
        return TopicBuilder.name(TOPIC_CONVERSATION_CREATED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic chatMessageSentTopic() {
        return TopicBuilder.name(TOPIC_MESSAGE_SENT)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic chatMessageSentDltTopic() {
        return TopicBuilder.name(TOPIC_MESSAGE_SENT_DLT)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic aiInferenceRequestedTopic() {
        return TopicBuilder.name(TOPIC_AI_REQUESTED)
                .partitions(6)
                .replicas(1)
                .build();
    }
}
