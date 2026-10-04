package com.nexaai.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nexaai.auth.config.AuthProperties;
import com.nexaai.auth.domain.AccountStatus;
import com.nexaai.auth.domain.AuthUser;
import com.nexaai.auth.domain.TokenSource;
import com.nexaai.auth.exception.AuthExceptions;
import com.nexaai.auth.outbox.OutboxService;
import com.nexaai.auth.repository.AuthEventRepository;
import com.nexaai.auth.repository.AuthUserRepository;
import com.nexaai.auth.repository.OneTimeTokenRepository;
import com.nexaai.auth.repository.OutboxEventRepository;
import com.nexaai.auth.repository.RefreshTokenRepository;
import com.nexaai.auth.repository.RevokedTokenRepository;
import com.nexaai.auth.security.JwtService;
import com.nexaai.auth.security.RefreshTokenService;
import com.nexaai.auth.service.AuthAuditService;
import com.nexaai.auth.service.GoogleOAuthService;
import com.nexaai.auth.service.SecurityStateWriter;
import com.nexaai.auth.support.TestKeys;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.ActiveProfiles;

/**
 * "Continue with Google": account creation, linking, and the refusals.
 *
 * <p>The provider is mocked. What is under test is this service's decision logic — when it
 * trusts a Google assertion, when it refuses, and what it writes — not Google's behaviour.
 */
@SpringBootTest
@ActiveProfiles("test")
class GoogleOAuthServiceTest extends PostgresIntegrationTest {

    private static final String GOOGLE_CLIENT_ID = "nexa-google-client";
    private static final String SUBJECT = "google-subject-1234567890";

    @Autowired
    private AuthUserRepository userRepository;

    @Autowired
    private AuthEventRepository authEventRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private OneTimeTokenRepository oneTimeTokenRepository;

    @Autowired
    private RevokedTokenRepository revokedTokenRepository;

    private GoogleOAuthService googleOAuthService;
    private AuthProperties properties;

    @BeforeEach
    void setUp() {
        assertThat(databaseAvailable()).isTrue();

        // Clear state: this class is not transactional, so tests share the database.
        refreshTokenRepository.deleteAllInBatch();
        oneTimeTokenRepository.deleteAllInBatch();
        authEventRepository.deleteAllInBatch();
        revokedTokenRepository.deleteAllInBatch();
        outboxEventRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        // Google ENABLED for the main service: these tests exercise the sign-in path.
        // The refusal tests build their own service with it switched off.
        properties = properties(true);

        // The refresh-token repository is real, so tokens actually persist and the sign-in
        // path is genuinely exercised rather than mocked away.
        googleOAuthService = new GoogleOAuthService(
                userRepository,
                new JwtService(properties),
                new RefreshTokenService(refreshTokenRepository, properties, stateWriter(properties)),
                new AuthAuditService(authEventRepository, userRepository),
                new OutboxService(outboxEventRepository, new tools.jackson.databind.ObjectMapper()),
                properties);
    }

    // ==================================================================
    // Tests
    // ==================================================================

    @Test
    @DisplayName("a first Google sign-in creates a verified, active account")
    void createsAccountOnFirstSignIn() {
        var result = googleOAuthService.authenticate(tokenFor("ada@example.com", true), "1.2.3.4", "UA", "cid");

        AuthUser user = result.user();
        assertThat(user.getEmail()).isEqualTo("ada@example.com");
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(user.getRole().name()).isEqualTo("USER");
        assertThat(user.getGoogleSubject()).isEqualTo(SUBJECT);
        // No Google password is ever stored. The provider never gives us one to store.
        assertThat(user.getPasswordHash()).isNull();
    }

    @Test
    @DisplayName("a returning Google user gets the same account, not a second one")
    void sameSubjectResolvesToSameAccount() {
        googleOAuthService.authenticate(tokenFor("ada@example.com", true), "1.2.3.4", "UA", "cid");
        var second = googleOAuthService.authenticate(tokenFor("ada@example.com", true), "1.2.3.4", "UA", "cid");

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(second.user().getEmail()).isEqualTo("ada@example.com");
    }

    @Test
    @DisplayName("an existing password account is linked, not duplicated")
    void linksExistingAccountByVerifiedEmail() {
        // The same person registered with a password, then used Google with the same address.
        // Google asserting that address is verified is what makes the link safe.
        AuthUser existing = AuthUser.register("ada@example.com", "hash", "Ada");
        existing.verifyEmail();
        userRepository.saveAndFlush(existing);

        var result = googleOAuthService.authenticate(tokenFor("ada@example.com", true), "1.2.3.4", "UA", "cid");

        assertThat(result.user().getId()).isEqualTo(existing.getId());
        assertThat(result.user().getGoogleSubject()).isEqualTo(SUBJECT);
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("an unverified Google email is refused")
    void refusesUnverifiedGoogleEmail() {
        // The single most important assertion in this class. Linking or creating on an
        // unverified address would let anyone who can make Google accept that address claim
        // the account.
        assertThatThrownBy(() -> googleOAuthService.authenticate(
                tokenFor("ada@example.com", false), "1.2.3.4", "UA", "cid"))
                .isInstanceOf(AuthExceptions.AccessDeniedException.class);

        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("the Google subject wins over a changed email")
    void sameSubjectIgnoresChangedEmail() {
        AuthUser first = AuthUser.fromGoogle("first@example.com", SUBJECT, "First");
        userRepository.saveAndFlush(first);

        // The same Google identity asserting a different email means the person changed it at
        // Google. The stored account is the same human either way, so it is authoritative and
        // the assertion cannot redirect the session to a different address.
        var result = googleOAuthService.authenticate(tokenFor("second@example.com", true), "1.2.3.4", "UA", "cid");

        assertThat(result.user().getId()).isEqualTo(first.getId());
        assertThat(result.user().getEmail()).isEqualTo("first@example.com");
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("an email belonging to another account's Google identity is refused")
    void refusesEmailLinkedToAnotherGoogleIdentity() {
        AuthUser theirs = AuthUser.fromGoogle("ada@example.com", "someone-elses-subject", "Theirs");
        userRepository.saveAndFlush(theirs);

        // Google verifies ada@example.com for us, but that address is already linked to a
        // DIFFERENT Google identity. Honouring it would let anyone who controls one Google
        // account take over an unrelated account sharing the address.
        assertThatThrownBy(() -> googleOAuthService.authenticate(
                tokenFor("ada@example.com", true), "1.2.3.4", "UA", "cid"))
                .isInstanceOf(AuthExceptions.AccessDeniedException.class);

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("a principal with neither name nor email is refused, not a NullPointerException")
    void refusesMissingEmail() {
        // Google may return a minimal principal. `email.split("@")` on null used to throw,
        // surfacing as a 500 rather than a refusal.
        OAuth2User principal = principal(Map.of("sub", SUBJECT, "email_verified", true));
        OAuth2AuthenticationToken token =
                new OAuth2AuthenticationToken(principal, List.of(), GOOGLE_CLIENT_ID);

        assertThatThrownBy(() -> googleOAuthService.authenticate(token, "1.2.3.4", "UA", "cid"))
                .isInstanceOf(AuthExceptions.AccessDeniedException.class);
    }

    @Test
    @DisplayName("sign-in is refused entirely when Google is disabled")
    void refusesWhenGoogleDisabled() {
        GoogleOAuthService disabled = service(properties(false));

        // The feature must be genuinely absent when switched off, not merely hidden.
        assertThatThrownBy(() -> disabled.authenticate(tokenFor("ada@example.com", true), "1.2.3.4", "UA", "cid"))
                .isInstanceOf(AuthExceptions.AccessDeniedException.class);
    }

    @Test
    @DisplayName("a suspended account cannot sign in through Google either")
    void suspendedAccountIsRefused() {
        AuthUser suspended = AuthUser.fromGoogle("ada@example.com", SUBJECT, "Ada");
        suspended.transitionTo(AccountStatus.SUSPENDED);
        userRepository.saveAndFlush(suspended);

        assertThatThrownBy(() -> googleOAuthService.authenticate(
                tokenFor("ada@example.com", true), "1.2.3.4", "UA", "cid"))
                .isInstanceOf(AuthExceptions.AccessDeniedException.class);
    }

    @Test
    @DisplayName("a Google sign-in issues a GOOGLE-sourced session")
    void issuesGoogleSourcedSession() {
        googleOAuthService.authenticate(tokenFor("ada@example.com", true), "1.2.3.4", "UA", "cid");

        var token = userRepository.findByEmail("ada@example.com").orElseThrow()
                .getId();
        assertThat(refreshTokenRepository.findByUserIdAndRevokedAtIsNull(token))
                .singleElement()
                .satisfies(t -> assertThat(t.getSource()).isEqualTo(TokenSource.GOOGLE));
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private GoogleOAuthService service(AuthProperties props) {
        RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
        when(tokens.save(any())).thenAnswer(i -> i.getArgument(0));

        return new GoogleOAuthService(
                userRepository,
                new JwtService(props),
                new RefreshTokenService(tokens, props, stateWriter(props)),
                new AuthAuditService(authEventRepository, userRepository),
                new OutboxService(outboxEventRepository, new tools.jackson.databind.ObjectMapper()),
                props);
    }

    private OAuth2AuthenticationToken tokenFor(String email, boolean verified) {
        return new OAuth2AuthenticationToken(
                principal(Map.of(
                        "sub", SUBJECT,
                        "email", email,
                        "email_verified", verified,
                        "name", "Ada Lovelace")),
                List.of(),
                GOOGLE_CLIENT_ID);
    }

    private OAuth2User principal(Map<String, Object> attributes) {
        return new DefaultOAuth2User(
                AuthorityUtils.createAuthorityList("ROLE_USER"),
                attributes,
                "sub");
    }

    private SecurityStateWriter stateWriter(AuthProperties props) {
        return new SecurityStateWriter(userRepository, props,
                new SecurityStateWriter.RefreshRevoker(mock(RefreshTokenRepository.class)));
    }

    /** Properties with a freshly generated RS256 pair, so no key is ever committed. */
    private AuthProperties properties(boolean googleEnabled) {
        TestKeys.TestKeyMaterial material = TestKeys.material();
        AuthProperties p = new AuthProperties();
        p.getJwt().setPrivateKey(material.privateKeyPem());
        p.getJwt().setPublicKey(material.publicKeyPem());
        p.getGoogle().setEnabled(googleEnabled);
        return p;
    }
}