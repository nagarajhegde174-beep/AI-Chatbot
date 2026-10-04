package com.nexaai.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The account state machine and the credential rules that hang off it.
 *
 * <p>These are pure domain tests: no Spring, no database. They exist because the rules they
 * cover are the ones that must hold no matter which caller mutates an account.
 */
class AuthUserTest {

    private static final String HASH = "$argon2id$v=19$m=65536,t=3,p=1$c2FsdA$aGFzaA";

    @Nested
    @DisplayName("registration")
    class Registration {

        @Test
        @DisplayName("a new account is pending verification, unverified, and a USER")
        void newAccountStartsPending() {
            AuthUser user = AuthUser.register("  Ada@Example.COM ", HASH, "Ada");

            assertThat(user.getEmail()).isEqualTo("ada@example.com");
            assertThat(user.getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
            assertThat(user.isEmailVerified()).isFalse();
            assertThat(user.getRole()).isEqualTo(Role.USER);
            assertThat(user.canAuthenticate()).isFalse();
        }

        @Test
        @DisplayName("email is normalised so uniqueness is a real guarantee")
        void emailIsNormalised() {
            assertThat(AuthUser.register("USER@Example.COM", HASH, "U").getEmail())
                    .isEqualTo("user@example.com");
            assertThat(AuthUser.register("  spaced@x.com  ", HASH, "U").getEmail())
                    .isEqualTo("spaced@x.com");
        }

        @Test
        @DisplayName("toString omits the password hash")
        void toStringOmitsHash() {
            // A default toString including every field is a credential in a log line.
            String text = AuthUser.register("a@b.com", HASH, "A").toString();

            assertThat(text).doesNotContain(HASH);
            assertThat(text).doesNotContain("passwordHash");
            assertThat(text).contains("a@b.com");
        }
    }

    @Nested
    @DisplayName("email verification")
    class Verification {

        @Test
        @DisplayName("verifying activates a pending account")
        void verificationActivates() {
            AuthUser user = AuthUser.register("a@b.com", HASH, "A");
            user.verifyEmail();

            assertThat(user.isEmailVerified()).isTrue();
            assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(user.canAuthenticate()).isTrue();
        }

        @Test
        @DisplayName("verifying twice is not an error")
        void verificationIsIdempotent() {
            // A user clicks the link, loses the response, clicks again. The second call must
            // succeed rather than report failure for work already done.
            AuthUser user = AuthUser.register("a@b.com", HASH, "A");
            user.verifyEmail();
            user.verifyEmail();

            assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(user.isEmailVerified()).isTrue();
        }
    }

    @Nested
    @DisplayName("password change")
    class PasswordChange {

        @Test
        @DisplayName("changing the password bumps the credentials version")
        void credentialsVersionBumps() {
            AuthUser user = AuthUser.register("a@b.com", HASH, "A");
            assertThat(user.getCredentialsVersion()).isEqualTo(1);

            user.changePasswordHash("new-hash");

            // Without this, a password change would leave every existing session valid.
            assertThat(user.getCredentialsVersion()).isEqualTo(2);
        }

        @Test
        @DisplayName("changing the password clears lockout")
        void changeClearsLockout() {
            AuthUser user = AuthUser.register("a@b.com", HASH, "A");
            user.recordFailedLogin(3, Duration.ofMinutes(15));
            assertThat(user.isLocked()).isFalse(); // 1 failure, threshold 3

            user.recordFailedLogin(3, Duration.ofMinutes(15));
            user.recordFailedLogin(3, Duration.ofMinutes(15));
            assertThat(user.isLocked()).isTrue();

            user.changePasswordHash("new-hash");

            // Someone who just reset their password is not an attacker. Locking them out would
            // lock a legitimate owner out of the account they just recovered.
            assertThat(user.isLocked()).isFalse();
            assertThat(user.getFailedLoginCount()).isZero();
        }
    }

    @Nested
    @DisplayName("lockout")
    class Lockout {

        @Test
        @DisplayName("locks at the threshold and reports that it did")
        void locksAtThreshold() {
            AuthUser user = AuthUser.register("a@b.com", HASH, "A");

            assertThat(user.recordFailedLogin(3, Duration.ofMinutes(15))).isFalse();
            assertThat(user.recordFailedLogin(3, Duration.ofMinutes(15))).isFalse();
            assertThat(user.recordFailedLogin(3, Duration.ofMinutes(15))).isTrue();

            assertThat(user.isLocked()).isTrue();
            assertThat(user.canAuthenticate()).isFalse();
        }

        @Test
        @DisplayName("an expired lock no longer counts as locked")
        void lockExpires() {
            AuthUser user = AuthUser.register("a@b.com", HASH, "A");
            user.recordFailedLogin(1, Duration.ofMinutes(15));
            assertThat(user.isLocked()).isTrue();

            // Simulate the window passing.
            user.verifyEmail();
            user.changePasswordHash("h");
            user.recordFailedLogin(1, Duration.ofSeconds(-1));

            assertThat(user.isLocked()).isFalse();
        }

        @Test
        @DisplayName("a successful sign-in clears the failure count")
        void successClearsFailures() {
            AuthUser user = AuthUser.register("a@b.com", HASH, "A");
            user.recordFailedLogin(5, Duration.ofMinutes(15));
            user.recordFailedLogin(5, Duration.ofMinutes(15));

            user.recordSuccessfulLogin();

            assertThat(user.getFailedLoginCount()).isZero();
            assertThat(user.getLastLoginAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("status transitions")
    class Transitions {

        @Test
        @DisplayName("only ACTIVE can sign in")
        void onlyActiveAuthenticates() {
            assertThat(AccountStatus.ACTIVE.canAuthenticate()).isTrue();
            assertThat(AccountStatus.PENDING_VERIFICATION.canAuthenticate()).isFalse();
            assertThat(AccountStatus.SUSPENDED.canAuthenticate()).isFalse();
            assertThat(AccountStatus.DEACTIVATED.canAuthenticate()).isFalse();
        }

        @Test
        @DisplayName("an active account can be suspended and reinstated")
        void suspendAndReinstate() {
            AuthUser user = AuthUser.register("a@b.com", HASH, "A");
            user.verifyEmail();

            user.transitionTo(AccountStatus.SUSPENDED);
            assertThat(user.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
            assertThat(user.canAuthenticate()).isFalse();

            user.transitionTo(AccountStatus.ACTIVE);
            assertThat(user.canAuthenticate()).isTrue();
        }

        @Test
        @DisplayName("DEACTIVATED is terminal")
        void deactivatedIsTerminal() {
            AuthUser user = AuthUser.register("a@b.com", HASH, "A");
            user.verifyEmail();
            user.transitionTo(AccountStatus.DEACTIVATED);

            // A closed account is never revived. Silently ignoring the attempt would leave the
            // caller thinking it worked.
            assertThatThrownBy(() -> user.transitionTo(AccountStatus.ACTIVE))
                    .isInstanceOf(IllegalStateTransitionException.class);
            assertThat(user.getStatus()).isEqualTo(AccountStatus.DEACTIVATED);
        }

        @Test
        @DisplayName("an illegal transition reports both states")
        void illegalTransitionCarriesContext() {
            AuthUser user = AuthUser.register("a@b.com", HASH, "A");
            user.verifyEmail();
            user.transitionTo(AccountStatus.DEACTIVATED);

            assertThatThrownBy(() -> user.transitionTo(AccountStatus.ACTIVE))
                    .isInstanceOfSatisfying(IllegalStateTransitionException.class, e -> {
                        assertThat(e.getFrom()).isEqualTo(AccountStatus.DEACTIVATED);
                        assertThat(e.getTo()).isEqualTo(AccountStatus.ACTIVE);
                    });
        }

        @Test
        @DisplayName("a suspended account cannot skip straight to verified")
        void noUnverifiedResurrection() {
            AuthUser user = AuthUser.register("a@b.com", HASH, "A");
            user.verifyEmail();
            user.transitionTo(AccountStatus.SUSPENDED);

            // verifyEmail must not silently reactivate a suspended account.
            user.verifyEmail();
            assertThat(user.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        }
    }

    @Nested
    @DisplayName("Google linking")
    class GoogleLinking {

        @Test
        @DisplayName("a Google account is verified and active from the start")
        void googleAccountStartsActive() {
            AuthUser user = AuthUser.fromGoogle("g@x.com", "google-subject-1", "G");

            assertThat(user.getGoogleSubject()).isEqualTo("google-subject-1");
            assertThat(user.isEmailVerified()).isTrue();
            assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
            // No Google password is ever stored.
            assertThat(user.getPasswordHash()).isNull();
        }

        @Test
        @DisplayName("linking an existing account verifies it without resurrecting a suspension")
        void linkDoesNotResurrectSuspension() {
            AuthUser user = AuthUser.register("a@b.com", HASH, "A");
            user.verifyEmail();
            user.transitionTo(AccountStatus.SUSPENDED);

            user.linkGoogle("google-subject-2");

            assertThat(user.getGoogleSubject()).isEqualTo("google-subject-2");
            assertThat(user.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        }
    }
}