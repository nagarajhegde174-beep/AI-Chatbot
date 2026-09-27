package com.nexaai.document.web;

import com.nexaai.document.web.dto.ServiceInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal boundary endpoint. Proves the Document Service is deployed as its own
 * application on its own port and knows only its own data. The real public API is
 * introduced in a later phase, see docs/TASKS.md.
 */
@RestController
@RequestMapping("/internal/v1/document")
@Tag(name = "internal", description = "Infrastructure and boundary endpoints, not for end users")
public class DocumentServiceInfoController {

    private final int port;

    public DocumentServiceInfoController(
            @Value("${server.port}") int port) {
        this.port = port;
    }

    @GetMapping("/info")
    @Operation(summary = "Describe this service's boundary")
    public ServiceInfoResponse info() {
        return new ServiceInfoResponse(
                "document-service",
                "File upload, text extraction, chunking and document metadata lifecycle.",
                port,
                "nexa_document",
                List.of("document.uploaded.v1", "document.chunked.v1"));
    }
}