package com.nexaai.document.controller;

import com.nexaai.document.dto.DocumentContentResponse;
import com.nexaai.document.service.DocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1")
public class DocumentInternalController {

    private final DocumentService documentService;

    public DocumentInternalController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * Boundary assertion endpoint per SERVICE_CONTRACTS.md §2
     */
    @GetMapping("/document/info")
    public ResponseEntity<Map<String, Object>> getServiceInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("service", "document-service");
        info.put("version", "0.1.0");
        info.put("role", "Upload, file validation, storage, text extraction, chunking, and document lifecycle");
        info.put("port", 8085);
        info.put("database", "nexa_document");
        info.put("listensKafka", false);
        info.put("consumes", List.of("none"));
        info.put("produces", List.of(
                "document.uploaded.v1",
                "document.chunked.v1",
                "document.deleted.v1",
                "document.processing.failed.v1"
        ));
        return ResponseEntity.ok(info);
    }

    /**
     * Internal endpoint for services (e.g., RAG service or AI service) to fetch extracted chunks
     */
    @GetMapping("/documents/{id}/content")
    public ResponseEntity<DocumentContentResponse> getInternalDocumentContent(@PathVariable("id") UUID id) {
        DocumentContentResponse response = documentService.getDocumentContent(id, null, true);
        return ResponseEntity.ok(response);
    }
}
