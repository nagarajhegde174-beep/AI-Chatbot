package com.nexaai.auth.web;

import com.nexaai.auth.web.dto.ServiceInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal boundary endpoint. Proves the Auth Service is deployed as its own application
 * on its own port and knows only its own data. The real auth API
 * ({@code /api/v1/auth/*}) is introduced in Phase 2.
 */
@RestController
@RequestMapping("/internal/v1/auth")
@Tag(name = "internal", description = "Infrastructure and boundary endpoints, not for end users")
public class AuthServiceInfoController {

    private final int port;

    public AuthServiceInfoController(
            @Value("${server.port}") int port) {
        this.port = port;
    }

    @GetMapping("/info")
    @Operation(summary = "Describe this service's boundary")
    public ServiceInfoResponse info() {
        return new ServiceInfoResponse(
                "auth-service",
                "Identity: registration, credentials, password rules, account status, "
                        + "refresh-token rotation and access-token issuance.",
                port,
                "nexa_auth",
                List.of("auth.user.registered.v1", "auth.user.disabled.v1", "auth.user.password-changed.v1"));
    }
}
