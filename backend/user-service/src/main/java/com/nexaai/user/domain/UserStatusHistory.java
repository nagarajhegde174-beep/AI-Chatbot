package com.nexaai.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * An immutable record of an account status change. Audit, not business state: the current
 * status is the field on the profile.
 *
 * <p><strong>Append-only.</strong> A row here is never updated or deleted. That is what makes
 * "was this account suspended, and why" answerable months later, and it is why an
 * administrator cannot quietly erase having suspended someone.
 *
 * <p>An {@link Entity} and not a Java record: Hibernate cannot persist a record without
 * additional plumbing, and pretending otherwise costs more than the immutability a record
 * would give. The immutability is enforced here instead, by there being no setter and by
 * {@link #of} being the only way to build one.
 */
@Entity
@Table(name = "user_status_history")
public class UserStatusHistory {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_profile_id", nullable = false)
    private UUID userProfileId;

    /** Null only for the initial creation of a profile. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32)
    private AccountStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private AccountStatus toStatus;

    @Column(name = "reason", length = 255)
    private String reason;

    /** The administrator who made the change. Null for a system-driven change. */
    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected UserStatusHistory() {
        // for JPA
    }

    private UserStatusHistory(UUID id, UUID userProfileId, AccountStatus fromStatus,
                             AccountStatus toStatus, String reason, UUID changedBy,
                             Instant changedAt) {
        this.id = id;
        this.userProfileId = userProfileId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    /** Records a status change. The only way to build one of these. */
    public static UserStatusHistory of(UUID userProfileId, AccountStatus from, AccountStatus to,
                                       String reason, UUID changedBy) {
        return new UserStatusHistory(UUID.randomUUID(), userProfileId, from, to,
                (reason == null || reason.isBlank()) ? null : reason.trim(),
                changedBy, Instant.now());
    }

    /**
     * A copy with the acting administrator removed, for showing history to the account holder.
     *
     * <p>A copy rather than a mutation, precisely because the real row must keep the actor:
     * the audit trail is the reason this table exists, and redacting it in place would defeat
     * that.
     */
    public UserStatusHistory withoutActor() {
        return new UserStatusHistory(id, userProfileId, fromStatus, toStatus, reason, null,
                changedAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserProfileId() {
        return userProfileId;
    }

    public AccountStatus getFromStatus() {
        return fromStatus;
    }

    public AccountStatus getToStatus() {
        return toStatus;
    }

    public String getReason() {
        return reason;
    }

    public UUID getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}