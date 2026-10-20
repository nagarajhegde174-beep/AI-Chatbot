package com.nexaai.ai.dto;

import java.util.List;

/**
 * Response from GET /internal/v1/ai/health.
 * Reports per-provider reachability. Callers must not infer API key validity
 * from this response — the check is a lightweight ping, not a token spend.
 */
public record HealthResponse(
        String status,           // "UP", "DEGRADED", "DOWN"
        List<ProviderHealth> providers
) {

    public record ProviderHealth(
            String provider,
            String status,       // "UP", "DOWN", "UNCONFIGURED"
            String latencyMs,    // string to allow "N/A"
            String message       // human-readable, no secrets
    ) {}
}
