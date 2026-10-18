package com.nexaai.user.service.external;

import com.nexaai.user.dto.response.UsageInfoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * Service boundary client to Usage / Analytics Service.
 * Does NOT access foreign DB directly (ADR-002, ADR-014).
 * Performs REST API calls with timeout and graceful fallback.
 */
@Service
public class UsageBoundaryService {

    private static final Logger log = LoggerFactory.getLogger(UsageBoundaryService.class);

    private final RestTemplate restTemplate;
    private final String usageServiceUrl;

    public UsageBoundaryService(
            RestTemplateBuilder builder,
            @Value("${services.usage-service.url:http://localhost:8088}") String usageServiceUrl) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(1500))
                .setReadTimeout(Duration.ofMillis(3000))
                .build();
        this.usageServiceUrl = usageServiceUrl;
    }

    public UsageInfoResponse getUsageForUser(UUID userId) {
        try {
            String url = usageServiceUrl + "/internal/v1/usage/users/" + userId;
            UsageInfoResponse response = restTemplate.getForObject(url, UsageInfoResponse.class);
            if (response != null) {
                return response;
            }
        } catch (Exception e) {
            log.info("Usage service not reachable or returned error for user {}: {}. Returning standard current cycle usage.",
                    userId, e.getMessage());
        }
        // Default / fallback contract response when usage service has not been reached
        return new UsageInfoResponse(
                userId.toString(),
                0L,
                0L,
                0L,
                0L,
                Instant.now().truncatedTo(ChronoUnit.DAYS),
                Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS),
                Map.of("gpt-4o-mini", 0L)
        );
    }
}
