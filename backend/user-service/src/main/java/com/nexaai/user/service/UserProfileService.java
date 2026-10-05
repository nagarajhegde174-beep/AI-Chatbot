package com.nexaai.user.service;

import com.nexaai.user.domain.UserPreference;
import com.nexaai.user.domain.UserProfile;
import com.nexaai.user.domain.UserStatusHistory;
import com.nexaai.user.exception.UserNotFoundException;
import com.nexaai.user.repository.UserProfileRepository;
import com.nexaai.user.repository.UserStatusHistoryRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The self-service side of User Service: a caller reading and updating their own profile,
 * preferences and account status.
 *
 * <p><strong>Every method here is scoped to the caller.</strong> There is no
 * {@code findProfile(UUID)} on this class. A service method that takes an arbitrary user id is
 * one call away from being reachable from a request parameter, which is exactly how one
 * service ends up serving another user's data. The administrative path is a separate class
 * ({@link AdminUserService}) with an explicit role check on every method.
 */
@Service
@Transactional
public class UserProfileService {

    private final UserProfileRepository profiles;
    private final UserStatusHistoryRepository history;

    public UserProfileService(UserProfileRepository profiles, UserStatusHistoryRepository history) {
        this.profiles = profiles;
        this.history = history;
    }

    /**
     * Loads the caller's own profile.
     *
     * @throws UserNotFoundException if the caller holds a valid token for an account whose
     *                               profile has not been created yet. Reachable in the window
     *                               between registration and the {@code auth.user.registered.v1}
     *                               event being consumed.
     */
    @Transactional(readOnly = true)
    public UserProfile getOwnProfile(UUID callerAuthUserId) {
        return profiles.findByAuthUserId(callerAuthUserId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new UserNotFoundException(callerAuthUserId));
    }

    /** Updates the caller's own display name and avatar. */
    public UserProfile updateOwnProfile(UUID callerAuthUserId, String displayName, String avatarUrl) {
        UserProfile profile = getOwnProfile(callerAuthUserId);
        profile.updateProfile(displayName, avatarUrl);
        return profiles.save(profile);
    }

    /** Applies a partial preferences update to the caller's own row. */
    public UserProfile updateOwnPreferences(UUID callerAuthUserId,
                                            UserPreference.Theme theme, String locale,
                                            String defaultModel, Boolean ragEnabled,
                                            Boolean streamResponses, Boolean emailNotifications,
                                            Boolean productUpdates) {
        UserProfile profile = getOwnProfile(callerAuthUserId);
        profile.getPreference().update(
                theme, locale, defaultModel, ragEnabled, streamResponses,
                emailNotifications, productUpdates);
        return profiles.save(profile);
    }

    /**
     * The caller's own status history.
     *
     * <p>Shown to the account holder so a suspension is never unexplained. It exposes that a
     * change happened, its direction, and the reason given; it does not expose which
     * administrator made it, because the holder does not need the operator's identity.
     */
    @Transactional(readOnly = true)
    public java.util.List<UserStatusHistory> getOwnStatusHistory(UUID callerAuthUserId) {
        UserProfile profile = getOwnProfile(callerAuthUserId);
        return history.findByUserProfileIdOrderByChangedAtDesc(profile.getId()).stream()
                .map(UserStatusHistory::withoutActor)
                .toList();
    }
}