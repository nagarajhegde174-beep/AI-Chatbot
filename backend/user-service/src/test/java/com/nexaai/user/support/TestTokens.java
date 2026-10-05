package com.nexaai.user.support;

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

/**
 * Generates RS256 tokens for tests.
 *
 * <p>Signs with a key pair generated in-process. The public half is injected into the service
 * under test, exactly as a real deployment injects Auth Service's public key.
 *
 * <p>This is deliberately a real signature over a real key pair rather than a stubbed verifier.
 * A mocked verifier cannot catch a wrong algorithm being accepted, an issuer that is not
 * checked, or a service that would happily verify a token signed with its own public key. The
 * expensive cases are the ones a mock throws away.
 */
public final class TestTokens {

    private static final KeyPair KEY_PAIR = generate();

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

    /** The PEM-encoded public key, as configuration would supply it. */
    public static String publicKeyPem() {
        RSAPublicKey key = (RSAPublicKey) KEY_PAIR.getPublic();
        String base64 = Base64.getEncoder().encodeToString(key.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
    }

    /** A valid USER access token. */
    public static String userToken() {
        return token(UUID.randomUUID(), "user@example.com", List.of("USER"), null, 3600);
    }

    /** A valid ADMIN access token. */
    public static String adminToken() {
        return token(UUID.randomUUID(), "admin@example.com", List.of("USER", "ADMIN"), null, 3600);
    }

    /** An ADMIN access token for a specific auth-service id, so the audit trail is checkable. */
    public static String adminToken(UUID authUserId) {
        return token(authUserId, "admin@example.com", List.of("USER", "ADMIN"), null, 3600);
    }

    /** A token with an explicit subject and roles, for tests that need to know who acted. */
    public static String tokenFor(UUID subject, List<String> roles) {
        return token(subject, "caller@example.com", roles, null, 3600);
    }

    /** A valid USER token for a specific auth-service id. */
    public static String userToken(UUID authUserId) {
        return token(authUserId, "user@example.com", List.of("USER"), null, 3600);
    }

    /** A token that has already expired. */
    public static String expiredToken() {
        return token(UUID.randomUUID(), "user@example.com", List.of("USER"), null, -3600);
    }

    /** A token for the wrong issuer. */
    public static String wrongIssuerToken() {
        return token(UUID.randomUUID(), "user@example.com", List.of("USER"), "someone-else", 3600);
    }

    /** A token for the wrong audience. */
    public static String wrongAudienceToken() {
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer("nexa-auth-service")
                .audience().add("some-other-client").and()
                .claim("email", "user@example.com")
                .claim("roles", List.of("USER"))
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .id(UUID.randomUUID().toString())
                .signWith(KEY_PAIR.getPrivate(), Jwts.SIG.RS256)
                .compact();
        return token;
    }

    /** An unsigned token, i.e. {@code alg: none}. */
    public static String unsignedToken() {
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer("nexa-auth-service")
                .audience().add("nexaai-web").and()
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .compact();
    }

    /**
     * A token signed with HS256 using the RSA <em>public</em> key as the HMAC secret.
     *
     * <p>The classic algorithm-confusion attack. A verifier that reads {@code alg} from the
     * token and then uses the RSA public key as an HMAC secret will accept this, and the
     * attacker becomes ADMIN. A verifier that pins RS256 cannot.
     *
     * <p>Built by hand with {@link javax.crypto.Mac}: jjwt refuses to sign an HMAC token with
     * a {@code PrivateKey}, because a correct library should not let this be expressed easily.
     * The attacker's tooling has no such restriction, so the test has to construct it directly.
     */
    public static String algorithmConfusionToken() {
        RSAPublicKey publicKey = (RSAPublicKey) KEY_PAIR.getPublic();
        Instant now = Instant.now();

        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(
                StandardCharsets.UTF_8));
        String payload = base64Url(("""
                {"sub":"%s","iss":"nexa-auth-service","aud":["nexaai-web"],\
                "email":"attacker@evil.test","roles":["ADMIN"],\
                "iat":%d,"exp":%d}"""
                .formatted(UUID.randomUUID(), now.getEpochSecond(),
                        now.plusSeconds(3600).getEpochSecond()))
                .getBytes(StandardCharsets.UTF_8));

        String signingInput = header + "." + payload;
        try {
            // The public key's DER bytes are the "secret". The attacker does not have the
            // private key; that is the whole trick.
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(publicKey.getEncoded(), "HmacSHA256"));
            String signature = base64Url(mac.doFinal(
                    signingInput.getBytes(StandardCharsets.UTF_8)));
            return signingInput + "." + signature;
        } catch (Exception e) {
            throw new IllegalStateException("Could not build the algorithm-confusion token.", e);
        }
    }

    /** Base64url without padding, as JWT requires. */
    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** A token signed by a different key pair, i.e. a forgery. */
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

    /** A structurally valid but garbage token. */
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
}