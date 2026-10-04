package com.nexaai.auth.domain;

/**
 * Account lifecycle status.
 *
 * <p>A {@code PENDING_VERIFICATION} account exists but cannot sign in. That is the reason
 * registration does not return tokens: it prevents mass unverified registration and keeps
 * the verification path real rather than decorative
 * ({@code docs/SERVICE_CONTRACTS.md} section 5.4).
 */
public enum AccountStatus {
    /** Registered; email not yet verified. Cannot sign in. */
    PENDING_VERIFICATION,
    /** Verified and permitted to sign in. */
    ACTIVE,
    /** Temporarily blocked by an administrator. A token is rejected even before expiry. */
    SUSPENDED,
    /** Permanently closed. */
    DEACTIVATED;

    /** Whether an account in this status may complete a sign-in. */
    public boolean canAuthenticate() {
        return this == ACTIVE;
    }

    /**
     * Whether a status change is legal.
     *
     * <p>Kept as an explicit table rather than a {@code switch} scattered across services,
     * so the transition rules live in one reviewable place. Illegal transitions are
     * rejected rather than silently ignored: an administrator who suspends an
     * already-deactivated account has made a mistake and should be told.
     */
    public boolean canTransitionTo(AccountStatus target) {
        return switch (this) {
            case PENDING_VERIFICATION -> target == ACTIVE
                    // An account can be deactivated before it is ever verified, and an
                    // administrator may block a pending account.
                    || target == SUSPENDED
                    || target == DEACTIVATED;
            case ACTIVE -> target == SUSPENDED || target == DEACTIVATED;
            // A suspended account may be reinstated or permanently closed.
            case SUSPENDED -> target == ACTIVE || target == DEACTIVATED;
            // Terminal. A deactivated account is never revived.
            case DEACTIVATED -> false;
        };
    }
}