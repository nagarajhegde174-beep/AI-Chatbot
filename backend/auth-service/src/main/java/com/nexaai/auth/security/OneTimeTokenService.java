package com.nexaai.auth.security;

import com.nexaai.auth.config.AuthProperties;
import com.nexaai.auth.domain.OneTimeToken;
import com.nexaai.auth.domain.OneTimeTokenPurpose;
import com.nexaai.auth.exception.AuthExceptions;
import com.nexaai.auth.repository.OneTimeTokenRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and redeems the single-use tokens behind email verification and password reset.
 *
 * <p><strong>Stored hashed.</strong> Read access to {@code one_time_token} must not be enough
 * to verify anyone's email or reset anyone's password, so the table holds a SHA-256 hash. As
 * with refresh tokens the input is random rather than user-chosen, so a fast hash is correct
 * here and a slow one would only add latency.
 *
 * <p><strong>Single use.</strong> {@link OneTimeToken#consume()} is what makes a reset link
 * unusable a second time. This is the property that limits the damage if a link is
 * intercepted.
 *
 * <p><strong>Latest wins.</strong> Issuing a new token consumes any earlier live token of the
 * same purpose, so a reset link from yesterday cannot work after a fresh request today.
 */
@Service
public class OneTimeTokenService {

    private static final Logger log = LoggerFactory.getLogger(OneTimeTokenService.class);

    private static final int TOKEN_BYTES = 32;
    private static final int MAX_ATTEMPTS = 5;

    private final OneTimeTokenRepository repository;
    private final AuthProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public OneTimeTokenService(OneTimeTokenRepository repository, AuthProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /** The plaintext token, which goes to the user, plus the persisted record. */
    public record IssuedToken(String plaintext, OneTimeToken entity) {
    }

    @Transactional
    public IssuedToken issue(UUID userId, OneTimeTokenPurpose purpose) {
        String plaintext = generateToken();
        var validity = purpose == OneTimeTokenPurpose.EMAIL_VERIFICATION
                ? properties.getToken().getVerificationTokenTtl()
                : properties.getToken().getResetTokenTtl();

        // Invalidate any earlier live token of the same purpose, so only the newest works.
        repository.consumeOthers(userId, purpose, Instant.now());

        OneTimeToken entity = new OneTimeToken(userId, RefreshTokenService.hash(plaintext), purpose, validity);
        repository.save(entity);

        return new IssuedToken(plaintext, entity);
    }

    /**
     * Redeems a token.
     *
     * @throws AuthExceptions.InvalidTokenException if unknown or over the attempt limit
     * @throws AuthExceptions.TokenExpiredException if past its validity
     * @throws AuthExceptions.TokenAlreadyConsumedException if already redeemed
     */
    @Transactional
    public OneTimeToken redeem(String plaintext, OneTimeTokenPurpose purpose) {
        OneTimeToken token = repository
                .findByTokenHashAndPurpose(RefreshTokenService.hash(plaintext), purpose)
                .orElseThrow(() -> new AuthExceptions.InvalidTokenException("This link is not valid."));

        if (token.isConsumed()) {
            throw new AuthExceptions.TokenAlreadyConsumedException();
        }

        if (token.isExpired()) {
            throw new AuthExceptions.TokenExpiredException();
        }

        if (token.getAttemptCount() >= MAX_ATTEMPTS) {
            // The correct token was never presented, only guesses. Refusing now stops an
            // unbounded guessing attempt against one token row.
            throw new AuthExceptions.InvalidTokenException("This link is not valid.");
        }

        token.consume();
        repository.save(token);
        return token;
    }

    /** Records a failed attempt against a token, for brute-force detection. */
    @Transactional
    public void recordFailedAttempt(String plaintext, OneTimeTokenPurpose purpose) {
        repository.findByTokenHashAndPurpose(RefreshTokenService.hash(plaintext), purpose)
                .ifPresent(token -> {
                    int attempts = token.recordFailedAttempt();
                    repository.save(token);
                    if (attempts >= MAX_ATTEMPTS) {
                        log.warn("One-time token for user {} exceeded {} failed attempts",
                                token.getUserId(), attempts);
                    }
                });
    }

    /** Generates a URL-safe random token. */
    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}