package com.nexaai.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * NexaAI API Gateway — the platform's single public entry point.
 *
 * <p>An independent Spring Boot application. It shares no code, no parent POM and no database
 * with any other service.
 *
 * <p><strong>What it does:</strong> terminates TLS, authenticates a token, applies edge
 * policy, and routes. That is the whole list.
 *
 * <p><strong>What it deliberately does not do:</strong>
 * <ul>
 *   <li>No business logic. A gateway that decides anything about a chat, a document or a
 *       subscription has become a second, divergent implementation of that service.</li>
 *   <li>No Auth Service logic. It verifies a token; it never issues, refreshes or revokes one.
 *       Delegating verification to Auth Service over HTTP would put Auth Service on the
 *       critical path of every request in the platform.</li>
 *   <li>No database. Not a connection pool, not a read replica, not a cache of somebody's
 *       tables. It owns nothing and stores nothing.</li>
 *   <li>No shared business module. The JWT verifier here is a deliberate duplicate of the one
 *       in auth-service and user-service; a shared jar would be the disguised-monolith path
 *       ({@code docs/RULES.md} §2).</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}