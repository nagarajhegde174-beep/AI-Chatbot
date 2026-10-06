package com.nexaai.ai.support;

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

/** RS256 tokens for the AI Service tests. Real signatures over a real in-process key pair. */
public final class TestTokens {

    private static final KeyPair KEY_PAIR = generate();

    /**
     * A stable subject per caller kind.
     *
     * <p>Fixed UUIDs, not names, because {@code JwtVerifier.subjectOf} parses the subject as a
     * UUID and that parse is what identifies the caller for every ownership check downstream. A
     * subject like {@code "nexa-user"} throws there, is caught, and silently becomes an
     * unauthenticated request -- so a test using one would be testing a 401 and calling it a
     * valid token.
     */
    private static final String USER_SUBJECT = "11111111-1111-4111-8111-111111111111";
    private static final String SERVICE_SUBJECT = "22222222-2222-4222-8222-222222222222";
    private static final String ADMIN_SUBJECT = "33333333-3333-4333-8333-333333333333";

    private TestTokens() {
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

    /** A valid service-caller token. */
    public static String serviceToken() {
        return token(SERVICE_SUBJECT, List.of("SERVICE"));
    }

    public static String userToken() {
        return token(USER_SUBJECT, List.of("USER"));
    }

    public static String adminToken() {
        return token(ADMIN_SUBJECT, List.of("USER", "ADMIN"));
    }

    /** The subject of a {@link #userToken()}, for asserting who the service saw. */
    public static String userSubject() {
        return USER_SUBJECT;
    }

    /** The subject of a {@link #serviceToken()}. */
    public static String serviceSubject() {
        return SERVICE_SUBJECT;
    }

    public static String expiredToken() {
        return build(USER_SUBJECT, List.of("USER"), "nexa-auth-service", "nexaai-web", -3600);
    }

    public static String wrongIssuerToken() {
        return build(USER_SUBJECT, List.of("USER"), "someone-else", "nexaai-web", 3600);
    }

    public static String wrongAudienceToken() {
        return build(USER_SUBJECT, List.of("USER"), "nexa-auth-service", "another-service", 3600);
    }

    /** {@code alg: none}: no signature at all. */
    public static String unsignedToken() {
        return Jwts.builder()
                .subject(ADMIN_SUBJECT)
                .issuer("nexa-auth-service")
                .audience().add("nexaai-web").and()
                .claim("roles", List.of("ADMIN"))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .compact();
    }

    /** HS256 signed with the RSA public key: the algorithm-confusion attack. */
    public static String algorithmConfusionToken() {
        RSAPublicKey publicKey = (RSAPublicKey) KEY_PAIR.getPublic();
        Instant now = Instant.now();

        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}"
                .getBytes(StandardCharsets.UTF_8));
        String payload = base64Url(("""
                {"sub":"%s","iss":"nexa-auth-service","aud":["nexaai-web"],\
                "roles":["ADMIN"],"iat":%d,"exp":%d}"""
                .formatted(ADMIN_SUBJECT, now.getEpochSecond(),
                        now.plusSeconds(3600).getEpochSecond()))
                .getBytes(StandardCharsets.UTF_8));

        String signingInput = header + "." + payload;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(publicKey.getEncoded(), "HmacSHA256"));
            return signingInput + "."
                    + base64Url(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String garbageToken() {
        return "not-a-jwt";
    }

    private static String token(String subject, List<String> roles) {
        return build(subject, roles, "nexa-auth-service", "nexaai-web", 3600);
    }

    private static String build(String subject, List<String> roles, String issuer,
                                String audience, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .issuer(issuer)
                .audience().add(audience).and()
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