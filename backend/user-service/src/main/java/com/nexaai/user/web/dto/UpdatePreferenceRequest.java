package com.nexaai.user.web.dto;

import com.nexaai.user.domain.UserPreference.Theme;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A preferences update. Every field is optional.
 *
 * <p>Each field is applied only when present, so a caller updating just the theme does not
 * silently reset their notification settings. A JSON {@code null} is treated as "not supplied"
 * rather than "clear this"; clearing the default model is done by sending an empty string.
 */
public record UpdatePreferenceRequest(
        @Pattern(regexp = "(?i)light|dark|system",
                message = "theme must be one of: light, dark, system")
        String theme,

        @Size(min = 2, max = 16, message = "locale must be between 2 and 16 characters")
        String locale,

        @Size(max = 128, message = "defaultModel must be at most 128 characters")
        String defaultModel,

        Boolean ragEnabledByDefault,
        Boolean streamResponses,
        Boolean emailNotifications,
        Boolean productUpdates) {

    /** Converts the wire theme to the enum, or null when not supplied. */
    public Theme themeOrNull() {
        if (theme == null || theme.isBlank()) {
            return null;
        }
        return Theme.valueOf(theme.trim().toUpperCase(java.util.Locale.ROOT));
    }
}