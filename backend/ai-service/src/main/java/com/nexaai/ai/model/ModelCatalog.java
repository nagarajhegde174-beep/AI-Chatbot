package com.nexaai.ai.model;

import com.nexaai.ai.config.AiProperties;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * What the router knows about every model before a request arrives.
 *
 * <p>Models are declared in configuration rather than discovered from a provider. That is what
 * makes three things possible that a discovery-based catalog makes awkward: fallback ordering is
 * knowable, token limits are knowable, and an unknown model name is a clean 400 rather than a
 * provider-side surprise discovered mid-generation.
 *
 * <p>The catalog holds no state and no credential. Availability is deliberately <em>not</em> a
 * property here: whether a provider can actually serve a model right now is
 * {@link com.nexaai.ai.provider.ProviderHealth}'s question, and keeping the two apart is what
 * stops a transient outage from being mistaken for a misconfiguration.
 */
@Component
public class ModelCatalog {

    private static final Logger log = LoggerFactory.getLogger(ModelCatalog.class);

    private final List<ModelDescriptor> models;
    private final String defaultModelName;

    public ModelCatalog(AiProperties properties) {
        this.defaultModelName = properties.getDefaults().getModel();

        List<ModelDescriptor> declared = properties.getModels().stream()
                .map(ModelDescriptor::from)
                .sorted(Comparator.comparingInt(ModelDescriptor::priority)
                        .thenComparing(ModelDescriptor::name))
                .toList();

        // A duplicate model name would make selection non-deterministic and is always an
        // operator error rather than a runtime condition to handle gracefully.
        for (int i = 0; i < declared.size(); i++) {
            for (int j = i + 1; j < declared.size(); j++) {
                if (declared.get(i).name().equals(declared.get(j).name())) {
                    throw new IllegalStateException("Duplicate model name '" + declared.get(i).name()
                            + "' in configuration. Model names must be unique.");
                }
            }
        }

        this.models = List.copyOf(declared);
        log.info("Model catalog loaded: {} model(s), default '{}'", models.size(), defaultModelName);

        if (find(defaultModelName).isEmpty()) {
            // Not fatal: the default is only consulted when a caller names no model, and the
            // alternative would be refusing to start over a value nothing has used yet. The
            // warning is the point — it will be a 503 on the first request that relies on it.
            log.warn("Default model '{}' is not in the catalog. Requests that name no model will "
                    + "fail until it is configured.", defaultModelName);
        }
    }

    /** Every configured model, in fallback order. */
    public List<ModelDescriptor> all() {
        return models;
    }

    /** Looks up one model by the name a caller used. Case-insensitive. */
    public Optional<ModelDescriptor> find(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String normalised = name.trim().toLowerCase(Locale.ROOT);
        return models.stream().filter(m -> m.name().toLowerCase(Locale.ROOT).equals(normalised))
                .findFirst();
    }

    /**
     * The model to use when a caller names none.
     *
     * @throws IllegalStateException if the configured default is not in the catalog, which is a
     *         configuration error the operator must see rather than a request to fall over
     */
    public ModelDescriptor defaultModel() {
        return find(defaultModelName).orElseThrow(() -> new IllegalStateException(
                "Default model '" + defaultModelName + "' is not in the catalog."));
    }

    public String defaultModelName() {
        return defaultModelName;
    }

    /** The fallback chain for a requested model. */
    public List<ModelDescriptor> fallbackChainFor(String requestedName) {
        Optional<ModelDescriptor> requested = find(requestedName);
        if (requested.isEmpty()) {
            return List.of();
        }
        ModelDescriptor primary = requested.get();
        if (!primary.allowFallback()) {
            return List.of(primary);
        }

        // Same provider first. Two models behind one provider are broadly interchangeable, so
        // substituting one for the other is a detail the caller does not need to weigh.
        List<ModelDescriptor> sameProvider = models.stream()
                .filter(m -> m.providerKind() == primary.providerKind())
                .sorted(catalogOrder())
                .toList();

        if (!primary.allowCrossProviderFallback()) {
            // The default, and the safe answer: a request for Gemini is answered by Gemini or
            // not at all. Note what this means in practice -- it also means fallback cannot
            // rescue a request during a provider outage, because every model on that provider
            // is down together. That is the cost of not silently substituting providers, and an
            // operator who wants availability over fidelity opts in per model.
            return sameProvider;
        }

        // Opted in: other providers follow, in catalog order, so the substitution is available
        // but still an explicit, configured decision rather than an accident of ordering.
        List<ModelDescriptor> others = models.stream()
                .filter(m -> m.providerKind() != primary.providerKind())
                .sorted(catalogOrder())
                .toList();

        return java.util.stream.Stream.concat(sameProvider.stream(), others.stream()).toList();
    }

    private Comparator<ModelDescriptor> catalogOrder() {
        return Comparator.comparingInt(ModelDescriptor::priority)
                .thenComparing(ModelDescriptor::name);
    }
}