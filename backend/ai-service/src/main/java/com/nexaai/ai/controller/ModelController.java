package com.nexaai.ai.controller;

import com.nexaai.ai.dto.HealthResponse;
import com.nexaai.ai.dto.ModelCatalogResponse;
import com.nexaai.ai.service.ModelCatalogService;
import com.nexaai.ai.service.ProviderHealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Model catalogue and health endpoints.
 *
 * GET /internal/v1/ai/models   — available models per provider
 * GET /internal/v1/ai/health   — per-provider reachability
 * GET /internal/v1/ai/info     — boundary assertion (service identity)
 */
@RestController
@RequestMapping("/internal/v1/ai")
public class ModelController {

    private final ModelCatalogService catalogService;
    private final ProviderHealthService healthService;

    public ModelController(ModelCatalogService catalogService, ProviderHealthService healthService) {
        this.catalogService = catalogService;
        this.healthService = healthService;
    }

    /**
     * Returns the full model catalogue derived from application configuration.
     * Circuit-breaker state (available = false) is reflected in the response
     * so callers can filter the UI accordingly.
     */
    @GetMapping("/models")
    public Mono<ModelCatalogResponse> models() {
        return Mono.just(catalogService.catalogue());
    }

    /**
     * Per-provider reachability check. Performs a live 1-token probe.
     * Infrastructure, not browser — see SERVICE_CONTRACTS.md §8.1.
     */
    @GetMapping("/health")
    public Mono<HealthResponse> health() {
        return healthService.checkHealth();
    }

    /**
     * Service identity endpoint — confirms this is AI Service and its port.
     * Used by architectural rule checks (RULES.md §7).
     */
    @GetMapping("/info")
    public Mono<Map<String, Object>> info() {
        return Mono.just(Map.of(
                "service", "ai-service",
                "port", 8084,
                "stateless", true,
                "database", "none",
                "timestamp", Instant.now().toString()
        ));
    }
}
