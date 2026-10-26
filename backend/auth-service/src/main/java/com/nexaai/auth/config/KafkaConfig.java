package com.nexaai.auth.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class KafkaConfig {

    public static final String TOPIC_USER_REGISTERED = "auth.user.registered.v1";
    public static final String TOPIC_USER_REGISTERED_DLT = "auth.user.registered.v1.DLT";
    public static final String TOPIC_USER_EMAIL_VERIFIED = "auth.user.email_verified.v1";
    public static final String TOPIC_USER_EMAIL_VERIFIED_DLT = "auth.user.email_verified.v1.DLT";
    public static final String TOPIC_USER_LOGIN = "auth.user.login.v1";

    @Bean
    public NewTopic userRegisteredTopic() {
        return TopicBuilder.name(TOPIC_USER_REGISTERED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic userRegisteredDltTopic() {
        return TopicBuilder.name(TOPIC_USER_REGISTERED_DLT)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic userEmailVerifiedTopic() {
        return TopicBuilder.name(TOPIC_USER_EMAIL_VERIFIED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic userEmailVerifiedDltTopic() {
        return TopicBuilder.name(TOPIC_USER_EMAIL_VERIFIED_DLT)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic userLoginTopic() {
        return TopicBuilder.name(TOPIC_USER_LOGIN)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
