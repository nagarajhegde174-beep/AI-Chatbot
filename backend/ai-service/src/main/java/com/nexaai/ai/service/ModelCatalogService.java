package com.nexaai.ai.service;

import com.nexaai.ai.config.AiProperties;
import com.nexaai.ai.domain.AiProvider;
import com.nexaai.ai.dto.ModelCatalogResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds the model catalogue from configuration. The catalogue is derived
 * from nexaai.ai.*.models in application.yml — no database, no network call.
 */
@Service
public class ModelCatalogService {

    private final AiProperties props;
    private final ModelRouter router;

    public ModelCatalogService(AiProperties props, ModelRouter router) {
        this.props = props;
        this.router = router;
    }

    public ModelCatalogResponse catalogue() {
        List<ModelCatalogResponse.ProviderModels> providers = new ArrayList<>();
        providers.add(buildProviderModels(AiProvider.OPENAI, props.openai()));
        providers.add(buildProviderModels(AiProvider.GEMINI, props.gemini()));
        providers.add(buildProviderModels(AiProvider.GROQ,   props.groq()));
        return new ModelCatalogResponse(providers);
    }

    private ModelCatalogResponse.ProviderModels buildProviderModels(
            AiProvider provider,
            AiProperties.ProviderConfig cfg
    ) {
        boolean available = cfg.enabled() && router.isAvailable(provider);

        List<ModelCatalogResponse.ModelInfo> models = new ArrayList<>();
        if (cfg.models() != null) {
            for (AiProperties.ModelDefinition def : cfg.models()) {
                models.add(new ModelCatalogResponse.ModelInfo(
                        def.id(),
                        def.displayName(),
                        def.maxContextTokens(),
                        def.maxOutputTokens(),
                        true,  // all models support streaming via SSE
                        def.supportsTemperature(),
                        Map.of("provider", provider.name().toLowerCase())
                ));
            }
        }

        return new ModelCatalogResponse.ProviderModels(provider.name(), available, models);
    }
}
