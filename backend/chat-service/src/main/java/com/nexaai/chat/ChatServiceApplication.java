package com.nexaai.chat;

import com.nexaai.chat.config.ChatProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Chat Service.
 *
 * <p>An independent Spring Boot application. It shares no code, no parent POM and no database
 * with any other NexaAI service.
 *
 * <p><strong>Conversations are the sessions.</strong> There is no separate session table: a
 * session IS a conversation, and a second table would let the two disagree about what exists.
 *
 * <p><strong>It does not read User Service's database.</strong> Where a user detail is genuinely
 * needed it crosses an HTTP boundary behind an interface. That boundary is built and disabled,
 * because retrofitting it later is the expensive order.
 */
@SpringBootApplication
@EnableConfigurationProperties(ChatProperties.class)
public class ChatServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatServiceApplication.class, args);
    }

    /**
     * The HTTP client used to cross to other services.
     *
     * <p>Declared explicitly because this application has both the servlet and WebFlux starters
     * on the classpath — WebFlux is present for {@code WebClient} alone — and an explicit bean
     * removes any doubt about which auto-configuration supplies the builder.
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}