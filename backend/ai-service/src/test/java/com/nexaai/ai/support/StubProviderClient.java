package com.nexaai.ai.support;

import com.nexaai.ai.provider.ProviderClient;
import com.nexaai.ai.provider.ProviderException;
import com.nexaai.ai.provider.ProviderFailureKind;
import com.nexaai.ai.provider.ProviderMessages;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Flux;

/**
 * A {@link ProviderClient} under full test control.
 *
 * <p>Where {@link StubChatModel} stands in for a provider at the Spring AI layer, this stands in
 * for one at the service's own seam. Having both is deliberate: the Spring AI stub proves the
 * translation layer, and this proves the router. A test suite with only one of them would leave
 * a whole layer unverified.
 *
 * <p>Counts calls and can be made to fail a fixed number of times, which is the only honest way
 * to assert that a retry happened exactly three times or that a fallback was not taken.
 */
public class StubProviderClient implements ProviderClient {

    private final String id;

    private boolean available = true;
    private String unavailabilityReason = null;

    /** Thrown while set, for every model this client serves. */
    private ProviderException failure;

    /**
     * Thrown for one model only.
     *
     * <p>Exists because provider-level and model-level failure are different events with
     * different recoveries: a provider that is down takes every model behind it down too (so
     * same-provider fallback cannot help), while a single model that is rate-limited or
     * decommissioned leaves its siblings working (so it can).
     */
    private String failingModelName;

    /** Provider failure for a specific model. */
    private ProviderException modelFailure;

    /** Number of initial calls that fail before succeeding. */
    private int failFirstCalls;

    private String content = "stub answer";
    private Integer inputTokens = 11;
    private Integer outputTokens = 7;

    /** Chunks emitted by the streaming path. */
    private List<String> streamChunks = List.of("Hello", " from", " ", "the model");

    private final AtomicInteger completeCalls = new AtomicInteger();
    private final AtomicInteger streamCalls = new AtomicInteger();
    private final List<ProviderMessages.GenerationRequest> requests = new ArrayList<>();

    public StubProviderClient(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String unavailabilityReason() {
        return unavailabilityReason;
    }

    @Override
    public boolean supports(com.nexaai.ai.model.ModelDescriptor model) {
        return id.equals(model.provider());
    }

    @Override
    public ProviderMessages.GenerationResult complete(
            ProviderMessages.GenerationRequest request) {
        requests.add(request);
        int callNumber = completeCalls.incrementAndGet();

        if (failure != null) {
            throw failure;
        }
        if (modelFailure != null && request.model().name().equals(failingModelName)) {
            throw modelFailure;
        }
        if (callNumber <= failFirstCalls) {
            throw new ProviderException(ProviderFailureKind.UNAVAILABLE, request.model(),
                    "stub transient failure " + callNumber, null, Duration.ZERO);
        }
        return new ProviderMessages.GenerationResult(content, request.model(), inputTokens,
                outputTokens, Duration.ofMillis(5));
    }

    @Override
    public Flux<String> stream(ProviderMessages.GenerationRequest request) {
        requests.add(request);
        int callNumber = streamCalls.incrementAndGet();

        return Flux.defer(() -> {
            if (failure != null) {
                return Flux.error(failure);
            }
            if (callNumber <= failFirstCalls) {
                return Flux.error(new ProviderException(ProviderFailureKind.UNAVAILABLE,
                        request.model(), "stub transient stream failure", null, Duration.ZERO));
            }
            // Emitted as separate signals, not one joined string. A stream that emits its
            // whole answer in one event would pass a naive test and defeat the feature.
            return Flux.fromIterable(streamChunks);
        });
    }

    // ------------------------------------------------------------------
    // Test controls
    // ------------------------------------------------------------------

    public StubProviderClient unavailable(String reason) {
        this.available = false;
        this.unavailabilityReason = reason;
        return this;
    }

    public StubProviderClient failingWith(ProviderFailureKind kind, String message) {
        this.failure = new ProviderException(kind, null, message, null, Duration.ZERO);
        return this;
    }

    /** Fails one model, leaving its siblings on the same provider working. */
    public StubProviderClient failingModel(String modelName, ProviderFailureKind kind,
                                           String message) {
        this.failingModelName = modelName;
        this.modelFailure = new ProviderException(kind, null, message, null, Duration.ZERO);
        return this;
    }

    public StubProviderClient failingFirst(int attempts) {
        this.failFirstCalls = attempts;
        return this;
    }

    public StubProviderClient returning(String content) {
        this.content = content;
        return this;
    }

    public StubProviderClient withUsage(Integer input, Integer output) {
        this.inputTokens = input;
        this.outputTokens = output;
        return this;
    }

    public StubProviderClient streaming(List<String> chunks) {
        this.streamChunks = List.copyOf(chunks);
        return this;
    }

    public int completeCalls() {
        return completeCalls.get();
    }

    public int streamCalls() {
        return streamCalls.get();
    }

    public List<ProviderMessages.GenerationRequest> requests() {
        return List.copyOf(requests);
    }

    public ProviderMessages.GenerationRequest lastRequest() {
        if (requests.isEmpty()) {
            throw new IllegalStateException("No request reached this provider.");
        }
        return requests.get(requests.size() - 1);
    }
}
