package com.nexaai.user.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * User Service configuration.
 *
 * <p>Every value comes from an environment variable. Nothing security-relevant has a default:
 * a missing verification key fails at startup, which is the correct time to find out.
 */
@Validated
@ConfigurationProperties(prefix = "nexa.user")
public class UserProperties {

    private final Jwt jwt = new Jwt();
    private final Admin admin = new Admin();
    private final Event event = new Event();
    private final Client client = new Client();

    public Jwt getJwt() {
        return jwt;
    }

    public Admin getAdmin() {
        return admin;
    }

    public Event getEvent() {
        return event;
    }

    public Client getClient() {
        return client;
    }

    /**
     * JWT verification settings.
     *
     * <p>This service verifies tokens and never signs them, so it needs only the public key.
     * There is deliberately no {@code privateKey} property: a service that cannot sign cannot
     * mint a token for itself.
     */
    public static class Jwt {

        /** PEM-encoded RS256 public key. Required. */
        private String publicKey;

        private String issuer = "nexa-auth-service";
        private String audience = "nexaai-web";
        private String keyId = "nexa-auth-rs256-1";

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

    /** Administrative limits. */
    public static class Admin {

        /** Largest page the admin listing will serve, whatever the caller asks for. */
        @Positive
        private int maxPageSize = 100;

        /** Default page size when the caller does not specify one. */
        @Positive
        private int defaultPageSize = 20;

        public int getMaxPageSize() {
            return maxPageSize;
        }

        public void setMaxPageSize(int maxPageSize) {
            this.maxPageSize = maxPageSize;
        }

        public int getDefaultPageSize() {
            return defaultPageSize;
        }

        public void setDefaultPageSize(int defaultPageSize) {
            this.defaultPageSize = defaultPageSize;
        }
    }

    /** Inbound event settings. */
    public static class Event {

        /**
         * Whether the Kafka listener is active.
         *
         * <p>Off in tests and in any environment without a broker. Registration is delivered by
         * replay in a fresh environment, so the service must be able to run without consuming.
         */
        private boolean enabled = true;

        /** Consumer group. Distinct per service, never shared. */
        private String consumerGroup = "user-profile-events";

        private List<String> topics = new ArrayList<>(List.of(
                "auth.user.registered.v1",
                "auth.user.email_verified.v1",
                "user.status.changed.v1",
                "subscription.activated.v1",
                "subscription.cancelled.v1"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getConsumerGroup() {
            return consumerGroup;
        }

        public void setConsumerGroup(String consumerGroup) {
            this.consumerGroup = consumerGroup;
        }

        public List<String> getTopics() {
            return topics;
        }

        public void setTopics(List<String> topics) {
            this.topics = topics;
        }
    }

    /**
     * Outbound service calls.
     *
     * <p>Subscription Service is implemented in Phase 9. Until then the admin view reports
     * usage and subscription figures as unavailable rather than showing zeros, because a zero
     * reads as "this user has used nothing" and is a different claim.
     */
    public static class Client {

        private String subscriptionServiceUrl = "http://localhost:8087";

        private Duration timeout = Duration.ofSeconds(3);

        /** Whether to attempt outbound calls at all. Off until the target service exists. */
        private boolean enabled = false;

        public String getSubscriptionServiceUrl() {
            return subscriptionServiceUrl;
        }

        public void setSubscriptionServiceUrl(String subscriptionServiceUrl) {
            this.subscriptionServiceUrl = subscriptionServiceUrl;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}