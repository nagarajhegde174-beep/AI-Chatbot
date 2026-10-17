package com.nexaai.auth.service;

import com.nexaai.auth.domain.entity.AuthUser;
import com.nexaai.auth.domain.entity.EmailVerificationToken;
import com.nexaai.auth.domain.model.AccountStatus;
import com.nexaai.auth.domain.repository.AuthUserRepository;
import com.nexaai.auth.domain.repository.EmailVerificationTokenRepository;
import com.nexaai.auth.exception.AuthException;
import com.nexaai.auth.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final AuthUserRepository userRepository;
    private final EmailNotificationService emailNotificationService;
    private final EventPublisher eventPublisher;
    private final int expirationHours;

    public EmailVerificationService(EmailVerificationTokenRepository tokenRepository,
                                    AuthUserRepository userRepository,
                                    EmailNotificationService emailNotificationService,
                                    EventPublisher eventPublisher,
                                    @Value("${auth.email.verification-expiration-hours:24}") int expirationHours) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.emailNotificationService = emailNotificationService;
        this.eventPublisher = eventPublisher;
        this.expirationHours = expirationHours;
    }

    @Transactional
    public EmailVerificationToken createVerificationToken(AuthUser user) {
        String tokenString = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(expirationHours, ChronoUnit.HOURS);
        EmailVerificationToken token = new EmailVerificationToken(null, user, tokenString, expiresAt);
        EmailVerificationToken saved = tokenRepository.save(token);
        emailNotificationService.sendVerificationEmail(user.getEmail(), tokenString);
        return saved;
    }

    @Transactional
    public AuthUser verifyEmail(String tokenString, String correlationId) {
        EmailVerificationToken token = tokenRepository.findByToken(tokenString)
                .orElseThrow(() -> new AuthException(
                        ErrorCode.AUTH_INVALID_VERIFICATION_TOKEN,
                        "Verification token is invalid or has expired.",
                        HttpStatus.BAD_REQUEST));

        if (token.isExpired() || token.isUsed()) {
            throw new AuthException(
                    ErrorCode.AUTH_INVALID_VERIFICATION_TOKEN,
                    "Verification token is invalid or has expired.",
                    HttpStatus.BAD_REQUEST);
        }

        AuthUser user = token.getUser();
        user.setStatus(AccountStatus.ACTIVE);
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        token.setUsedAt(Instant.now());
        tokenRepository.save(token);

        eventPublisher.publishUserEmailVerified(user, correlationId);
        return user;
    }
}
