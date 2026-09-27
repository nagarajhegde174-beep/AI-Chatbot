package com.nexaai.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the NexaAI API Gateway.
 *
 * <p>Owns nothing but the edge: it is the single public entry point, terminates the client
 * connection, applies cross-cutting HTTP policy (routing, rate limiting, correlation ids,
 * CORS) and forwards the request to exactly one downstream service.
 *
 * <p>It holds no database, contains no business logic and never talks to another service
 * over REST or Kafka. Business rules stay in the owning service. Token verification is
 * added here in Phase 2, see docs/TASKS.md.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
