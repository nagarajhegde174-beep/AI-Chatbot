package com.nexaai.auth.web.dto;

import com.nexaai.auth.domain.AccountStatus;
import com.nexaai.auth.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Request and response payloads.
 *
 * <p><strong>No password hash, and no refresh token, appears in any response type.</strong>
 * Not one DTO in this package has a field capable of carrying one, so the rule
 * ({@code docs/SERVICE_CONTRACTS.md} section 3.4) is enforced by the type system rather than
 * by remembering not to include one.
 *
 * <p>Records rather than classes: immutable by construction, and a missing field is a compile
 * error instead of a {@code null} discovered at runtime.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    // ==================================================================
    // Requests
    // ==================================================================

    /**
     * Registration request.
     *
     * <p>The password has a maximum length as well as a minimum. An unbounded password is a
     * denial-of-service vector against the hashing itself: Argon2 is deliberately expensive,
     * so a megabyte-long password costs real CPU per attempt.
     */
    public record RegisterRequest(
            @NotBlank(message = "email is required")
            @Email(message = "must be a valid email address")
            @Size(max = 320, message = "must be at most 320 characters")
            String email,

            @NotBlank(message = "password is required")
            @Size(min = 12, max = 128, message = "must be between 12 and 128 characters")
            String password,

            @NotBlank(message = "displayName is required")
            @Size(max = 120, message = "must be at most 120 characters")
            String displayName) {
    }

    /** Sign-in request. */
    public record LoginRequest(
            @NotBlank(message = "email is required")
            @Email(message = "must be a valid email address")
            String email,

            @NotBlank(message = "password is required")
            @Size(max = 128, message = "must be at most 128 characters")
            String password) {
    }

    /** Refresh request. The token normally arrives in the cookie; this is the fallback. */
    public record RefreshRequest(String refreshToken) {
    }

    /** Password reset request. Carries the email so a response can be tailored. */
    public record ForgotPasswordRequest(
            @NotBlank(message = "email is required")
            @Email(message = "must be a valid email address")
            String email) {
    }

    /** Password reset completion. */
    public record ResetPasswordRequest(
            @NotBlank(message = "token is required") String token,

            @NotBlank(message = "password is required")
            @Size(min = 12, max = 128, message = "must be between 12 and 128 characters")
            String newPassword) {
    }

    /** Authenticated password change. */
    public record ChangePasswordRequest(
            @NotBlank(message = "currentPassword is required") String currentPassword,

            @NotBlank(message = "newPassword is required")
            @Size(min = 12, max = 128, message = "must be between 12 and 128 characters")
            String newPassword) {
    }

    /** Email verification. The token arrives in the query string from the emailed link. */
    public record VerifyEmailRequest(
            @NotBlank(message = "token is required") String token) {
    }

    // ==================================================================
    // Responses
    // ==================================================================

    /**
     * Sign-in response.
     *
     * <p>Carries no token values. Both tokens are delivered as HTTP-only cookies, so they
     * cannot be read by script even if this response is intercepted by a page bug. The
     * {@code expiresIn} is present so the client can refresh proactively without having to
     * decode a JWT.
     */
    public record LoginResponse(
            String tokenType,
            long expiresIn,
            UserSummary user) {
    }

    /**
     * Registration response.
     *
     * <p><strong>Carries no tokens.</strong> The account must verify its email first. This
     * prevents mass unverified registration and keeps the verification path real
     * ({@code docs/SERVICE_CONTRACTS.md} section 5.4).
     */
    public record RegisterResponse(
            UUID userId,
            String email,
            String displayName,
            List<String> roles,
            String status,
            String message,
            Instant createdAt) {
    }

    /** Current principal. */
    public record UserSummary(
            UUID userId,
            String email,
            String displayName,
            Role role,
            AccountStatus status,
            boolean emailVerified,
            Instant lastLoginAt) {
    }

    /**
     * Generic acknowledgement.
     *
     * <p>Used wherever the response must not reveal whether an account exists.
     */
    public record GenericResponse(String message) {
    }

    /** Result of an introspection call, for service-to-service verification. */
    public record IntrospectionResponse(
            boolean active,
            UUID userId,
            String email,
            List<String> roles,
            String status,
            Instant expiresAt) {
    }

    /** The service's own boundary declaration. */
    public record ServiceInfoResponse(
            String service,
            String version,
            String role,
            int port,
            String database,
            List<String> consumes,
            List<String> produces) {
    }

    /** Result of a password change, including how many sessions were invalidated. */
    public record PasswordChangedResponse(
            String message,
            int revokedSessions) {
    }
}