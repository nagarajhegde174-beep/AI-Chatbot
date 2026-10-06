package com.nexaai.ai.provider;

import com.nexaai.ai.model.ModelDescriptor;
import reactor.core.publisher.Flux;

/**
 * A configured provider, reachable or not.
 *
 * <p><strong>This is the seam the whole service is built around.</strong> Everything above it —
 * routing, fallback, retry, limits, health, streaming — is exercised in tests against an
 * implementation of this interface, which is why none of that logic needs a network, an API key
 * or a provider account to be trustworthy.
 *
 * <p>Implementations talk to Spring AI. Nothing above this interface knows that, and nothing
 * below it knows about routing.
 */
public interface ProviderClient {

    /** The provider id, matching a key in configuration. */
    String id();

    /**
     * Whether this provider can serve requests right now.
     *
     * <p>False for a provider with no credential, no client bean, or currently marked unhealthy.
     * Distinct from "healthy": an unavailable provider is one the platform should route around
     * without an error, while an unhealthy one is one that just failed.
     */
    boolean isAvailable();

    /** A non-sensitive description of why it is unavailable. Never contains a credential. */
    String unavailabilityReason();

    /** Runs one turn to completion. */
    ProviderMessages.GenerationResult complete(ProviderMessages.GenerationRequest request);

    /**
     * Streams one turn.
     *
     * <p>Emits incremental text chunks. Implementations must not buffer the whole response before
     * emitting, or progressive rendering is defeated: the user would see the answer arrive all at
     * once, which is the exact problem streaming exists to solve.
     */
    Flux<String> stream(ProviderMessages.GenerationRequest request);

    /** The models this client can serve. */
    boolean supports(ModelDescriptor model);
}
