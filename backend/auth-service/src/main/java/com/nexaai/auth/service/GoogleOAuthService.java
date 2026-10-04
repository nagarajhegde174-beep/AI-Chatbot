package com.nexaai.auth.service;

import com.nexaai.auth.config.AuthProperties;
import com.nexaai.auth.domain.AccountStatus;
import com.nexaai.auth.domain.AuthEventType;
import com.nexaai.auth.domain.AuthUser;
import com.nexaai.auth.domain.EmailNormalizer;
import com.nexaai.auth.exception.AuthExceptions;
import com.nexaai.auth.outbox.OutboxService;
import com.nexaai.auth.repository.AuthUserRepository;
import com.nexaai.auth.security.JwtService;
import com.nexaai.auth.security.RefreshTokenService;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Continue with Google".
 *
 * <p><strong>No Google password is ever seen, stored or handled by this service.</strong> Google
 * authenticates the user; this service receives only an assertion. There is no code path that
 * could store one, and no {@code password_hash} is ever written for a Google-only account.
 *
 * <p><strong>Linking rule.</strong> An account is linked to a Google identity when either the
 * Google subject is already linked, or the email matches an existing account <em>and</em> Google
 * asserts it is verified. Linking is never performed on an unverified match, because that would
 * let anyone who can create an account with someone's email address claim that account.
 *
 * <p><strong>What is trusted.</strong> {@code email_verified} from Google's ID token. It is
 * part of the signed assertion, not a claim the caller can supply.
 */
@Service
public class GoogleOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthService.class);

    private final AuthUserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthAuditService auditService;
    private final OutboxService outboxService;
    private final AuthProperties properties;

    public GoogleOAuthService(AuthUserRepository userRepository,
                              JwtService jwtService,
                              RefreshTokenService refreshTokenService,
                              AuthAuditService auditService,
                              OutboxService outboxService,
                              AuthProperties properties) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.outboxService = outboxService;
        this.properties = properties;
    }

    /**
     * Resolves a Google sign-in to a local account, creating one if needed.
     *
     * @throws AuthExceptions.AccessDeniedException if Google has not verified the email, since
     *         this service will not create or link an account on an unverified address
     */
    @Transactional
    public AuthService.AuthenticationResult authenticate(OAuth2AuthenticationToken oauth2Token,
                                                         String ip, String userAgent,
                                                         String correlationId) {
        if (!properties.getGoogle().isEnabled()) {
            throw new AuthExceptions.AccessDeniedException();
        }

        OAuth2User principal = oauth2Token.getPrincipal();
        String subject = resolveSubject(principal);
        String email = EmailNormalizer.normalize(principal.getAttribute("email"));
        boolean emailVerifiedByGoogle = Boolean.TRUE.equals(principal.getAttribute("email_verified"));
        String displayName = resolveDisplayName(principal, email);

        if (email == null || email.isBlank()) {
            // Without an email there is no account to create or link.
            throw new AuthExceptions.AccessDeniedException();
        }

        if (!emailVerifiedByGoogle) {
            // Refusing here is the whole reason this service checks the flag. An unverified
            // Google email proves nothing about who controls the mailbox.
            log.warn("Refusing Google sign-in for {}: Google has not verified the email", email);
            throw new AuthExceptions.AccessDeniedException();
        }

        AuthUser user = resolveAccount(subject, email, displayName, ip, userAgent, correlationId);

        JwtService.IssuedAccessToken access = jwtService.issue(user);
        RefreshTokenService.IssuedRefreshToken refresh =
                refreshTokenService.issue(user.getId(), com.nexaai.auth.domain.TokenSource.GOOGLE);

        user.recordSuccessfulLogin();
        userRepository.save(user);

        auditService.record(user.getId(), AuthEventType.LOGIN, true, ip, userAgent);
        log.info("Issued tokens for user {} via GOOGLE", user.getId());

        return new AuthService.AuthenticationResult(user, access, refresh);
    }

    /**
     * Finds or creates the account for a Google identity, and refuses one that cannot sign in.
     *
     * <p>Three resolution cases, in order:
     * <ol>
     *   <li>The Google subject is already linked: use it. The authoritative match.</li>
     *   <li>No subject linked, but the email matches an account with no Google link: link it.
     *       Google verified the email, and the account's email is the same address, so this is
     *       the same person claiming an account they already registered with.</li>
     *   <li>Nothing matches: create a new, already-verified account.</li>
     * </ol>
     *
     * <p><strong>Status is checked after resolution, for every case.</strong> An earlier version
     * returned the account as soon as the subject matched, without looking at its status — so a
     * suspended or deactivated account could still sign in through Google while being locked
     * out of password sign-in. Found by the test that suspends an account and then signs in
     * with Google.
     */
    private AuthUser resolveAccount(String subject, String email, String displayName,
                                   String ip, String userAgent, String correlationId) {
        AuthUser user = resolveOrCreate(subject, email, displayName, ip, userAgent, correlationId);

        if (user.getStatus() != AccountStatus.ACTIVE) {
            log.warn("Refusing Google sign-in for user {}: account is {}", user.getId(), user.getStatus());
            throw new AuthExceptions.AccessDeniedException();
        }
        if (user.isLocked()) {
            log.warn("Refusing Google sign-in for user {}: account is locked", user.getId());
            throw new AuthExceptions.AccessDeniedException();
        }

        return user;
    }

    private AuthUser resolveOrCreate(String subject, String email, String displayName,
                                    String ip, String userAgent, String correlationId) {
        Optional<AuthUser> bySubject = userRepository.findByGoogleSubject(subject);
        if (bySubject.isPresent()) {
            // The subject is the identity key. A different email in the assertion for the same
            // subject means Google changed it; the account is the same person either way, so the
            // stored account is authoritative and the asserted email is ignored.
            return bySubject.get();
        }

        Optional<AuthUser> byEmail = userRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            AuthUser existing = byEmail.get();
            if (existing.getGoogleSubject() == null) {
                existing.linkGoogle(subject);
                userRepository.save(existing);
                auditService.record(existing.getId(), AuthEventType.GOOGLE_LINKED, true, ip, userAgent);
                log.info("Linked Google identity to existing account {}", existing.getId());
                return existing;
            }
            // The email matches an account already linked to a DIFFERENT Google identity.
            // Refused: allowing it would let someone who controls one Google account take over
            // an unrelated account that happens to share the address.
            log.warn("Refusing Google sign-in for {}: the email belongs to an account linked to another Google identity", email);
            throw new AuthExceptions.AccessDeniedException();
        }

        AuthUser created = AuthUser.fromGoogle(email, subject, displayName);
        try {
            userRepository.saveAndFlush(created);
        } catch (DataIntegrityViolationException e) {
            // A concurrent first sign-in created the account. Re-read and continue rather than
            // failing a legitimate sign-in over a race.
            return userRepository.findByEmail(email).orElseThrow(() -> e);
        }

        outboxService.recordUserRegistered(created, correlationId);
        log.info("Created account {} from a Google identity", created.getId());
        return created;
    }

    /**
     * Google's immutable user id.
     *
     * <p>Preferred over the email as the identity key. An email can change; the subject cannot,
     * and using the email as the key would let someone who acquires an address take over the
     * account.
     */
    private String resolveSubject(OAuth2User principal) {
        Object subject = principal.getAttribute("sub");
        if (subject == null || subject.toString().isBlank()) {
            throw new AuthExceptions.AccessDeniedException();
        }
        return subject.toString();
    }

    private String resolveDisplayName(OAuth2User principal, String email) {
        String name = principal.getAttribute("name");
        if (name != null && !name.isBlank()) {
            return name.length() > 120 ? name.substring(0, 120) : name;
        }
        if (email == null || email.isBlank()) {
            // Google returned neither a name nor an email. `email.split("@")` on a null here
            // threw a NullPointerException that surfaced as a 500; this was caught by a test
            // using a minimal principal with only a subject.
            return "NexaAI user";
        }
        return email.split("@")[0];
    }
}