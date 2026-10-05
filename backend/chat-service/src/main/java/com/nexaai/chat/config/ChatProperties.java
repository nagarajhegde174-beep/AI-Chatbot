package com.nexaai.chat.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Chat Service configuration. Every value comes from the environment. */
@Validated
@ConfigurationProperties(prefix = "nexa.chat")
public class ChatProperties {

    private final Jwt jwt = new Jwt();
    private final Limits limits = new Limits();
    private final Generation generation = new Generation();
    private final Event event = new Event();
    private final UserServiceClient userService = new UserServiceClient();

    public Jwt getJwt() {
        return jwt;
    }

    public Limits getLimits() {
        return limits;
    }

    public Generation getGeneration() {
        return generation;
    }

    public Event getEvent() {
        return event;
    }

    public UserServiceClient getUserService() {
        return userService;
    }

    /** JWT verification settings. Verification only; there is no private-key property. */
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

    /** Request limits. */
    public static class Limits {

        /** Largest page the listing will serve, whatever the caller asks for. */
        @Positive
        private int maxPageSize = 100;

        @Positive
        private int defaultPageSize = 20;

        /**
         * Longest single message, in characters.
         *
         * <p>Characters rather than bytes because that is what the column and the UI reason about.
         * A byte limit would reject messages a user cannot tell are long.
         */
        @Positive
        private int maxMessageLength = 32_000;

        /** Longest title, matching the column. */
        @Positive
        private int maxTitleLength = 200;

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

        public int getMaxMessageLength() {
            return maxMessageLength;
        }

        public void setMaxMessageLength(int maxMessageLength) {
            this.maxMessageLength = maxMessageLength;
        }

        public int getMaxTitleLength() {
            return maxTitleLength;
        }

        public void setMaxTitleLength(int maxTitleLength) {
            this.maxTitleLength = maxTitleLength;
        }
    }

    /**
     * AI generation.
     *
     * <p><strong>Disabled in this phase.</strong> There is no AI Service yet, so generation is off
     * and a sent message gets a PENDING placeholder that stays PENDING. That is the honest state:
     * the user can see that a reply is expected and has not arrived, rather than being shown a
     * fabricated answer or an error claiming a service failed.
     */
    public static class Generation {

        private boolean enabled = false;

        /** Where generation is attempted when enabled. Unused while disabled. */
        private String aiServiceUrl = "http://localhost:8084";

        private Duration timeout = Duration.ofSeconds(60);

        /** The model used when a conversation does not pin one. */
        private String defaultModel = "nexa-default";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAiServiceUrl() {
            return aiServiceUrl;
        }

        public void setAiServiceUrl(String aiServiceUrl) {
            this.aiServiceUrl = aiServiceUrl;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public String getDefaultModel() {
            return defaultModel;
        }

        public void setDefaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
        }
    }

    /** Inbound event settings. */
    public static class Event {

        /**
         * Whether the Kafka listener exists at all.
         *
         * <p>Off by default in this phase. The service starts and serves traffic with no broker.
         */
        private boolean enabled = false;

        /** This service's own group. Never shared: a shared group makes services compete. */
        private String consumerGroup = "chat-events";

        private List<String> topics = new ArrayList<>(List.of(
                "user.account.deleted.v1",
                "chat.conversation.created.v1"));

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
     * Outbound calls to User Service.
     *
     * <p>This is the REST boundary the design requires when user information is needed. It is off
     * until something actually needs it, so the service does not fail to start over an absent
     * dependency it never calls.
     */
    public static class UserServiceClient {

        private boolean enabled = false;

        private String baseUrl = "http://localhost:8082";

        private Duration timeout = Duration.ofSeconds(3);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }
}