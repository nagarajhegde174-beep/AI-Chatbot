package com.nexaai.analytics.controller;

import com.nexaai.analytics.dto.UserUsageSummaryDto;
import com.nexaai.analytics.security.UserPrincipal;
import com.nexaai.analytics.service.UserUsageService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/usage")
public class UserUsageController {

    private final UserUsageService userUsageService;

    public UserUsageController(UserUsageService userUsageService) {
        this.userUsageService = userUsageService;
    }

    @GetMapping("/summary")
    public ResponseEntity<UserUsageSummaryDto> getUserUsageSummary(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId
    ) {
        UUID userId = null;
        if (principal != null && principal.getUserId() != null) {
            userId = principal.getUserId();
        } else if (headerUserId != null && !headerUserId.isBlank()) {
            userId = UUID.fromString(headerUserId.trim());
        }

        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }

        UserUsageSummaryDto summary = userUsageService.getUserUsageSummary(userId);
        return ResponseEntity.ok(summary);
    }
}
