package com.nexaai.ai.service;

import com.nexaai.ai.config.AiProperties;
import com.nexaai.ai.domain.AiProvider;
import com.nexaai.ai.domain.FinishReason;
import com.nexaai.ai.dto.CompletionRequest;
import com.nexaai.ai.dto.CompletionResponse;
import com.nexaai.ai.dto.StreamChunk;
import com.nexaai.ai.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Core inference service. Handles both blocking and streaming completions
 * through Spring AI. All three providers (OpenAI, Gemini, Groq) use the
 * OpenAI-compatible ChatModel abstraction.
 *
 * Retry logic:
 *   - Retries once on timeout (AI_TIMEOUT).
 *   - Retries up to maxAttempts on rate limit (AI_RATE_LIMITED) with backoff.
 *   - Does not retry on content filter (AI_CONTENT_FILTERED) or model not
 *     found (AI_MODEL_NOT_FOUND).
 */
@Service
public class AiInferenceService {

    private static final Logger log = LoggerFactory.getLogger(AiInferenceService.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);

    private final OpenAiChatModel openAiModel;
    private final OpenAiChatModel groqModel;
    private final OpenAiChatModel geminiModel;
    private final ModelRouter router;
    private final InferenceEventPublisher eventPublisher;
    private final AiProperties props;

    public AiInferenceService(
            @Qualifier("openAiChatModel")  OpenAiChatModel openAiModel,
            @Qualifier("groqChatModel")    OpenAiChatModel groqModel,
            @Qualifier("geminiChatModel")  OpenAiChatModel geminiModel,
            ModelRouter router,
            InferenceEventPublisher eventPublisher,
            AiProperties props
    ) {
        this.openAiModel = openAiModel;
        this.groqModel = groqModel;
        this.geminiModel = geminiModel;
        this.router = router;
        this.eventPublisher = eventPublisher;
        this.props = props;
    }

    // ------------------------------------------------------------------ blocking

    /**
     * Non-streaming completion. Returns a full response after the provider
     * finishes generation.
     */
    public Mono<CompletionResponse> complete(CompletionRequest req) {
        // Validate eagerly so AiNotConfiguredException / AiModelNotFoundException
        // surface directly without being wrapped by the retry operator.
        AiProvider provider;
        try {
            provider = resolveProvider(req.provider());
            router.validate(provider, req.model());
        } catch (AiException ex) {
            return Mono.error(ex);
        }

        final AiProvider finalProvider = provider;
        Prompt prompt = buildPrompt(req, finalProvider);
        OpenAiChatModel model = modelFor(finalProvider);
        long startMs = System.currentTimeMillis();

        return Mono.fromCallable(() -> model.call(prompt))
                .timeout(REQUEST_TIMEOUT)
                .map(response -> toCompletionResponse(req, finalProvider, response, startMs))
                .doOnSuccess(r -> {
                    router.recordSuccess(finalProvider);
                    eventPublisher.publish(req.requestId(), finalProvider, req.model(),
                            r.usage().promptTokens(), r.usage().completionTokens(),
                            r.latencyMs(), true);
                })
                .onErrorMap(ex -> classifyError(finalProvider, ex))
                .doOnError(ex -> {
                    if (ex instanceof AiException) {
                        router.recordFailure(finalProvider);
                        eventPublisher.publish(req.requestId(), finalProvider, req.model(),
                                0, 0, System.currentTimeMillis() - startMs, false);
                    }
                })
                .retryWhen(buildRetrySpec(finalProvider))
                // Unwrap RetryExhaustedException to expose the underlying classified error
                .onErrorMap(reactor.core.Exceptions::isRetryExhausted,
                        ex -> ex.getCause() != null ? ex.getCause() : ex);
    }

    // ------------------------------------------------------------------ streaming

    /**
     * Streaming completion via SSE. Emits token chunks, then a final "done"
     * chunk with usage metadata.
     */
    public Flux<StreamChunk> stream(CompletionRequest req) {
        AiProvider provider;
        try {
            provider = resolveProvider(req.provider());
            router.validate(provider, req.model());
        } catch (AiException ex) {
            return Flux.error(ex);
        }

        final AiProvider finalProvider = provider;
        Prompt prompt = buildPrompt(req, finalProvider);
        OpenAiChatModel model = modelFor(finalProvider);
        long startMs = System.currentTimeMillis();
        StringBuilder fullContent = new StringBuilder();
        AtomicLong tokenCount = new AtomicLong(0);

        return model.stream(prompt)
                .timeout(REQUEST_TIMEOUT)
                .map(response -> {
                    String token = extractToken(response);
                    if (token != null && !token.isEmpty()) {
                        fullContent.append(token);
                        tokenCount.incrementAndGet();
                    }
                    return StreamChunk.token(req.requestId(), token != null ? token : "");
                })
                .filter(chunk -> !chunk.token().isEmpty())
                .concatWith(Mono.fromCallable(() -> {
                    long latency = System.currentTimeMillis() - startMs;
                    int estPromptTokens = req.messages().stream()
                            .mapToInt(m -> m.content().length() / 4)
                            .sum();
                    int completionTokens = (int) tokenCount.get();
                    router.recordSuccess(finalProvider);
                    eventPublisher.publish(req.requestId(), finalProvider, req.model(),
                            estPromptTokens, completionTokens, latency, true);

                    CompletionResponse summary = new CompletionResponse(
                            req.requestId(), finalProvider.name(), req.model(),
                            fullContent.toString(), FinishReason.STOP,
                            new CompletionResponse.TokenUsage(estPromptTokens, completionTokens,
                                    estPromptTokens + completionTokens),
                            latency
                    );
                    return StreamChunk.done(req.requestId(), summary);
                }))
                .onErrorMap(ex -> classifyError(finalProvider, ex))
                .doOnError(ex -> {
                    router.recordFailure(finalProvider);
                    eventPublisher.publish(req.requestId(), finalProvider, req.model(),
                            0, (int) tokenCount.get(), System.currentTimeMillis() - startMs, false);
                });
    }

    // ------------------------------------------------------------------ helpers

    private AiProvider resolveProvider(String providerStr) {
        try {
            return AiProvider.valueOf(providerStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AiNotConfiguredException(providerStr);
        }
    }

    private OpenAiChatModel modelFor(AiProvider provider) {
        return switch (provider) {
            case OPENAI -> openAiModel;
            case GROQ   -> groqModel;
            case GEMINI -> geminiModel;
        };
    }

    private Prompt buildPrompt(CompletionRequest req, AiProvider provider) {
        List<Message> messages = new ArrayList<>();

        // Prepend system prompt if provided separately
        if (req.systemPrompt() != null && !req.systemPrompt().isBlank()) {
            messages.add(new SystemMessage(req.systemPrompt()));
        }

        for (CompletionRequest.CompletionMessage m : req.messages()) {
            Message msg = switch (m.role().toUpperCase()) {
                case "SYSTEM"    -> new SystemMessage(m.content());
                case "ASSISTANT" -> new AssistantMessage(m.content());
                default          -> new UserMessage(m.content());
            };
            messages.add(msg);
        }

        // Build options from the request; model def may cap maxOutputTokens
        OpenAiChatOptions.Builder optBuilder = OpenAiChatOptions.builder()
                .model(req.model())
                .maxTokens(req.maxOutputTokens());

        if (req.temperature() != null) {
            optBuilder.temperature(req.temperature());
        }

        return new Prompt(messages, optBuilder.build());
    }

    private CompletionResponse toCompletionResponse(
            CompletionRequest req,
            AiProvider provider,
            ChatResponse response,
            long startMs
    ) {
        String content = response.getResult() != null
                ? response.getResult().getOutput().getText()
                : "";

        int promptTokens = 0;
        int completionTokens = 0;
        if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
            var usage = response.getMetadata().getUsage();
            promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens().intValue() : 0;
            completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens().intValue() : 0;
        }

        FinishReason finishReason = resolveFinishReason(response);

        return new CompletionResponse(
                req.requestId(),
                provider.name(),
                req.model(),
                content,
                finishReason,
                new CompletionResponse.TokenUsage(promptTokens, completionTokens, promptTokens + completionTokens),
                System.currentTimeMillis() - startMs
        );
    }

    private FinishReason resolveFinishReason(ChatResponse response) {
        if (response.getResult() == null || response.getResult().getMetadata() == null) {
            return FinishReason.STOP;
        }
        String raw = String.valueOf(response.getResult().getMetadata().getFinishReason());
        return switch (raw.toUpperCase()) {
            case "LENGTH"         -> FinishReason.LENGTH;
            case "CONTENT_FILTER" -> FinishReason.CONTENT_FILTER;
            case "STOP", "END_TURN" -> FinishReason.STOP;
            default               -> FinishReason.STOP;
        };
    }

    private String extractToken(ChatResponse response) {
        if (response.getResult() == null) return null;
        return response.getResult().getOutput().getText();
    }

    private Throwable classifyError(AiProvider provider, Throwable ex) {
        if (ex instanceof AiException) return ex; // already classified

        String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";

        if (ex instanceof java.util.concurrent.TimeoutException
                || msg.contains("timeout") || msg.contains("timed out")) {
            return new AiTimeoutException(provider.name(), ex);
        }
        if (msg.contains("rate limit") || msg.contains("429") || msg.contains("too many requests")) {
            return new AiRateLimitedException(provider.name(), ex);
        }
        if (msg.contains("content") && (msg.contains("filter") || msg.contains("policy"))) {
            return new AiContentFilteredException(provider.name());
        }
        return new AiProviderException(provider.name(), ex);
    }

    private reactor.util.retry.Retry buildRetrySpec(AiProvider provider) {
        AiProperties.RetryConfig cfg = props.retry() != null
                ? props.retry()
                : AiProperties.RetryConfig.defaults();

        return reactor.util.retry.Retry.backoff(cfg.maxAttempts(), Duration.ofMillis(cfg.initialBackoffMs()))
                .maxBackoff(Duration.ofMillis(cfg.maxBackoffMs()))
                .filter(ex -> ex instanceof AiException ae && ae.isRetryable())
                .doBeforeRetry(signal ->
                        log.warn("Retrying {} request after error: {} (attempt {})",
                                provider, signal.failure().getClass().getSimpleName(),
                                signal.totalRetries() + 1));
    }
}
