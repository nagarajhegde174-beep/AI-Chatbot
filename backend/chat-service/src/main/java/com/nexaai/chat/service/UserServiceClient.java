package com.nexaai.chat.service;

import com.nexaai.chat.dto.UserSummaryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * REST Client for communicating with user-service.
 * Strictly adheres to RULES.md §3: Chat Service never accesses User Service database.
 */
@Service
public class UserServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);

    private final RestClient restClient;

    public UserServiceClient(@Value("${services.user.base-url:http://localhost:8082}") String userServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(userServiceUrl)
                .build();
    }

    public Optional<UserSummaryResponse> getUserInfo(UUID userId, String authHeader) {
        try {
            var request = restClient.get()
                    .uri("/api/v1/users/me")
                    .accept(MediaType.APPLICATION_JSON);

            if (authHeader != null && !authHeader.isBlank()) {
                request.header("Authorization", authHeader);
            }

            UserSummaryResponse response = request.retrieve()
                    .body(UserSummaryResponse.class);

            return Optional.ofNullable(response);
        } catch (Exception e) {
            log.warn("Could not fetch user profile from user-service for user {}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }
}
