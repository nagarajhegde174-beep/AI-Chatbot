package com.nexaai.rag.controller;

import com.nexaai.rag.dto.RagRetrieveRequest;
import com.nexaai.rag.dto.RagRetrieveResponse;
import com.nexaai.rag.security.UserPrincipal;
import com.nexaai.rag.service.RagRetrievalService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rag")
public class RagSearchController {

    private final RagRetrievalService retrievalService;

    public RagSearchController(RagRetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    /**
     * User-facing semantic search across user documents with automatic tenant scoping.
     */
    @PostMapping("/search")
    public ResponseEntity<RagRetrieveResponse> search(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody SearchRequest searchRequest
    ) {
        RagRetrieveRequest request = new RagRetrieveRequest(
                principal.getUserId(),
                searchRequest.query(),
                searchRequest.topK(),
                searchRequest.documentIds(),
                searchRequest.minScore()
        );
        RagRetrieveResponse response = retrievalService.retrieve(request);
        return ResponseEntity.ok(response);
    }

    public record SearchRequest(
            @NotBlank(message = "query must not be blank")
            String query,
            Integer topK,
            List<UUID> documentIds,
            Double minScore
    ) {}
}
