package com.nexaai.auth.repository;

import com.nexaai.auth.domain.RefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@code refresh_token}, addressed by hash because the plaintext is never stored. */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByFamilyIdAndRevokedAtIsNull(UUID familyId);

    List<RefreshToken> findByUserIdAndRevokedAtIsNull(UUID userId);

    /**
     * Revokes every live token in a rotation family.
     *
     * <p>Used on token reuse detection. A bulk update rather than a loop, because revoking
     * a family can touch many rows and doing it one at a time invites a partial revoke.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    /** Revokes every live token for a user. Used by logout-all. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    /** Deletes tokens that expired before a cutoff. Housekeeping. */
    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}