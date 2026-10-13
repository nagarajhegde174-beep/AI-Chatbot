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
    private final Memory memory = new Memory();
    private final Event event = new Event();
    private final UserServiceClient userService = new UserServiceClient();

    public Jwt getJwt() {
        return jwt;
    }

    public Limits getLimits() {
        return limits;
    }

    public Memory getMemory() {
        return memory;
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

        /**
         * The AI Service generate-stream path, relative to {@link #aiServiceUrl}.
         *
         * <p>Configurable rather than hard-coded so this service does not have to be recompiled
         * when AI Service moves an internal route. It is a path, not a full URL: composing it
         * here keeps the AI Service host in exactly one property, so a deployment cannot end up
         * with two different hosts depending on which route was used.
         */
        private String streamPath = "/internal/v1/ai/generate/stream";

        /**
         * The timeout for a BLOCKING generation.
         *
         * <p>Does not apply to the streamed path, which has no read timeout at all: a generation
         * may legitimately take minutes and must not be cut off halfway, leaving the user with a
         * partial answer and no error to explain it.
         */
        private Duration timeout = Duration.ofSeconds(60);

        public String getStreamPath() {
            return streamPath;
        }

        public void setStreamPath(String streamPath) {
            this.streamPath = streamPath;
        }

        /**
         * How long the browser-facing SSE connection is held open with nothing to say.
         *
         * <p>Not a generation timeout. Generations legitimately take minutes, and cutting the
         * connection off mid-answer would leave the user with a partial message and no error
         * explaining it — worse than waiting. This bounds the case where AI Service accepted the
         * request and then produced nothing at all.
         */
        private Duration streamTimeout = Duration.ofMinutes(5);

        public Duration getStreamTimeout() {
            return streamTimeout;
        }

        public void setStreamTimeout(Duration streamTimeout) {
            this.streamTimeout = streamTimeout;
        }

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
    /**
     * Conversation memory: how much context is carried, when it is compressed, and what it costs.
     *
     * <p><strong>The budget is this service's, not AI Service's.</strong> The model catalogue —
     * which model has which context window — belongs to AI Service, and duplicating it here would
     * be a second thing to keep in sync and a second thing to get wrong. So this budget is a
     * platform policy, set conservatively enough to fit any configured model, and AI Service
     * remains the final authority: it refuses anything that does not fit the model actually
     * chosen. Belt and braces, each layer catching the other's mistake.
     */
    public static class Memory {

        /**
         * Total approximate tokens of context to send with a request.
         *
         * <p>Covers the summary AND the verbatim turns. Deliberately well under the smallest
         * configured model's window: a conversation that fits this budget fits every model, so
         * changing model mid-conversation never causes a surprise rejection.
         */
        private int contextTokenBudget = 6000;

        /**
         * How many of the most recent turns are always sent verbatim, whatever else is dropped.
         *
         * <p>Token-based selection alone will, under pressure, drop the turn immediately before
         * the question — which is usually the one that explains what the question refers to. The
         * result is a model confidently answering a question whose subject it can no longer see.
         */
        private int alwaysKeepRecentTurns = 6;

        /**
         * Fraction of the budget above which older turns get compressed.
         *
         * <p>Summarisation costs a model call, so it must not run on every message. Triggering at
         * half the budget means it runs when there is genuine pressure and not before, and that
         * the summary plus the kept turns fit with room to spare.
         */
        private double summariseThreshold = 0.5;

        /**
         * Whether summarisation runs at all.
         *
         * <p>Off is a supported configuration, not a degraded one: with it off, memory is still
         * context-window managed, it just uses verbatim turns only and refuses a conversation
         * too long to fit rather than compressing it.
         */
        private boolean summarisationEnabled = true;

        /** How long to wait for a summarisation call. Separate from the generation timeout. */
        private Duration summariseTimeout = Duration.ofSeconds(45);

        public int getContextTokenBudget() {
            return contextTokenBudget;
        }

        public void setContextTokenBudget(int contextTokenBudget) {
            this.contextTokenBudget = contextTokenBudget;
        }

        public int getAlwaysKeepRecentTurns() {
            return alwaysKeepRecentTurns;
        }

        public void setAlwaysKeepRecentTurns(int alwaysKeepRecentTurns) {
            this.alwaysKeepRecentTurns = alwaysKeepRecentTurns;
        }

        public double getSummariseThreshold() {
            return summariseThreshold;
        }

        public void setSummariseThreshold(double summariseThreshold) {
            this.summariseThreshold = summariseThreshold;
        }

        public boolean isSummarisationEnabled() {
            return summarisationEnabled;
        }

        public void setSummarisationEnabled(boolean summarisationEnabled) {
            this.summarisationEnabled = summarisationEnabled;
        }

        public Duration getSummariseTimeout() {
            return summariseTimeout;
        }

        public void setSummariseTimeout(Duration summariseTimeout) {
            this.summariseTimeout = summariseTimeout;
        }
    }

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