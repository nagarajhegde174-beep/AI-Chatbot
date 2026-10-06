package com.nexaai.ai.provider;

/**
 * Why a provider call failed, classified by whether trying again could help.
 *
 * <p>The classification is the whole value. "Retry everything" turns a rejected request — a bad
 * API key, a prompt the provider refuses — into five requests and a slower failure.
 * "Retry nothing" turns a one-second blip into a user-visible error. So the distinction is made
 * once, here, and everything upstream of it branches on {@link #isRetryable()} rather than on
 * exception types it would otherwise have to know about.
 */
public enum ProviderFailureKind {

    /** The provider could not be reached: DNS, connection refused, timeout. Worth retrying. */
    UNAVAILABLE(true),

    /** The provider returned 429 or 5xx. Worth retrying, with backoff. */
    THROTTLED(true),

    /** The credentials are absent, wrong or revoked. Retrying cannot help. */
    UNAUTHORISED(false),

    /** The provider rejected the request itself. Retrying the same request cannot help. */
    REJECTED(false),

    /** The request exceeded a limit the provider enforces. */
    LIMIT_EXCEEDED(false),

    /** The provider is configured but this deployment has no client for it. */
    NOT_CONFIGURED(false),

    /** Anything unclassified. Retried once, because an unknown fault is often transient. */
    UNKNOWN(true);

    private final boolean retryable;

    ProviderFailureKind(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }

    /**
     * Whether a different model should be attempted after this failure.
     *
     * <p>Separate from {@link #isRetryable()}, because the two answer different questions.
     * Retry asks "would the same request work again?"; fallback asks "would a different model do
     * better?" A request the provider <em>refused</em> is one that will be refused again by every
     * sibling model on that provider — falling back there spends another provider call to arrive
     * at the same rejection, and produces a log line implying the system recovered when it did
     * not.
     *
     * <p>Only transient, infrastructure-shaped failures justify trying someone else. Whether
     * trying a <em>different provider</em> is also permitted is a per-model decision, not a global
     * one: see {@code Model.allowCrossProviderFallback}.
     */
    public boolean shouldFallBack() {
        return switch (this) {
            // The provider refused this request, or refused us. A sibling model will too.
            case REJECTED, LIMIT_EXCEEDED, UNAUTHORISED, NOT_CONFIGURED -> false;
            // Genuinely transient or model-specific: worth another model, and worth a backoff.
            case UNAVAILABLE, THROTTLED, UNKNOWN -> true;
        };
    }
}