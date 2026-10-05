package com.nexaai.user.web.dto;

import com.nexaai.user.domain.AccountStatus;
import com.nexaai.user.domain.Role;
import com.nexaai.user.domain.UserStatusHistory;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The administrative view of one user: profile, preferences, and the full status history.
 *
 * <p>Returned only to an ADMIN. The status history is included here and nowhere else, because
 * who suspended an account and why is exactly the material that must not reach the subject of
 * the suspension beyond the reason itself.
 */
public record AdminUserDetail(
        UserProfileResponse profile,
        PreferenceResponse preferences,
        List<StatusChangeEntry> statusHistory) {

    /** One entry in the append-only status history. */
    public record StatusChangeEntry(
            UUID id,
            AccountStatus fromStatus,
            AccountStatus toStatus,
            String reason,
            UUID changedBy,
            Instant changedAt) {

        public static StatusChangeEntry from(UserStatusHistory h) {
            return new StatusChangeEntry(
                    h.getId(),
                    h.getFromStatus(),
                    h.getToStatus(),
                    h.getReason(),
                    h.getChangedBy(),
                    h.getChangedAt());
        }
    }

    public static AdminUserDetail from(UserProfileResponse profile,
                                       PreferenceResponse preferences,
                                       List<UserStatusHistory> history) {
        return new AdminUserDetail(
                profile,
                preferences,
                history.stream().map(StatusChangeEntry::from).toList());
    }
}