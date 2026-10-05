package com.nexaai.user.client;

import com.nexaai.user.config.UserProperties;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Calls Subscription Service over HTTP.
 *
 * <p><strong>Inactive until Phase 9.</strong> Configuration is present so the wiring is real,
 * but {@code nexa.user.client.enabled} defaults to {@code false}. With it off, this bean
 * reports unavailable and never opens a connection, so User Service does not fail to start or
 * log a connection error for a service that has not been built.
 *
 * <p>When enabled it degrades rather than throws: an unreachable Subscription Service must not
 * take down the admin user listing, which is the page that matters.
 */
@Component
public class HttpSubscriptionLookup implements SubscriptionLookup {

    private static final Logger log = LoggerFactory.getLogger(HttpSubscriptionLookup.class);

    private final UserProperties properties;
    private final WebClient webClient;

    public HttpSubscriptionLookup(UserProperties properties, WebClient.Builder builder) {
        this.properties = properties;
        UserProperties.Client client = properties.getClient();
        this.webClient = builder
                .baseUrl(client.getSubscriptionServiceUrl())
                .build();
    }

    @Override
    public UsageView usageFor(UUID authUserId) {
        UserProperties.Client client = properties.getClient();
        if (!client.isEnabled()) {
            return UsageView.unavailable(
                    "Subscription data is unavailable: Subscription Service integration is not enabled.");
        }

        try {
            UsageView view = webClient.get()
                    .uri("/internal/v1/users/{id}/usage", authUserId)
                    .retrieve()
                    .bodyToMono(UsageView.class)
                    .block(client.getTimeout());
            return view == null
                    ? UsageView.unavailable("Subscription Service returned no data.")
                    : view;
        } catch (Exception e) {
            // The message is logged, not returned: an internal URL or a stack frame is not
            // something to hand to an admin browser.
            log.warn("Could not fetch subscription usage for {}: {}",
                    authUserId, e.getClass().getSimpleName());
            return UsageView.unavailable(
                    "Subscription data is temporarily unavailable. Try again shortly.");
        }
    }
}