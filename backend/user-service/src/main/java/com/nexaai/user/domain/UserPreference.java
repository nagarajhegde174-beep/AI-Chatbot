package com.nexaai.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;

/**
 * A user's preferences and settings.
 *
 * <p>Separate from the profile so a preferences update cannot rewrite a profile field. One row
 * per user, created with the profile.
 */
@Embeddable
public class UserPreference {

    /** Referenced by the frontend theme switch. */
    public enum Theme {
        LIGHT, DARK, SYSTEM
    }

    /**
     * Every column is prefixed, because this embeddable shares a table with {@link UserProfile}
     * and an unprefixed {@code updated_at} would collide with the profile's own. Prefixing also
     * makes a mis-mapped column obvious in a schema diff rather than a silent overwrite.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "pref_theme", nullable = false, length = 16)
    private Theme theme = Theme.SYSTEM;

    @Column(name = "pref_locale", nullable = false, length = 16)
    private String locale = "en";

    @Column(name = "pref_default_model", length = 128)
    private String defaultModel;

    @Column(name = "pref_rag_enabled_by_default", nullable = false)
    private boolean ragEnabledByDefault = true;

    @Column(name = "pref_stream_responses", nullable = false)
    private boolean streamResponses = true;

    @Column(name = "pref_email_notifications", nullable = false)
    private boolean emailNotifications = true;

    @Column(name = "pref_product_updates", nullable = false)
    private boolean productUpdates = true;

    @Column(name = "pref_updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public static UserPreference defaults() {
        return new UserPreference();
    }

    public void update(Theme theme, String locale, String defaultModel,
                       Boolean ragEnabled, Boolean streamResponses,
                       Boolean emailNotifications, Boolean productUpdates) {
        // Null means "not supplied", so a partial update does not silently reset a setting the
        // caller did not mention.
        if (theme != null) {
            this.theme = theme;
        }
        if (locale != null) {
            this.locale = locale;
        }
        if (defaultModel != null) {
            this.defaultModel = defaultModel.isBlank() ? null : defaultModel.trim();
        }
        if (ragEnabled != null) {
            this.ragEnabledByDefault = ragEnabled;
        }
        if (streamResponses != null) {
            this.streamResponses = streamResponses;
        }
        if (emailNotifications != null) {
            this.emailNotifications = emailNotifications;
        }
        if (productUpdates != null) {
            this.productUpdates = productUpdates;
        }
        this.updatedAt = Instant.now();
    }

    public Theme getTheme() {
        return theme;
    }

    public String getLocale() {
        return locale;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public boolean isRagEnabledByDefault() {
        return ragEnabledByDefault;
    }

    public boolean isStreamResponses() {
        return streamResponses;
    }

    public boolean isEmailNotifications() {
        return emailNotifications;
    }

    public boolean isProductUpdates() {
        return productUpdates;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}