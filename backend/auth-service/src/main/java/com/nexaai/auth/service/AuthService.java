package com.nexaai.auth.service;

import com.nexaai.auth.config.AuthProperties;
import com.nexaai.auth.domain.AccountStatus;
import com.nexaai.auth.domain.AuthEventType;
import com.nexaai.auth.domain.AuthUser;
import com.nexaai.auth.domain.EmailNormalizer;
import com.nexaai.auth.domain.OneTimeToken;
import com.nexaai.auth.domain.OneTimeTokenPurpose;
import com.nexaai.auth.domain.RevokedToken;
import com.nexaai.auth.domain.TokenSource;
import com.nexaai.auth.exception.AuthExceptions;
import com.nexaai.auth.outbox.OutboxService;
import com.nexaai.auth.repository.AuthUserRepository;
import com.nexaai.auth.repository.OneTimeTokenRepository;
import com.nexaai.auth.repository.RevokedTokenRepository;
import com.nexaai.auth.security.JwtService;
import com.nexaai.auth.security.OneTimeTokenService;
import com.nexaai.auth.security.RefreshTokenService;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The authentication flows: registration, sign-in, refresh, sign-out, email verification,
 * password reset and password change.
 *
 * <p>Profile business logic does not belong here. User Service owns profiles and learns of an
 * account from {@code auth.user.registered.v1}.
 *
 * <p><strong>The enumeration rule.</strong> Every endpoint that could reveal whether an email
 * is registered returns the same answer either way: {@link #forgotPassword} always succeeds,
 * {@link #register} is the one exception and does report a duplicate, because silently
 * ignoring it leaves a user staring at a form that appears to work and then cannot sign in.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthUserRepository userRepository;
    private final OneTimeTokenRepository oneTimeTokenRepository;
    private final RevokedTokenRepository revokedTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final OneTimeTokenService oneTimeTokenService;
    private final AuthAuditService auditService;
    private final EmailNotificationService emailService;
    private final OutboxService outboxService;
    private final SecurityStateWriter securityState;
    private final AuthProperties properties;

    public AuthService(AuthUserRepository userRepository,
                       OneTimeTokenRepository oneTimeTokenRepository,
                       RevokedTokenRepository revokedTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       OneTimeTokenService oneTimeTokenService,
                       AuthAuditService auditService,
                       EmailNotificationService emailService,
                       OutboxService outboxService,
                       SecurityStateWriter securityState,
                       AuthProperties properties) {
        this.userRepository = userRepository;
        this.oneTimeTokenRepository = oneTimeTokenRepository;
        this.revokedTokenRepository = revokedTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.oneTimeTokenService = oneTimeTokenService;
        this.auditService = auditService;
        this.emailService = emailService;
        this.outboxService = outboxService;
        this.securityState = securityState;
        this.properties = properties;
    }

    /** A completed sign-in: the tokens plus the user they belong to. */
    public record AuthenticationResult(AuthUser user, JwtService.IssuedAccessToken accessToken,
                                       RefreshTokenService.IssuedRefreshToken refreshToken) {
    }

    // ==================================================================
    // Registration
    // ==================================================================

    /**
     * Creates an account.
     *
     * <p>Returns no tokens. The account must verify its email first, which prevents mass
     * unverified registration ({@code docs/SERVICE_CONTRACTS.md} section 5.4).
     *
     * <p>The duplicate check runs twice on purpose: once here for a clean 409, and once at the
     * database level via the unique index. The pre-check narrows the window; the index is what
     * actually prevents two concurrent registrations of the same email, which is the case the
     * pre-check cannot catch.
     */
    @Transactional
    public AuthUser register(String email, String rawPassword, String displayName,
                             String ip, String userAgent, String correlationId) {
        String normalized = EmailNormalizer.normalize(email);

        if (userRepository.existsByEmail(normalized)) {
            auditService.recordFailureByEmail(normalized, AuthEventType.REGISTER, ip, userAgent);
            throw new AuthExceptions.EmailAlreadyRegisteredException();
        }

        String passwordHash = passwordEncoder.encode(rawPassword);
        AuthUser user = AuthUser.register(normalized, passwordHash, displayName);

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // The unique index caught a concurrent duplicate that the pre-check missed.
            auditService.recordFailureByEmail(normalized, AuthEventType.REGISTER, ip, userAgent);
            throw new AuthExceptions.EmailAlreadyRegisteredException();
        }

        var token = oneTimeTokenService.issue(user.getId(), OneTimeTokenPurpose.EMAIL_VERIFICATION);
        emailService.sendVerificationEmail(user.getEmail(), token);

        outboxService.recordUserRegistered(user, correlationId);
        auditService.record(user.getId(), AuthEventType.REGISTER, true, ip, userAgent);

        log.info("Registered account {}", user.getId());
        return user;
    }

    // ==================================================================
    // Sign-in
    // ==================================================================

    /**
     * Verifies credentials and issues tokens.
     *
     * <p><strong>Enumeration safety.</strong> An unknown email costs a real Argon2 verification
     * against a dummy hash, so the response time is indistinguishable from a known email with
     * a wrong password. Returning early on an unknown email would make timing a reliable oracle
     * for which addresses are registered.
     */
    @Transactional
    public AuthenticationResult login(String email, String rawPassword, TokenSource source,
                                      String ip, String userAgent, String correlationId) {
        String normalized = EmailNormalizer.normalize(email);
        AuthUser user = userRepository.findByEmail(normalized).orElse(null);

        if (user == null) {
            burnTimeOnMissingUser(rawPassword);
            auditService.recordFailureByEmail(normalized, AuthEventType.LOGIN_FAILED, ip, userAgent);
            throw new AuthExceptions.InvalidCredentialsException();
        }

        if (user.isLocked()) {
            auditService.recordFailure(user.getId(), AuthEventType.LOGIN_FAILED, ip, userAgent);
            throw new AuthExceptions.AccountLockedException();
        }

        if (user.getStatus() != AccountStatus.ACTIVE) {
            auditService.recordFailure(user.getId(), AuthEventType.LOGIN_FAILED, ip, userAgent);
            throw accountStatusException(user.getStatus());
        }

        if (!user.isEmailVerified()) {
            auditService.recordFailure(user.getId(), AuthEventType.LOGIN_FAILED, ip, userAgent);
            throw new AuthExceptions.AccountNotActiveException(
                    "Verify your email address before signing in.");
        }

        if (user.getPasswordHash() == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            // Written in an independent transaction on purpose: this branch throws, and a write
            // in the throwing request's own transaction would be rolled back with it, so the
            // failure would never count and the account could be brute-forced forever.
            boolean nowLocked = securityState.recordFailedLogin(user.getId());
            auditService.recordFailure(user.getId(), AuthEventType.LOGIN_FAILED, ip, userAgent);
            if (nowLocked) {
                log.warn("Account {} locked after repeated failed sign-in attempts", user.getId());
                throw new AuthExceptions.AccountLockedException();
            }
            throw new AuthExceptions.InvalidCredentialsException();
        }

        return issueTokens(user, source, ip, userAgent);
    }

    /**
     * Issues tokens for a user whose identity is already established.
     *
     * <p>Used by both password sign-in and the Google callback, so the two paths cannot drift
     * apart on lockout checks, status checks or auditing.
     */
    private AuthenticationResult issueTokens(AuthUser user, TokenSource source,
                                             String ip, String userAgent) {
        JwtService.IssuedAccessToken access = jwtService.issue(user);
        RefreshTokenService.IssuedRefreshToken refresh = refreshTokenService.issue(user.getId(), source);

        user.recordSuccessfulLogin();
        userRepository.save(user);

        auditService.record(user.getId(), AuthEventType.LOGIN, true, ip, userAgent);
        log.info("Issued tokens for user {} via {}", user.getId(), source);

        return new AuthenticationResult(user, access, refresh);
    }

    /**
     * Performs a hash verification that cannot succeed, to equalise timing.
     *
     * <p>Without this, an unknown email returns in microseconds and a known email takes the
     * Argon2 cost, which is an oracle for which addresses are registered. The result is
     * discarded.
     */
    private void burnTimeOnMissingUser(String rawPassword) {
        try {
            passwordEncoder.matches(rawPassword, DUMMY_HASH);
        } catch (RuntimeException e) {
            // A failure here changes nothing; the point was only the time taken.
        }
    }

    /**
     * A valid Argon2id hash of a value nobody can supply.
     *
     * <p>It only has to be a well-formed hash that {@code matches} will evaluate, and that no
     * user will ever sign in with.
     */
    private static final String DUMMY_HASH =
            "$argon2id$v=19$m=65536,t=3,p=1$Y2FuYXJ5dW1teXVtdXVtZXRlc3R0ZXN0"
            + "$8k3QvJ8hQ2mN5pR7sT1uV4wX6yZ0aB2cD4eF6gH8jK0";

    private AuthExceptions.AccountNotActiveException accountStatusException(AccountStatus status) {
        return switch (status) {
            case SUSPENDED -> new AuthExceptions.AccountNotActiveException(
                    "This account has been suspended. Contact support.");
            case DEACTIVATED -> new AuthExceptions.AccountNotActiveException(
                    "This account has been closed.");
            case PENDING_VERIFICATION -> new AuthExceptions.AccountNotActiveException(
                    "Verify your email address before signing in.");
            case ACTIVE -> new AuthExceptions.AccountNotActiveException("This account cannot sign in.");
        };
    }

    // ==================================================================
    // Refresh
    // ==================================================================

    /**
     * Exchanges a refresh token.
     *
     * <p>Re-checks account status and lockout here, not only at sign-in. A suspended account's
     * refresh token must stop working immediately, not whenever its access token happens to
     * expire.
     */
    @Transactional
    public AuthenticationResult refresh(String presentedRefreshToken, String ip, String userAgent) {
        var exchange = refreshTokenService.exchange(presentedRefreshToken);

        AuthUser user = userRepository.findById(exchange.userId())
                .orElseThrow(() -> new AuthExceptions.InvalidTokenException("Account no longer exists."));

        if (user.getStatus() != AccountStatus.ACTIVE) {
            auditService.record(user.getId(), AuthEventType.TOKEN_REFRESH, false, ip, userAgent);
            throw accountStatusException(user.getStatus());
        }
        if (user.isLocked()) {
            auditService.record(user.getId(), AuthEventType.TOKEN_REFRESH, false, ip, userAgent);
            throw new AuthExceptions.AccountLockedException();
        }

        JwtService.IssuedAccessToken access = jwtService.issue(user);
        auditService.record(user.getId(), AuthEventType.TOKEN_REFRESH, true, ip, userAgent);

        return new AuthenticationResult(user, access, exchange.replacement());
    }

    // ==================================================================
    // Sign-out
    // ==================================================================

    /**
     * Signs out the current session.
     *
     * <p>The presented refresh token is revoked, and its access token is added to the denylist
     * so the cookie that may still carry it stops working immediately rather than after 15
     * minutes. Returning 204 whether or not a token was present keeps this endpoint from
     * becoming an oracle for whether a session existed.
     */
    @Transactional
    public void logout(UUID userId, String refreshToken, String accessToken,
                       String ip, String userAgent) {
        if (refreshToken != null) {
            refreshTokenService.revokeToken(refreshToken);
        }
        revokeAccessToken(userId, accessToken, "LOGOUT");
        auditService.record(userId, AuthEventType.LOGOUT, true, ip, userAgent);
    }

    /** Signs out every session, and denylists the presented access token too. */
    @Transactional
    public int logoutAll(UUID userId, String accessToken, String ip, String userAgent) {
        int revoked = securityState.revokeAllRefreshTokens(userId);
        revokeAccessToken(userId, accessToken, "LOGOUT_ALL");
        auditService.record(userId, AuthEventType.LOGOUT, true, ip, userAgent);
        return revoked;
    }

    /**
     * Adds an access token's {@code jti} to the denylist.
     *
     * <p>Best-effort: if the presented token cannot be read it simply is not denylisted, and
     * sign-out still succeeds. A sign-out that fails because a malformed token was supplied
     * would be worse than one that leaves a token to expire on its own.
     */
    private void revokeAccessToken(UUID userId, String accessToken, String reason) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        try {
            var claims = jwtService.verify(accessToken);
            String jti = claims.getId();
            if (jti == null) {
                return;
            }
            Instant expiry = claims.getExpiration() == null
                    ? Instant.now().plus(properties.getToken().getAccessTokenTtl())
                    : claims.getExpiration().toInstant();
            revokedTokenRepository.save(new RevokedToken(jti, userId, expiry, reason));
        } catch (RuntimeException e) {
            log.debug("Could not denylist the presented access token; it will expire naturally");
        }
    }

    // ==================================================================
    // Email verification
    // ==================================================================

    /**
     * Verifies an email address.
     *
     * <p>Idempotent: a link followed twice succeeds both times rather than reporting an error
     * for work already done. A user who clicks, loses the response and clicks again must not be
     * shown a failure.
     */
    @Transactional
    public AuthUser verifyEmail(String token, String ip, String userAgent) {
        OneTimeToken oneTime;
        try {
            oneTime = oneTimeTokenService.redeem(token, OneTimeTokenPurpose.EMAIL_VERIFICATION);
        } catch (AuthExceptions.AuthException e) {
            oneTimeTokenService.recordFailedAttempt(token, OneTimeTokenPurpose.EMAIL_VERIFICATION);
            throw e;
        }

        AuthUser user = userRepository.findById(oneTime.getUserId())
                .orElseThrow(() -> new AuthExceptions.InvalidTokenException("This link is not valid."));

        user.verifyEmail();
        userRepository.save(user);

        auditService.record(user.getId(), AuthEventType.EMAIL_VERIFIED, true, ip, userAgent);
        log.info("Verified email for user {}", user.getId());
        return user;
    }

    // ==================================================================
    // Password reset
    // ==================================================================

    /**
     * Starts a password reset.
     *
     * <p><strong>Always succeeds, always returns the same thing.</strong> Reporting "no account
     * with that email" would turn this endpoint into an address enumeration oracle
     * ({@code docs/SECURITY.md} section 2.1).
     *
     * <p>The token is emailed to the address on the account, never to an address supplied in
     * the request, so this endpoint cannot be used to mail arbitrary people.
     */
    @Transactional
    public void forgotPassword(String email, String ip, String userAgent) {
        String normalized = EmailNormalizer.normalize(email);
        AuthUser user = userRepository.findByEmail(normalized).orElse(null);

        if (user == null || user.getStatus() == AccountStatus.DEACTIVATED) {
            auditService.recordFailureByEmail(normalized, AuthEventType.PASSWORD_RESET_REQUESTED, ip, userAgent);
            return;
        }

        var token = oneTimeTokenService.issue(user.getId(), OneTimeTokenPurpose.PASSWORD_RESET);
        emailService.sendPasswordResetEmail(user.getEmail(), token);

        auditService.record(user.getId(), AuthEventType.PASSWORD_RESET_REQUESTED, true, ip, userAgent);
        log.info("Issued a password reset token for user {}", user.getId());
    }

    /**
     * Completes a password reset.
     *
     * <p>Revokes every refresh token, because a password reset is how someone recovers from a
     * compromise; leaving the attacker's sessions alive would defeat the entire point. Also
     * clears any lockout, since the user has just proved control of the mailbox.
     */
    @Transactional
    public AuthUser resetPassword(String token, String newPassword, String ip, String userAgent) {
        OneTimeToken oneTime;
        try {
            oneTime = oneTimeTokenService.redeem(token, OneTimeTokenPurpose.PASSWORD_RESET);
        } catch (AuthExceptions.AuthException e) {
            oneTimeTokenService.recordFailedAttempt(token, OneTimeTokenPurpose.PASSWORD_RESET);
            throw e;
        }

        AuthUser user = userRepository.findById(oneTime.getUserId())
                .orElseThrow(() -> new AuthExceptions.InvalidTokenException("This link is not valid."));

        user.changePasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        int revoked = securityState.revokeAllRefreshTokens(user.getId());
        log.info("Password reset completed for user {}; {} refresh token(s) revoked", user.getId(), revoked);

        auditService.record(user.getId(), AuthEventType.PASSWORD_RESET_COMPLETED, true, ip, userAgent);
        return user;
    }

    /**
     * Changes a password for a signed-in user.
     *
     * <p>The current password is required. Without it, a stolen access token would be enough
     * to lock the real owner out permanently, which turns a token theft into a denial of
     * service.
     */
    @Transactional
    public int changePassword(UUID userId, String currentPassword, String newPassword,
                              String currentAccessToken, String ip, String userAgent) {
        AuthUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthExceptions.InvalidTokenException("Account no longer exists."));

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new AuthExceptions.WrongPasswordException();
        }

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            // Not an error the caller needs to handle, but reusing a password is worth saying
            // so, and the message reveals nothing about the stored hash.
            throw new IllegalArgumentException("The new password must be different from the current one.");
        }

        user.changePasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Every other session dies. The caller's own access token is denied too, so the
        // remaining lifetime of the old credential is spent rather than 15 minutes.
        int revoked = securityState.revokeAllRefreshTokens(userId);
        revokeAccessToken(userId, currentAccessToken, "PASSWORD_CHANGED");

        auditService.record(userId, AuthEventType.PASSWORD_CHANGED, true, ip, userAgent);
        log.info("Password changed for user {}; {} refresh token(s) revoked", userId, revoked);
        return revoked;
    }
}