package com.nexaai.user.event;

import com.nexaai.user.domain.AccountStatus;
import com.nexaai.user.domain.Role;
import com.nexaai.user.domain.UserProfile;
import com.nexaai.user.repository.ProcessedEvent;
import com.nexaai.user.repository.ProcessedEventRepository;
import com.nexaai.user.repository.UserProfileRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns {@code auth.user.registered.v1} into a profile.
 *
 * <p><strong>This is the only way a profile comes into existence.</strong> There is no
 * self-registration endpoint in this service. That is not a missing feature: registration
 * belongs to auth-service, and a second registration path would be a second opinion about who
 * may hold an account ({@code docs/ARCHITECTURE.md} §13, decision 001).
 *
 * <p><strong>Idempotent.</strong> Kafka delivers at least once. The {@code processed_event}
 * insert and the profile insert are in one transaction, so a replay either sees the recorded
 * event id and does nothing, or races and loses on the unique constraint. Both paths leave one
 * profile, never two.
 */
@Service
public class UserRegistrationEventHandler {

    private static final Logger log = LoggerFactory.getLogger(UserRegistrationEventHandler.class);

    private final UserProfileRepository profiles;
    private final ProcessedEventRepository processedEvents;

    public UserRegistrationEventHandler(UserProfileRepository profiles,
                                        ProcessedEventRepository processedEvents) {
        this.profiles = profiles;
        this.processedEvents = processedEvents;
    }

    @Transactional
    public void onUserRegistered(DomainEvent<UserRegisteredPayload> event) {
        UUID eventId = event.eventId();

        // Fast path: already consumed. Cheaper than relying on the constraint to fail.
        if (processedEvents.existsById(eventId)) {
            log.info("Skipping already-processed event {} of type {}", eventId, event.eventType());
            return;
        }

        UserRegisteredPayload payload = event.payload();
        if (payload == null || payload.authUserId() == null) {
            // Malformed. Recorded as processed so it is not retried forever, and logged at
            // error: an event that can never succeed must not block the partition.
            log.error("Discarding event {} with a missing payload or authUserId", eventId);
            recordProcessed(eventId, event.eventType());
            return;
        }

        try {
            if (profiles.existsByAuthUserId(payload.authUserId())) {
                // The account exists but this event id is new: a re-registration under a new
                // event id. The profile must not be duplicated, and the profile's own identity
                // is unchanged.
                log.warn("Profile already exists for auth user {}; not creating a second. Event {}.",
                        payload.authUserId(), eventId);
                recordProcessed(eventId, event.eventType());
                return;
            }

            UserProfile profile = UserProfile.fromRegistration(
                    payload.authUserId(),
                    payload.email(),
                    displayNameOf(payload),
                    payload.emailVerified(),
                    registeredViaOf(payload.registeredVia()),
                    statusOf(payload.accountStatus()),
                    roleOf(payload.role()));

            profiles.save(profile);
            recordProcessed(eventId, event.eventType());

            log.info("Created profile {} for auth user {}", profile.getId(), payload.authUserId());
        } catch (DataIntegrityViolationException e) {
            // A concurrent consumer of the same event won the race. Losing that race is a
            // success, not a failure: exactly one profile exists either way.
            log.info("Lost the race to create a profile for {}; it already exists.",
                    payload.authUserId());
        }
    }

    private void recordProcessed(UUID eventId, String eventType) {
        processedEvents.save(new ProcessedEvent(eventId,
                eventType == null ? "unknown" : eventType));
    }

    // ------------------------------------------------------------------
    // Payload coercion
    //
    // An enum value arrives as a String on the wire. An unrecognised value must not throw:
    // that would rethrow forever and block the partition on data this consumer merely does
    // not understand. It falls back to the safe default and says so in the log.
    // ------------------------------------------------------------------

    private static String displayNameOf(UserRegisteredPayload payload) {
        if (payload.displayName() != null && !payload.displayName().isBlank()) {
            return payload.displayName().trim();
        }
        // No display name: derive something from the local part of the email rather than
        // storing a null that every UI would have to special-case.
        String email = payload.email() == null ? "" : payload.email();
        int at = email.indexOf('@');
        return (at > 0 ? email.substring(0, at) : "user").trim();
    }

    private static UserProfile.RegisteredVia registeredViaOf(String value) {
        if (value == null) {
            return UserProfile.RegisteredVia.PASSWORD;
        }
        try {
            return UserProfile.RegisteredVia.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("Unknown registeredVia '{}'; defaulting to PASSWORD.", value);
            return UserProfile.RegisteredVia.PASSWORD;
        }
    }

    private static AccountStatus statusOf(String value) {
        if (value == null) {
            return AccountStatus.PENDING_VERIFICATION;
        }
        try {
            return AccountStatus.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("Unknown accountStatus '{}'; defaulting to PENDING_VERIFICATION.", value);
            return AccountStatus.PENDING_VERIFICATION;
        }
    }

    private static Role roleOf(String value) {
        if (value == null) {
            return Role.USER;
        }
        try {
            return Role.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("Unknown role '{}'; defaulting to USER.", value);
            return Role.USER;
        }
    }
}