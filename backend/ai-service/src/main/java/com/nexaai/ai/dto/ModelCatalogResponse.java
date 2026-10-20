package com.nexaai.ai.dto;

import java.util.List;
import java.util.Map;

/**
 * Response from GET /internal/v1/ai/models.
 * Lists available models grouped by provider.
 */
public record ModelCatalogResponse(
        List<ProviderModels> providers
) {

    public record ProviderModels(
            String provider,
            boolean available,
            List<ModelInfo> models
    ) {}

    public record ModelInfo(
            String id,
            String displayName,
            int maxContextTokens,
            int maxOutputTokens,
            boolean supportsStreaming,
            boolean supportsTemperature,
            Map<String, String> tags
    ) {}
}
