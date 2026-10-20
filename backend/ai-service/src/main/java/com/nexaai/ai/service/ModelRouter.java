package com.nexaai.ai.service;

import com.nexaai.ai.config.AiProperties;
import com.nexaai.ai.domain.AiProvider;
import com.nexaai.ai.exception.AiCircuitOpenException;
import com.nexaai.ai.exception.AiModelNotFoundException;
import com.nexaai.ai.exception.AiNotConfiguredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Model selection, availability checking, and simple circuit-breaker state
 * machine per provider.
 *
 * Circuit breaker states:
 *   CLOSED   — normal operation
 *   OPEN     — provider is down; reject quickly, check after waitDurationMs
 *   HALF_OPEN — tentative: allow one request through to test recovery
 */
@Component
public class ModelRouter {

    private static final Logger log = LoggerFactory.getLogger(ModelRouter.class);

    private final AiProperties props;

    // Per-provider failure counts and reset timestamps
    private final Map<AiProvider, AtomicInteger> failures = new ConcurrentHashMap<>();
    private final Map<AiProvider, AtomicLong> openUntil = new ConcurrentHashMap<>();
    private final Map<AiProvider, AtomicInteger> halfOpenSuccesses = new ConcurrentHashMap<>();

    public ModelRouter(AiProperties props) {
        this.props = props;
        for (AiProvider p : AiProvider.values()) {
            failures.put(p, new AtomicInteger(0));
            openUntil.put(p, new AtomicLong(0));
            halfOpenSuccesses.put(p, new AtomicInteger(0));
        }
    }

    // ------------------------------------------------------------------ validation

    /**
     * Validate that the requested provider is enabled and the model exists.
     *
     * @throws AiNotConfiguredException  provider is disabled or not configured
     * @throws AiModelNotFoundException  model not in the provider's catalogue
     * @throws AiCircuitOpenException    circuit breaker is open for the provider
     */
    public void validate(AiProvider provider, String model) {
        AiProperties.ProviderConfig cfg = configFor(provider);

        if (!cfg.enabled()) {
            throw new AiNotConfiguredException(provider.name());
        }

        checkCircuit(provider);

        boolean modelKnown = cfg.models() != null &&
                cfg.models().stream().anyMatch(m -> m.id().equalsIgnoreCase(model));
        if (!modelKnown) {
            throw new AiModelNotFoundException(model, provider.name());
        }
    }

    /** Returns the model definition for the given provider+model pair. */
    public Optional<AiProperties.ModelDefinition> findModel(AiProvider provider, String model) {
        AiProperties.ProviderConfig cfg = configFor(provider);
        if (cfg.models() == null) return Optional.empty();
        return cfg.models().stream()
                .filter(m -> m.id().equalsIgnoreCase(model))
                .findFirst();
    }

    // ------------------------------------------------------------------ circuit breaker

    /** Record a successful call — resets failure count or closes the circuit. */
    public void recordSuccess(AiProvider provider) {
        failures.get(provider).set(0);
        openUntil.get(provider).set(0);
        halfOpenSuccesses.get(provider).set(0);
        log.debug("Circuit breaker: {} SUCCESS — circuit CLOSED", provider);
    }

    /** Record a failure — may trip the circuit breaker. */
    public void recordFailure(AiProvider provider) {
        int count = failures.get(provider).incrementAndGet();
        int threshold = circuitBreakerCfg().failureThreshold();
        if (count >= threshold) {
            long waitMs = circuitBreakerCfg().waitDurationMs();
            openUntil.get(provider).set(System.currentTimeMillis() + waitMs);
            log.warn("Circuit breaker: {} OPEN after {} failures (retry after {}ms)", provider, count, waitMs);
        }
    }

    /** Returns true if the circuit is currently CLOSED or HALF_OPEN (allowing a probe). */
    public boolean isAvailable(AiProvider provider) {
        long until = openUntil.get(provider).get();
        if (until == 0) return true; // CLOSED
        if (System.currentTimeMillis() > until) {
            log.info("Circuit breaker: {} HALF_OPEN — probing", provider);
            return true; // HALF_OPEN
        }
        return false; // OPEN
    }

    private void checkCircuit(AiProvider provider) {
        if (!isAvailable(provider)) {
            throw new AiCircuitOpenException(provider.name());
        }
    }

    // ------------------------------------------------------------------ helpers

    private AiProperties.ProviderConfig configFor(AiProvider provider) {
        return switch (provider) {
            case OPENAI -> props.openai();
            case GEMINI -> props.gemini();
            case GROQ   -> props.groq();
        };
    }

    private AiProperties.CircuitBreakerConfig circuitBreakerCfg() {
        return props.circuitBreaker() != null
                ? props.circuitBreaker()
                : AiProperties.CircuitBreakerConfig.defaults();
    }
}
