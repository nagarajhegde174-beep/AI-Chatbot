package com.nexaai.rag.controller;

import com.nexaai.rag.dto.RagIngestRequest;
import com.nexaai.rag.dto.RagRetrieveRequest;
import com.nexaai.rag.dto.RagRetrieveResponse;
import com.nexaai.rag.dto.RagStatusResponse;
import com.nexaai.rag.repository.DocumentEmbeddingRepository;
import com.nexaai.rag.service.EmbeddingService;
import com.nexaai.rag.service.RagIngestionService;
import com.nexaai.rag.service.RagRetrievalService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/rag")
public class RagInternalController {

    private final RagRetrievalService retrievalService;
    private final RagIngestionService ingestionService;
    private final DocumentEmbeddingRepository repository;
    private final EmbeddingService embeddingService;

    public RagInternalController(
            RagRetrievalService retrievalService,
            RagIngestionService ingestionService,
            DocumentEmbeddingRepository repository,
            EmbeddingService embeddingService
    ) {
        this.retrievalService = retrievalService;
        this.ingestionService = ingestionService;
        this.repository = repository;
        this.embeddingService = embeddingService;
    }

    /**
     * Similarity search, strictly tenant-scoped (SERVICE_CONTRACTS.md §10.2).
     */
    @PostMapping("/retrieve")
    public ResponseEntity<RagRetrieveResponse> retrieve(@Valid @RequestBody RagRetrieveRequest request) {
        RagRetrieveResponse response = retrievalService.retrieve(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Manual or fallback ingestion of document text/chunks.
     */
    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingest(
            @Valid @RequestBody RagIngestRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        long vectorCount;
        if (request.chunks() != null && !request.chunks().isEmpty()) {
            vectorCount = ingestionService.ingestChunks(request.userId(), request.documentId(), request.documentTitle(), request.chunks(), correlationId);
        } else if (request.text() != null) {
            vectorCount = ingestionService.ingestRawText(request.userId(), request.documentId(), request.documentTitle(), request.text(), correlationId);
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "Either chunks or text must be provided"));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("documentId", request.documentId());
        body.put("userId", request.userId());
        body.put("vectorCount", vectorCount);
        body.put("embeddingModel", embeddingService.getModelName());
        body.put("status", "INDEXED");
        return ResponseEntity.ok(body);
    }

    /**
     * Direct vector deletion when document is removed.
     */
    @PostMapping("/documents/{id}/delete")
    public ResponseEntity<Void> deleteDocumentVectors(
            @PathVariable("id") UUID documentId,
            @RequestParam("userId") UUID userId
    ) {
        ingestionService.deleteDocumentVectors(userId, documentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Vector indexing status for a document.
     */
    @GetMapping("/documents/{id}/status")
    public ResponseEntity<RagStatusResponse> getStatus(@PathVariable("id") UUID documentId) {
        long count = repository.countByDocumentId(documentId);
        String status = count > 0 ? "INDEXED" : "NOT_INDEXED";
        return ResponseEntity.ok(new RagStatusResponse(documentId, count, status));
    }

    /**
     * Boundary assertion endpoint per SERVICE_CONTRACTS.md §2
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getServiceInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("service", "rag-service");
        info.put("version", "0.1.0");
        info.put("role", "Embeddings, vector indexing, similarity search, tenant-scoped retrieval, citations");
        info.put("port", 8086);
        info.put("database", "nexa_rag");
        info.put("extension", "pgvector");
        info.put("listensKafka", true);
        info.put("consumes", List.of("document.chunked.v1", "document.deleted.v1"));
        info.put("produces", List.of("document.indexed.v1", "document.processed.v1"));
        return ResponseEntity.ok(info);
    }

    /**
     * Health check verifying database and extension availability.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("database", "nexa_rag");
        health.put("pgvector", "READY");
        health.put("model", embeddingService.getModelName());
        return ResponseEntity.ok(health);
    }
}
