package com.nexaai.chat.client;

import com.nexaai.chat.config.ChatProperties;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Chat Service's view of User Service.
 *
 * <p><strong>An interface behind HTTP, not a database read.</strong> Chat Service owns
 * conversations; User Service owns profiles. Reading User Service's tables directly would break
 * the one-database-per-service rule in the same way reading Auth Service's would
 * ({@code docs/RULES.md} §3), so the boundary is REST.
 *
 * <p><strong>Deliberately unused in this phase.</strong> Every operation here is scoped to the
 * calling user's own conversation, and this service already knows the auth-service user id from
 * the verified token, so it has no need to ask who the user is. The seam exists because Phase 9
 * subscription entitlements will need a user's plan, and adding that as a database read later
 * would be the wrong move. Building the HTTP client now, disabled, is cheaper than retrofitting
 * it later.
 */
@Component
public class HttpUserServiceClient implements UserDirectory {

    private static final Logger log = LoggerFactory.getLogger(HttpUserServiceClient.class);

    private final ChatProperties properties;
    private final WebClient webClient;

    public HttpUserServiceClient(ChatProperties properties, WebClient.Builder builder) {
        this.properties = properties;
        this.webClient = builder.baseUrl(properties.getUserService().getBaseUrl()).build();
    }

    @Override
    public boolean isEnabled() {
        return properties.getUserService().isEnabled();
    }

    @Override
    public UserSummary findByAuthUserId(UUID authUserId) {
        if (!isEnabled()) {
            return UserSummary.unavailable();
        }
        try {
            UserSummary summary = webClient.get()
                    .uri("/api/v1/admin/users/{id}", authUserId)
                    // The gateway's own authentication is not reused here: this is a
                    // service-to-service call and Phase 3 has no service credential to present.
                    // Until one exists the call is off by default rather than half-authenticated.
                    .retrieve()
                    .bodyToMono(UserSummary.class)
                    .block(properties.getUserService().getTimeout());
            return summary == null ? UserSummary.unavailable() : summary;
        } catch (Exception e) {
            // Logged by type only. The reason is not returned: an internal URL or a stack frame
            // is not something to hand to a caller.
            log.warn("Could not reach User Service for {}: {}", authUserId,
                    e.getClass().getSimpleName());
            return UserSummary.unavailable();
        }
    }
}