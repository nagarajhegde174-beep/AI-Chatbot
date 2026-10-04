package com.nexaai.auth.repository;

import com.nexaai.auth.domain.AuthEvent;
import com.nexaai.auth.domain.AuthEventType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@code auth_event}: the audit trail and the lockout signal. */
public interface AuthEventRepository extends JpaRepository<AuthEvent, Long> {

    /**
     * Counts recent failures for an account.
     *
     * <p>Bounded by {@code since} so the count cannot grow without limit and the lockout
     * window is explicit: a failure an hour ago should not contribute to a decision now.
     */
    long countByUserIdAndEventTypeAndSuccessFalseAndOccurredAtAfter(
            UUID userId, AuthEventType eventType, Instant since);

    List<AuthEvent> findTop50ByUserIdOrderByOccurredAtDesc(UUID userId);

    List<AuthEvent> findTop100ByEventTypeOrderByOccurredAtDesc(AuthEventType eventType);
}