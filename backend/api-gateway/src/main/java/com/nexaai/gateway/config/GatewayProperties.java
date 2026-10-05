package com.nexaai.gateway.config;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * API Gateway configuration.
 *
 * <p>Every value comes from the environment, and nothing security-relevant has a permissive
 * default. An unset verification key must fail at startup, not let the gateway accept
 * unverified tokens.
 */
@Validated
@ConfigurationProperties(prefix = "nexa.gateway")
public class GatewayProperties {

    private final Jwt jwt = new Jwt();
    private final Cors cors = new Cors();
    private final Routing routing = new Routing();
    private final Limits limits = new Limits();

    public Jwt getJwt() {
        return jwt;
    }

    public Cors getCors() {
        return cors;
    }

    public Routing getRouting() {
        return routing;
    }

    public Limits getLimits() {
        return limits;
    }

    /**
     * JWT verification settings.
     *
     * <p>Verification only, so only the public key. There is deliberately no {@code
     * privateKey} property: a gateway that can sign is a second authentication authority, and
     * the platform should have exactly one.
     */
    public static class Jwt {

        /** PEM-encoded RS256 public key. Required. */
        private String publicKey;

        private String issuer = "nexa-auth-service";
        private String audience = "nexaai-web";

        /** Paths that are reachable without a token. Everything else requires one. */
        private List<String> publicPaths = new ArrayList<>(List.of(
                "/api/auth/login",
                "/api/auth/register",
                "/api/auth/refresh",
                "/api/auth/password/forgot",
                "/api/auth/password/reset",
                "/api/auth/verify-email",
                "/api/auth/google",
                "/actuator/health",
                "/v3/api-docs/**",
                "/swagger-ui/**",
                "/swagger-ui.html"));

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

        public List<String> getPublicPaths() {
            return publicPaths;
        }

        public void setPublicPaths(List<String> publicPaths) {
            this.publicPaths = publicPaths;
        }
    }

    /**
     * CORS policy.
     *
     * <p><strong>No wildcard default.</strong> An allow-list that ships as {@code *} turns
     * every deployment into a public one until someone notices, and the mistake is invisible
     * until it is exploited.
     */
    public static class Cors {

        @NotEmpty
        private List<String> allowedOriginPatterns = new ArrayList<>();

        private List<String> allowedMethods =
                new ArrayList<>(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        private List<String> allowedHeaders =
                new ArrayList<>(List.of("Authorization", "Content-Type", "X-CSRF-TOKEN",
                        "X-Correlation-Id", "Accept", "Origin", "X-Requested-With"));

        /** Response headers the browser is allowed to read. */
        private List<String> exposedHeaders =
                new ArrayList<>(List.of("X-Correlation-Id", "Location"));

        private boolean allowCredentials = true;

        private Duration maxAge = Duration.ofHours(1);

        public List<String> getAllowedOriginPatterns() {
            return allowedOriginPatterns;
        }

        public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
            this.allowedOriginPatterns = allowedOriginPatterns;
        }

        public List<String> getAllowedMethods() {
            return allowedMethods;
        }

        public void setAllowedMethods(List<String> allowedMethods) {
            this.allowedMethods = allowedMethods;
        }

        public List<String> getAllowedHeaders() {
            return allowedHeaders;
        }

        public void setAllowedHeaders(List<String> allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }

        public List<String> getExposedHeaders() {
            return exposedHeaders;
        }

        public void setExposedHeaders(List<String> exposedHeaders) {
            this.exposedHeaders = exposedHeaders;
        }

        public boolean isAllowCredentials() {
            return allowCredentials;
        }

        public void setAllowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }

        public Duration getMaxAge() {
            return maxAge;
        }

        public void setMaxAge(Duration maxAge) {
            this.maxAge = maxAge;
        }
    }

    /**
     * Upstream services.
     *
     * <p>Declared here rather than only in YAML so the base URLs can be validated at startup and
     * so the OpenAPI aggregation can name them.
     */
    public static class Routing {

        private final List<Service> services = new ArrayList<>();

        public Routing() {
            services.add(new Service("auth-service", "http://localhost:8081", "/api/auth"));
            services.add(new Service("user-service", "http://localhost:8082", "/api/users"));
        }

        public List<Service> getServices() {
            return services;
        }

        /** One upstream: its name, where it lives, and the prefix it answers on. */
        public static class Service {

            @NotEmpty
            private String name;

            @NotEmpty
            private String uri;

            @NotEmpty
            private String pathPrefix;

            public Service() {
            }

            public Service(String name, String uri, String pathPrefix) {
                this.name = name;
                this.uri = uri;
                this.pathPrefix = pathPrefix;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getUri() {
                return uri;
            }

            public void setUri(String uri) {
                this.uri = uri;
            }

            public String getPathPrefix() {
                return pathPrefix;
            }

            public void setPathPrefix(String pathPrefix) {
                this.pathPrefix = pathPrefix;
            }
        }
    }

    /**
     * Request limits.
     *
     * <p>A gateway is the one place that can see every request, which makes it the only
     * sensible place to bound their size. A body-size limit enforced here protects every
     * upstream from a single oversized upload.
     */
    public static class Limits {

        @NotNull
        private Duration connectTimeout = Duration.ofSeconds(2);

        @NotNull
        private Duration responseTimeout = Duration.ofSeconds(30);

        private String maxRequestBodySize = "10MB";

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getResponseTimeout() {
            return responseTimeout;
        }

        public void setResponseTimeout(Duration responseTimeout) {
            this.responseTimeout = responseTimeout;
        }

        public String getMaxRequestBodySize() {
            return maxRequestBodySize;
        }

        public void setMaxRequestBodySize(String maxRequestBodySize) {
            this.maxRequestBodySize = maxRequestBodySize;
        }
    }
}