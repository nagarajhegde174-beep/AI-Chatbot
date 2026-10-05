package com.nexaai.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Outbound HTTP client.
 *
 * <p>Declared explicitly rather than relying on auto-configuration. This application has both
 * {@code spring-boot-starter-web} and {@code spring-boot-starter-webflux} on the classpath —
 * WebFlux is present for {@code WebClient} alone — and an explicit bean removes any doubt about
 * which auto-configuration supplies the builder.
 *
 * <p>No connect or read timeout is set here. They come from
 * {@code nexa.user.client.timeout}, which {@link HttpSubscriptionLookup} applies per call, so
 * one slow service cannot hold a connection open for the whole client lifetime.
 */
@Configuration
public class ClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}