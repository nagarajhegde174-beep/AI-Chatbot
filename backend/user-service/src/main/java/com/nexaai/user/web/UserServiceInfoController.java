package com.nexaai.user.web;

import com.nexaai.user.web.dto.ServiceInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal boundary endpoint. Proves the User Service is deployed as its own
 * application on its own port and knows only its own data. The real public API is
 * introduced in a later phase, see docs/TASKS.md.
 */
@RestController
@RequestMapping("/internal/v1/user")
@Tag(name = "internal", description = "Infrastructure and boundary endpoints, not for end users")
public class UserServiceInfoController {

    private final int port;

    public UserServiceInfoController(
            @Value("${server.port}") int port) {
        this.port = port;
    }

    @GetMapping("/info")
    @Operation(summary = "Describe this service's boundary")
    public ServiceInfoResponse info() {
        return new ServiceInfoResponse(
                "user-service",
                "User profile, preferences, roles and administrative user administration.",
                port,
                "nexa_user",
                List.of("user.profile.updated.v1"));
    }
}