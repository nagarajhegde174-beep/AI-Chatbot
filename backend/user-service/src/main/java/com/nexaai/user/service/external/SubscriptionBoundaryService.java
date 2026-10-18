package com.nexaai.user.service.external;

import com.nexaai.user.dto.response.SubscriptionInfoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Service boundary client to Subscription Service.
 * Does NOT access subscription DB directly (ADR-002, ADR-014).
 * Performs REST API calls to subscription-service:8087 with timeout and graceful fallback.
 */
@Service
public class SubscriptionBoundaryService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionBoundaryService.class);

    private final RestTemplate restTemplate;
    private final String subscriptionServiceUrl;

    public SubscriptionBoundaryService(
            RestTemplateBuilder builder,
            @Value("${services.subscription-service.url:http://localhost:8087}") String subscriptionServiceUrl) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(1500))
                .setReadTimeout(Duration.ofMillis(3000))
                .build();
        this.subscriptionServiceUrl = subscriptionServiceUrl;
    }

    public SubscriptionInfoResponse getSubscriptionForUser(UUID userId) {
        try {
            String url = subscriptionServiceUrl + "/internal/v1/subscriptions/users/" + userId;
            SubscriptionInfoResponse response = restTemplate.getForObject(url, SubscriptionInfoResponse.class);
            if (response != null) {
                return response;
            }
        } catch (Exception e) {
            log.info("Subscription service not reachable or returned error for user {}: {}. Returning default free tier.",
                    userId, e.getMessage());
        }
        // Default / fallback contract response when subscription-service has not been reached
        return new SubscriptionInfoResponse(
                userId.toString(),
                "FREE",
                "ACTIVE",
                Instant.now().truncatedTo(ChronoUnit.DAYS),
                Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS),
                false
        );
    }
}
