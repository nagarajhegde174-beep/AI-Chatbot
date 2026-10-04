package com.nexaai.auth.service;

import com.nexaai.auth.domain.AuthEvent;
import com.nexaai.auth.domain.AuthEventType;
import com.nexaai.auth.domain.AuthUser;
import com.nexaai.auth.domain.EmailNormalizer;
import com.nexaai.auth.repository.AuthEventRepository;
import com.nexaai.auth.repository.AuthUserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records authentication events for the audit trail and the lockout signal.
 *
 * <p><strong>Two propagation modes, for a reason the tests found the hard way.</strong>
 *
 * <p>{@link #record} joins the caller's transaction ({@code REQUIRED}). An event about a user
 * created in that same transaction has to be written inside it: an isolated transaction cannot
 * see an uncommitted row, so the insert violates the {@code auth_event_user_id_fkey} foreign
 * key. That is not hypothetical — it is exactly what {@code REQUIRES_NEW} did on registration
 * before this was split in two.
 *
 * <p>{@link #recordIndependent} uses {@code REQUIRES_NEW}, for events that must survive the
 * caller rolling back. A failed sign-in is exactly that case: the surrounding work is rolled
 * back, so recording the evidence inside it would discard the evidence. Isolating it is safe
 * there because the account it references is already committed.
 *
 * <p>Records that an event happened. Never a credential, never a password
 * ({@code docs/SECURITY.md} section 11.2).
 */
@Service
public class AuthAuditService {

    private static final Logger log = LoggerFactory.getLogger(AuthAuditService.class);

    private final AuthEventRepository eventRepository;
    private final AuthUserRepository userRepository;

    public AuthAuditService(AuthEventRepository eventRepository, AuthUserRepository userRepository) {
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
    }

    /**
     * Records an event inside the caller's transaction.
     *
     * <p>Never throws. An audit write failing must not turn a successful sign-in into a 500,
     * and it must not mask the original exception. The failure is logged at ERROR instead: a
     * missing audit record is itself worth noticing
     * ({@code docs/SECURITY.md} section 11.3).
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void record(UUID userId, AuthEventType type, boolean success, String ip, String userAgent) {
        try {
            eventRepository.save(new AuthEvent(userId, type, success, ip, userAgent));
        } catch (RuntimeException e) {
            log.error("Failed to record audit event {} for user {}: {}", type, userId, e.getMessage(), e);
        }
    }

    /**
     * Records an event in its own transaction, so it survives the caller's rollback.
     *
     * <p>For failure paths only, and only where the referenced user is already committed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependent(UUID userId, AuthEventType type, boolean success,
                                  String ip, String userAgent) {
        try {
            eventRepository.save(new AuthEvent(userId, type, success, ip, userAgent));
        } catch (RuntimeException e) {
            log.error("Failed to record audit event {} for user {}: {}", type, userId, e.getMessage(), e);
        }
    }

    /** Records an event against an email, resolving the user when one exists. */
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordByEmail(String email, AuthEventType type, boolean success,
                              String ip, String userAgent) {
        record(resolveUserId(email), type, success, ip, userAgent);
    }

    /**
     * Records a failure against an email, isolated so it survives the rollback.
     *
     * <p>This is the path a failed sign-in takes, which is why it exists separately from
     * {@link #recordByEmail}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailureByEmail(String email, AuthEventType type, String ip, String userAgent) {
        recordIndependent(resolveUserId(email), type, false, ip, userAgent);
    }

    /** Records a failure against a known user, isolated. Used where the account is committed. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID userId, AuthEventType type, String ip, String userAgent) {
        recordIndependent(userId, type, false, ip, userAgent);
    }

    private UUID resolveUserId(String email) {
        if (email == null) {
            return null;
        }
        try {
            return userRepository.findByEmail(EmailNormalizer.normalize(email))
                    .map(AuthUser::getId)
                    .orElse(null);
        } catch (RuntimeException e) {
            // An audit event with a null user id is still worth recording: it says the attempt
            // happened, which is what the failure count depends on.
            log.debug("Could not resolve a user id for an audit event", e);
            return null;
        }
    }

    /** Whether the account currently has too many recent failures to allow a sign-in attempt. */
    @Transactional(readOnly = true)
    public boolean hasTooManyRecentFailures(UUID userId, int maxAttempts, Duration window) {
        long failures = eventRepository.countByUserIdAndEventTypeAndSuccessFalseAndOccurredAtAfter(
                userId, AuthEventType.LOGIN_FAILED, Instant.now().minus(window));
        return failures >= maxAttempts;
    }
}