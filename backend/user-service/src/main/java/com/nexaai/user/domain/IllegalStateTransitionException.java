package com.nexaai.user.domain;

/** Thrown when a status transition the lifecycle forbids is attempted. */
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