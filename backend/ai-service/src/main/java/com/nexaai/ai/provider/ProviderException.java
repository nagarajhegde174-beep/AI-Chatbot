package com.nexaai.ai.provider;

import com.nexaai.ai.model.ModelDescriptor;
import java.time.Duration;

/**
 * A provider failure, already classified.
 *
 * <p><strong>The message is written for a log, not a user.</strong> It may name the provider, the
 * model and an upstream status. It must never contain a provider credential, a full prompt or a
 * stack trace, because this value is attached to a message that is persisted and returned. The
 * controller maps it to a generic user-facing message; this one goes to the log.
 */
public class ProviderException extends RuntimeException {

    private final ProviderFailureKind kind;
    private final ModelDescriptor model;
    private final Duration elapsed;

    public ProviderException(ProviderFailureKind kind, ModelDescriptor model, String message,
                             Throwable cause, Duration elapsed) {
        super(message, cause);
        this.kind = kind;
        this.model = model;
        this.elapsed = elapsed == null ? Duration.ZERO : elapsed;
    }

    public static ProviderException unavailable(ModelDescriptor model, String message,
                                                Throwable cause) {
        return new ProviderException(ProviderFailureKind.UNAVAILABLE, model, message, cause,
                Duration.ZERO);
    }

    public static ProviderException notConfigured(ModelDescriptor model, String message) {
        return new ProviderException(ProviderFailureKind.NOT_CONFIGURED, model, message, null,
                Duration.ZERO);
    }

    public static ProviderException rejected(ModelDescriptor model, String message,
                                             Throwable cause) {
        return new ProviderException(ProviderFailureKind.REJECTED, model, message, cause,
                Duration.ZERO);
    }

    public ProviderFailureKind kind() {
        return kind;
    }

    public ModelDescriptor model() {
        return model;
    }

    public Duration elapsed() {
        return elapsed;
    }

    public boolean isRetryable() {
        return kind.isRetryable();
    }
}
