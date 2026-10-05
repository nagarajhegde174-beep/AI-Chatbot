package com.nexaai.user.domain;

/**
 * Account status as this service knows it.
 *
 * <p><strong>This is a projection, not the source of truth.</strong> auth-service owns account
 * status and republishes changes on {@code user.status.changed.v1}. The copy here exists so
 * the admin view can filter and explain without a network call per row, and so a suspension is
 * visible on the profile the user sees.
 *
 * <p>Because it is derived state, this service must never invent a transition auth-service did
 * not authorise. Every transition here is either applied by an inbound event, or an
 * administrative action that is republished so auth-service remains authoritative.
 */
public enum AccountStatus {
    /** Registered; email not yet verified. */
    PENDING_VERIFICATION,
    /** Verified and permitted to sign in. */
    ACTIVE,
    /** Temporarily blocked by an administrator. */
    SUSPENDED,
    /** Permanently closed. */
    DEACTIVATED;

    public boolean canTransitionTo(AccountStatus target) {
        return switch (this) {
            case PENDING_VERIFICATION -> target == AccountStatus.ACTIVE
                    || target == AccountStatus.SUSPENDED
                    || target == AccountStatus.DEACTIVATED;
            case ACTIVE -> target == AccountStatus.SUSPENDED
                    || target == AccountStatus.DEACTIVATED;
            // SUSPENDED -> SUSPENDED is deliberately permitted, and only that self-transition
            // is. It exists so an administrator can correct the reason on an existing
            // suspension without having to unsuspend and re-suspend. The status history is
            // append-only, so the original entry is preserved rather than overwritten.
            case SUSPENDED -> target == AccountStatus.SUSPENDED
                    || target == AccountStatus.ACTIVE
                    || target == AccountStatus.DEACTIVATED;
            // Terminal. A DEACTIVATED account is closed permanently; there is no path back,
            // because "deactivated" that can be undone is just a suspended account with extra
            // steps.
            case DEACTIVATED -> false;
        };
    }

    /** Whether this status counts as "the account exists and is usable". */
    public boolean isUsable() {
        return this == ACTIVE;
    }
}