package com.nexaai.user;

import com.nexaai.user.security.JwtVerifier;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class TestJwtHelper {

    private final PrivateKey privateKey;

    public TestJwtHelper(JwtVerifier jwtVerifier) throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        this.privateKey = kp.getPrivate();
        jwtVerifier.setPublicKey(kp.getPublic());
    }

    public String generateToken(UUID userId, String email, List<String> roles) {
        return generateToken(userId, email, roles, "ACTIVE");
    }

    public String generateToken(UUID userId, String email, List<String> roles, String status) {
        return Jwts.builder()
                .issuer("nexa-auth-service")
                .audience().add("nexaai-web").and()
                .subject(userId.toString())
                .claim("email", email)
                .claim("roles", roles)
                .claim("status", status)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }
}
