package com.nexaai.user.web;

import com.nexaai.user.domain.UserProfile;
import com.nexaai.user.domain.UserStatusHistory;
import com.nexaai.user.security.CurrentCaller;
import com.nexaai.user.service.UserProfileService;
import com.nexaai.user.web.dto.PreferenceResponse;
import com.nexaai.user.web.dto.UpdatePreferenceRequest;
import com.nexaai.user.web.dto.UpdateProfileRequest;
import com.nexaai.user.web.dto.UserProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A caller's own profile, preferences and status.
 *
 * <p><strong>No path or parameter here names a user.</strong> Every route is about "me". The
 * caller is read from the security context, never from the request. This is the single most
 * important property of this controller: if it accepted a user id, one forgotten
 * authorisation check anywhere in the chain would serve another user's data.
 *
 * <p>All routes sit under {@code /api/v1/me}, so they are separate from the administrative
 * routes under {@code /api/v1/admin/users} at the routing level, not only by a role check.
 */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Profile", description = "The authenticated caller's own profile, preferences and status.")
public class MeController {

    private final UserProfileService profiles;

    public MeController(UserProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    @Operation(summary = "Get your own profile")
    public UserProfileResponse getOwnProfile() {
        return UserProfileResponse.from(profiles.getOwnProfile(CurrentCaller.authUserId()));
    }

    @PatchMapping
    @Operation(summary = "Update your own profile",
            description = "Updates display name and avatar only. Email, role and status are "
                    + "not accepted here: they are owned elsewhere and accepting them would be "
                    + "a privilege escalation.")
    public UserProfileResponse updateOwnProfile(@Valid @RequestBody UpdateProfileRequest request) {
        UserProfile updated = profiles.updateOwnProfile(
                CurrentCaller.authUserId(), request.displayName(), request.avatarUrl());
        return UserProfileResponse.from(updated);
    }

    @GetMapping("/preferences")
    @Operation(summary = "Get your own preferences")
    public PreferenceResponse getOwnPreferences() {
        return PreferenceResponse.from(profiles.getOwnProfile(CurrentCaller.authUserId()));
    }

    @PatchMapping("/preferences")
    @Operation(summary = "Update your own preferences",
            description = "Every field is optional; only fields present in the body are changed.")
    public PreferenceResponse updateOwnPreferences(
            @Valid @RequestBody UpdatePreferenceRequest request) {
        UserProfile updated = profiles.updateOwnPreferences(
                CurrentCaller.authUserId(),
                request.themeOrNull(),
                request.locale(),
                request.defaultModel(),
                request.ragEnabledByDefault(),
                request.streamResponses(),
                request.emailNotifications(),
                request.productUpdates());
        return PreferenceResponse.from(updated);
    }

    /**
     * The caller's own account status and change history.
     *
     * <p>Exists so a blocked account can explain itself. The response omits which administrator
     * made each change; the account holder needs to know that a suspension happened and why,
     * not who typed it.
     */
    @GetMapping("/status")
    @Operation(summary = "Get your own account status and history")
    public ResponseEntity<AccountStatusResponse> getOwnStatus() {
        UserProfile profile = profiles.getOwnProfile(CurrentCaller.authUserId());
        List<UserStatusHistory> history = profiles.getOwnStatusHistory(CurrentCaller.authUserId());
        return ResponseEntity.ok(AccountStatusResponse.from(profile, history));
    }

    /** The caller's status, with their own history. */
    public record AccountStatusResponse(
            String status,
            String reason,
            UserProfileResponse profile,
            List<HistoryEntry> history) {

        /** One entry, with the acting administrator deliberately absent. */
        public record HistoryEntry(String fromStatus, String toStatus, String reason, String changedAt) {

            static HistoryEntry from(UserStatusHistory h) {
                return new HistoryEntry(
                        h.getFromStatus() == null ? null : h.getFromStatus().name(),
                        h.getToStatus().name(),
                        h.getReason(),
                        h.getChangedAt().toString());
            }
        }

        static AccountStatusResponse from(UserProfile profile, List<UserStatusHistory> history) {
            return new AccountStatusResponse(
                    profile.getAccountStatus().name(),
                    profile.getStatusReason(),
                    UserProfileResponse.from(profile),
                    history.stream().map(HistoryEntry::from).toList());
        }
    }
}