package com.nexaai.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * An authentication account.
 *
 * <p>This entity is the only place a credential exists. It never leaves this service: no
 * DTO exposes {@link #getPasswordHash()}, and {@code toString} deliberately omits it.
 *
 * <p>Business rules that touch more than one field are enforced by methods on this class
 * rather than by the service layer, so they cannot be bypassed by a second caller.
 */
@Entity
@Table(name = "auth_user")
public class AuthUser {

    /**
     * Identity, assigned by the entity itself rather than by the persistence layer.
     *
     * <p>An object that only receives its id when it is saved is an object that can be used
     * before it is valid. Assigning here means a freshly constructed account is immediately
     * safe to pass to a service that logs or references it, and it makes the failure mode
     * "the id is always there" instead of "the id is null until you remember to save".
     */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    /**
     * Email, always stored lower-cased and trimmed.
     *
     * <p>Normalising on write is what makes the unique index a genuine duplicate-email
     * guarantee. Without it {@code User@Example.com} and {@code user@example.com} would be
     * two accounts, and a user could register twice in a way that looks like a bug.
     */
    @Column(name = "email", nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    /**
     * Argon2id hash. Never the plaintext, never logged, never returned.
     *
     * <p>Null for an account that exists only through Google OAuth, which has no password.
     * A NOT NULL column would force a fake hash to exist for those accounts.
     */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    /**
     * Incremented whenever the password changes.
     *
     * <p>Tokens carry this value; verification compares it. Without it, changing a password
     * would leave every existing session valid, which is the most common way password
     * changes quietly fail to do their job.
     */
    @Column(name = "credentials_version", nullable = false)
    private int credentialsVersion = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AccountStatus status = AccountStatus.PENDING_VERIFICATION;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private Role role = Role.USER;

    /** Google's stable user id, when linked. Unique, so one Google identity maps to one account. */
    @Column(name = "google_subject", unique = true, length = 255)
    private String googleSubject;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected AuthUser() {
        // for JPA
    }

    /** Creates a locally-registered, unverified account. */
    public static AuthUser register(String rawEmail, String passwordHash, String displayName) {
        AuthUser user = new AuthUser();
        user.email = EmailNormalizer.normalize(rawEmail);
        user.passwordHash = passwordHash;
        user.displayName = displayName;
        user.status = AccountStatus.PENDING_VERIFICATION;
        user.role = Role.USER;
        user.emailVerified = false;
        user.createdAt = Instant.now();
        user.updatedAt = user.createdAt;
        return user;
    }

    /** Creates an already-verified account from a Google identity. */
    public static AuthUser fromGoogle(String rawEmail, String googleSubject, String displayName) {
        AuthUser user = new AuthUser();
        user.email = EmailNormalizer.normalize(rawEmail);
        user.googleSubject = googleSubject;
        user.displayName = displayName;
        // Google asserts the email is verified, so re-verifying it would be theatre.
        user.emailVerified = true;
        user.status = AccountStatus.ACTIVE;
        user.role = Role.USER;
        user.createdAt = Instant.now();
        user.updatedAt = user.createdAt;
        return user;
    }

    // ------------------------------------------------------------------
    // Behaviour
    // ------------------------------------------------------------------

    /**
     * Marks the email verified and activates the account.
     *
     * <p><strong>Only a pending account is activated here.</strong> This matters: an earlier
     * version asked {@code status.canTransitionTo(ACTIVE)}, which is true for SUSPENDED. That
     * meant anyone with a stale verification link could click it and lift an administrator's
     * suspension — a real bypass of the one control that stops a suspended user signing in.
     * A test now pins this.
     *
     * <p>Idempotent, because a verification link may legitimately be followed twice: the user
     * clicks, the response is lost, they clicks again. The second call must succeed rather
     * than report an error for work that is already done.
     */
    public void verifyEmail() {
        emailVerified = true;
        if (status == AccountStatus.PENDING_VERIFICATION) {
            status = AccountStatus.ACTIVE;
        }
        touch();
    }

    /**
     * Replaces the password hash and invalidates existing sessions.
     *
     * <p>Also clears lockout state. Someone who just proved they can reset their password
     * is not an attacker, and leaving them locked out would lock a legitimate user out of
     * the account they just recovered.
     */
    public void changePasswordHash(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.credentialsVersion++;
        this.failedLoginCount = 0;
        this.lockedUntil = null;
        touch();
    }

    /**
     * Links a Google identity to this account and marks the email verified.
     *
     * <p>Like {@link #verifyEmail()}, this activates only a pending account. Linking a Google
     * identity must not lift a suspension for the same reason verification must not.
     */
    public void linkGoogle(String subject) {
        this.googleSubject = subject;
        this.emailVerified = true;
        if (status == AccountStatus.PENDING_VERIFICATION) {
            status = AccountStatus.ACTIVE;
        }
        touch();
    }

    /** Records a successful sign-in. */
    public void recordSuccessfulLogin() {
        this.failedLoginCount = 0;
        this.lockedUntil = null;
        this.lastLoginAt = Instant.now();
        touch();
    }

    /**
     * Records a failed sign-in and locks the account once the threshold is reached.
     *
     * @return true when this failure caused a lock
     */
    public boolean recordFailedLogin(int maxAttempts, Duration lockDuration) {
        this.failedLoginCount++;
        this.updatedAt = Instant.now();
        if (this.failedLoginCount >= maxAttempts && !isLocked()) {
            this.lockedUntil = Instant.now().plus(lockDuration);
            return true;
        }
        return false;
    }

    /** Whether the account is currently locked out. */
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    /** Whether this account may sign in at all. */
    public boolean canAuthenticate() {
        return status.canAuthenticate() && emailVerified && !isLocked();
    }

    /**
     * Transitions status, rejecting an illegal move.
     *
     * @throws IllegalStateTransitionException when the transition is not permitted
     */
    public void transitionTo(AccountStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateTransitionException(status, target);
        }
        this.status = target;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getCredentialsVersion() {
        return credentialsVersion;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Role getRole() {
        return role;
    }

    public String getGoogleSubject() {
        return googleSubject;
    }

    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Deliberately omits the password hash.
     *
     * <p>A default {@code toString} including every field is a credential in a log line.
     * This override exists because forgetting it is the actual failure mode, not an edge
     * case.
     */
    @Override
    public String toString() {
        return "AuthUser{id=%s, email=%s, status=%s, role=%s, emailVerified=%s}"
                .formatted(id, email, status, role, emailVerified);
    }
}
