package com.nexaai.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the NexaAI Auth Service.
 *
 * <p>Owns: credentials, password hashing, account status, refresh-token rotation and
 * access-token issuance. It is the only service allowed to write to the {@code nexa_auth}
 * database. See docs/SERVICE_CONTRACTS.md for the REST and Kafka contracts owned here.
 */
@SpringBootApplication
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
