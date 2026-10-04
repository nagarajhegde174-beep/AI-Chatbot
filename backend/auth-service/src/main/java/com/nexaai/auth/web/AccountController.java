package com.nexaai.auth.web;

import com.nexaai.auth.domain.AccountStatus;
import com.nexaai.auth.domain.AuthEventType;
import com.nexaai.auth.domain.AuthUser;
import com.nexaai.auth.exception.AuthExceptions;
import com.nexaai.auth.outbox.OutboxService;
import com.nexaai.auth.security.JwtAuthenticationFilter;
import com.nexaai.auth.security.TokenCookieService;
import com.nexaai.auth.service.AuthAuditService;
import com.nexaai.auth.service.AuthService;
import com.nexaai.auth.web.dto.AuthDtos.ChangePasswordRequest;
import com.nexaai.auth.web.dto.AuthDtos.ForgotPasswordRequest;
import com.nexaai.auth.web.dto.AuthDtos.GenericResponse;
import com.nexaai.auth.web.dto.AuthDtos.PasswordChangedResponse;
import com.nexaai.auth.web.dto.AuthDtos.RegisterRequest;
import com.nexaai.auth.web.dto.AuthDtos.RegisterResponse;
import com.nexaai.auth.web.dto.AuthDtos.ResetPasswordRequest;
import com.nexaai.auth.web.dto.AuthDtos.VerifyEmailRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration, email verification, password reset and password change.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Registration, verification and password management")
public class AccountController {

    private final AuthService authService;
    private final AuthAuditService auditService;
    private final OutboxService outboxService;
    private final TokenCookieService cookieService;

    public AccountController(AuthService authService,
                             AuthAuditService auditService,
                             OutboxService outboxService,
                             TokenCookieService cookieService) {
        this.authService = authService;
        this.auditService = auditService;
        this.outboxService = outboxService;
        this.cookieService = cookieService;
    }

    @PostMapping("/register")
    @Operation(summary = "Create an account",
            description = "Returns no tokens. The account must verify its email before it can sign in.")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request,
                                                     HttpServletRequest httpRequest) {
        ClientContext client = ClientContext.from(httpRequest);

        AuthUser user = authService.register(
                request.email(), request.password(), request.displayName(),
                client.ipAddress(), client.userAgent(), client.correlationId());

        return ResponseEntity.status(HttpStatus.CREATED).body(new RegisterResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                List.of(user.getRole().name()),
                user.getStatus().name(),
                "Account created. Check your email to verify your address.",
                user.getCreatedAt()));
    }

    @PostMapping("/email/verify")
    @Operation(summary = "Verify an email address",
            description = "Idempotent: following the link twice succeeds both times.")
    public ResponseEntity<GenericResponse> verifyEmail(@RequestBody VerifyEmailRequest request,
                                                       HttpServletRequest httpRequest) {
        ClientContext client = ClientContext.from(httpRequest);
        AuthUser user = authService.verifyEmail(request.token(),
                client.ipAddress(), client.userAgent());

        outboxService.recordEmailVerified(user, client.correlationId());
        return ResponseEntity.ok(new GenericResponse(
                "Email verified. You can sign in now."));
    }

    @PostMapping("/email/verify-link")
    @Operation(summary = "Verify an email address from a clicked link",
            description = "Same operation as /email/verify, accepting the token as a query "
                    + "parameter so a browser can reach it from an email link.")
    public ResponseEntity<GenericResponse> verifyEmailFromQuery(
            @org.springframework.web.bind.annotation.RequestParam("token") String token,
            HttpServletRequest httpRequest) {
        // A GET link would make this a state change on navigation, which is exactly what CSRF
        // protection exists to prevent. A dedicated POST endpoint gives the email link
        // something to point at without weakening the rule.
        return verifyEmail(new VerifyEmailRequest(token), httpRequest);
    }

    @PostMapping("/password/forgot")
    @Operation(summary = "Request a password reset",
            description = "Always returns the same response, whether or not the email is "
                    + "registered, so it cannot be used to discover accounts.")
    public ResponseEntity<GenericResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                                         HttpServletRequest httpRequest) {
        ClientContext client = ClientContext.from(httpRequest);
        authService.forgotPassword(request.email(), client.ipAddress(), client.userAgent());

        return ResponseEntity.ok(new GenericResponse(
                "If an account exists for that address, we have sent reset instructions."));
    }

    @PostMapping("/password/reset")
    @Operation(summary = "Complete a password reset",
            description = "The token is single use. Every existing session is revoked.")
    public ResponseEntity<GenericResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request,
                                                         HttpServletRequest httpRequest) {
        ClientContext client = ClientContext.from(httpRequest);
        AuthUser user = authService.resetPassword(request.token(), request.newPassword(),
                client.ipAddress(), client.userAgent());

        return ResponseEntity.ok(new GenericResponse(
                "Your password has been changed. Please sign in with your new password."));
    }

    @PostMapping("/password/change")
    @Operation(summary = "Change the password while signed in",
            description = "Requires the current password, so a stolen token alone cannot lock the "
                    + "owner out. Every other session is revoked.")
    public ResponseEntity<PasswordChangedResponse> changePassword(
            @Valid @RequestBody ChangePasswordRequest request, HttpServletRequest httpRequest) {

        AuthContext.requireAuthenticated();
        ClientContext client = ClientContext.from(httpRequest);
        UUID userId = AuthContext.currentUserId();

        int revoked = authService.changePassword(userId, request.currentPassword(),
                request.newPassword(), cookieService.readAccessToken(httpRequest).orElse(null),
                client.ipAddress(), client.userAgent());

        return ResponseEntity.ok(new PasswordChangedResponse(
                "Your password has been changed. Please sign in again.", revoked));
    }
}