package com.nexaai.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Partial preference updates.
 *
 * <p>The behaviour under test is what a null field must <em>not</em> do. A preferences update
 * that resets every field the caller did not mention is a bug that looks correct, and only a
 * test pins it down.
 */
class UserPreferenceTest {

    private static UserPreference withAllSet() {
        UserPreference preference = new UserPreference();
        preference.update(UserPreference.Theme.DARK, "fr", "gpt-x",
                false, false, false, false);
        return preference;
    }

    @Test
    @DisplayName("nulls leave existing values alone")
    void nullsAreNoOps() {
        UserPreference preference = withAllSet();

        preference.update(null, null, null, null, null, null, null);

        assertThat(preference.getTheme()).isEqualTo(UserPreference.Theme.DARK);
        assertThat(preference.getLocale()).isEqualTo("fr");
        assertThat(preference.getDefaultModel()).isEqualTo("gpt-x");
        assertThat(preference.isRagEnabledByDefault()).isFalse();
        assertThat(preference.isStreamResponses()).isFalse();
        assertThat(preference.isEmailNotifications()).isFalse();
        assertThat(preference.isProductUpdates()).isFalse();
    }

    @Test
    @DisplayName("one supplied field changes and the rest survive")
    void partialUpdateTouchesOnlyTheSuppliedField() {
        UserPreference preference = withAllSet();

        preference.update(UserPreference.Theme.LIGHT, null, null, null, null, null, null);

        assertThat(preference.getTheme()).isEqualTo(UserPreference.Theme.LIGHT);
        assertThat(preference.getLocale()).isEqualTo("fr");
        assertThat(preference.getDefaultModel()).isEqualTo("gpt-x");
        assertThat(preference.isStreamResponses()).isFalse();
    }

    @Test
    @DisplayName("false is applied, not treated as absent")
    void falseIsNotNoOp() {
        UserPreference preference = UserPreference.defaults();
        assertThat(preference.isStreamResponses()).isTrue();

        preference.update(null, null, null, null, false, null, null);

        // The trap: a naive implementation using a truthiness check would skip false and leave
        // the setting on, which is precisely the value a user turns off.
        assertThat(preference.isStreamResponses()).isFalse();
    }

    @Test
    @DisplayName("an empty default model clears it")
    void blankDefaultModelClears() {
        UserPreference preference = withAllSet();

        preference.update(null, null, "   ", null, null, null, null);

        assertThat(preference.getDefaultModel()).isNull();
    }

    @Test
    @DisplayName("a supplied default model is trimmed")
    void trimsDefaultModel() {
        UserPreference preference = UserPreference.defaults();

        preference.update(null, null, "  gpt-x  ", null, null, null, null);

        assertThat(preference.getDefaultModel()).isEqualTo("gpt-x");
    }

    @Test
    void defaultsAreDocumentedValues() {
        UserPreference preference = UserPreference.defaults();

        assertThat(preference.getTheme()).isEqualTo(UserPreference.Theme.SYSTEM);
        assertThat(preference.getLocale()).isEqualTo("en");
        assertThat(preference.getDefaultModel()).isNull();
        assertThat(preference.isRagEnabledByDefault()).isTrue();
        assertThat(preference.isStreamResponses()).isTrue();
        assertThat(preference.isEmailNotifications()).isTrue();
        assertThat(preference.isProductUpdates()).isTrue();
        assertThat(preference.getUpdatedAt()).isNotNull();
    }
}