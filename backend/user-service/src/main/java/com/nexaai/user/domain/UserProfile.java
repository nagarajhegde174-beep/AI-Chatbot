package com.nexaai.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A user profile.
 *
 * <p><strong>No credential is stored here, and none can be.</strong> There is no password
 * field, no token field and no secret field. This service does not know a password and cannot
 * verify one, because Auth Service owns the only copy of the hash and this service is not
 * permitted to read that database ({@code docs/RULES.md} §3).
 *
 * <p>A profile exists only because {@code auth.user.registered.v1} was consumed. There is no
 * self-registration here: registration is auth-service's responsibility.
 */
@Entity
@Table(name = "user_profile")
public class UserProfile {

    /** How the account was originally created. */
    public enum RegisteredVia {
        PASSWORD, GOOGLE
    }

    /** NexaAI's internal surrogate key. Never an authorisation key. */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UUID.randomUUID();

    /**
     * The stable identifier issued by auth-service.
     *
     * <p>Authorisation keys on this, never on the email. An email can change; this cannot, so
     * keying on it would let a user change their identity by changing their address.
     */
    @Column(name = "auth_user_id", nullable = false, unique = true)
    private UUID authUserId;

    @Column(name = "email", nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "avatar_url", length = 1024)
    private String avatarUrl;

    /** Administrative projection of auth-service's status. Not authoritative. */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 32)
    private AccountStatus accountStatus = AccountStatus.PENDING_VERIFICATION;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private Role role = Role.USER;

    /**
     * Why the account was suspended or closed.
     *
     * <p>Surfaced to the account holder. A user who cannot see why their account is blocked
     * cannot do anything about it.
     */
    @Column(name = "status_reason", length = 255)
    private String statusReason;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Column(name = "status_changed_by")
    private UUID statusChangedBy;

    /** Administrative projection, refreshed from subscription-service events. */
    @Column(name = "plan_id", length = 64)
    private String planId;

    @Column(name = "plan_name", length = 120)
    private String planName;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Enumerated(EnumType.STRING)
    @Column(name = "registered_via", nullable = false, length = 16)
    private RegisteredVia registeredVia = RegisteredVia.PASSWORD;

    @Embedded
    private UserPreference preference = UserPreference.defaults();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /**
     * Soft-delete timestamp. The row is retained so an audit trail and any derived data in
     * other services keep resolving; listings exclude deleted rows.
     */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected UserProfile() {
        // for JPA
    }

    /** Creates the profile that {@code auth.user.registered.v1} describes. */
    public static UserProfile fromRegistration(UUID authUserId, String email, String displayName,
                                              boolean emailVerified, RegisteredVia via,
                                              AccountStatus status, Role role) {
        UserProfile profile = new UserProfile();
        profile.authUserId = authUserId;
        profile.email = EmailNormalizer.normalize(email);
        profile.displayName = displayName;
        profile.emailVerified = emailVerified;
        profile.registeredVia = via;
        profile.accountStatus = status;
        profile.role = role;
        profile.preference = UserPreference.defaults();
        profile.createdAt = Instant.now();
        profile.updatedAt = profile.createdAt;
        return profile;
    }

    // ------------------------------------------------------------------
    // Behaviour
    // ------------------------------------------------------------------

    /**
     * Updates the fields the user is allowed to change about themselves.
     *
     * <p>Deliberately cannot change email, role, status or verification state. Those are
     * owned by auth-service and by administrative action, and a user-controlled endpoint that
     * can write them is a privilege escalation waiting to happen.
     */
    public void updateProfile(String displayName, String avatarUrl) {
        if (displayName != null && !displayName.isBlank()) {
            this.displayName = displayName.trim();
        }
        this.avatarUrl = (avatarUrl == null || avatarUrl.isBlank()) ? null : avatarUrl.trim();
        touch();
    }

    /**
     * Applies a status change made by an administrator.
     *
     * @throws IllegalStateTransitionException if the lifecycle forbids the move
     */
    public void applyStatusChange(AccountStatus target, String reason, UUID changedBy) {
        if (!accountStatus.canTransitionTo(target)) {
            throw new IllegalStateTransitionException(accountStatus, target);
        }
        this.accountStatus = target;
        this.statusReason = (reason == null || reason.isBlank()) ? null : reason.trim();
        this.statusChangedAt = Instant.now();
        this.statusChangedBy = changedBy;
        touch();
    }

    /** Applies a status projected from an auth-service event. */
    public void applyProjectedStatus(AccountStatus target) {
        this.accountStatus = target;
        this.statusChangedAt = Instant.now();
        touch();
    }

    public void markEmailVerified() {
        this.emailVerified = true;
        touch();
    }

    /** Updates the subscription projection carried on {@code subscription.*} events. */
    public void applySubscription(String planId, String planName) {
        this.planId = planId;
        this.planName = planName;
        touch();
    }

    /** Soft-deletes the account. */
    public void softDelete() {
        this.deletedAt = Instant.now();
        touch();
    }

    public boolean isDeleted() {
        return deletedAt != null;
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

    public UUID getAuthUserId() {
        return authUserId;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public Role getRole() {
        return role;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    public UUID getStatusChangedBy() {
        return statusChangedBy;
    }

    public String getPlanId() {
        return planId;
    }

    public String getPlanName() {
        return planName;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public RegisteredVia getRegisteredVia() {
        return registeredVia;
    }

    public UserPreference getPreference() {
        return preference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    /**
     * Omits the email and identifiers.
     *
     * <p>A default {@code toString} on an entity is how an email address ends up in a log
     * line nobody intended.
     */
    @Override
    public String toString() {
        return "UserProfile{id=%s, status=%s, role=%s}".formatted(id, accountStatus, role);
    }
}