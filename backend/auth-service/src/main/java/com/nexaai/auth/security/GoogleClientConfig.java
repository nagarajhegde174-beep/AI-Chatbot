package com.nexaai.auth.security;

import com.nexaai.auth.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * Registers Google as an OAuth2 client, but only when it is explicitly enabled.
 *
 * <p><strong>Why this is code and not YAML.</strong> Declaring {@code registration.google} in
 * {@code application.yml} with empty {@code client-id} makes Spring Boot fail at startup with
 * "Client id of registration 'google' must not be empty". That was found by running the
 * integration tests, and it means a plain YAML block makes Google credentials load-bearing for
 * every deployment, including the ones that will never use it.
 *
 * <p>Declaring the registration here, behind {@code nexa.auth.google.enabled=true}, means the
 * registration genuinely does not exist until someone asks for it. The absence is real rather
 * than an empty value the framework has to tolerate.
 */
@Configuration
@ConditionalOnProperty(prefix = "nexa.auth.google", name = "enabled", havingValue = "true")
public class GoogleClientConfig {

    private static final Logger log = LoggerFactory.getLogger(GoogleClientConfig.class);

    private static final String REGISTRATION_ID = "google";

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        String clientId = requiredEnv("GOOGLE_CLIENT_ID");
        String clientSecret = requiredEnv("GOOGLE_CLIENT_SECRET");

        ClientRegistration google = ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .userNameAttributeName("sub")
                .build();

        log.info("Google OAuth2 client is enabled");
        return new InMemoryClientRegistrationRepository(google);
    }

    /**
     * Reads a required credential.
     *
     * <p>Fails startup when missing. Google being enabled and Google being unusable at runtime
     * are both broken states, and a clear startup failure is better than a sign-in button that
     * does not work.
     */
    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Google sign-in is enabled (nexa.auth.google.enabled=true) but " + name
                    + " is not set. Set it, or turn Google sign-in off.");
        }
        return value;
    }
}