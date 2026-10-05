package com.nexaai.gateway.support;

import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import io.jsonwebtoken.Jwts;

/**
 * Generates RS256 tokens for the gateway tests.
 *
 * <p>Same reasoning as the equivalent helper in user-service: a mocked verifier cannot catch a
 * wrong algorithm being accepted or an unchecked issuer. These tokens are genuinely signed and
 * genuinely verified.
 */
public final class GatewayTestTokens {

    private static final KeyPair KEY_PAIR = generate();

    private GatewayTestTokens() {
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Could not generate a test RSA key pair.", e);
        }
    }

    public static String publicKeyPem() {
        RSAPublicKey key = (RSAPublicKey) KEY_PAIR.getPublic();
        String base64 = Base64.getEncoder().encodeToString(key.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
    }

    public static String userToken() {
        return token(UUID.randomUUID(), "user@example.com", List.of("USER"), null, 3600);
    }

    public static String adminToken() {
        return token(UUID.randomUUID(), "admin@example.com", List.of("USER", "ADMIN"), null, 3600);
    }

    /** A token for a known subject, so the forwarded header can be asserted exactly. */
    public static String userToken(UUID subject) {
        return token(subject, "user@example.com", List.of("USER"), null, 3600);
    }

    public static String expiredToken() {
        return token(UUID.randomUUID(), "user@example.com", List.of("USER"), null, -3600);
    }

    public static String wrongIssuerToken() {
        return token(UUID.randomUUID(), "user@example.com", List.of("USER"), "someone-else", 3600);
    }

    public static String wrongAudienceToken() {
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer("nexa-auth-service")
                .audience().add("some-other-client").and()
                .claim("roles", List.of("USER"))
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(KEY_PAIR.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    /** {@code alg: none}: no signature at all. */
    public static String unsignedToken() {
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer("nexa-auth-service")
                .audience().add("nexaai-web").and()
                .claim("roles", List.of("ADMIN"))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .compact();
    }

    /**
     * HS256 signed with the RSA public key as the HMAC secret: the algorithm-confusion attack.
     *
     * <p>Built by hand with {@link Mac}, because jjwt correctly refuses to sign an HMAC token
     * with a {@code PrivateKey}. An attacker's tooling has no such restriction.
     */
    public static String algorithmConfusionToken() {
        RSAPublicKey publicKey = (RSAPublicKey) KEY_PAIR.getPublic();
        Instant now = Instant.now();

        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}"
                .getBytes(StandardCharsets.UTF_8));
        String payload = base64Url(("""
                {"sub":"%s","iss":"nexa-auth-service","aud":["nexaai-web"],\
                "email":"attacker@evil.test","roles":["ADMIN"],\
                "iat":%d,"exp":%d}"""
                .formatted(UUID.randomUUID(), now.getEpochSecond(),
                        now.plusSeconds(3600).getEpochSecond()))
                .getBytes(StandardCharsets.UTF_8));

        String signingInput = header + "." + payload;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(publicKey.getEncoded(), "HmacSHA256"));
            return signingInput + "."
                    + base64Url(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not build the algorithm-confusion token.", e);
        }
    }

    /** RS256, but signed by a key pair the gateway was never configured with. */
    public static String foreignKeyToken() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair foreign = generator.generateKeyPair();
            return Jwts.builder()
                    .subject(UUID.randomUUID().toString())
                    .issuer("nexa-auth-service")
                    .audience().add("nexaai-web").and()
                    .claim("roles", List.of("ADMIN"))
                    .issuedAt(Date.from(Instant.now()))
                    .expiration(Date.from(Instant.now().plusSeconds(3600)))
                    .signWith((RSAPrivateKey) foreign.getPrivate(), Jwts.SIG.RS256)
                    .compact();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String garbageToken() {
        return "not-a-jwt";
    }

    private static String token(UUID subject, String email, List<String> roles,
                                String issuer, long ttlSeconds) {
        String effectiveIssuer = issuer == null ? "nexa-auth-service" : issuer;
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject.toString())
                .issuer(effectiveIssuer)
                .audience().add("nexaai-web").and()
                .claim("email", email)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .id(UUID.randomUUID().toString())
                .signWith(KEY_PAIR.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}