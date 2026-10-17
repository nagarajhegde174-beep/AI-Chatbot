package com.nexaai.auth.service;

import com.nexaai.auth.config.JwtProperties;
import com.nexaai.auth.domain.entity.AuthUser;
import com.nexaai.auth.domain.entity.RefreshToken;
import com.nexaai.auth.domain.model.AccountStatus;
import com.nexaai.auth.domain.model.Role;
import com.nexaai.auth.domain.repository.AuthUserRepository;
import com.nexaai.auth.domain.repository.RefreshTokenRepository;
import com.nexaai.auth.dto.request.ChangePasswordRequest;
import com.nexaai.auth.dto.request.LoginRequest;
import com.nexaai.auth.dto.request.RegisterRequest;
import com.nexaai.auth.dto.response.AuthResponse;
import com.nexaai.auth.dto.response.RegisterResponse;
import com.nexaai.auth.exception.AuthException;
import com.nexaai.auth.exception.ErrorCode;
import com.nexaai.auth.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthUserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final LoginAttemptService loginAttemptService;
    private final EmailVerificationService emailVerificationService;
    private final TokenRevocationService tokenRevocationService;
    private final EventPublisher eventPublisher;

    public AuthService(AuthUserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       JwtProperties jwtProperties,
                       LoginAttemptService loginAttemptService,
                       EmailVerificationService emailVerificationService,
                       TokenRevocationService tokenRevocationService,
                       EventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtProperties = jwtProperties;
        this.loginAttemptService = loginAttemptService;
        this.emailVerificationService = emailVerificationService;
        this.tokenRevocationService = tokenRevocationService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request, String correlationId) {
        if (userRepository.existsByEmail(request.email())) {
            throw new AuthException(
                    ErrorCode.AUTH_EMAIL_ALREADY_EXISTS,
                    "An account with this email address already exists.",
                    HttpStatus.CONFLICT
            );
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        String displayName = request.displayName();
        if (displayName == null || displayName.isBlank()) {
            displayName = request.email().split("@")[0];
        }

        AuthUser user = new AuthUser(
                null,
                request.email(),
                encodedPassword,
                displayName,
                Role.USER,
                AccountStatus.PENDING_VERIFICATION
        );

        AuthUser saved = userRepository.save(user);
        emailVerificationService.createVerificationToken(saved);
        eventPublisher.publishUserRegistered(saved, correlationId);

        log.info("Registered new user: {} (Status: PENDING_VERIFICATION)", saved.getEmail());

        return new RegisterResponse(
                saved.getId(),
                saved.getEmail(),
                saved.getDisplayName(),
                List.of(saved.getRole().name()),
                saved.getStatus().name(),
                saved.getCreatedAt()
        );
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress) {
        if (loginAttemptService.isLocked(request.email())) {
            throw new AuthException(
                    ErrorCode.AUTH_RATE_LIMITED,
                    "Too many failed login attempts. Please try again later.",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }

        Optional<AuthUser> userOpt = userRepository.findByEmail(request.email());
        if (userOpt.isEmpty()) {
            loginAttemptService.executeDummyPasswordCheck(request.password());
            loginAttemptService.recordFailure(request.email(), ipAddress);
            throw new AuthException(
                    ErrorCode.AUTH_INVALID_CREDENTIALS,
                    "Email or password is incorrect.",
                    HttpStatus.UNAUTHORIZED
            );
        }

        AuthUser user = userOpt.get();
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttemptService.executeDummyPasswordCheck(request.password());
            loginAttemptService.recordFailure(request.email(), ipAddress);
            throw new AuthException(
                    ErrorCode.AUTH_INVALID_CREDENTIALS,
                    "Email or password is incorrect.",
                    HttpStatus.UNAUTHORIZED
            );
        }

        if (user.getStatus() == AccountStatus.PENDING_VERIFICATION) {
            throw new AuthException(
                    ErrorCode.AUTH_ACCOUNT_NOT_VERIFIED,
                    "Account is pending email verification. Please verify your email before logging in.",
                    HttpStatus.FORBIDDEN
            );
        }

        if (user.getStatus() == AccountStatus.SUSPENDED || user.getStatus() == AccountStatus.DEACTIVATED) {
            throw new AuthException(
                    ErrorCode.AUTH_ACCOUNT_SUSPENDED,
                    "Account is inactive or suspended.",
                    HttpStatus.FORBIDDEN
            );
        }

        loginAttemptService.recordSuccess(request.email(), ipAddress);
        return issueTokens(user);
    }

    @Transactional(noRollbackFor = AuthException.class)
    public AuthResponse refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new AuthException(ErrorCode.AUTH_INVALID_TOKEN, "Refresh token is missing.", HttpStatus.UNAUTHORIZED);
        }

        String tokenHash = sha256(rawRefreshToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AuthException(ErrorCode.AUTH_INVALID_TOKEN, "Refresh token is invalid.", HttpStatus.UNAUTHORIZED));

        if (token.isRevoked()) {
            // Theft detection per docs/SERVICE_CONTRACTS.md section 5.6: revoke whole family
            refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now());
            log.warn("Revoked refresh token presented for family {}. Revoking entire family (theft detection).", token.getFamilyId());
            throw new AuthException(ErrorCode.AUTH_TOKEN_REVOKED, "Refresh token has been revoked.", HttpStatus.UNAUTHORIZED);
        }

        if (token.isExpired()) {
            throw new AuthException(ErrorCode.AUTH_TOKEN_EXPIRED, "Refresh token has expired.", HttpStatus.UNAUTHORIZED);
        }

        AuthUser user = token.getUser();
        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new AuthException(ErrorCode.AUTH_ACCOUNT_SUSPENDED, "Account is not active.", HttpStatus.FORBIDDEN);
        }

        // Revoke the old token
        token.setRevokedAt(Instant.now());
        refreshTokenRepository.save(token);

        // Issue a new token pair in the same family
        return issueTokensInFamily(user, token.getFamilyId());
    }

    @Transactional
    public void logout(String rawRefreshToken, String bearerToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String tokenHash = sha256(rawRefreshToken);
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(rt -> {
                rt.setRevokedAt(Instant.now());
                refreshTokenRepository.save(rt);
            });
        }

        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            String jwt = bearerToken.substring(7);
            try {
                Claims claims = jwtTokenProvider.validateAndGetClaims(jwt);
                String jti = jwtTokenProvider.getJtiFromClaims(claims);
                UUID userId = jwtTokenProvider.getUserIdFromClaims(claims);
                Instant exp = jwtTokenProvider.getExpirationFromClaims(claims);
                tokenRevocationService.revokeAccessToken(jti, userId, exp);
            } catch (Exception ignored) {
                // Ignore invalid or already expired tokens on logout
            }
        }
    }

    @Transactional
    public void logoutAll(UUID userId, String bearerToken) {
        AuthUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(ErrorCode.AUTH_USER_NOT_FOUND, "User not found.", HttpStatus.NOT_FOUND));

        refreshTokenRepository.revokeAllForUser(user, Instant.now());

        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            String jwt = bearerToken.substring(7);
            try {
                Claims claims = jwtTokenProvider.validateAndGetClaims(jwt);
                String jti = jwtTokenProvider.getJtiFromClaims(claims);
                Instant exp = jwtTokenProvider.getExpirationFromClaims(claims);
                tokenRevocationService.revokeAccessToken(jti, userId, exp);
            } catch (Exception ignored) {
            }
        }
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        AuthUser user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(ErrorCode.AUTH_USER_NOT_FOUND, "User not found.", HttpStatus.NOT_FOUND));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new AuthException(
                    ErrorCode.AUTH_INVALID_CURRENT_PASSWORD,
                    "Current password does not match.",
                    HttpStatus.BAD_REQUEST
            );
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        log.info("Password changed successfully for user: {}", user.getEmail());
    }

    public AuthResponse issueTokens(AuthUser user) {
        return issueTokensInFamily(user, UUID.randomUUID());
    }

    private AuthResponse issueTokensInFamily(AuthUser user, UUID familyId) {
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String rawRefreshToken = UUID.randomUUID().toString() + "-" + UUID.randomUUID();
        String tokenHash = sha256(rawRefreshToken);

        Instant expiresAt = Instant.now().plus(jwtProperties.getRefreshTokenExpirationSeconds(), ChronoUnit.SECONDS);
        RefreshToken refreshTokenEntity = new RefreshToken(null, user, tokenHash, familyId, expiresAt);
        refreshTokenRepository.save(refreshTokenEntity);

        return AuthResponse.of(
                accessToken,
                rawRefreshToken,
                jwtProperties.getAccessTokenExpirationSeconds(),
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                List.of(user.getRole().name())
        );
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
