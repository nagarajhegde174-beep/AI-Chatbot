package com.nexaai.ai.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * AI Service configuration.
 *
 * <p><strong>No provider key has a default.</strong> A provider with no key is simply
 * <em>unavailable</em> — it is not silently skipped at startup and it is not defaulted to a
 * shared value. That is the whole reason this service can run with zero providers configured and
 * still answer `/models` honestly.
 *
 * <p><strong>Keys live here and nowhere else.</strong> They are read from the environment and
 * never logged, never returned by an endpoint, and never reach the frontend: this service is
 * server-side only and the browser never sees a provider credential.
 */
@Validated
@ConfigurationProperties(prefix = "nexa.ai")
public class AiProperties {

    private final Jwt jwt = new Jwt();
    private final Defaults defaults = new Defaults();
    private final Retry retry = new Retry();
    private final Usage usage = new Usage();
    private final Map<String, Provider> providers = new LinkedHashMap<>();
    private final List<Model> models = new ArrayList<>();

    public Jwt getJwt() {
        return jwt;
    }

    public Defaults getDefaults() {
        return defaults;
    }

    public Retry getRetry() {
        return retry;
    }

    public Usage getUsage() {
        return usage;
    }

    public Map<String, Provider> getProviders() {
        return providers;
    }

    public List<Model> getModels() {
        return models;
    }

    /**
     * Refuses a provider key this build does not know.
     *
     * <p>Spring binds {@code providers} as a map and preserves each key exactly as written, so a
     * typo like {@code openal:} binds cleanly and then means nothing: the operator sees a
     * provider that is "not configured" and no hint that the name was misspelled. Failing at
     * startup is the only point where the mistake is still cheap to fix.
     *
     * <p>Consistent with {@code ModelCatalog}, which refuses an unknown provider in the model
     * list for the same reason.
     */
    @jakarta.annotation.PostConstruct
    void validateProviderKeys() {
        for (String key : providers.keySet()) {
            if (!com.nexaai.ai.model.ProviderKind.isKnown(key)) {
                throw new IllegalStateException(
                        "Unknown provider '" + key + "' under nexa.ai.providers. Expected one of: "
                                + "openai, gemini, groq. Provider ids are matched "
                                + "case-insensitively.");
            }
        }
    }

    /** JWT verification. Verification only; this service never signs a token. */
    public static class Jwt {

        /** PEM-encoded RS256 public key. Required. */
        private String publicKey;

        private String issuer = "nexa-auth-service";
        private String audience = "nexaai-web";

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
    }

    /** Platform-wide defaults. */
    public static class Defaults {

        /**
         * The model used when a request names none.
         *
         * <p>Named explicitly rather than "whatever happens to be first", so behaviour does not
         * change when an operator adds a model to the list.
         */
        private String model = "nexa-default";

        private double temperature = 0.7;

        @Min(1)
        @Max(8192)
        private int maxOutputTokens = 1024;

        /**
         * Hard ceiling on output tokens per request.
         *
         * <p>Applied on top of whatever the caller asks for, because the caller is not the only
         * party with an interest in the bill.
         */
        @Positive
        private int maxOutputTokensCeiling = 8192;

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getMaxOutputTokens() {
            return maxOutputTokens;
        }

        public void setMaxOutputTokens(int maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
        }

        public int getMaxOutputTokensCeiling() {
            return maxOutputTokensCeiling;
        }

        public void setMaxOutputTokensCeiling(int maxOutputTokensCeiling) {
            this.maxOutputTokensCeiling = maxOutputTokensCeiling;
        }
    }

    /**
     * Retry policy for retryable provider failures.
     *
     * <p>Deliberately small. A retry policy that keeps a request alive for thirty seconds turns
     * a provider outage into a slow failure for every caller, which is worse for everyone than a
     * fast error and a fallback.
     */
    public static class Retry {

        private boolean enabled = true;

        @Min(1)
        @Max(5)
        private int maxAttempts = 3;

        private java.time.Duration initialBackoff = java.time.Duration.ofMillis(250);

        private java.time.Duration maxBackoff = java.time.Duration.ofSeconds(2);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public java.time.Duration getInitialBackoff() {
            return initialBackoff;
        }

        public void setInitialBackoff(java.time.Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
        }

        public java.time.Duration getMaxBackoff() {
            return maxBackoff;
        }

        public void setMaxBackoff(java.time.Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
        }
    }

    /**
     * Usage foundation.
     *
     * <p>This service holds NO DATABASE (CI rule R5), so usage is recorded in memory and exposed
     * through an endpoint. It is the seam a later phase backs with real storage; keeping it an
     * interface now means adding that is a new implementation rather than a new concept.
     */
    public static class Usage {

        /** Whether usage is recorded at all. Off is meaningfully cheaper per request. */
        private boolean enabled = true;

        /** How many recent records to retain in memory. */
        @Positive
        private int retainRecords = 500;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getRetainRecords() {
            return retainRecords;
        }

        public void setRetainRecords(int retainRecords) {
            this.retainRecords = retainRecords;
        }
    }

    /**
     * One provider.
     *
     * <p>Groq is an OpenAI-protocol endpoint, so it is configured exactly like OpenAI with a
     * different base URL rather than needing its own integration.
     */
    public static class Provider {

        /** Whether this provider is enabled for this deployment. */
        private boolean enabled = false;

        /** Provider credential. Never logged, never returned. Empty means unavailable. */
        private String apiKey = "";

        /** Base URL. Groq and any OpenAI-compatible endpoint differ only here. */
        private String baseUrl = "";

        /**
         * The Spring AI {@code ChatModel} bean name backing this provider.
         *
         * <p>Named rather than assumed so a deployment can choose its wiring, and so a missing
         * bean is reported as "provider unavailable" rather than as a startup crash.
         */
        private String chatModelBean = "";

        /**
         * The Spring AI {@code StreamingChatModel} bean backing this provider.
         *
         * <p><strong>Named explicitly rather than derived.</strong> Guessing a streaming bean name
         * from the blocking one — by appending a suffix, say — is how a deployment ends up with
         * streaming silently unavailable while the blocking path works fine and the health
         * endpoint still reports the provider as up. An absent streaming model is reported as
         * "no streaming model is wired", which is a fact an operator can act on.
         */
        private String streamingModelBean = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getChatModelBean() {
            return chatModelBean;
        }

        public void setChatModelBean(String chatModelBean) {
            this.chatModelBean = chatModelBean;
        }

        public String getStreamingModelBean() {
            return streamingModelBean;
        }

        public void setStreamingModelBean(String streamingModelBean) {
            this.streamingModelBean = streamingModelBean;
        }
    }

    /**
     * One model the router may select.
     *
     * <p>A model is a name plus a provider plus the limits that provider enforces. Declaring
     * models here rather than discovering them from a provider is what makes fallback ordering and
     * token limits knowable before a request arrives, and what makes an unknown model a clean 400
     * instead of a provider-side surprise.
     */
    public static class Model {

        /** The name callers use. Unique across the catalog. */
        @NotBlank
        private String name;

        /** Which configured provider serves it. */
        @NotBlank
        private String provider;

        /** Fallback order: lower is tried first. Unique per provider group. */
        @Min(1)
        private int priority = 100;

        /** Whether this model accepts a temperature parameter. */
        private boolean supportsTemperature = true;

        @Positive
        private int maxInputTokens = 128_000;

        @Positive
        private int maxOutputTokens = 4096;

        /**
         * Whether a provider failure should fall through to the next candidate.
         *
         * <p>On for interchangeable models. Off where a silent downgrade would be wrong — for
         * instance a caller who asked for a specific large-context model and would not accept a
         * smaller one.
         */
        private boolean allowFallback = true;

        /**
         * Whether fallback may cross to a <em>different provider</em>.
         *
         * <p><strong>Off by default, deliberately.</strong> Same-provider fallback is a safe
         * degradation: two models on one provider are broadly interchangeable, so substituting one
         * for another is a detail. Crossing providers is not — a request for Gemini answered by
         * Llama is a different product, and the caller did not agree to it. So an operator has to
         * say yes, per model, knowing the answer will come from somewhere else.
         *
         * <p>This is also the only thing that lets fallback survive a provider <em>outage</em>,
         * which same-provider fallback structurally cannot do: when a provider is down, every
         * model behind it is down too.
         */
        private boolean allowCrossProviderFallback = false;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

        public boolean isSupportsTemperature() {
            return supportsTemperature;
        }

        public void setSupportsTemperature(boolean supportsTemperature) {
            this.supportsTemperature = supportsTemperature;
        }

        public int getMaxInputTokens() {
            return maxInputTokens;
        }

        public void setMaxInputTokens(int maxInputTokens) {
            this.maxInputTokens = maxInputTokens;
        }

        public int getMaxOutputTokens() {
            return maxOutputTokens;
        }

        public void setMaxOutputTokens(int maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
        }

        public boolean isAllowFallback() {
            return allowFallback;
        }

        public void setAllowFallback(boolean allowFallback) {
            this.allowFallback = allowFallback;
        }

        public boolean isAllowCrossProviderFallback() {
            return allowCrossProviderFallback;
        }

        public void setAllowCrossProviderFallback(boolean allowCrossProviderFallback) {
            this.allowCrossProviderFallback = allowCrossProviderFallback;
        }
    }
}
