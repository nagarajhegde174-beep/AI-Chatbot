package com.nexaai.document.controller;

import com.nexaai.document.dto.DocumentContentResponse;
import com.nexaai.document.dto.DocumentResponse;
import com.nexaai.document.security.UserPrincipal;
import com.nexaai.document.service.DocumentService;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * Upload document (PDF, DOCX, TXT, Markdown, CSV).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> uploadDocument(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        DocumentResponse response = documentService.uploadDocument(principal.getUserId(), file, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Document library: list own documents with pagination and optional query.
     */
    @GetMapping
    public ResponseEntity<Page<DocumentResponse>> listDocuments(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(value = "q", required = false) String query,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<DocumentResponse> page = documentService.listDocuments(principal.getUserId(), query, pageable);
        return ResponseEntity.ok(page);
    }

    /**
     * Get document metadata.
     */
    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocument(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") UUID id
    ) {
        DocumentResponse response = documentService.getDocument(id, principal.getUserId(), principal.isAdmin());
        return ResponseEntity.ok(response);
    }

    /**
     * Get extracted text content and chunks.
     */
    @GetMapping("/{id}/content")
    public ResponseEntity<DocumentContentResponse> getDocumentContent(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") UUID id
    ) {
        DocumentContentResponse response = documentService.getDocumentContent(id, principal.getUserId(), principal.isAdmin());
        return ResponseEntity.ok(response);
    }

    /**
     * Download original file securely with attachment header.
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadDocument(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") UUID id
    ) throws IOException {
        DocumentService.StoredFileDownload download = documentService.getDocumentForDownload(id, principal.getUserId(), principal.isAdmin());

        String encodedFilename = URLEncoder.encode(download.originalFilename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.originalFilename() + "\"; filename*=UTF-8''" + encodedFilename)
                .body(download.resource());
    }

    /**
     * Reprocess an existing document: re-extract text, re-chunk and republish.
     */
    @PostMapping("/{id}/reprocess")
    public ResponseEntity<DocumentResponse> reprocessDocument(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") UUID id,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        DocumentResponse response = documentService.reprocessDocument(id, principal.getUserId(), principal.isAdmin(), correlationId);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete document and its stored file and chunks.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") UUID id,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId
    ) {
        documentService.deleteDocument(id, principal.getUserId(), principal.isAdmin(), correlationId);
        return ResponseEntity.noContent().build();
    }
}
