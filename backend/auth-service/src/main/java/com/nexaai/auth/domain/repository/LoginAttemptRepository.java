package com.nexaai.auth.domain.repository;

import com.nexaai.auth.domain.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {
    @Query("SELECT COUNT(l) FROM LoginAttempt l WHERE l.email = :email AND l.success = false AND l.attemptedAt >= :since")
    long countFailedAttemptsSince(@Param("email") String email, @Param("since") Instant since);

    void deleteByAttemptedAtBefore(Instant before);
}
