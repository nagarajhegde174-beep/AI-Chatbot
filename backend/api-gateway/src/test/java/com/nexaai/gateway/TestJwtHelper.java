package com.nexaai.gateway;

import io.jsonwebtoken.Jwts;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class TestJwtHelper {

    private final KeyPair keyPair;

    public TestJwtHelper() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            this.keyPair = kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public PublicKey getPublicKey() {
        return keyPair.getPublic();
    }

    public PrivateKey getPrivateKey() {
        return keyPair.getPrivate();
    }

    public String generateToken(UUID userId, String email, List<String> roles, String status, long validityMs) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("roles", roles)
                .claim("status", status)
                .issuer("nexa-auth-service")
                .audience().add("nexaai-web").and()
                .issuedAt(new Date(now))
                .expiration(new Date(now + validityMs))
                .id(UUID.randomUUID().toString())
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }
}
