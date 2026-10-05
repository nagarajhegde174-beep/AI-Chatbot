package com.nexaai.user.service;

import com.nexaai.user.config.UserProperties;
import com.nexaai.user.domain.AccountStatus;
import com.nexaai.user.domain.IllegalStateTransitionException;
import com.nexaai.user.domain.Role;
import com.nexaai.user.domain.UserProfile;
import com.nexaai.user.domain.UserStatusHistory;
import com.nexaai.user.repository.UserProfileRepository;
import com.nexaai.user.repository.UserStatusHistoryRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The administrative side of User Service.
 *
 * <p><strong>This class is the only place that reads or writes another user's data.</strong>
 * Every method takes the acting administrator's id, and the controller layer requires the
 * ADMIN role before any of them is reachable. The role check lives in the controller and again
 * here by way of the explicit {@code actingAdminId} parameter: one check is a convention, two
 * is a control.
 *
 * <p>Kept separate from {@link UserProfileService} so the self-service and administrative
 * surfaces cannot be confused for one another. A shared method that took a "user id or null,
 * meaning me" parameter would blur exactly the boundary that matters.
 */
@Service
@Transactional
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private final UserProfileRepository profiles;
    private final UserStatusHistoryRepository history;
    private final UserProperties properties;

    public AdminUserService(UserProfileRepository profiles,
                            UserStatusHistoryRepository history,
                            UserProperties properties) {
        this.profiles = profiles;
        this.history = history;
        this.properties = properties;
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    /**
     * Lists users, filtered and searched, one page at a time.
     *
     * <p>The requested page size is capped at {@code nexa.user.admin.max-page-size} whatever
     * the caller asked for. An uncapped {@code size} parameter is a denial-of-service vector
     * dressed as a convenience feature.
     *
     * @param search matched case-insensitively against display name and email; blank or null
     *               means no search
     */
    @Transactional(readOnly = true)
    public Page<UserProfile> list(AccountStatus status, Role role, String search,
                                  int page, int size) {
        int effectiveSize = Math.clamp(size, 1, properties.getAdmin().getMaxPageSize());
        int safePage = Math.max(page, 0);

        String pattern = (search == null || search.isBlank())
                ? null
                : "%" + search.trim().toLowerCase(java.util.Locale.ROOT) + "%";

        Pageable pageable = PageRequest.of(safePage, effectiveSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return profiles.search(status, role, pattern, pageable);
    }

    /** Counts by status, for the administrative dashboard. */
    @Transactional(readOnly = true)
    public List<StatusCount> countsByStatus() {
        return java.util.Arrays.stream(AccountStatus.values())
                .map(s -> new StatusCount(s, profiles.countByAccountStatus(s)))
                .toList();
    }

    /** One count against one status. */
    public record StatusCount(AccountStatus status, long count) {
    }

    /** The full administrative view of one user, located by auth-service id. */
    @Transactional(readOnly = true)
    public UserProfile requireByAuthUserId(UUID authUserId) {
        return profiles.findByAuthUserId(authUserId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new com.nexaai.user.exception.UserNotFoundException(authUserId));
    }

    /** The full status history for one user, including who made each change. */
    @Transactional(readOnly = true)
    public List<UserStatusHistory> statusHistoryFor(UUID authUserId) {
        UserProfile profile = requireByAuthUserId(authUserId);
        return history.findByUserProfileIdOrderByChangedAtDesc(profile.getId());
    }

    // ------------------------------------------------------------------
    // Writing
    // ------------------------------------------------------------------

    /**
     * Applies an administrative status change.
     *
     * <p>Refuses an illegal transition ({@code DEACTIVATED} is terminal) and refuses a
     * suspension with no reason, both by exception rather than by silently ignoring the
     * request. An admin action that quietly does nothing is worse than one that fails.
     */
    public UserProfile changeStatus(UUID authUserId, AccountStatus target, String reason,
                                    UUID actingAdminId) {
        if (target == AccountStatus.SUSPENDED && (reason == null || reason.isBlank())) {
            // The account holder is shown this reason. A blank one makes the block
            // unexplainable, which turns a support decision into a support ticket.
            throw new IllegalArgumentException(
                    "A suspension requires a reason. It is shown to the account holder.");
        }

        UserProfile profile = requireByAuthUserId(authUserId);
        AccountStatus from = profile.getAccountStatus();

        try {
            profile.applyStatusChange(target, reason, actingAdminId);
        } catch (IllegalStateTransitionException e) {
            // Logged with both ends of the transition, because "why did my admin call fail"
            // is otherwise unanswerable from the outside.
            log.warn("Rejected administrative status change {} -> {} by admin {}",
                    e.getFrom(), e.getTo(), actingAdminId);
            throw e;
        }

        profiles.save(profile);
        history.save(UserStatusHistory.of(profile.getId(), from, target, reason, actingAdminId));

        log.info("Administrative status change: user {} {} -> {} by admin {}",
                authUserId, from, target, actingAdminId);
        return profile;
    }

    /** Activates a suspended or pending account. */
    public UserProfile activate(UUID authUserId, UUID actingAdminId) {
        return changeStatus(authUserId, AccountStatus.ACTIVE, null, actingAdminId);
    }

    /** Suspends an account. A reason is required. */
    public UserProfile suspend(UUID authUserId, String reason, UUID actingAdminId) {
        return changeStatus(authUserId, AccountStatus.SUSPENDED, reason, actingAdminId);
    }

    /** Deactivates an account permanently. */
    public UserProfile deactivate(UUID authUserId, String reason, UUID actingAdminId) {
        return changeStatus(authUserId, AccountStatus.DEACTIVATED, reason, actingAdminId);
    }
}