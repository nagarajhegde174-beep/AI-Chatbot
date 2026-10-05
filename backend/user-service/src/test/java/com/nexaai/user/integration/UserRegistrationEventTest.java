package com.nexaai.user.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexaai.user.domain.AccountStatus;
import com.nexaai.user.domain.Role;
import com.nexaai.user.domain.UserProfile;
import com.nexaai.user.event.DomainEvent;
import com.nexaai.user.event.UserRegisteredPayload;
import com.nexaai.user.event.UserRegistrationEventHandler;
import com.nexaai.user.repository.ProcessedEventRepository;
import com.nexaai.user.repository.UserProfileRepository;
import com.nexaai.user.repository.UserStatusHistoryRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consuming {@code auth.user.registered.v1}.
 *
 * <p><strong>Kafka delivers at least once.</strong> Every one of these tests describes a case
 * that will happen in production — a replay, a duplicate publish, a rebalance redelivering a
 * batch — and none of them may produce a second profile. Idempotency is the property that makes
 * an at-least-once broker safe to build on, and it can only be tested by delivering the same
 * event repeatedly.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserRegistrationEventTest extends PostgresIntegrationTest {

    @Autowired
    private UserRegistrationEventHandler handler;

    @Autowired
    private UserProfileRepository profiles;

    @Autowired
    private ProcessedEventRepository processedEvents;

    private UUID authUserId;

    @BeforeEach
    void setUp() {
        authUserId = UUID.randomUUID();
    }

    private DomainEvent<UserRegisteredPayload> event(UUID eventId, String email, String name) {
        return new DomainEvent<>(
                eventId,
                UserRegisteredPayload.TOPIC,
                1,
                Instant.now(),
                UUID.randomUUID(),
                new UserRegisteredPayload(authUserId, email, name, true,
                        "PASSWORD", AccountStatus.ACTIVE.name(), Role.USER.name()));
    }

    // ==================================================================
    // The happy path
    // ==================================================================

    @Test
    @DisplayName("creates one profile and one preference row")
    void createsProfile() {
        handler.onUserRegistered(event(UUID.randomUUID(), "a@example.com", "Alpha"));

        UserProfile profile = profiles.findByAuthUserId(authUserId).orElseThrow();
        assertThat(profile.getEmail()).isEqualTo("a@example.com");
        assertThat(profile.getDisplayName()).isEqualTo("Alpha");
        assertThat(profile.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(profile.getRole()).isEqualTo(Role.USER);
        assertThat(profile.isEmailVerified()).isTrue();
        assertThat(profile.getPreference()).isNotNull();
    }

    @Test
    @DisplayName("records the event id so a replay can be recognised")
    void recordsEventId() {
        UUID eventId = UUID.randomUUID();
        handler.onUserRegistered(event(eventId, "a@example.com", "Alpha"));

        assertThat(processedEvents.findById(eventId)).isPresent();
    }

    // ==================================================================
    // Replay: the same event, twice and more
    // ==================================================================

    @Test
    @DisplayName("the SAME event delivered twice creates one profile")
    void replayIsIdempotent() {
        UUID eventId = UUID.randomUUID();
        DomainEvent<UserRegisteredPayload> e = event(eventId, "a@example.com", "Alpha");

        handler.onUserRegistered(e);
        handler.onUserRegistered(e);
        handler.onUserRegistered(e);

        assertThat(profiles.findByAuthUserId(authUserId))
                .as("a replayed event must not create a second profile")
                .isPresent();
        assertThat(profiles.findAll())
                .filteredOn(p -> p.getAuthUserId().equals(authUserId))
                .hasSize(1);
    }

    @Test
    @DisplayName("a DIFFERENT event for the same account creates no second profile")
    void secondDistinctEventForSameAccountIsAlsoIdempotent() {
        // Stronger than replay: even a different event id must not produce a second profile,
        // because the account is the same. The unique constraint on auth_user_id is what stops
        // this, and it is asserted by the duplicate-suppression branch in the handler.
        handler.onUserRegistered(event(UUID.randomUUID(), "a@example.com", "Alpha"));
        handler.onUserRegistered(event(UUID.randomUUID(), "a@example.com", "Alpha Again"));

        assertThat(profiles.findAll())
                .filteredOn(p -> p.getAuthUserId().equals(authUserId))
                .hasSize(1);

        assertThat(profiles.findByAuthUserId(authUserId).orElseThrow().getDisplayName())
                .as("the first event wins; a re-registration must not overwrite the profile")
                .isEqualTo("Alpha");
    }

    @Test
    @DisplayName("re-different event types are tracked separately")
    void eventIdsAreTrackedIndividually() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        handler.onUserRegistered(event(first, "a@example.com", "Alpha"));
        handler.onUserRegistered(event(second, "b@example.com", "Beta"));

        assertThat(processedEvents.findById(first)).isPresent();
        assertThat(processedEvents.findById(second)).isPresent();
    }

    // ==================================================================
    // Malformed events
    // ==================================================================

    @Test
    @DisplayName("an event with a null payload is discarded, not retried forever")
    void nullPayloadIsDiscarded() {
        UUID eventId = UUID.randomUUID();
        DomainEvent<UserRegisteredPayload> broken = new DomainEvent<>(
                eventId, UserRegisteredPayload.TOPIC, 1, Instant.now(), UUID.randomUUID(), null);

        handler.onUserRegistered(broken);

        assertThat(profiles.findByAuthUserId(authUserId)).isEmpty();
        // Recorded, so the same broken event is not redelivered and retried on every poll.
        assertThat(processedEvents.findById(eventId)).isPresent();
    }

    @Test
    @DisplayName("an event with a null authUserId is discarded")
    void nullAuthUserIdIsDiscarded() {
        UUID eventId = UUID.randomUUID();
        DomainEvent<UserRegisteredPayload> broken = new DomainEvent<>(
                eventId, UserRegisteredPayload.TOPIC, 1, Instant.now(), UUID.randomUUID(),
                new UserRegisteredPayload(null, "a@example.com", "Alpha", false,
                        "PASSWORD", "ACTIVE", "USER"));

        handler.onUserRegistered(broken);

        assertThat(profiles.findByAuthUserId(authUserId)).isEmpty();
        assertThat(processedEvents.findById(eventId)).isPresent();
    }

    @Test
    @DisplayName("an unrecognised status falls back to PENDING_VERIFICATION rather than throwing")
    void unknownStatusFallsBack() {
        // A value this consumer does not understand must not throw. Throwing would rethrow on
        // every redelivery and block the partition behind data this service merely cannot
        // interpret.
        UUID eventId = UUID.randomUUID();
        DomainEvent<UserRegisteredPayload> odd = new DomainEvent<>(
                eventId, UserRegisteredPayload.TOPIC, 1, Instant.now(), UUID.randomUUID(),
                new UserRegisteredPayload(authUserId, "a@example.com", "Alpha", false,
                        "CARRIER_PIGEON", "BANANA", "SUPERUSER"));

        handler.onUserRegistered(odd);

        UserProfile profile = profiles.findByAuthUserId(authUserId).orElseThrow();
        assertThat(profile.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        // Critically: an unknown role must NOT become ADMIN.
        assertThat(profile.getRole()).isEqualTo(Role.USER);
        assertThat(profile.getRegisteredVia())
                .isEqualTo(UserProfile.RegisteredVia.PASSWORD);
    }

    @Test
    @DisplayName("an ADMIN role in the payload is honoured, because auth-service assigns it")
    void adminRoleIsHonoured() {
        UUID eventId = UUID.randomUUID();
        DomainEvent<UserRegisteredPayload> admin = new DomainEvent<>(
                eventId, UserRegisteredPayload.TOPIC, 1, Instant.now(), UUID.randomUUID(),
                new UserRegisteredPayload(authUserId, "a@example.com", "Admin", true,
                        "PASSWORD", AccountStatus.ACTIVE.name(), Role.ADMIN.name()));

        handler.onUserRegistered(admin);

        assertThat(profiles.findByAuthUserId(authUserId).orElseThrow().getRole())
                .isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("a missing display name is derived from the email's local part")
    void derivesMissingDisplayName() {
        handler.onUserRegistered(event(UUID.randomUUID(), "bravo@charlie.test", "  "));

        assertThat(profiles.findByAuthUserId(authUserId).orElseThrow().getDisplayName())
                .isEqualTo("bravo");
    }

    @Test
    @DisplayName("the email is normalised on the way in")
    void normalisesEmail() {
        handler.onUserRegistered(event(UUID.randomUUID(), "MiXeD@Example.COM", "Mixed"));

        assertThat(profiles.findByAuthUserId(authUserId).orElseThrow().getEmail())
                .isEqualTo("mixed@example.com");
    }
}