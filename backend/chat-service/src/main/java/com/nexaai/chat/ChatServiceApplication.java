package com.nexaai.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the NexaAI Chat Service.
 *
 * <p>Owns conversations and messages. It assembles the prompt from conversation memory and retrieved context, then streams the AI Service answer back to the caller as Server-Sent Events. It is the only service that writes conversation history.
 *
 * <p>This application is deployed, scaled and versioned on its own. It is not a module of a
 * larger backend and it never connects to a database owned by another service.
 * See docs/SERVICE_CONTRACTS.md for the contracts owned here.
 */
@SpringBootApplication
public class ChatServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatServiceApplication.class, args);
    }
}