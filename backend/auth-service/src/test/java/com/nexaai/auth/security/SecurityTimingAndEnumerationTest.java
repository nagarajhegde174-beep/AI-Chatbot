package com.nexaai.auth.security;

import com.nexaai.auth.config.JwtProperties;
import com.nexaai.auth.domain.entity.AuthUser;
import com.nexaai.auth.domain.model.AccountStatus;
import com.nexaai.auth.domain.model.Role;
import com.nexaai.auth.domain.repository.AuthUserRepository;
import com.nexaai.auth.domain.repository.PasswordResetTokenRepository;
import com.nexaai.auth.domain.repository.RefreshTokenRepository;
import com.nexaai.auth.dto.request.LoginRequest;
import com.nexaai.auth.exception.AuthException;
import com.nexaai.auth.exception.ErrorCode;
import com.nexaai.auth.service.AuthService;
import com.nexaai.auth.service.EmailNotificationService;
import com.nexaai.auth.service.EmailVerificationService;
import com.nexaai.auth.service.EventPublisher;
import com.nexaai.auth.service.LoginAttemptService;
import com.nexaai.auth.service.PasswordResetService;
import com.nexaai.auth.service.TokenRevocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityTimingAndEnumerationTest {

    @Mock
    private AuthUserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private TokenRevocationService tokenRevocationService;

    @Mock
    private EventPublisher eventPublisher;

    @Mock
    private PasswordResetTokenRepository resetTokenRepository;

    @Mock
    private EmailNotificationService emailNotificationService;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;
    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(12);
        JwtProperties jwtProperties = new JwtProperties();
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(jwtProperties);
        jwtTokenProvider.init();

        authService = new AuthService(
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                jwtTokenProvider,
                jwtProperties,
                loginAttemptService,
                emailVerificationService,
                tokenRevocationService,
                eventPublisher
        );

        passwordResetService = new PasswordResetService(
                userRepository,
                resetTokenRepository,
                refreshTokenRepository,
                emailNotificationService,
                loginAttemptService,
                passwordEncoder,
                15
        );
    }

    @Test
    void testUnknownEmailAndWrongPasswordProduceIdenticalFailure() {
        String existingEmail = "exists@example.com";
        String nonExistingEmail = "unknown@example.com";
        String realPassword = "CorrectPassword123!";
        String wrongPassword = "IncorrectPassword123!";

        AuthUser existingUser = new AuthUser(
                UUID.randomUUID(),
                existingEmail,
                passwordEncoder.encode(realPassword),
                "Existing",
                Role.USER,
                AccountStatus.ACTIVE
        );

        when(userRepository.findByEmail(existingEmail)).thenReturn(Optional.of(existingUser));
        when(userRepository.findByEmail(nonExistingEmail)).thenReturn(Optional.empty());

        // 1. Test unknown email
        AuthException ex1 = assertThrows(AuthException.class, () ->
                authService.login(new LoginRequest(nonExistingEmail, wrongPassword), "127.0.0.1")
        );

        // 2. Test wrong password for known email
        AuthException ex2 = assertThrows(AuthException.class, () ->
                authService.login(new LoginRequest(existingEmail, wrongPassword), "127.0.0.1")
        );

        // Verify dummy hash calculation was executed for non-existing user (and for wrong password)
        verify(loginAttemptService, org.mockito.Mockito.times(2)).executeDummyPasswordCheck(wrongPassword);

        // Verify identical status, code, and message
        assertEquals(HttpStatus.UNAUTHORIZED, ex1.getHttpStatus());
        assertEquals(HttpStatus.UNAUTHORIZED, ex2.getHttpStatus());
        assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS, ex1.getErrorCode());
        assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS, ex2.getErrorCode());
        assertEquals(ex1.getMessage(), ex2.getMessage());
        assertEquals("Email or password is incorrect.", ex1.getMessage());
    }

    @Test
    void testForgotPasswordDoesNotRevealEmailExistence() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        // Must not throw and must execute dummy hash check to prevent timing analysis
        assertDoesNotThrow(() -> passwordResetService.requestPasswordReset("unknown@example.com"));
        verify(loginAttemptService).executeDummyPasswordCheck(anyString());

        AuthUser activeUser = new AuthUser(
                UUID.randomUUID(),
                "active@example.com",
                passwordEncoder.encode("Pass123!"),
                "Active",
                Role.USER,
                AccountStatus.ACTIVE
        );
        when(userRepository.findByEmail("active@example.com")).thenReturn(Optional.of(activeUser));

        assertDoesNotThrow(() -> passwordResetService.requestPasswordReset("active@example.com"));
        verify(emailNotificationService).sendPasswordResetEmail(any(), any(), any());
    }
}
