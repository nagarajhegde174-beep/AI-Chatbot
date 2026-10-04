package com.nexaai.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Auth Service — an independent Spring Boot application.
 *
 * <p>Credentials, token issuance, refresh, revocation, email verification, password
 * reset and account status. It owns {@code nexa_auth} and nothing else.
 *
 * <p>This is the only service in NexaAI that ever handles a password. No password
 * hash, and no token value, ever leaves this service in any DTO
 * ({@code docs/SERVICE_CONTRACTS.md} section 3.4).
 *
 * <p>User profile business logic does NOT belong here. User Service owns profiles and
 * learns of an account from {@code auth.user.registered.v1}. Anything in this service
 * that starts to look like a profile is in the wrong service.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}