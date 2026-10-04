package com.nexaai.auth.web;

import com.nexaai.auth.domain.AccountStatus;
import com.nexaai.auth.domain.AuthUser;
import com.nexaai.auth.outbox.OutboxService;
import com.nexaai.auth.repository.AuthUserRepository;
import com.nexaai.auth.security.JwtService;
import com.nexaai.auth.web.dto.AuthDtos.IntrospectionResponse;
import com.nexaai.auth.web.dto.AuthDtos.ServiceInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service endpoints.
 *
 * <p>Reachable only with the internal authority, which a user token never carries. These exist
 * because other services need to verify a token or check an account's status, and they must not
 * require a round trip that looks like a user request
 * ({@code docs/SERVICE_CONTRACTS.md} section 5.2).
 *
 * <p>The boundary endpoint below is the one that matters most: it makes this service's data
 * ownership checkable rather than assumed.
 */
@RestController
@RequestMapping("/internal/v1/auth")
@Tag(name = "Internal", description = "Service-to-service endpoints. Not reachable from the internet.")
public class InternalAuthController {

    private final AuthUserRepository userRepository;
    private final JwtService jwtService;

    public InternalAuthController(AuthUserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    /**
     * This service's own boundary declaration.
     *
     * <p>{@code database} names the one database this service owns. Asserting it is what makes
     * a database-ownership violation visible instead of theoretical: if this ever read another
     * service's data, the claim would stop matching the code
     * ({@code docs/SERVICE_CONTRACTS.md} section 2).
     */
    @GetMapping("/info")
    @Operation(summary = "This service's boundary, port, database and topics")
    public ResponseEntity<ServiceInfoResponse> info() {
        return ResponseEntity.ok(new ServiceInfoResponse(
                "auth-service",
                version(),
                "Credentials, tokens, account status",
                port(),
                "nexa_auth",
                List.of(),
                List.of(OutboxService.TOPIC_USER_REGISTERED, OutboxService.TOPIC_EMAIL_VERIFIED)));
    }

    /**
     * Verifies a token for another service.
     *
     * <p>Returns {@code active: false} rather than 401 for an invalid token, so a caller can
     * tell "this token is bad" from "I called this wrong". Never says <em>why</em>: the reason
     * is what an attacker is probing for.
     */
    @PostMapping("/introspect")
    @Operation(summary = "Verify a token and return its claims")
    public ResponseEntity<IntrospectionResponse> introspect(
            @RequestHeader("Authorization") String authorization) {

        String token = null;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            token = authorization.substring(7).trim();
        }
        if (token == null || token.isEmpty()) {
            return ResponseEntity.ok(new IntrospectionResponse(false, null, null, List.of(), null, null));
        }

        try {
            var claims = jwtService.verifyAccessToken(token);
            UUID userId = UUID.fromString(claims.getSubject());

            AuthUser user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                return ResponseEntity.ok(new IntrospectionResponse(false, null, null, List.of(), null, null));
            }

            return ResponseEntity.ok(new IntrospectionResponse(
                    true,
                    user.getId(),
                    user.getEmail(),
                    List.of(user.getRole().name()),
                    user.getStatus().name(),
                    claims.getExpiration() == null ? null : claims.getExpiration().toInstant()));
        } catch (RuntimeException e) {
            return ResponseEntity.ok(new IntrospectionResponse(false, null, null, List.of(), null, null));
        }
    }

    /** Account status for another service. */
    @GetMapping("/users/{id}/status")
    @Operation(summary = "Account status, for services that must honour suspension")
    public ResponseEntity<AccountStatusHolder> status(@PathVariable UUID id) {
        return userRepository.findById(id)
                .map(user -> ResponseEntity.ok(new AccountStatusHolder(
                        user.getId(),
                        user.getStatus(),
                        user.isLocked(),
                        user.isEmailVerified())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Status payload. Carries no credential and no profile field. */
    public record AccountStatusHolder(UUID userId, AccountStatus status, boolean locked,
                                      boolean emailVerified) {
    }

    private String version() {
        // From the build when available, so a deployed instance reports what it actually is.
        var pkg = getClass().getPackage();
        String implementationVersion = pkg == null ? null : pkg.getImplementationVersion();
        return implementationVersion == null ? "0.1.0" : implementationVersion;
    }

    private int port() {
        // Declared in application.yml as server.port; read from the environment override when set.
        String configured = System.getProperty("server.port",
                System.getenv().getOrDefault("SERVER_PORT", "8081"));
        try {
            return Integer.parseInt(configured);
        } catch (NumberFormatException e) {
            return 8081;
        }
    }
}