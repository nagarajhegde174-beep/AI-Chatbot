package com.nexaai.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexaai.auth.config.SecurityConfig;
import com.nexaai.auth.domain.entity.AuthUser;
import com.nexaai.auth.domain.model.AccountStatus;
import com.nexaai.auth.domain.model.Role;
import com.nexaai.auth.domain.repository.AuthUserRepository;
import com.nexaai.auth.domain.repository.TokenDenylistRepository;
import com.nexaai.auth.dto.request.ForgotPasswordRequest;
import com.nexaai.auth.dto.request.LoginRequest;
import com.nexaai.auth.dto.request.RefreshTokenRequest;
import com.nexaai.auth.dto.request.RegisterRequest;
import com.nexaai.auth.dto.request.ResetPasswordRequest;
import com.nexaai.auth.dto.request.VerifyEmailRequest;
import com.nexaai.auth.dto.response.AuthResponse;
import com.nexaai.auth.dto.response.RegisterResponse;
import com.nexaai.auth.exception.AuthException;
import com.nexaai.auth.exception.ErrorCode;
import com.nexaai.auth.exception.GlobalExceptionHandler;
import com.nexaai.auth.security.CustomUserDetailsService;
import com.nexaai.auth.security.JwtAuthenticationFilter;
import com.nexaai.auth.security.JwtTokenProvider;
import com.nexaai.auth.security.oauth2.OAuth2AuthenticationSuccessHandler;
import com.nexaai.auth.service.AuthService;
import com.nexaai.auth.service.CookieService;
import com.nexaai.auth.service.EmailVerificationService;
import com.nexaai.auth.service.GoogleOAuthService;
import com.nexaai.auth.service.PasswordResetService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, InternalAuthController.class, JwksController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private EmailVerificationService emailVerificationService;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @MockitoBean
    private GoogleOAuthService googleOAuthService;

    @MockitoBean
    private CookieService cookieService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private TokenDenylistRepository tokenDenylistRepository;

    @MockitoBean
    private AuthUserRepository authUserRepository;

    @MockitoBean
    private OAuth2AuthenticationSuccessHandler oauth2SuccessHandler;

    private RSAPublicKey rsaPublicKey;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        this.rsaPublicKey = (RSAPublicKey) kp.getPublic();

        when(cookieService.createRefreshTokenCookie(anyString(), anyLong()))
                .thenReturn(ResponseCookie.from("refresh_token", "mock-refresh-token")
                        .httpOnly(true)
                        .secure(false)
                        .path("/")
                        .build());

        when(cookieService.clearRefreshTokenCookie())
                .thenReturn(ResponseCookie.from("refresh_token", "")
                        .httpOnly(true)
                        .secure(false)
                        .path("/")
                        .maxAge(0)
                        .build());
    }

    @Test
    void testRegisterSuccess() throws Exception {
        UUID userId = UUID.randomUUID();
        RegisterResponse response = new RegisterResponse(
                userId,
                "ada@example.com",
                "Ada",
                List.of("USER"),
                "PENDING_VERIFICATION",
                Instant.now()
        );

        when(authService.register(any(RegisterRequest.class), any())).thenReturn(response);

        RegisterRequest request = new RegisterRequest("ada@example.com", "Password123!", "Ada");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    void testRegisterValidationError() throws Exception {
        RegisterRequest invalid = new RegisterRequest("not-an-email", "weak", "Ada");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details.email").exists())
                .andExpect(jsonPath("$.error.details.password").exists());
    }

    @Test
    void testRegisterDuplicateEmailConflict() throws Exception {
        when(authService.register(any(RegisterRequest.class), any()))
                .thenThrow(new AuthException(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS, "Email in use", HttpStatus.CONFLICT));

        RegisterRequest request = new RegisterRequest("existing@example.com", "Password123!", "User");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AUTH_EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void testLoginSuccessSetsHttpOnlyCookie() throws Exception {
        UUID userId = UUID.randomUUID();
        AuthResponse response = AuthResponse.of("access-token-123", "refresh-token-123", 900, userId, "user@example.com", "User", List.of("USER"));

        when(authService.login(any(LoginRequest.class), anyString())).thenReturn(response);

        LoginRequest req = new LoginRequest("user@example.com", "Password123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token-123"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-123"))
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")));
    }

    @Test
    void testLoginInvalidCredentialsReturns401() throws Exception {
        when(authService.login(any(LoginRequest.class), anyString()))
                .thenThrow(new AuthException(ErrorCode.AUTH_INVALID_CREDENTIALS, "Email or password is incorrect.", HttpStatus.UNAUTHORIZED));

        LoginRequest req = new LoginRequest("user@example.com", "WrongPassword123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.error.message").value("Email or password is incorrect."));
    }

    @Test
    void testRefreshEndpointWithCookie() throws Exception {
        UUID userId = UUID.randomUUID();
        AuthResponse response = AuthResponse.of("new-acc-tok", "new-ref-tok", 900, userId, "user@example.com", "User", List.of("USER"));

        when(authService.refresh(eq("cookie-token"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", "cookie-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-acc-tok"))
                .andExpect(header().exists("Set-Cookie"));
    }

    @Test
    void testLogoutClearsCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refresh_token", "token-to-revoke")))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));

        verify(authService).logout(eq("token-to-revoke"), any());
    }

    @Test
    void testForgotPasswordGenericResponse() throws Exception {
        ForgotPasswordRequest req = new ForgotPasswordRequest("user@example.com");

        mockMvc.perform(post("/api/v1/auth/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If the email is registered, password reset instructions have been sent."));

        verify(passwordResetService).requestPasswordReset("user@example.com");
    }

    @Test
    void testResetPasswordSuccess() throws Exception {
        ResetPasswordRequest req = new ResetPasswordRequest("token-xyz", "NewPassword123!");

        mockMvc.perform(post("/api/v1/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(passwordResetService).resetPassword("token-xyz", "NewPassword123!");
    }

    @Test
    void testEmailVerifyPostAndGet() throws Exception {
        VerifyEmailRequest req = new VerifyEmailRequest("ver-token-123");

        mockMvc.perform(post("/api/v1/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(get("/api/v1/auth/email/verify").param("token", "ver-token-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testInternalInfoEndpoint() throws Exception {
        mockMvc.perform(get("/internal/v1/auth/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("auth-service"))
                .andExpect(jsonPath("$.database").value("nexa_auth"))
                .andExpect(jsonPath("$.listensKafka").value(true));
    }

    @Test
    void testInternalIntrospectEndpoint() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(UUID.randomUUID().toString());
        when(claims.get("roles", List.class)).thenReturn(List.of("USER"));
        when(claims.get("status", String.class)).thenReturn("ACTIVE");
        when(claims.getExpiration()).thenReturn(Date.from(Instant.now().plusSeconds(900)));

        when(jwtTokenProvider.validateAndGetClaims("sample-jwt")).thenReturn(claims);
        when(jwtTokenProvider.getJtiFromClaims(claims)).thenReturn("jti-123");
        when(tokenDenylistRepository.existsByJti("jti-123")).thenReturn(false);

        mockMvc.perform(post("/internal/v1/auth/introspect")
                        .header("Authorization", "Bearer sample-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // When token is revoked
        when(tokenDenylistRepository.existsByJti("jti-123")).thenReturn(true);
        mockMvc.perform(post("/internal/v1/auth/introspect")
                        .header("Authorization", "Bearer sample-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void testJwksEndpoint() throws Exception {
        when(jwtTokenProvider.getPublicKey()).thenReturn(rsaPublicKey);
        when(jwtTokenProvider.getKeyId()).thenReturn("nexa-auth-key-1");

        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].kid").value("nexa-auth-key-1"))
                .andExpect(jsonPath("$.keys[0].n").exists())
                .andExpect(jsonPath("$.keys[0].e").exists());
    }
}
