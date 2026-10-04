package com.nexaai.auth.repository;

import com.nexaai.auth.domain.OneTimeToken;
import com.nexaai.auth.domain.OneTimeTokenPurpose;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@code one_time_token}: verification and password-reset tokens. */
public interface OneTimeTokenRepository extends JpaRepository<OneTimeToken, UUID> {

    Optional<OneTimeToken> findByTokenHashAndPurpose(String tokenHash, OneTimeTokenPurpose purpose);

    /**
     * Finds the live token for a user and purpose, if any.
     *
     * <p>Used to invalidate an older token when a newer one is issued, so a link sent
     * yesterday cannot work after a fresh request today.
     */
    @Query("""
            select t from OneTimeToken t
            where t.userId = :userId
              and t.purpose = :purpose
              and t.consumedAt is null
            order by t.issuedAt desc
            """)
    Optional<OneTimeToken> findLatestLive(@Param("userId") UUID userId,
                                          @Param("purpose") OneTimeTokenPurpose purpose);

    /** Consumes any other live token of the same purpose, so only the newest works. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OneTimeToken t set t.consumedAt = :now
            where t.userId = :userId
              and t.purpose = :purpose
              and t.consumedAt is null
            """)
    int consumeOthers(@Param("userId") UUID userId,
                      @Param("purpose") OneTimeTokenPurpose purpose,
                      @Param("now") java.time.Instant now);

    @Modifying
    @Query("delete from OneTimeToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") java.time.Instant cutoff);
}