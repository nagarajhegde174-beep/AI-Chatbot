package com.nexaai.auth.web;

import com.nexaai.auth.domain.TokenSource;
import com.nexaai.auth.security.JwtAuthenticationFilter;
import com.nexaai.auth.security.TokenCookieService;
import com.nexaai.auth.service.AuthService;
import com.nexaai.auth.web.dto.AuthDtos.LoginRequest;
import com.nexaai.auth.web.dto.AuthDtos.LoginResponse;
import com.nexaai.auth.web.dto.AuthDtos.UserSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign-in, refresh and sign-out.
 *
 * <p>Tokens are written as HTTP-only cookies and deliberately absent from the response body,
 * so no script on the page can read them.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Sign-in, token refresh and sign-out")
public class AuthenticationController {

    private final AuthService authService;
    private final TokenCookieService cookieService;

    public AuthenticationController(AuthService authService, TokenCookieService cookieService) {
        this.authService = authService;
        this.cookieService = cookieService;
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in with email and password",
            description = "Sets HTTP-only cookies for the access and refresh tokens. "
                    + "An unknown email and a wrong password produce an identical response.")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest,
                                               HttpServletResponse httpResponse) {
        ClientContext client = ClientContext.from(httpRequest);

        AuthService.AuthenticationResult result = authService.login(
                request.email(), request.password(), TokenSource.PASSWORD,
                client.ipAddress(), client.userAgent(), client.correlationId());

        cookieService.writeAccessTokenCookie(httpResponse,
                result.accessToken().token(), result.accessToken().expiresAt());
        cookieService.writeRefreshTokenCookie(httpResponse,
                result.refreshToken().token(), result.refreshToken().entity().getExpiresAt());

        LoginResponse body = new LoginResponse(
                "Bearer",
                result.accessToken().expiresAt().getEpochSecond()
                        - result.accessToken().issuedAt().getEpochSecond(),
                toSummary(result));

        return ResponseEntity.ok(body);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new pair",
            description = "Rotates both tokens. Presenting an already-rotated token revokes the "
                    + "entire rotation family, because that indicates a stolen token.")
    public ResponseEntity<LoginResponse> refresh(HttpServletRequest httpRequest,
                                                 HttpServletResponse httpResponse) {
        ClientContext client = ClientContext.from(httpRequest);

        // Read the cookie first. The body is a fallback for clients that cannot hold cookies.
        String refreshToken = cookieService.readRefreshToken(httpRequest)
                .orElseGet(() -> extractFromBody(httpRequest));

        if (refreshToken == null || refreshToken.isBlank()) {
            throw new com.nexaai.auth.exception.AuthExceptions
                    .InvalidTokenException("No refresh token was supplied.");
        }

        AuthService.AuthenticationResult result = authService.refresh(
                refreshToken, client.ipAddress(), client.userAgent());

        cookieService.writeAccessTokenCookie(httpResponse,
                result.accessToken().token(), result.accessToken().expiresAt());
        cookieService.writeRefreshTokenCookie(httpResponse,
                result.refreshToken().token(), result.refreshToken().entity().getExpiresAt());

        return ResponseEntity.ok(new LoginResponse("Bearer",
                result.accessToken().expiresAt().getEpochSecond()
                        - result.accessToken().issuedAt().getEpochSecond(),
                toSummary(result)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Sign out of the current session",
            description = "Revokes the presented refresh token and denies its access token. "
                    + "Returns 204 whether or not a session existed, so it cannot be used to "
                    + "probe for one.")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        ClientContext client = ClientContext.from(httpRequest);

        UUID userId = AuthContext.currentUserId();
        String refreshToken = cookieService.readRefreshToken(httpRequest).orElse(null);
        String accessToken = cookieService.readAccessToken(httpRequest).orElse(null);

        if (userId != null) {
            authService.logout(userId, refreshToken, accessToken,
                    client.ipAddress(), client.userAgent());
        }

        cookieService.clearTokenCookies(httpResponse);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Sign out of every session")
    public ResponseEntity<Void> logoutAll(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        ClientContext client = ClientContext.from(httpRequest);
        UUID userId = AuthContext.currentUserId();
        if (userId == null) {
            cookieService.clearTokenCookies(httpResponse);
            return ResponseEntity.noContent().build();
        }
        authService.logoutAll(userId, cookieService.readAccessToken(httpRequest).orElse(null),
                client.ipAddress(), client.userAgent());
        cookieService.clearTokenCookies(httpResponse);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Current principal")
    public ResponseEntity<com.nexaai.auth.web.dto.AuthDtos.IntrospectionResponse> me() {
        UUID userId = AuthContext.currentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        JwtAuthenticationFilter.AuthPrincipal principal = AuthContext.principal();
        return ResponseEntity.ok(new com.nexaai.auth.web.dto.AuthDtos.IntrospectionResponse(
                true, principal.userId(), principal.email(),
                List.of(principal.role().name()), principal.status().name(), null));
    }

    // ------------------------------------------------------------------

    private String extractFromBody(HttpServletRequest request) {
        return request.getParameter("refreshToken");
    }

    static UserSummary toSummary(AuthService.AuthenticationResult result) {
        var user = result.user();
        return new UserSummary(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getRole(), user.getStatus(), user.isEmailVerified(), user.getLastLoginAt());
    }

}