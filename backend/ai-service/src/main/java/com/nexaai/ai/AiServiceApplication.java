package com.nexaai.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the NexaAI AI Service.
 *
 * <p>Owns provider adapters for OpenAI, Google Gemini and Groq/LLaMA, the model catalog, prompt templates and token metering. It holds no database: history and context arrive in the request and provider keys come from the environment.
 *
 * <p>This application is deployed, scaled and versioned on its own. It is not a module of a
 * larger backend and it never connects to a database owned by another service.
 * See docs/SERVICE_CONTRACTS.md for the contracts owned here.
 */
@SpringBootApplication
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}