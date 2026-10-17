package com.nexaai.auth.service;

import com.nexaai.auth.domain.entity.TokenDenylist;
import com.nexaai.auth.domain.repository.TokenDenylistRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class TokenRevocationService {

    private final TokenDenylistRepository tokenDenylistRepository;

    public TokenRevocationService(TokenDenylistRepository tokenDenylistRepository) {
        this.tokenDenylistRepository = tokenDenylistRepository;
    }

    @Transactional
    public void revokeAccessToken(String jti, UUID userId, Instant expiresAt) {
        if (jti != null && !tokenDenylistRepository.existsByJti(jti)) {
            tokenDenylistRepository.save(new TokenDenylist(null, jti, userId, expiresAt));
        }
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(String jti) {
        return tokenDenylistRepository.existsByJti(jti);
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void purgeExpiredRevokedTokens() {
        tokenDenylistRepository.deleteByExpiresAtBefore(Instant.now());
    }
}
