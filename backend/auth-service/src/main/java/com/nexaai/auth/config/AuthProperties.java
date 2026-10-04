package com.nexaai.auth.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Auth Service configuration.
 *
 * <p>Every value comes from an environment variable. There is no default for anything
 * security-relevant, and no fallback that lets the service start with a weak
 * configuration: a missing key fails at startup, which is the correct time to discover it.
 * A missing API key is a deployment error, not a runtime condition
 * ({@code docs/SECURITY.md} section 10).
 *
 * <p>Bound from {@code nexa.auth.*}, which maps to {@code NEXA_AUTH_*} environment
 * variables.
 */
@Validated
@ConfigurationProperties(prefix = "nexa.auth")
public class AuthProperties {

    private final Jwt jwt = new Jwt();
    private final Token token = new Token();
    private final Cookie cookie = new Cookie();
    private final Password password = new Password();
    private final Lockout lockout = new Lockout();
    private final OneTime oneTime = new OneTime();
    private final Outbox outbox = new Outbox();
    private final Google google = new Google();

    public Jwt getJwt() {
        return jwt;
    }

    public Token getToken() {
        return token;
    }

    public Cookie getCookie() {
        return cookie;
    }

    public Password getPassword() {
        return password;
    }

    public Lockout getLockout() {
        return lockout;
    }

    public OneTime getOneTime() {
        return oneTime;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public Google getGoogle() {
        return google;
    }

    /** JWT signing and verification. */
    public static class Jwt {

        /**
         * PEM-encoded RS256 private key, supplied inline or as a path.
         *
         * <p>{@code openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem}
         */
        private String privateKey;

        /**
         * PEM-encoded RS256 public key.
         *
         * <p>Read separately from the private key so a deployment can publish the JWKS
         * without ever loading the private key into a verifier.
         */
        private String publicKey;

        private String issuer = "nexa-auth-service";
        private String audience = "nexaai-web";
        private String keyId = "nexa-auth-rs256-1";

        public String getPrivateKey() {
            return privateKey;
        }

        public void setPrivateKey(String privateKey) {
            this.privateKey = privateKey;
        }

        public String getPublicKey() {
            return publicKey;
        }

        public void setPublicKey(String publicKey) {
            this.publicKey = publicKey;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }
    }

    /** Token lifetimes. */
    public static class Token {

        /**
         * Access token lifetime: 15 minutes.
         *
         * <p>Short deliberately. It bounds how long a leaked access token remains useful,
         * and it is the main lever for making account suspension take effect quickly
         * without needing a revocation check on every request.
         */
        private Duration accessTokenTtl = Duration.ofMinutes(15);

        /** Refresh token lifetime: 30 days. */
        private Duration refreshTokenTtl = Duration.ofDays(30);

        /** Email verification link lifetime: 24 hours. */
        private Duration verificationTokenTtl = Duration.ofHours(24);

        /** Password reset link lifetime: 1 hour, deliberately shorter. */
        private Duration resetTokenTtl = Duration.ofHours(1);

        /**
         * Maximum wrong attempts before an account is locked for {@code lockDuration}.
         */
        @Positive
        private int maxFailedLogins = 5;

        /** How long an account stays locked. */
        private Duration lockDuration = Duration.ofMinutes(15);

        /**
         * Window over which failures are counted.
         *
         * <p>Bounded so a failure from last month cannot contribute to a decision today.
         */
        private Duration failureWindow = Duration.ofMinutes(15);

        public Duration getAccessTokenTtl() {
            return accessTokenTtl;
        }

        public void setAccessTokenTtl(Duration accessTokenTtl) {
            this.accessTokenTtl = accessTokenTtl;
        }

        public Duration getRefreshTokenTtl() {
            return refreshTokenTtl;
        }

        public void setRefreshTokenTtl(Duration refreshTokenTtl) {
            this.refreshTokenTtl = refreshTokenTtl;
        }

        public Duration getVerificationTokenTtl() {
            return verificationTokenTtl;
        }

        public void setVerificationTokenTtl(Duration verificationTokenTtl) {
            this.verificationTokenTtl = verificationTokenTtl;
        }

        public Duration getResetTokenTtl() {
            return resetTokenTtl;
        }

        public void setResetTokenTtl(Duration resetTokenTtl) {
            this.resetTokenTtl = resetTokenTtl;
        }

        public int getMaxFailedLogins() {
            return maxFailedLogins;
        }

        public void setMaxFailedLogins(int maxFailedLogins) {
            this.maxFailedLogins = maxFailedLogins;
        }

        public Duration getLockDuration() {
            return lockDuration;
        }

        public void setLockDuration(Duration lockDuration) {
            this.lockDuration = lockDuration;
        }

        public Duration getFailureWindow() {
            return failureWindow;
        }

        public void setFailureWindow(Duration failureWindow) {
            this.failureWindow = failureWindow;
        }
    }

    /**
     * Cookie settings for token delivery.
     *
     * <p>The access and refresh tokens are delivered as HTTP-only cookies, never in a JSON
     * body. An HTTP-only cookie is unreadable by JavaScript, so a cross-site scripting bug
     * becomes a nuisance rather than a credential theft
     * ({@code docs/SECURITY.md} section 3).
     */
    public static class Cookie {

        /** Prefix for the cookie names, so several services can share a domain. */
        private String prefix = "nexa";

        /**
         * Whether to set the {@code Secure} flag.
         *
         * <p>Must be true everywhere except plain-HTTP local development. A cookie without
         * {@code Secure} is sent over plaintext, so a token would cross the network in the
         * clear.
         */
        private boolean secure = true;

        /**
         * {@code SameSite} mode.
         *
         * <p>{@code Strict} is the default and the safest. Lax or None widen cross-site
         * behaviour and must be a deliberate choice with a reason, not a default.
         */
        private String sameSite = "Strict";

        /** Root path, so the cookies accompany every API call. */
        private String path = "/";

        /** Optional cookie domain. Unset means host-only, which is the safer default. */
        private String domain;

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public boolean isSecure() {
            return secure;
        }

        public void setSecure(boolean secure) {
            this.secure = secure;
        }

        public String getSameSite() {
            return sameSite;
        }

        public void setSameSite(String sameSite) {
            this.sameSite = sameSite;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getDomain() {
            return domain;
        }

        public void setDomain(String domain) {
            this.domain = domain;
        }
    }

    /** Password hashing. */
    public static class Password {

        /**
         * Encoding algorithm. {@code argon2} (memory-hard, preferred) or {@code bcrypt}.
         *
         * <p>Argon2id is the default because it resists GPU and side-channel attacks better
         * than bcrypt at equivalent cost.
         */
        private String encoder = "argon2";

        /**
         * Argon2 memory cost in kibibytes.
         *
         * <p>{@code null} means Spring Security's own default, which tracks current OWASP
         * guidance. Pinning a number here would silently rot as hardware improves.
         */
        private Integer argon2Memory;

        private Integer argon2Iterations;
        private Integer argon2Parallelism;

        /** Minimum accepted password length. */
        @Positive
        private int minLength = 12;

        /** Maximum accepted length, to bound hashing cost on a hostile input. */
        @Positive
        private int maxLength = 128;

        public String getEncoder() {
            return encoder;
        }

        public void setEncoder(String encoder) {
            this.encoder = encoder;
        }

        public Integer getArgon2Memory() {
            return argon2Memory;
        }

        public void setArgon2Memory(Integer argon2Memory) {
            this.argon2Memory = argon2Memory;
        }

        public Integer getArgon2Iterations() {
            return argon2Iterations;
        }

        public void setArgon2Iterations(Integer argon2Iterations) {
            this.argon2Iterations = argon2Iterations;
        }

        public Integer getArgon2Parallelism() {
            return argon2Parallelism;
        }

        public void setArgon2Parallelism(Integer argon2Parallelism) {
            this.argon2Parallelism = argon2Parallelism;
        }

        public int getMinLength() {
            return minLength;
        }

        public void setMinLength(int minLength) {
            this.minLength = minLength;
        }

        public int getMaxLength() {
            return maxLength;
        }

        public void setMaxLength(int maxLength) {
            this.maxLength = maxLength;
        }
    }

    /** Placeholder so the lockout settings live under {@code nexa.auth.lockout}. */
    public static class Lockout {

        /** Whether lockout is enabled. */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /** One-time token settings. */
    public static class OneTime {

        /**
         * Whether one-time tokens are logged instead of emailed.
         *
         * <p>For development only. It writes the token to the log so a developer can click
         * the link without a mail server. It is refused outside the local and test profiles,
         * so it cannot be switched on in a deployed environment by accident.
         */
        private boolean logTokens = false;

        public boolean isLogTokens() {
            return logTokens;
        }

        public void setLogTokens(boolean logTokens) {
            this.logTokens = logTokens;
        }
    }

    /** Outbox publisher settings. */
    public static class Outbox {

        /** Whether the publisher runs. Off in tests, where events are asserted directly. */
        private boolean enabled = true;

        /** How often the publisher polls. */
        private Duration pollInterval = Duration.ofSeconds(5);

        /** Rows claimed per poll. Bounded so a backlog cannot be loaded whole. */
        private int batchSize = 100;

        /** Attempts before an event is flagged as needing operator attention. */
        @Positive
        private int maxAttempts = 10;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getPollInterval() {
            return pollInterval;
        }

        public void setPollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }
    }

    /** Google OAuth2 client settings. */
    public static class Google {

        /**
         * Whether the Google provider is enabled.
         *
         * <p>Off unless credentials are configured, so a deployment that has not set up
         * Google does not fail at startup, and one that has gets the button.
         */
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}