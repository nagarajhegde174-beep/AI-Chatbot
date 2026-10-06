package com.nexaai.ai.retry;

import com.nexaai.ai.config.AiProperties;
import com.nexaai.ai.provider.ProviderException;
import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Bounded retry with exponential backoff, for retryable provider failures only.
 *
 * <p><strong>Small on purpose.</strong> Three attempts with a 250 ms base and a 2 s ceiling. A
 * retry policy that keeps a request alive for thirty seconds turns a provider outage into a
 * slow failure for every caller at once, which is worse for everyone than a fast error followed
 * by a fallback.
 *
 * <p>Only {@link com.nexaai.ai.provider.ProviderFailureKind#isRetryable()} failures are
 * retried. Retrying a rejected request is how one bad prompt becomes five provider calls.
 */
@Component
public class RetryExecutor {

    private static final Logger log = LoggerFactory.getLogger(RetryExecutor.class);

    private final AiProperties.Retry config;

    public RetryExecutor(AiProperties properties) {
        this.config = properties.getRetry();
    }

    /**
     * Runs {@code operation}, retrying retryable failures.
     *
     * @throws ProviderException the last failure, once attempts are exhausted
     */
    public <T> T execute(String description, Supplier<T> operation) {
        if (!config.isEnabled()) {
            return operation.get();
        }

        int maxAttempts = Math.max(1, config.getMaxAttempts());
        ProviderException last = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return operation.get();
            } catch (ProviderException e) {
                last = e;
                if (!e.isRetryable() || attempt == maxAttempts) {
                    break;
                }

                Duration backoff = backoffFor(attempt);
                log.warn("{} failed ({}), attempt {}/{}; retrying in {}",
                        description, e.kind(), attempt, maxAttempts, backoff);
                sleep(backoff);
            }
        }

        log.warn("{} exhausted {} attempt(s); last failure was {}",
                description, maxAttempts, last == null ? "unknown" : last.kind());
        throw last;
    }

    /**
     * Exponential backoff, capped.
     *
     * <p>Public so the schedule can be asserted directly. Testing this through {@link #execute}
     * would mean sleeping through the delays to observe them, which is a slow test that fails
     * for reasons unrelated to what it claims to check.
     */
    public Duration backoffFor(int attempt) {
        long baseMillis = Math.max(0, config.getInitialBackoff().toMillis());
        long maxMillis = Math.max(baseMillis, config.getMaxBackoff().toMillis());
        long scaled = baseMillis * (1L << (attempt - 1));
        return Duration.ofMillis(Math.min(scaled, maxMillis));
    }

    /**
     * Sleeps, tolerating interruption.
     *
     * <p>Interrupted sets the flag and returns: swallowing it silently would let a request
     * proceed to the provider while the thread is being shut down, which is how a deploy
     * produces requests that should never have been sent.
     */
    private void sleep(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}