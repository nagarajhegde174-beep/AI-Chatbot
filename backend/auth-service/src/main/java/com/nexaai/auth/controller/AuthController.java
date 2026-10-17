package com.nexaai.auth.controller;

import com.nexaai.auth.domain.entity.AuthUser;
import com.nexaai.auth.dto.request.ChangePasswordRequest;
import com.nexaai.auth.dto.request.ForgotPasswordRequest;
import com.nexaai.auth.dto.request.GoogleOAuthRequest;
import com.nexaai.auth.dto.request.LoginRequest;
import com.nexaai.auth.dto.request.RefreshTokenRequest;
import com.nexaai.auth.dto.request.RegisterRequest;
import com.nexaai.auth.dto.request.ResetPasswordRequest;
import com.nexaai.auth.dto.request.VerifyEmailRequest;
import com.nexaai.auth.dto.response.AuthResponse;
import com.nexaai.auth.dto.response.MessageResponse;
import com.nexaai.auth.dto.response.RegisterResponse;
import com.nexaai.auth.dto.response.UserInfoResponse;
import com.nexaai.auth.security.UserPrincipal;
import com.nexaai.auth.service.AuthService;
import com.nexaai.auth.service.CookieService;
import com.nexaai.auth.service.EmailVerificationService;
import com.nexaai.auth.service.GoogleOAuthService;
import com.nexaai.auth.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Endpoints for registration, login, token refresh, email verification, password reset, and OAuth")
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final GoogleOAuthService googleOAuthService;
    private final CookieService cookieService;

    public AuthController(AuthService authService,
                          EmailVerificationService emailVerificationService,
                          PasswordResetService passwordResetService,
                          GoogleOAuthService googleOAuthService,
                          CookieService cookieService) {
        this.authService = authService;
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
        this.googleOAuthService = googleOAuthService;
        this.cookieService = cookieService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new account", description = "Creates a new user in PENDING_VERIFICATION status. Sends verification email.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User registered successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "409", description = "Email already registered")
    })
    public ResponseEntity<RegisterResponse> register(
            @Valid @RequestBody RegisterRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        RegisterResponse response = authService.register(request, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with email and password", description = "Returns RS256 JWT access token and refresh token, and sets secure HTTP-only refresh cookie.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "403", description = "Account pending verification or suspended"),
            @ApiResponse(responseCode = "429", description = "Rate limited / account lockout")
    })
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        String clientIp = resolveClientIp(servletRequest);
        AuthResponse authResponse = authService.login(request, clientIp);

        ResponseCookie cookie = cookieService.createRefreshTokenCookie(
                authResponse.refreshToken(),
                7 * 24 * 60 * 60
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(authResponse);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token", description = "Exchanges a valid refresh token for a new access token and rotating refresh token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
            @ApiResponse(responseCode = "401", description = "Invalid, expired, or revoked refresh token")
    })
    public ResponseEntity<AuthResponse> refresh(
            @RequestBody(required = false) RefreshTokenRequest request,
            @CookieValue(name = "refresh_token", required = false) String cookieRefreshToken,
            HttpServletRequest servletRequest) {
        String tokenToUse = (request != null && StringUtils.hasText(request.refreshToken()))
                ? request.refreshToken()
                : (StringUtils.hasText(cookieRefreshToken) ? cookieRefreshToken : cookieService.extractRefreshTokenFromCookie(servletRequest));

        AuthResponse authResponse = authService.refresh(tokenToUse);

        ResponseCookie cookie = cookieService.createRefreshTokenCookie(
                authResponse.refreshToken(),
                7 * 24 * 60 * 60
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(authResponse);
    }

    @PostMapping("/logout")
    @Operation(summary = "Log out", description = "Revokes refresh token, denylists access token, and clears HTTP-only cookie.")
    public ResponseEntity<MessageResponse> logout(
            @RequestBody(required = false) RefreshTokenRequest request,
            @CookieValue(name = "refresh_token", required = false) String cookieRefreshToken,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest servletRequest) {
        String tokenToUse = (request != null && StringUtils.hasText(request.refreshToken()))
                ? request.refreshToken()
                : (StringUtils.hasText(cookieRefreshToken) ? cookieRefreshToken : cookieService.extractRefreshTokenFromCookie(servletRequest));

        authService.logout(tokenToUse, authHeader);
        ResponseCookie clearCookie = cookieService.clearRefreshTokenCookie();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .body(new MessageResponse("Logged out successfully."));
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Log out all sessions", description = "Revokes all refresh tokens for the current user and clears cookies.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MessageResponse> logoutAll(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.logoutAll(principal.getId(), authHeader);
        ResponseCookie clearCookie = cookieService.clearRefreshTokenCookie();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .body(new MessageResponse("All sessions have been revoked."));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user info", description = "Returns user id, email, display name, roles, and status.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<UserInfoResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(new UserInfoResponse(
                principal.getId(),
                principal.getEmail(),
                principal.getDisplayName(),
                List.of(principal.getRole().name()),
                principal.getStatus().name()
        ));
    }

    @PostMapping("/email/verify")
    @Operation(summary = "Verify user email via POST body", description = "Activates user account using verification token.")
    public ResponseEntity<MessageResponse> verifyEmailPost(
            @Valid @RequestBody VerifyEmailRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        emailVerificationService.verifyEmail(request.token(), correlationId);
        return ResponseEntity.ok(new MessageResponse("Email verified successfully. You may now log in."));
    }

    @GetMapping("/email/verify")
    @Operation(summary = "Verify user email via query parameter", description = "Activates user account from a browser click link.")
    public ResponseEntity<MessageResponse> verifyEmailGet(
            @RequestParam("token") String token,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        emailVerificationService.verifyEmail(token, correlationId);
        return ResponseEntity.ok(new MessageResponse("Email verified successfully. You may now log in."));
    }

    @PostMapping("/password/forgot")
    @Operation(summary = "Request password reset", description = "Sends reset instructions to the requested email. Always returns generic 200 response to prevent enumeration.")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestPasswordReset(request.email());
        return ResponseEntity.ok(new MessageResponse("If the email is registered, password reset instructions have been sent."));
    }

    @PostMapping("/password/reset")
    @Operation(summary = "Complete password reset", description = "Resets user password using valid token and securely hashes new password.")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(new MessageResponse("Password has been successfully reset. Please log in with your new password."));
    }

    @PostMapping("/password/change")
    @Operation(summary = "Change password for authenticated user", description = "Verifies old password and securely hashes new password.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MessageResponse> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.getId(), request);
        return ResponseEntity.ok(new MessageResponse("Password changed successfully."));
    }

    @PostMapping("/oauth/google")
    @Operation(summary = "Continue with Google", description = "Exchanges Google ID token or credentials for JWT access token and HTTP-only refresh cookie. Performs verified-email account linking.")
    public ResponseEntity<AuthResponse> googleOAuth(
            @RequestBody GoogleOAuthRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        AuthUser user = googleOAuthService.authenticateWithGoogle(request, correlationId);
        AuthResponse authResponse = authService.issueTokens(user);

        ResponseCookie cookie = cookieService.createRefreshTokenCookie(
                authResponse.refreshToken(),
                7 * 24 * 60 * 60
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(authResponse);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        } else if (ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
