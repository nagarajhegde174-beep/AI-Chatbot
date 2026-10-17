package com.nexaai.auth.security;

import com.nexaai.auth.config.JwtProperties;
import com.nexaai.auth.domain.entity.AuthUser;
import com.nexaai.auth.exception.AuthException;
import com.nexaai.auth.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final JwtProperties jwtProperties;
    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;
    private String keyId;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @PostConstruct
    public void init() {
        this.keyId = "nexa-auth-key-1";
        if (jwtProperties.getPrivateKey() != null && !jwtProperties.getPrivateKey().isBlank()
                && jwtProperties.getPublicKey() != null && !jwtProperties.getPublicKey().isBlank()) {
            try {
                this.privateKey = parsePrivateKey(jwtProperties.getPrivateKey());
                this.publicKey = parsePublicKey(jwtProperties.getPublicKey());
                log.info("Initialized RS256 keys from configured properties");
                return;
            } catch (Exception e) {
                log.warn("Failed to parse configured RSA keys; falling back to generating an in-memory RSA key pair: {}", e.getMessage());
            }
        }

        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();
            this.privateKey = (RSAPrivateKey) kp.getPrivate();
            this.publicKey = (RSAPublicKey) kp.getPublic();
            log.info("Generated local 2048-bit RSA key pair for RS256 token signing");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA algorithm not available", e);
        }
    }

    public String generateAccessToken(AuthUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtProperties.getAccessTokenExpirationSeconds(), ChronoUnit.SECONDS);
        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
                .header()
                .keyId(keyId)
                .and()
                .subject(user.getId().toString())
                .issuer(jwtProperties.getIssuer())
                .audience().add(jwtProperties.getAudience()).and()
                .claim("roles", List.of(user.getRole().name()))
                .claim("status", user.getStatus().name())
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public Claims validateAndGetClaims(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .requireIssuer(jwtProperties.getIssuer())
                    .requireAudience(jwtProperties.getAudience())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String status = claims.get("status", String.class);
            if (!"ACTIVE".equals(status)) {
                throw new AuthException(ErrorCode.AUTH_ACCOUNT_SUSPENDED, "Account is not active.", HttpStatus.FORBIDDEN);
            }

            return claims;
        } catch (ExpiredJwtException e) {
            log.debug("JWT expired: {}", e.getMessage());
            throw new AuthException(ErrorCode.AUTH_TOKEN_EXPIRED, "Token has expired.", HttpStatus.UNAUTHORIZED);
        } catch (JwtException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            throw new AuthException(ErrorCode.AUTH_INVALID_TOKEN, "Token is invalid.", HttpStatus.UNAUTHORIZED);
        }
    }

    public UUID getUserIdFromClaims(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public String getJtiFromClaims(Claims claims) {
        return claims.getId();
    }

    public Instant getExpirationFromClaims(Claims claims) {
        return claims.getExpiration().toInstant();
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    public String getKeyId() {
        return keyId;
    }

    private RSAPrivateKey parsePrivateKey(String keyPem) throws Exception {
        String cleanPem = keyPem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(cleanPem);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private RSAPublicKey parsePublicKey(String keyPem) throws Exception {
        String cleanPem = keyPem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(cleanPem);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
    }
}
