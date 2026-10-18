package com.nexaai.user.controller;

import com.nexaai.user.dto.response.ServiceInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Service Info", description = "Service boundary and health assertions")
public class ServiceInfoController {

    @GetMapping("/api/v1/user-service/info")
    @Operation(summary = "Service metadata", description = "Asserts database ownership and event contracts per SERVICE_CONTRACTS.md §2.")
    public ResponseEntity<ServiceInfoResponse> getServiceInfo() {
        return ResponseEntity.ok(new ServiceInfoResponse(
                "user-service",
                "0.1.0-SNAPSHOT",
                "User profile, preferences, account status, admin user management",
                8082,
                "nexa_user",
                true,
                List.of("auth.user.registered.v1", "auth.user.email_verified.v1", "subscription.activated.v1", "subscription.cancelled.v1"),
                List.of("user.profile.updated.v1", "user.account.deleted.v1", "user.status.changed.v1")
        ));
    }

    @GetMapping("/healthz")
    @Operation(summary = "Liveness probe", description = "Lightweight liveness probe for orchestrators")
    public ResponseEntity<String> healthz() {
        return ResponseEntity.ok("OK");
    }
}
