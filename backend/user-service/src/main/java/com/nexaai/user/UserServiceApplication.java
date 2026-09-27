package com.nexaai.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the NexaAI User Service.
 *
 * <p>Owns the user profile aggregate, user preferences, role assignment and the administrative user API. It consumes auth.user.registered.v1 to provision a profile and never stores a password.
 *
 * <p>This application is deployed, scaled and versioned on its own. It is not a module of a
 * larger backend and it never connects to a database owned by another service.
 * See docs/SERVICE_CONTRACTS.md for the contracts owned here.
 */
@SpringBootApplication
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}