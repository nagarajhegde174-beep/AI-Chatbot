package com.nexaai.document;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the NexaAI Document Service.
 *
 * <p>Owns the document aggregate, binary storage, text extraction and chunking. It publishes chunks and never embeds them; embedding belongs to the RAG Service.
 *
 * <p>This application is deployed, scaled and versioned on its own. It is not a module of a
 * larger backend and it never connects to a database owned by another service.
 * See docs/SERVICE_CONTRACTS.md for the contracts owned here.
 */
@SpringBootApplication
public class DocumentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumentServiceApplication.class, args);
    }
}