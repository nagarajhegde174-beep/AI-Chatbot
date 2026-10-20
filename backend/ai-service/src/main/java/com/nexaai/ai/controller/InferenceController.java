package com.nexaai.ai.controller;

import com.nexaai.ai.dto.CompletionRequest;
import com.nexaai.ai.dto.CompletionResponse;
import com.nexaai.ai.dto.StreamChunk;
import com.nexaai.ai.service.AiInferenceService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Inference endpoints consumed by Chat Service only.
 * There is NO public route to this service; the gateway and NGINX both block
 * /internal/** from external callers (ARCHITECTURE.md §3, ADR-014).
 *
 * POST /internal/v1/ai/completions   — blocking full-response completion
 * POST /internal/v1/ai/stream        — streaming SSE completion
 */
@RestController
@RequestMapping("/internal/v1/ai")
public class InferenceController {

    private final AiInferenceService inferenceService;

    public InferenceController(AiInferenceService inferenceService) {
        this.inferenceService = inferenceService;
    }

    /**
     * Non-streaming completion.
     * Returns the full model response once generation is complete.
     */
    @PostMapping("/completions")
    public Mono<CompletionResponse> complete(@Valid @RequestBody CompletionRequest request) {
        return inferenceService.complete(request);
    }

    /**
     * Streaming completion via Server-Sent Events.
     * Each event is a JSON-serialised {@link StreamChunk}. The last event has
     * {@code done = true} and carries a {@code summary} with token usage.
     *
     * The frontend must:
     * 1. Open the stream with {@code Accept: text/event-stream}.
     * 2. Append each {@code token} field to the display buffer.
     * 3. Close on the first chunk where {@code done == true}.
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StreamChunk> stream(@Valid @RequestBody CompletionRequest request) {
        return inferenceService.stream(request);
    }
}
