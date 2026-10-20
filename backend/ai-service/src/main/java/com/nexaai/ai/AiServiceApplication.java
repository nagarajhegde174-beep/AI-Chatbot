package com.nexaai.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI Service — stateless multi-model inference through Spring AI.
 *
 * Rules:
 * - No datasource, no JPA, no Flyway.
 * - All provider credentials come from the environment; the service refuses to
 *   start if a configured provider has no key.
 * - No prompt content or API keys appear in logs, on any path including
 *   exception paths.
 */
@SpringBootApplication
@EnableScheduling
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
