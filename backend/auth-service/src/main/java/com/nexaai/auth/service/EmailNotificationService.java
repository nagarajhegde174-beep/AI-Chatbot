package com.nexaai.auth.service;

import com.nexaai.auth.config.AuthProperties;
import com.nexaai.auth.domain.OneTimeToken;
import com.nexaai.auth.domain.OneTimeTokenPurpose;
import com.nexaai.auth.security.OneTimeTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Turns a one-time token into a link the user can click.
 *
 * <p>Phase 1 has no mail service, so this publishes a domain event carrying the link rather
 * than sending mail. Wiring an actual transport in a later phase means adding a listener; it
 * does not mean changing the flows.
 *
 * <p><strong>The plaintext token exists only here and in the link.</strong> It is never
 * written to the database, never logged outside the explicit local-development setting, and
 * never returned in an API response.
 */
@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    /** Published once a verification link is ready. */
    public record VerificationEmailReady(String email, String link, java.time.Instant expiresAt) {
    }

    /** Published once a password reset link is ready. */
    public record PasswordResetEmailReady(String email, String link, java.time.Instant expiresAt) {
    }

    private final AuthProperties properties;
    private final ApplicationEventPublisher publisher;
    private final Environment environment;

    public EmailNotificationService(AuthProperties properties,
                                    ApplicationEventPublisher publisher,
                                    Environment environment) {
        this.properties = properties;
        this.publisher = publisher;
        this.environment = environment;
    }

    /**
     * Base URL for links in emails.
     *
     * <p>Read from configuration rather than derived from the request, so a forged
     * {@code Host} header cannot turn a reset email into a phishing email pointing at an
     * attacker's domain. That is a real attack and the only defence is not to trust the
     * request for the link host.
     */
    private String baseUrl() {
        String configured = environment.getProperty("nexa.auth.frontend-base-url", "");
        if (configured == null || configured.isBlank()) {
            // Local development fallback only.
            return "http://localhost:5173";
        }
        return configured;
    }

    @Transactional(readOnly = true)
    public void sendVerificationEmail(String email, OneTimeTokenService.IssuedToken token) {
        String link = UriComponentsBuilder
                .fromUriString(baseUrl())
                .path("/verify-email")
                .queryParam("token", token.plaintext())
                .build()
                .toUriString();

        maybeLog(email, link, "email verification");
        publisher.publishEvent(new VerificationEmailReady(email, link, token.entity().getExpiresAt()));
    }

    @Transactional(readOnly = true)
    public void sendPasswordResetEmail(String email, OneTimeTokenService.IssuedToken token) {
        String link = UriComponentsBuilder
                .fromUriString(baseUrl())
                .path("/reset-password")
                .queryParam("token", token.plaintext())
                .build()
                .toUriString();

        maybeLog(email, link, "password reset");
        publisher.publishEvent(new PasswordResetEmailReady(email, link, token.entity().getExpiresAt()));
    }

    /**
     * Logs the link in local development only.
     *
     * <p>Refused outside a local profile. A verification or reset token in a log is a
     * credential in a widely readable place, and a development convenience must not be
     * switchable in production ({@code docs/SECURITY.md} section 5.1).
     */
    private void maybeLog(String email, String link, String purpose) {
        if (!properties.getOneTime().isLogTokens()) {
            return;
        }
        String[] profiles = environment.getActiveProfiles();
        boolean local = java.util.Arrays.stream(profiles)
                .anyMatch(p -> "local".equals(p) || "dev".equals(p) || "test".equals(p));
        if (!local) {
            log.error("nexa.auth.one-time.log-tokens is enabled outside a local profile. Refusing to log a {}.", purpose);
            return;
        }
        log.warn("LOCAL DEVELOPMENT: {} link for {}: {}", purpose, email, link);
    }
}