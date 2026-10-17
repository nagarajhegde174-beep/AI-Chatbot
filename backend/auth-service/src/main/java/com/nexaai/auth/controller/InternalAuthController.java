package com.nexaai.auth.controller;

import com.nexaai.auth.domain.entity.AuthUser;
import com.nexaai.auth.domain.repository.AuthUserRepository;
import com.nexaai.auth.domain.repository.TokenDenylistRepository;
import com.nexaai.auth.dto.response.ServiceInfoResponse;
import com.nexaai.auth.dto.response.TokenIntrospectResponse;
import com.nexaai.auth.dto.response.UserStatusResponse;
import com.nexaai.auth.exception.AuthException;
import com.nexaai.auth.exception.ErrorCode;
import com.nexaai.auth.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/auth")
@Tag(name = "Internal Auth", description = "Inter-service internal endpoints for token introspection, service info, and account status")
public class InternalAuthController {

    private final JwtTokenProvider tokenProvider;
    private final TokenDenylistRepository tokenDenylistRepository;
    private final AuthUserRepository userRepository;
    private final int serverPort;

    public InternalAuthController(JwtTokenProvider tokenProvider,
                                  TokenDenylistRepository tokenDenylistRepository,
                                  AuthUserRepository userRepository,
                                  @Value("${server.port:8081}") int serverPort) {
        this.tokenProvider = tokenProvider;
        this.tokenDenylistRepository = tokenDenylistRepository;
        this.userRepository = userRepository;
        this.serverPort = serverPort;
    }

    @GetMapping("/info")
    @Operation(summary = "Boundary assertion endpoint", description = "Reports service boundary per docs/SERVICE_CONTRACTS.md section 2.")
    public ResponseEntity<ServiceInfoResponse> getServiceInfo() {
        return ResponseEntity.ok(new ServiceInfoResponse(
                "auth-service",
                "0.1.0",
                "Credentials, tokens, account status",
                serverPort,
                "nexa_auth",
                true,
                List.of("none"),
                List.of("auth.user.registered.v1", "auth.user.email_verified.v1")
        ));
    }

    @PostMapping("/introspect")
    @Operation(summary = "Introspect access token", description = "Validates a Bearer token and returns its claims or inactive state.")
    public ResponseEntity<TokenIntrospectResponse> introspect(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.ok(TokenIntrospectResponse.inactive());
        }

        String jwt = authHeader.substring(7);
        try {
            Claims claims = tokenProvider.validateAndGetClaims(jwt);
            String jti = tokenProvider.getJtiFromClaims(claims);

            if (tokenDenylistRepository.existsByJti(jti)) {
                return ResponseEntity.ok(TokenIntrospectResponse.inactive());
            }

            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            String status = claims.get("status", String.class);
            long exp = claims.getExpiration().toInstant().getEpochSecond();

            return ResponseEntity.ok(TokenIntrospectResponse.active(
                    claims.getSubject(),
                    roles != null ? roles : List.of("USER"),
                    status,
                    exp
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(TokenIntrospectResponse.inactive());
        }
    }

    @GetMapping("/users/{id}/status")
    @Operation(summary = "Get user account status", description = "Used by User Service and Chat Service to verify account standing.")
    public ResponseEntity<UserStatusResponse> getUserStatus(@PathVariable("id") UUID userId) {
        AuthUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(ErrorCode.AUTH_USER_NOT_FOUND, "User not found.", HttpStatus.NOT_FOUND));

        return ResponseEntity.ok(new UserStatusResponse(
                user.getId(),
                user.getStatus().name(),
                List.of(user.getRole().name())
        ));
    }
}
