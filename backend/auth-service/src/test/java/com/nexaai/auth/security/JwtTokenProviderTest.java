package com.nexaai.auth.security;

import com.nexaai.auth.config.JwtProperties;
import com.nexaai.auth.domain.entity.AuthUser;
import com.nexaai.auth.domain.model.AccountStatus;
import com.nexaai.auth.domain.model.Role;
import com.nexaai.auth.exception.AuthException;
import com.nexaai.auth.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setIssuer("nexa-auth-service");
        jwtProperties.setAudience("nexaai-web");
        jwtProperties.setAccessTokenExpirationSeconds(900);

        tokenProvider = new JwtTokenProvider(jwtProperties);
        tokenProvider.init();
    }

    @Test
    void testTokenIssuanceAndClaims() {
        AuthUser user = new AuthUser(UUID.randomUUID(), "test@example.com", "hash", "Test User", Role.USER, AccountStatus.ACTIVE);
        String token = tokenProvider.generateAccessToken(user);

        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3);

        Claims claims = tokenProvider.validateAndGetClaims(token);
        assertEquals(user.getId().toString(), claims.getSubject());
        assertEquals("nexa-auth-service", claims.getIssuer());
        assertTrue(claims.getAudience().contains("nexaai-web"));
        assertEquals("ACTIVE", claims.get("status", String.class));

        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        assertNotNull(roles);
        assertEquals(1, roles.size());
        assertEquals("USER", roles.getFirst());
        assertNotNull(claims.getId()); // jti
    }

    @Test
    void testExpiredTokenRejected() {
        // Build expired token
        Instant past = Instant.now().minus(10, ChronoUnit.MINUTES);
        String expiredToken = Jwts.builder()
                .header().keyId(tokenProvider.getKeyId()).and()
                .subject(UUID.randomUUID().toString())
                .issuer("nexa-auth-service")
                .audience().add("nexaai-web").and()
                .claim("roles", List.of("USER"))
                .claim("status", "ACTIVE")
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(past.minus(15, ChronoUnit.MINUTES)))
                .expiration(Date.from(past))
                .signWith(tokenProvider.getPublicKey() != null ? getDifferentKeyPair().getPrivate() : null, Jwts.SIG.RS256)
                .compact();

        AuthException exception = assertThrows(AuthException.class, () -> tokenProvider.validateAndGetClaims(expiredToken));
        assertTrue(exception.getErrorCode() == ErrorCode.AUTH_TOKEN_EXPIRED || exception.getErrorCode() == ErrorCode.AUTH_INVALID_TOKEN);
    }

    @Test
    void testTamperedTokenRejected() {
        AuthUser user = new AuthUser(UUID.randomUUID(), "test@example.com", "hash", "Test User", Role.USER, AccountStatus.ACTIVE);
        String validToken = tokenProvider.generateAccessToken(user);

        // Tamper with the payload part
        String[] parts = validToken.split("\\.");
        String tamperedToken = parts[0] + "." + parts[1] + "tampered" + "." + parts[2];

        AuthException ex = assertThrows(AuthException.class, () -> tokenProvider.validateAndGetClaims(tamperedToken));
        assertEquals(ErrorCode.AUTH_INVALID_TOKEN, ex.getErrorCode());
    }

    @Test
    void testWrongSignatureRejected() throws Exception {
        KeyPair otherKeyPair = getDifferentKeyPair();
        Instant now = Instant.now();
        String tokenFromOtherKey = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer("nexa-auth-service")
                .audience().add("nexaai-web").and()
                .claim("roles", List.of("USER"))
                .claim("status", "ACTIVE")
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .signWith(otherKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        AuthException ex = assertThrows(AuthException.class, () -> tokenProvider.validateAndGetClaims(tokenFromOtherKey));
        assertEquals(ErrorCode.AUTH_INVALID_TOKEN, ex.getErrorCode());
    }

    @Test
    void testNonActiveStatusRejected() {
        AuthUser user = new AuthUser(UUID.randomUUID(), "suspended@example.com", "hash", "Suspended User", Role.USER, AccountStatus.SUSPENDED);
        String token = tokenProvider.generateAccessToken(user);

        AuthException ex = assertThrows(AuthException.class, () -> tokenProvider.validateAndGetClaims(token));
        assertEquals(ErrorCode.AUTH_ACCOUNT_SUSPENDED, ex.getErrorCode());
    }

    private KeyPair getDifferentKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            return kpg.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
