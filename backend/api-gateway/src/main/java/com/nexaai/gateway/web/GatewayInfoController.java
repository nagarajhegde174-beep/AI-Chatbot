package com.nexaai.gateway.web;

import com.nexaai.gateway.web.dto.GatewayInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal boundary endpoint. Proves the gateway fans out to seven independent services and
 * owns no data of its own. Phase 0 keeps the edge unauthenticated on this path; Phase 2 adds
 * the token-verification filter chain described in docs/SECURITY.md.
 */
@RestController
@RequestMapping("/internal/v1/gateway")
@Tag(name = "internal", description = "Infrastructure and boundary endpoints, not for end users")
public class GatewayInfoController {

    private final int port;

    public GatewayInfoController(
            @Value("${server.port}") int port) {
        this.port = port;
    }

    @GetMapping("/info")
    @Operation(summary = "Describe which services this gateway routes to")
    public GatewayInfoResponse info() {
        return new GatewayInfoResponse(
                "api-gateway",
                "Edge only: terminate the client connection, apply cross-cutting HTTP policy "
                        + "and forward to exactly one owning service. No database, no business logic.",
                port,
                List.of(
                        "/api/v1/auth/**",
                        "/api/v1/users/**",
                        "/api/v1/chat/**",
                        "/api/v1/ai/**",
                        "/api/v1/documents/**",
                        "/api/v1/rag/**",
                        "/api/v1/subscriptions/**"),
                List.of(
                        "auth-service",
                        "user-service",
                        "chat-service",
                        "ai-service",
                        "document-service",
                        "rag-service",
                        "subscription-service"));
    }
}
