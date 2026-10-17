package com.nexaai.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexaai.auth.domain.entity.AuthUser;
import com.nexaai.auth.domain.model.AccountStatus;
import com.nexaai.auth.domain.model.Role;
import com.nexaai.auth.domain.repository.AuthUserRepository;
import com.nexaai.auth.dto.request.GoogleOAuthRequest;
import com.nexaai.auth.exception.AuthException;
import com.nexaai.auth.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
public class GoogleOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthService.class);

    private final AuthUserRepository userRepository;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public GoogleOAuthService(AuthUserRepository userRepository,
                              EventPublisher eventPublisher,
                              ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AuthUser processGoogleUser(String googleId, String email, String displayName, boolean emailVerified, String correlationId) {
        if (!StringUtils.hasText(googleId) || !StringUtils.hasText(email)) {
            throw new AuthException(ErrorCode.VALIDATION_FAILED, "Google ID and email are required.", HttpStatus.BAD_REQUEST);
        }

        // 1. Check if user already linked with this Google ID
        Optional<AuthUser> byGoogleId = userRepository.findByGoogleId(googleId);
        if (byGoogleId.isPresent()) {
            AuthUser user = byGoogleId.get();
            if (user.getStatus() == AccountStatus.SUSPENDED || user.getStatus() == AccountStatus.DEACTIVATED) {
                throw new AuthException(ErrorCode.AUTH_ACCOUNT_SUSPENDED, "Account is not active.", HttpStatus.FORBIDDEN);
            }
            user.setUpdatedAt(Instant.now());
            return userRepository.save(user);
        }

        // 2. Check if user exists by email (verified-email account linking)
        Optional<AuthUser> byEmail = userRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            AuthUser user = byEmail.get();
            if (!emailVerified) {
                throw new AuthException(
                        ErrorCode.AUTH_OAUTH_EMAIL_UNVERIFIED,
                        "Google email is not verified; cannot link account.",
                        HttpStatus.BAD_REQUEST);
            }
            if (user.getStatus() == AccountStatus.SUSPENDED || user.getStatus() == AccountStatus.DEACTIVATED) {
                throw new AuthException(ErrorCode.AUTH_ACCOUNT_SUSPENDED, "Account is not active.", HttpStatus.FORBIDDEN);
            }

            user.setGoogleId(googleId);
            boolean wasPending = (user.getStatus() == AccountStatus.PENDING_VERIFICATION);
            if (wasPending) {
                user.setStatus(AccountStatus.ACTIVE);
            }
            user.setUpdatedAt(Instant.now());
            AuthUser saved = userRepository.save(user);

            if (wasPending) {
                eventPublisher.publishUserEmailVerified(saved, correlationId);
            }
            log.info("Linked Google account ID to existing user email: {}", email);
            return saved;
        }

        // 3. Create new user for Google sign-in (never store Google passwords)
        AuthUser newUser = new AuthUser(
                null,
                email,
                null, // No password hash for OAuth accounts
                displayName != null ? displayName : email.split("@")[0],
                Role.USER,
                emailVerified ? AccountStatus.ACTIVE : AccountStatus.PENDING_VERIFICATION
        );
        newUser.setGoogleId(googleId);
        AuthUser saved = userRepository.save(newUser);

        eventPublisher.publishUserRegistered(saved, correlationId);
        if (saved.getStatus() == AccountStatus.ACTIVE) {
            eventPublisher.publishUserEmailVerified(saved, correlationId);
        }

        log.info("Created new user from Google OAuth: {}", email);
        return saved;
    }

    @Transactional
    public AuthUser authenticateWithGoogle(GoogleOAuthRequest request, String correlationId) {
        String googleId = request.googleId();
        String email = request.email();
        String displayName = request.displayName();
        boolean emailVerified = Boolean.TRUE.equals(request.emailVerified());

        if (StringUtils.hasText(request.idToken())) {
            // Parse token claims from Google ID Token
            try {
                String[] parts = request.idToken().split("\\.");
                if (parts.length >= 2) {
                    byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
                    JsonNode claims = objectMapper.readTree(new String(payloadBytes, StandardCharsets.UTF_8));
                    if (claims.has("sub")) {
                        googleId = claims.get("sub").asText();
                    }
                    if (claims.has("email")) {
                        email = claims.get("email").asText();
                    }
                    if (claims.has("name")) {
                        displayName = claims.get("name").asText();
                    }
                    if (claims.has("email_verified")) {
                        emailVerified = claims.get("email_verified").asBoolean();
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse Google ID token: {}", e.getMessage());
            }
        }

        return processGoogleUser(googleId, email, displayName, emailVerified, correlationId);
    }
}
