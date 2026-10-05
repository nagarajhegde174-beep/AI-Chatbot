package com.nexaai.user.web.dto;

import com.nexaai.user.domain.UserPreference;
import com.nexaai.user.domain.UserProfile;
import java.time.Instant;

/**
 * A user's preferences and settings.
 *
 * <p>A separate resource from the profile, so that updating preferences cannot touch profile
 * fields and vice versa. Separate request and response types because the two directions have
 * genuinely different fields.
 */
public record PreferenceResponse(
        String theme,
        String locale,
        String defaultModel,
        boolean ragEnabledByDefault,
        boolean streamResponses,
        boolean emailNotifications,
        boolean productUpdates,
        Instant updatedAt) {

    public static PreferenceResponse from(UserProfile p) {
        UserPreference pref = p.getPreference();
        return new PreferenceResponse(
                pref.getTheme().name().toLowerCase(java.util.Locale.ROOT),
                pref.getLocale(),
                pref.getDefaultModel(),
                pref.isRagEnabledByDefault(),
                pref.isStreamResponses(),
                pref.isEmailNotifications(),
                pref.isProductUpdates(),
                pref.getUpdatedAt());
    }
}