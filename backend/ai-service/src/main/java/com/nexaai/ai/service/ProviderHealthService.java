package com.nexaai.ai.service;

import com.nexaai.ai.config.AiProperties;
import com.nexaai.ai.domain.AiProvider;
import com.nexaai.ai.dto.HealthResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Performs a lightweight per-provider health probe.
 *
 * The probe sends a minimal 1-token request to each enabled provider.
 * Failures are logged at WARN level; the probe result is returned to the
 * caller without exposing any provider-specific details.
 *
 * Note: this intentionally spends one token per enabled provider per call.
 * Callers (infrastructure health checks, not the browser) should not call
 * this endpoint more than once every 30 seconds.
 */
@Service
public class ProviderHealthService {

    private static final Logger log = LoggerFactory.getLogger(ProviderHealthService.class);
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(15);

    private final OpenAiChatModel openAiModel;
    private final OpenAiChatModel groqModel;
    private final OpenAiChatModel geminiModel;
    private final AiProperties props;
    private final ModelRouter router;

    public ProviderHealthService(
            @Qualifier("openAiChatModel") OpenAiChatModel openAiModel,
            @Qualifier("groqChatModel")   OpenAiChatModel groqModel,
            @Qualifier("geminiChatModel") OpenAiChatModel geminiModel,
            AiProperties props,
            ModelRouter router
    ) {
        this.openAiModel = openAiModel;
        this.groqModel = groqModel;
        this.geminiModel = geminiModel;
        this.props = props;
        this.router = router;
    }

    public Mono<HealthResponse> checkHealth() {
        return Flux.merge(
                        probe(AiProvider.OPENAI, openAiModel, props.openai()),
                        probe(AiProvider.GEMINI, geminiModel, props.gemini()),
                        probe(AiProvider.GROQ,   groqModel,   props.groq())
                )
                .collectList()
                .map(list -> {
                    boolean allDown = list.stream().allMatch(h -> "DOWN".equals(h.status()) || "UNCONFIGURED".equals(h.status()));
                    boolean anyUp   = list.stream().anyMatch(h -> "UP".equals(h.status()));
                    String overall = allDown ? "DOWN" : anyUp ? (list.size() > 1 ? "DEGRADED" : "UP") : "DOWN";
                    // If only one provider enabled and it's up → UP
                    long configured = list.stream().filter(h -> !"UNCONFIGURED".equals(h.status())).count();
                    long up = list.stream().filter(h -> "UP".equals(h.status())).count();
                    overall = (configured == up && up > 0) ? "UP" : (up > 0 ? "DEGRADED" : "DOWN");
                    return new HealthResponse(overall, list);
                });
    }

    private Mono<HealthResponse.ProviderHealth> probe(
            AiProvider provider,
            OpenAiChatModel model,
            AiProperties.ProviderConfig cfg
    ) {
        if (!cfg.enabled()) {
            return Mono.just(new HealthResponse.ProviderHealth(
                    provider.name(), "UNCONFIGURED", "N/A", "Provider not enabled"));
        }

        if (!router.isAvailable(provider)) {
            return Mono.just(new HealthResponse.ProviderHealth(
                    provider.name(), "DOWN", "N/A", "Circuit breaker open"));
        }

        // Use the smallest available model for the probe
        String probeModel = cfg.models() != null && !cfg.models().isEmpty()
                ? cfg.models().get(0).id()
                : "gpt-4o-mini";

        long start = System.currentTimeMillis();
        var prompt = new Prompt(List.of(new UserMessage("Hi")),
                org.springframework.ai.openai.OpenAiChatOptions.builder()
                        .model(probeModel)
                        .maxTokens(1)
                        .build());

        return Mono.fromCallable(() -> model.call(prompt))
                .timeout(PROBE_TIMEOUT)
                .map(r -> {
                    long latency = System.currentTimeMillis() - start;
                    router.recordSuccess(provider);
                    return new HealthResponse.ProviderHealth(
                            provider.name(), "UP", latency + "ms", "OK");
                })
                .onErrorResume(ex -> {
                    long latency = System.currentTimeMillis() - start;
                    log.warn("Health probe failed for {}: {}", provider, ex.getMessage());
                    router.recordFailure(provider);
                    return Mono.just(new HealthResponse.ProviderHealth(
                            provider.name(), "DOWN", latency + "ms", "Probe failed"));
                });
    }
}
