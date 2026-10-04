package com.nexaai.auth.domain;

/**
 * Thrown when a caller attempts a status transition the lifecycle forbids.
 *
 * <p>Distinct from a validation exception so the API layer can answer 409 Conflict. An
 * illegal transition is a conflict with current state, not a malformed request.
 */
public class IllegalStateTransitionException extends RuntimeException {

    private final AccountStatus from;
    private final AccountStatus to;

    public IllegalStateTransitionException(AccountStatus from, AccountStatus to) {
        super("Illegal account status transition: %s -> %s".formatted(from, to));
        this.from = from;
        this.to = to;
    }

    public AccountStatus getFrom() {
        return from;
    }

    public AccountStatus getTo() {
        return to;
    }
}