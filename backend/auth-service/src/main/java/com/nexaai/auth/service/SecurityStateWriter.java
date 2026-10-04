package com.nexaai.auth.service;

import com.nexaai.auth.config.AuthProperties;
import com.nexaai.auth.domain.AuthUser;
import com.nexaai.auth.repository.AuthUserRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes security state that must survive the request failing.
 *
 * <p><strong>Why this is a separate bean.</strong> Both operations here are performed and then
 * the request is <em>rejected</em>. Doing that inside the request's own transaction means the
 * rejection rolls the write back, and the security state is silently discarded:
 *
 * <ul>
 *   <li>The failed-login counter never persisted, so an account could be brute-forced forever
 *       and never actually lock. Found by the integration test that expects a 423.</li>
 *   <li>The refresh-token family revocation was rolled back, so detecting a stolen token and
 *       then not actually logging the attacker out was a no-op. Found by the integration test
 *       that expects the rotated token to die.</li>
 * </ul>
 *
 * <p>{@code REQUIRES_NEW} suspends the caller's transaction, so the write commits even when
 * the caller then throws. Both operations act on rows that are already committed, so isolation
 * is safe: an independent transaction can see them.
 *
 * <p>A separate <em>bean</em>, not merely a separate method, because {@code REQUIRES_NEW} on a
 * self-invoked method is silently ignored — the proxy is not involved.
 */
@Component
public class SecurityStateWriter {

    private static final Logger log = LoggerFactory.getLogger(SecurityStateWriter.class);

    private final AuthUserRepository userRepository;
    private final AuthProperties properties;
    /**
     * Repository-level revocation, invoked inside a {@code REQUIRES_NEW} boundary.
     *
     * <p>Kept as a collaborator so the revocation goes through its own proxy rather than a
     * self-invocation, which {@code REQUIRES_NEW} would silently ignore.
     */
    private final RefreshRevoker refreshTokenRevoker;

    public SecurityStateWriter(AuthUserRepository userRepository,
                               AuthProperties properties,
                               RefreshRevoker refreshTokenRevoker) {
        this.userRepository = userRepository;
        this.properties = properties;
        this.refreshTokenRevoker = refreshTokenRevoker;
    }

    /**
     * Increments the failure counter and locks the account at the threshold.
     *
     * <p>Committed independently, so a rejected sign-in still counts.
     *
     * @return true when this failure caused the account to lock
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordFailedLogin(UUID userId) {
        AuthUser user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }
        boolean locked = properties.getLockout().isEnabled()
                && user.recordFailedLogin(properties.getToken().getMaxFailedLogins(),
                properties.getToken().getLockDuration());
        userRepository.saveAndFlush(user);
        return locked;
    }

    /**
     * Revokes every live refresh token in a rotation family, independently.
     *
     * <p>Called when an already-revoked token is presented. The caller then raises an error,
     * and without an independent transaction that error would roll the revocation back —
     * leaving the stolen token usable, which is precisely the situation the detection exists
     * to end.
     *
     * @return how many tokens were revoked
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeRefreshTokenFamily(UUID familyId) {
        int revoked = refreshTokenRevoker.revoke(familyId);
        log.warn("Refresh token reuse detected for family {}; revoked {} token(s)", familyId, revoked);
        return revoked;
    }

    /**
     * Revokes every refresh token for a user, independently.
     *
     * <p>Used by password change and password reset, where the intent is that every existing
     * session dies even though the surrounding operation may fail to commit.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeAllRefreshTokens(UUID userId) {
        return refreshTokenRevoker.revokeAll(userId);
    }

    /** Repository-level revocation, invoked inside a {@code REQUIRES_NEW} boundary. */

    /** Repository-level revocation, invoked inside a {@code REQUIRES_NEW} boundary. */
    @Component
    public static class RefreshRevoker {

        private final com.nexaai.auth.repository.RefreshTokenRepository repository;

        public RefreshRevoker(com.nexaai.auth.repository.RefreshTokenRepository repository) {
            this.repository = repository;
        }

        @Transactional(propagation = Propagation.REQUIRED)
        public int revoke(UUID familyId) {
            return repository.revokeFamily(familyId, Instant.now());
        }

        @Transactional(propagation = Propagation.REQUIRED)
        public int revokeAll(UUID userId) {
            return repository.revokeAllForUser(userId, Instant.now());
        }
    }
}