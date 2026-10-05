package com.nexaai.chat.client;

import com.nexaai.chat.config.ChatProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds exactly one {@link ChatGenerationPort}.
 *
 * <p>Registered only while generation is disabled, so it backs off the moment a real port
 * appears. That conditional ordering is the whole mechanism: adding an implementation in a later
 * phase plus setting {@code nexa.chat.generation.enabled=true} replaces this without touching any
 * other part of Chat Service.
 */
@Configuration
public class ChatGenerationConfig {

    @Bean
    @ConditionalOnProperty(prefix = "nexa.chat.generation", name = "enabled",
            havingValue = "false", matchIfMissing = true)
    @ConditionalOnMissingBean(ChatGenerationPort.class)
    public ChatGenerationPort disabledGenerationPort(ChatProperties properties) {
        return new DisabledChatGenerationPort(properties);
    }
}