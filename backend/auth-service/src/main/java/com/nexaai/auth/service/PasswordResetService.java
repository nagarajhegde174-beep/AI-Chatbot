package com.nexaai.auth.service;

import com.nexaai.auth.domain.entity.AuthUser;
import com.nexaai.auth.domain.entity.PasswordResetToken;
import com.nexaai.auth.domain.model.AccountStatus;
import com.nexaai.auth.domain.repository.AuthUserRepository;
import com.nexaai.auth.domain.repository.PasswordResetTokenRepository;
import com.nexaai.auth.domain.repository.RefreshTokenRepository;
import com.nexaai.auth.exception.AuthException;
import com.nexaai.auth.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetService {

    private final AuthUserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailNotificationService emailNotificationService;
    private final LoginAttemptService loginAttemptService;
    private final PasswordEncoder passwordEncoder;
    private final int expirationMinutes;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(AuthUserRepository userRepository,
                                PasswordResetTokenRepository resetTokenRepository,
                                RefreshTokenRepository refreshTokenRepository,
                                EmailNotificationService emailNotificationService,
                                LoginAttemptService loginAttemptService,
                                PasswordEncoder passwordEncoder,
                                @Value("${auth.email.reset-expiration-minutes:15}") int expirationMinutes) {
        this.userRepository = userRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.emailNotificationService = emailNotificationService;
        this.loginAttemptService = loginAttemptService;
        this.passwordEncoder = passwordEncoder;
        this.expirationMinutes = expirationMinutes;
    }

    @Transactional
    public void requestPasswordReset(String email) {
        Optional<AuthUser> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty() || userOpt.get().getStatus() != AccountStatus.ACTIVE) {
            // Constant-time dummy work to prevent user enumeration
            loginAttemptService.executeDummyPasswordCheck("dummy-reset-work");
            return;
        }

        AuthUser user = userOpt.get();
        // Invalidate previous reset requests
        resetTokenRepository.invalidateAllForUser(user, Instant.now());

        String rawToken = UUID.randomUUID().toString();
        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));
        String tokenHash = sha256(rawToken);

        Instant expiresAt = Instant.now().plus(expirationMinutes, ChronoUnit.MINUTES);
        PasswordResetToken resetToken = new PasswordResetToken(null, user, tokenHash, otp, expiresAt);
        resetTokenRepository.save(resetToken);

        emailNotificationService.sendPasswordResetEmail(user.getEmail(), rawToken, otp);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        String tokenHash = sha256(rawToken);
        PasswordResetToken resetToken = resetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AuthException(
                        ErrorCode.AUTH_INVALID_RESET_TOKEN,
                        "Password reset token is invalid or has expired.",
                        HttpStatus.BAD_REQUEST));

        if (resetToken.isExpired() || resetToken.isUsed()) {
            throw new AuthException(
                    ErrorCode.AUTH_INVALID_RESET_TOKEN,
                    "Password reset token is invalid or has expired.",
                    HttpStatus.BAD_REQUEST);
        }

        AuthUser user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        // One-time use: invalidate this reset token
        resetToken.setUsedAt(Instant.now());
        resetTokenRepository.save(resetToken);

        // Revoke all existing refresh tokens for the user to force re-login
        refreshTokenRepository.revokeAllForUser(user, Instant.now());
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
