package com.nexaai.auth.domain.repository;

import com.nexaai.auth.domain.entity.TokenDenylist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TokenDenylistRepository extends JpaRepository<TokenDenylist, UUID> {
    boolean existsByJti(String jti);
    Optional<TokenDenylist> findByJti(String jti);
    void deleteByExpiresAtBefore(Instant now);
}
