package com.nexaai.auth.security;

import com.nexaai.auth.config.AuthProperties;
import com.nexaai.auth.domain.RefreshToken;
import com.nexaai.auth.domain.TokenSource;
import com.nexaai.auth.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, verifies and revokes refresh tokens.
 *
 * <p><strong>Storage.</strong> Only a SHA-256 hash is persisted. A database dump must not
 * yield a usable refresh token, so the plaintext token exists only in the HTTP response and
 * never reaches the database ({@code docs/SECURITY.md} section 2.2).
 *
 * <p><strong>Rotation and reuse detection.</strong> Every exchange issues a new token and
 * revokes the old one. Presenting an already-revoked token means the token was copied, because
 * the legitimate holder always holds the newest. The entire rotation family is then revoked,
 * which logs the attacker out as well as the victim.
 *
 * <p><strong>Randomness.</strong> Tokens come from {@link SecureRandom}, 32 bytes, URL-safe
 * base64. Never a UUID: 122 bits with a predictable structure is not the right primitive for
 * a bearer credential.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final AuthProperties.Token tokenProperties;
    private final com.nexaai.auth.service.SecurityStateWriter securityStateWriter;

    public RefreshTokenService(RefreshTokenRepository repository,
                               AuthProperties properties,
                               com.nexaai.auth.service.SecurityStateWriter securityStateWriter) {
        this.repository = repository;
        this.tokenProperties = properties.getToken();
        this.securityStateWriter = securityStateWriter;
    }

    /** A freshly generated refresh token: the plaintext for the response, plus its stored record. */
    public record IssuedRefreshToken(String token, RefreshToken entity) {
    }

    /** Issues the first token in a new rotation family. */
    @Transactional
    public IssuedRefreshToken issue(UUID userId, TokenSource source) {
        return issue(userId, UUID.randomUUID(), source);
    }

    /** Issues a token that continues an existing family. */
    @Transactional
    public IssuedRefreshToken rotate(UUID familyId, UUID userId, TokenSource source) {
        return issue(userId, familyId, source);
    }

    private IssuedRefreshToken issue(UUID userId, UUID familyId, TokenSource source) {
        String plaintext = generateToken();
        Instant expiresAt = Instant.now().plus(tokenProperties.getRefreshTokenTtl());

        RefreshToken entity = new RefreshToken(
                familyId, hash(plaintext), userId, expiresAt, source);
        repository.save(entity);

        return new IssuedRefreshToken(plaintext, entity);
    }

    /**
     * The result of exchanging a refresh token.
     *
     * @param reuseDetected true when the presented token had already been exchanged, in which
     *                      case the whole family has been revoked and the new token, if any,
     *                      must be discarded
     */
    public record ExchangeResult(UUID userId, IssuedRefreshToken replacement, boolean reuseDetected) {
    }

    /**
     * Exchanges a refresh token for a new one.
     *
     * @throws com.nexaai.auth.exception.AuthExceptions.InvalidTokenException if unknown or expired
     * @throws com.nexaai.auth.exception.AuthExceptions.TokenReuseDetectedException if already used
     */
    @Transactional
    public ExchangeResult exchange(String presentedToken) {
        RefreshToken stored = repository.findByTokenHash(hash(presentedToken))
                .orElseThrow(() -> new com.nexaai.auth.exception.AuthExceptions
                        .InvalidTokenException("Refresh token is not recognised."));

        if (stored.isAlreadyUsed()) {
            // Reuse of a rotated token: it was copied, because the legitimate holder always
            // holds the newest one. Revoke the whole family.
            //
            // The revocation runs in its OWN transaction. This branch throws, and a write in the
            // throwing request's transaction would roll straight back, leaving the stolen token
            // usable — which would make the detection pointless. That was found by an
            // integration test asserting the rotated token dies.
            securityStateWriter.revokeRefreshTokenFamily(stored.getFamilyId());
            throw new com.nexaai.auth.exception.AuthExceptions.TokenReuseDetectedException();
        }

        if (!stored.isUsable()) {
            throw new com.nexaai.auth.exception.AuthExceptions
                    .InvalidTokenException("Refresh token has expired.");
        }

        stored.revoke();
        repository.save(stored);

        IssuedRefreshToken replacement = rotate(
                stored.getFamilyId(), stored.getUserId(), stored.getSource());

        return new ExchangeResult(stored.getUserId(), replacement, false);
    }

    /** Revokes one refresh token. Used by sign-out. */
    @Transactional
    public boolean revokeToken(String presentedToken) {
        return repository.findByTokenHash(hash(presentedToken))
                .map(token -> {
                    token.revoke();
                    repository.save(token);
                    return true;
                })
                .orElse(false);
    }

    /** Revokes every live refresh token for a user. Used by sign-out-all and password changes. */
    @Transactional
    public int revokeAllForUser(UUID userId) {
        return repository.revokeAllForUser(userId, Instant.now());
    }

    /** Generates a URL-safe random token. */
    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256, hex encoded.
     *
     * <p>Plain SHA-256 rather than bcrypt or Argon2 on purpose. The input is 256 bits of
     * cryptographically random data, so there is nothing to brute force; a slow hash here
     * would only add latency to every refresh. A slow hash is right for a user-chosen
     * password, which is why the two are hashed differently.
     */
    static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the platform but is unavailable", e);
        }
    }
}