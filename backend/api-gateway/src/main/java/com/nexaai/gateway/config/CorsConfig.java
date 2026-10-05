package com.nexaai.gateway.config;

import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * CORS, configured from an explicit allow-list.
 *
 * <p><strong>The allow-list has no default, and the application refuses to start without
 * one.</strong> A CORS default of {@code *} is the most common way a browser-facing API ends
 * up readable by any site on the internet, and the mistake is invisible until it is exploited.
 *
 * <p>Origin <em>patterns</em> rather than exact origins, because a deployment legitimately
 * needs several hosts and a subdomain rule. That is a deliberate trade: a pattern such as
 * {@code https://*.nexaai.com} trusts every such host, so the list should stay as narrow as
 * the deployment allows.
 *
 * <p>Credentials are allowed because the API authenticates with a cookie. Note the useful
 * consequence of that combination: browsers refuse {@code Allow-Credentials: true} together
 * with a wildcard origin, so a wildcard here would break the SPA loudly rather than quietly
 * opening the API.
 */
@Configuration
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    @Bean
    public CorsWebFilter corsWebFilter(GatewayProperties properties) {
        GatewayProperties.Cors cors = properties.getCors();

        if (cors.getAllowedOriginPatterns().isEmpty()) {
            throw new IllegalStateException(
                    "No CORS origins configured. Set NEXA_GATEWAY_CORS_ALLOWED_ORIGIN_PATTERNS to a "
                    + "comma-separated list of trusted origins. Refusing to start is correct; the "
                    + "alternative is defaulting to '*' and shipping a public API.");
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(cors.getAllowedOriginPatterns());
        configuration.setAllowedMethods(cors.getAllowedMethods());
        configuration.setAllowedHeaders(cors.getAllowedHeaders());
        configuration.setExposedHeaders(cors.getExposedHeaders());
        configuration.setAllowCredentials(cors.isAllowCredentials());
        configuration.setMaxAge(cors.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        log.info("CORS configured for origins: {}", cors.getAllowedOriginPatterns());
        return new CorsWebFilter(source);
    }

    /**
     * Splits a comma-separated environment variable into origins, dropping blanks.
     *
     * <p>A trailing comma or a stray space in a deployment variable is common, and an empty
     * entry in the pattern list is the kind of thing that fails much later and somewhere else.
     */
    public static List<String> parseOrigins(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }
}