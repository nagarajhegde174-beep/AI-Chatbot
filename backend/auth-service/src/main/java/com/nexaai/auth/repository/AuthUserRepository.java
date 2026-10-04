package com.nexaai.auth.repository;

import com.nexaai.auth.domain.AccountStatus;
import com.nexaai.auth.domain.AuthUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for {@code auth_user}.
 *
 * <p>Every lookup is by normalised email or by id. There is deliberately no
 * {@code findByEmailContaining} or any query that could return more than one row for an
 * email, because a query that returns a list where the caller expects one account is how a
 * password change ends up applied to the wrong user.
 */
public interface AuthUserRepository extends JpaRepository<AuthUser, UUID> {

    /** Finds by normalised email. Email is normalised on write, so a plain lookup is exact. */
    Optional<AuthUser> findByEmail(String email);

    /**
     * Whether an account already exists for this email.
     *
     * <p>Used by registration to produce a duplicate-email conflict without loading the
     * entity, and by tests.
     */
    boolean existsByEmail(String email);

    /** Finds an account by its linked Google subject. */
    Optional<AuthUser> findByGoogleSubject(String googleSubject);

    /** Whether a Google identity is already linked to an account. */
    boolean existsByGoogleSubject(String googleSubject);

    /** Counts accounts in a status. Used by the admin surface from Phase 2. */
    long countByStatus(AccountStatus status);

    /**
     * Locks an account row for the duration of the transaction.
     *
     * <p>Used on the sign-in path so that two concurrent failed logins cannot both read the
     * same failure count and both write count+1, which would under-count and let an
     * attacker slip past the lockout threshold.
     */
    @Query("select u from AuthUser u where u.id = :id")
    Optional<AuthUser> findByIdForUpdate(@Param("id") UUID id);
}