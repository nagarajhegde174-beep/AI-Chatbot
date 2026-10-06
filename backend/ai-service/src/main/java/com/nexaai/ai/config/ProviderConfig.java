package com.nexaai.ai.config;

import com.nexaai.ai.provider.ProviderClient;
import com.nexaai.ai.provider.SpringAiProviderClient;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Wiring for the provider clients and the outbound HTTP client.
 *
 * <p><strong>Every provider gets a client bean, enabled or not.</strong> A provider with no
 * credential is a supported configuration and must report itself unavailable with a reason, not
 * vanish from the context — an absent bean and an unavailable provider are different facts, and
 * only the second tells an operator what to fix.
 */
@Configuration
public class ProviderConfig {

    /**
     * One client per known provider, in a fixed order.
     *
     * <p>Fixed rather than derived from configured keys, so a provider absent from configuration
     * still appears in {@code /models} with a reason. Fixed order so the list is stable across
     * restarts and reads identically in two environments.
     */
    @Bean
    public List<ProviderClient> providerClients(ApplicationContext context,
                                                AiProperties properties) {
        return new ArrayList<>(SpringAiProviderClient.forConfiguredProviders(context, properties));
    }

    /**
     * The outbound HTTP client.
     *
     * <p>Declared explicitly because this application has both the servlet and WebFlux starters
     * on the classpath — WebFlux is present for {@code WebClient} alone — so an explicit bean
     * removes any doubt about which auto-configuration supplies the builder.
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}