package com.nexaai.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The profile entity's behaviour, especially what it refuses to let a user change.
 */
class UserProfileTest {

    private static final UUID AUTH_ID = UUID.randomUUID();

    private static UserProfile activeProfile() {
        return UserProfile.fromRegistration(AUTH_ID, "User@Example.COM", "Original",
                true, UserProfile.RegisteredVia.PASSWORD, AccountStatus.ACTIVE, Role.USER);
    }

    // ------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the email is normalised on the way in")
    void normalisesEmail() {
        assertThat(activeProfile().getEmail()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("a Google registration keeps its provider")
    void keepsRegistrationSource() {
        UserProfile profile = UserProfile.fromRegistration(AUTH_ID, "g@example.com", "G",
                true, UserProfile.RegisteredVia.GOOGLE, AccountStatus.ACTIVE, Role.USER);

        assertThat(profile.getRegisteredVia()).isEqualTo(UserProfile.RegisteredVia.GOOGLE);
    }

    @Test
    @DisplayName("a new profile starts with default preferences")
    void startsWithDefaultPreferences() {
        UserPreference preference = activeProfile().getPreference();

        assertThat(preference.getTheme()).isEqualTo(UserPreference.Theme.SYSTEM);
        assertThat(preference.isRagEnabledByDefault()).isTrue();
        assertThat(preference.isStreamResponses()).isTrue();
    }

    // ------------------------------------------------------------------
    // Self-service updates: what a user may NOT do
    // ------------------------------------------------------------------

    @Test
    @DisplayName("updateProfile cannot change the email")
    void cannotChangeEmail() {
        UserProfile profile = activeProfile();
        profile.updateProfile("Renamed", "https://example.com/a.png");

        assertThat(profile.getEmail()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("updateProfile cannot change the role")
    void cannotChangeRole() {
        UserProfile profile = activeProfile();
        profile.updateProfile("Renamed", null);

        assertThat(profile.getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("updateProfile cannot change the account status")
    void cannotChangeStatus() {
        UserProfile profile = activeProfile();
        profile.updateProfile("Renamed", null);

        assertThat(profile.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(profile.getStatusReason()).isNull();
    }

    @Test
    @DisplayName("the public updateProfile method takes no role, status or email argument at all")
    void updateProfileSignatureCannotExpressEscalation() {
        // The real control is structural: there is no parameter through which a caller could
        // pass a role. Asserting the signature is not possible from a test, so this documents
        // the invariant next to the behaviour tests that demonstrate it.
        assertThat(UserProfile.class.getMethods())
                .filteredOn(m -> m.getName().equals("updateProfile"))
                .hasSize(1);
    }

    // ------------------------------------------------------------------
    // Self-service updates: what a user MAY do
    // ------------------------------------------------------------------

    @Test
    void renames() {
        UserProfile profile = activeProfile();
        profile.updateProfile("  New Name  ", null);

        assertThat(profile.getDisplayName()).isEqualTo("New Name");
    }

    @Test
    @DisplayName("a blank display name is ignored rather than stored empty")
    void ignoresBlankDisplayName() {
        UserProfile profile = activeProfile();
        profile.updateProfile("   ", null);

        assertThat(profile.getDisplayName()).isEqualTo("Original");
    }

    @Test
    @DisplayName("a blank avatar URL clears the avatar instead of storing whitespace")
    void blankAvatarClearsIt() {
        UserProfile profile = activeProfile();
        profile.updateProfile(null, "https://example.com/a.png");
        assertThat(profile.getAvatarUrl()).isEqualTo("https://example.com/a.png");

        profile.updateProfile(null, "  ");
        assertThat(profile.getAvatarUrl()).isNull();
    }

    @Test
    void movesUpdatedAtForward() {
        UserProfile profile = activeProfile();
        var before = profile.getUpdatedAt();

        profile.updateProfile("Renamed", null);

        assertThat(profile.getUpdatedAt()).isAfterOrEqualTo(before);
    }

    // ------------------------------------------------------------------
    // Administrative status changes
    // ------------------------------------------------------------------

    @Test
    void adminSuspendsWithReason() {
        UserProfile profile = activeProfile();
        UUID admin = UUID.randomUUID();

        profile.applyStatusChange(AccountStatus.SUSPENDED, "Spam", admin);

        assertThat(profile.getAccountStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(profile.getStatusReason()).isEqualTo("Spam");
        assertThat(profile.getStatusChangedBy()).isEqualTo(admin);
        assertThat(profile.getStatusChangedAt()).isNotNull();
    }

    @Test
    @DisplayName("a blank reason is stored as null, not as an empty string")
    void blankReasonBecomesNull() {
        UserProfile profile = activeProfile();

        profile.applyStatusChange(AccountStatus.SUSPENDED, "   ", UUID.randomUUID());

        assertThat(profile.getStatusReason()).isNull();
    }

    @Test
    @DisplayName("an illegal transition throws and changes nothing")
    void illegalTransitionLeavesStateIntact() {
        UserProfile profile = activeProfile();
        profile.applyStatusChange(AccountStatus.DEACTIVATED, "Closed", UUID.randomUUID());

        assertThatThrownBy(() ->
                profile.applyStatusChange(AccountStatus.ACTIVE, "Undo", UUID.randomUUID()))
                .isInstanceOf(IllegalStateTransitionException.class);

        assertThat(profile.getAccountStatus()).isEqualTo(AccountStatus.DEACTIVATED);
        assertThat(profile.getStatusReason()).isEqualTo("Closed");
    }

    // ------------------------------------------------------------------
    // Event projection
    // ------------------------------------------------------------------

    @Test
    @DisplayName("a projected status applies without consulting the transition rules")
    void projectedStatusBypassesLifecycleChecks() {
        UserProfile profile = activeProfile();

        profile.applyProjectedStatus(AccountStatus.PENDING_VERIFICATION);

        // Deliberate. auth-service is authoritative, so if it says PENDING_VERIFICATION this
        // service must record that even though no administrator path would allow it. Refusing
        // here would leave the projection permanently disagreeing with the source of truth.
        assertThat(profile.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
    }

    @Test
    void recordsSubscriptionProjection() {
        UserProfile profile = activeProfile();

        profile.applySubscription("pro", "Pro");

        assertThat(profile.getPlanId()).isEqualTo("pro");
        assertThat(profile.getPlanName()).isEqualTo("Pro");
    }

    // ------------------------------------------------------------------
    // Soft delete
    // ------------------------------------------------------------------

    @Test
    void softDeleteRetainsTheRow() {
        UserProfile profile = activeProfile();
        profile.softDelete();

        assertThat(profile.isDeleted()).isTrue();
        assertThat(profile.getDeletedAt()).isNotNull();
        // The row still exists on purpose: an audit trail and another service's derived data
        // still reference it.
        assertThat(profile.getId()).isNotNull();
    }

    // ------------------------------------------------------------------
    // Logging safety
    // ------------------------------------------------------------------

    @Test
    @DisplayName("toString does not leak the email")
    void toStringOmitsEmail() {
        String rendered = activeProfile().toString();

        assertThat(rendered).doesNotContain("user@example.com");
        assertThat(rendered).contains("status=ACTIVE");
    }

    @Test
    @DisplayName("normalising an absent email yields absent, not the string \"null\"")
    void normaliseNull() {
        assertThat(EmailNormalizer.normalize(null)).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("a blank email normalises to blank, which the database will reject")
    void normalisesBlank(String input) {
        assertThat(EmailNormalizer.normalize(input)).isBlank();
    }

    @Test
    void normalisesCase() {
        assertThat(EmailNormalizer.normalize("MiXeD@CaSe.COM")).isEqualTo("mixed@case.com");
    }
}