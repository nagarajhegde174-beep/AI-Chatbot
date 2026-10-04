package com.nexaai.auth.repository;

import com.nexaai.auth.domain.RevokedToken;
import java.time.Instant;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/** Repository for {@code token_denylist}: the durable record of revoked access tokens. */
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {

    boolean existsByJti(String jti);

    /** Batch existence check, so a burst of verifications is one query rather than N. */
    @Query("select r.jti from RevokedToken r where r.jti in :jtis")
    Set<String> findRevokedAmong(@Param("jtis") Set<String> jtis);

    @Modifying
    @Query("delete from RevokedToken r where r.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);

    long countByUserId(UUID userId);
}