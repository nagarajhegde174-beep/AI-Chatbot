package com.nexaai.ai;

import com.nexaai.ai.config.AiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * AI Service.
 *
 * <p>An independent Spring Boot application. It shares no code, no parent POM and no database
 * with any other NexaAI service.
 *
 * <p><strong>Owns no database.</strong> A model router holds no conversation state and every
 * request carries what it needs, which is why CI rule R5 forbids a datasource here and there is
 * no JPA, no Flyway and no migration. It also means this service cannot lose data, because it
 * never held any.
 *
 * <p><strong>The only place a provider integration exists.</strong> Chat Service calls this one;
 * no provider SDK is called from anywhere else in the repository
 * ({@code docs/RULES.md} §7).
 *
 * <p><strong>Provider credentials live here and nowhere else.</strong> They are read from the
 * environment, never logged, never returned by an endpoint, and never reach the browser.
 */
@SpringBootApplication
@EnableConfigurationProperties(AiProperties.class)
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}