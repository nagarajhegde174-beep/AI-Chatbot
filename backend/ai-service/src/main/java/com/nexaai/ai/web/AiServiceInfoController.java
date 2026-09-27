package com.nexaai.ai.web;

import com.nexaai.ai.web.dto.ServiceInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal boundary endpoint. Proves the AI Service is deployed as its own
 * application on its own port and knows only its own data. The real public API is
 * introduced in a later phase, see docs/TASKS.md.
 */
@RestController
@RequestMapping("/internal/v1/ai")
@Tag(name = "internal", description = "Infrastructure and boundary endpoints, not for end users")
public class AiServiceInfoController {

    private final int port;

    public AiServiceInfoController(
            @Value("${server.port}") int port) {
        this.port = port;
    }

    @GetMapping("/info")
    @Operation(summary = "Describe this service's boundary")
    public ServiceInfoResponse info() {
        return new ServiceInfoResponse(
                "ai-service",
                "Stateless model gateway: multi-provider LLM inference, model catalog and token metering.",
                port,
                "none - stateless, owns no database",
                List.of("ai.inference.completed.v1"));
    }
}