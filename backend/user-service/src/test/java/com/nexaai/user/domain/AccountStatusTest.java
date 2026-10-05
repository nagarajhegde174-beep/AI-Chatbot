package com.nexaai.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The account status lifecycle.
 *
 * <p>These tests exist because the rules here are the ones an administrator can violate by
 * clicking the wrong button, and because {@code DEACTIVATED} being terminal is the kind of
 * rule that gets quietly relaxed by a later change without anyone noticing.
 */
class AccountStatusTest {

    @Nested
    @DisplayName("allowed transitions")
    class Allowed {

        @Test
        void pendingVerificationActivates() {
            assertThat(AccountStatus.PENDING_VERIFICATION.canTransitionTo(AccountStatus.ACTIVE))
                    .isTrue();
        }

        @Test
        void activeSuspends() {
            assertThat(AccountStatus.ACTIVE.canTransitionTo(AccountStatus.SUSPENDED)).isTrue();
        }

        @Test
        void suspendedReactivates() {
            assertThat(AccountStatus.SUSPENDED.canTransitionTo(AccountStatus.ACTIVE)).isTrue();
        }

        @Test
        @DisplayName("an already-suspended account can be suspended again, without history loss")
        void suspendedToSuspended() {
            // Permitted so a reason can be corrected. The history table is append-only, so
            // the earlier entry is preserved rather than overwritten.
            assertThat(AccountStatus.SUSPENDED.canTransitionTo(AccountStatus.SUSPENDED)).isTrue();
        }
    }

    @Nested
    @DisplayName("forbidden transitions")
    class Forbidden {

        @Test
        @DisplayName("DEACTIVATED is terminal")
        void deactivatedIsTerminal() {
            for (AccountStatus target : AccountStatus.values()) {
                assertThat(AccountStatus.DEACTIVATED.canTransitionTo(target))
                        .as("DEACTIVATED must not move to %s", target)
                        .isFalse();
            }
        }

        @Test
        @DisplayName("PENDING_VERIFICATION cannot go straight to itself")
        void pendingToPending() {
            assertThat(AccountStatus.PENDING_VERIFICATION
                    .canTransitionTo(AccountStatus.PENDING_VERIFICATION)).isFalse();
        }

        @Test
        @DisplayName("ACTIVE cannot move to PENDING_VERIFICATION: un-verifying is not a thing")
        void activeToPending() {
            assertThat(AccountStatus.ACTIVE.canTransitionTo(AccountStatus.PENDING_VERIFICATION))
                    .isFalse();
        }

        @Test
        void noStatusTransitionsToItselfOtherThanSuspended() {
            for (AccountStatus status : AccountStatus.values()) {
                if (status == AccountStatus.SUSPENDED) {
                    continue;
                }
                assertThat(status.canTransitionTo(status))
                        .as("%s -> %s should be refused", status, status)
                        .isFalse();
            }
        }
    }

    @Test
    @DisplayName("only ACTIVE counts as usable")
    void onlyActiveIsUsable() {
        assertThat(AccountStatus.ACTIVE.isUsable()).isTrue();
        for (AccountStatus status : AccountStatus.values()) {
            if (status != AccountStatus.ACTIVE) {
                assertThat(status.isUsable()).as("%s must not be usable", status).isFalse();
            }
        }
    }

    @Test
    @DisplayName("the transition exception names both ends of the rejected move")
    void exceptionCarriesBothStatuses() {
        // canTransitionTo answers the question rather than throwing; the entity throws when it
        // is asked to perform a refused move. Both halves are asserted, because the exception
        // is what reaches the API client and the message is all it has to go on.
        assertThat(AccountStatus.DEACTIVATED.canTransitionTo(AccountStatus.ACTIVE)).isFalse();

        IllegalStateTransitionException e =
                new IllegalStateTransitionException(AccountStatus.DEACTIVATED, AccountStatus.ACTIVE);

        assertThat(e.getFrom()).isEqualTo(AccountStatus.DEACTIVATED);
        assertThat(e.getTo()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(e.getMessage()).contains("DEACTIVATED").contains("ACTIVE");
    }

    @Test
    @DisplayName("the entity refuses an illegal transition by throwing")
    void entityThrowsOnIllegalTransition() {
        UserProfile profile = UserProfile.fromRegistration(
                UUID.randomUUID(), "closed@example.com", "Closed", true,
                UserProfile.RegisteredVia.PASSWORD, AccountStatus.DEACTIVATED, Role.USER);

        assertThatThrownBy(() -> profile.applyStatusChange(
                AccountStatus.ACTIVE, "Undo", UUID.randomUUID()))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @ParameterizedTest
    @EnumSource(AccountStatus.class)
    @DisplayName("every status has a name the database CHECK constraint accepts")
    void everyStatusIsPersistable(AccountStatus status) {
        // The constraint lists the four names literally. If a fifth status is ever added to
        // the enum without updating V1__user_schema.sql, this catches it only if the constraint
        // list is derived here too, so assert against the same set the migration declares.
        assertThat(status.name()).isIn("PENDING_VERIFICATION", "ACTIVE", "SUSPENDED",
                "DEACTIVATED");
    }
}