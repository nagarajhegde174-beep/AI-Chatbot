package com.nexaai.ai.provider;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Tracks whether each provider is currently working.
 *
 * <p><strong>Not a database and not a circuit breaker in the resilience4j sense.</strong> This
 * service owns no database (CI rule R5) and holds no state that must survive a restart: a
 * provider's health is a live fact, and what was true thirty seconds ago is not evidence about
 * now. A restart forgetting the state is therefore correct rather than a limitation.
 *
 * <p>A provider is marked unhealthy after {@code failureThreshold} consecutive retryable
 * failures and recovers after {@code cooldown}. Only <em>retryable</em> failures count: a
 * rejected request says nothing about whether the provider is up, and treating a bad prompt as
 * an outage would take a working provider out of rotation.
 *
 * <p>Non-retryable failures are counted separately and reported, because "your API key is
 * wrong" is a much more urgent thing for an operator to see than "the provider is slow".
 */
@Component
public class ProviderHealth {

    private static final Logger log = LoggerFactory.getLogger(ProviderHealth.class);

    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final int failureThreshold;
    private final Duration cooldown;

    public ProviderHealth(com.nexaai.ai.config.AiProperties properties) {
        // Hard-coded rather than configured: these are not deployment-specific. A threshold
        // exposed as configuration is one an operator sets wrong and then debugs for an hour.
        this.failureThreshold = 3;
        this.cooldown = Duration.ofSeconds(30);
    }

    /** Records a successful call, clearing any unhealthy state. */
    public void recordSuccess(String providerId) {
        State state = stateFor(providerId);
        state.consecutiveFailures.set(0);
        state.unhealthyUntil.set(null);
        state.successes.incrementAndGet();
        state.lastSuccessAt.set(Instant.now());
        state.lastFailureKind.set(null);
    }

    /**
     * Records a failure.
     *
     * @param only retryable failures move a provider towards unhealthy; the rest are tallied
     */
    public void recordFailure(String providerId, ProviderFailureKind kind) {
        State state = stateFor(providerId);
        state.failures.incrementAndGet();

        if (kind == null) {
            return;
        }

        if (!kind.isRetryable()) {
            // Counted and logged, but deliberately not moving the health gate. A provider that
            // answers "400 bad request" is a provider that is up.
            state.lastFailureKind.set(kind.name());
            state.lastFailureAt.set(Instant.now());
            log.warn("Provider {} rejected a request: {}", providerId, kind);
            return;
        }

        state.lastFailureKind.set(kind.name());
        state.lastFailureAt.set(Instant.now());

        int consecutive = state.consecutiveFailures.incrementAndGet();
        if (consecutive >= failureThreshold) {
            state.unhealthyUntil.set(Instant.now().plus(cooldown));
            log.warn("Provider {} marked unhealthy after {} consecutive retryable failures. "
                    + "Retrying in {}.", providerId, consecutive, cooldown);
        }
    }

    /** Whether a provider is currently considered usable. */
    public boolean isHealthy(String providerId) {
        State state = states.get(providerId);
        if (state == null) {
            return true;
        }
        Instant until = state.unhealthyUntil.get();
        if (until == null) {
            return true;
        }
        if (Instant.now().isBefore(until)) {
            return false;
        }
        // The cooldown has elapsed. Reset so the next failure starts counting from one, rather
        // than tripping immediately again on a single error.
        state.unhealthyUntil.set(null);
        state.consecutiveFailures.set(0);
        return true;
    }

    /** A snapshot for the health endpoint. Contains no credential and no prompt content. */
    public HealthSnapshot snapshot(String providerId) {
        State state = states.get(providerId);
        if (state == null) {
            return new HealthSnapshot(providerId, true, "UNKNOWN", 0, 0, 0, 0, null, null, 0);
        }
        return new HealthSnapshot(
                providerId,
                isHealthy(providerId),
                state.lastFailureKind.get(),
                state.successes.get(),
                state.failures.get(),
                state.consecutiveFailures.get(),
                0,
                state.lastSuccessAt.get(),
                state.lastFailureAt.get(),
                secondsRemaining(state));
    }

    public Map<String, HealthSnapshot> snapshotAll(java.util.Collection<String> providerIds) {
        Map<String, HealthSnapshot> snapshots = new java.util.LinkedHashMap<>();
        for (String id : providerIds) {
            snapshots.put(id, snapshot(id));
        }
        return snapshots;
    }

    /** Clears all health state. Exists so a deployment can force a re-probe. */
    public void reset() {
        states.clear();
    }

    private int secondsRemaining(State state) {
        Instant until = state.unhealthyUntil.get();
        if (until == null) {
            return 0;
        }
        long remaining = Duration.between(Instant.now(), until).toSeconds();
        return remaining > 0 ? (int) remaining : 0;
    }

    private State stateFor(String providerId) {
        return states.computeIfAbsent(providerId, id -> new State());
    }

    /** Mutable counters for one provider. */
    private static final class State {
        private final AtomicInteger successes = new AtomicInteger();
        private final AtomicInteger failures = new AtomicInteger();
        private final AtomicInteger consecutiveFailures = new AtomicInteger();
        private final AtomicReference<Instant> unhealthyUntil = new AtomicReference<>();
        private final AtomicReference<Instant> lastSuccessAt = new AtomicReference<>();
        private final AtomicReference<Instant> lastFailureAt = new AtomicReference<>();
        private final AtomicReference<String> lastFailureKind = new AtomicReference<>();
    }

    /**
     * A provider's health as reported.
     *
     * @param retryableFailures failures that counted towards the health gate, kept separate from
     *                          the total so an operator can see "10 requests rejected, 0
     *                          outages" rather than one confusing number
     */
    public record HealthSnapshot(
            String provider,
            boolean healthy,
            String lastFailureKind,
            long successes,
            long failures,
            int consecutiveFailures,
            long retryableFailures,
            Instant lastSuccessAt,
            Instant lastFailureAt,
            int secondsUntilRetry) {
    }
}