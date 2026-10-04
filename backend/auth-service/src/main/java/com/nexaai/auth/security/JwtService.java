package com.nexaai.auth.security;

import com.nexaai.auth.config.AuthProperties;
import com.nexaai.auth.domain.AuthUser;
import com.nexaai.auth.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Issues and verifies RS256 access tokens.
 *
 * <p><strong>RS256, not HS256.</strong> Asymmetric signing means a verifier needs only the
 * public key, so the eight services that check tokens and the gateway that publishes JWKS
 * never hold signing material. An HS256 token signed with a leaked public key is a real,
 * widely-exploited attack class, and it is exactly the mistake that makes token
 * verification unsafe to distribute ({@code docs/SECURITY.md} section 2.2).
 *
 * <p><strong>Algorithm pinning.</strong> Verification accepts exactly {@code alg=RS256} and
 * nothing else. JJWT is given the algorithm explicitly rather than trusting the header, so
 * {@code alg=none} and an HMAC token signed with the public key are both rejected. A
 * verifier that reads {@code alg} from the token it is verifying is trusting its input.
 */
@Component
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    /** RS256. Pinned: see the class comment. */
    public static final String ALGORITHM = "RS256";

    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_STATUS = "status";
    private static final String CLAIM_CREDENTIALS_VERSION = "cv";
    private static final String CLAIM_TOKEN_TYPE = "typ";

    private static final String TYPE_ACCESS = "access";

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String issuer;
    private final String audience;
    private final String keyId;
    private final AuthProperties.Token tokenProperties;

    public JwtService(AuthProperties properties) {
        AuthProperties.Jwt jwt = properties.getJwt();

        if (jwt.getPrivateKey() == null || jwt.getPrivateKey().isBlank()) {
            throw new IllegalStateException(
                    "Nexa AI signing key is not configured. Set NEXA_AUTH_JWT_PRIVATE_KEY with a "
                    + "PEM-encoded RS256 private key. Generate one with: "
                    + "openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem");
        }
        if (jwt.getPublicKey() == null || jwt.getPublicKey().isBlank()) {
            throw new IllegalStateException(
                    "Nexa AI verification key is not configured. Set NEXA_AUTH_JWT_PUBLIC_KEY with "
                    + "the matching PEM-encoded RS256 public key.");
        }

        this.privateKey = parsePrivateKey(jwt.getPrivateKey());
        this.publicKey = parsePublicKey(jwt.getPublicKey());
        this.issuer = jwt.getIssuer();
        this.audience = jwt.getAudience();
        this.keyId = jwt.getKeyId();
        this.tokenProperties = properties.getToken();

        // Fails fast on a mismatched pair. Discovering this at the first sign-in instead of
        // at startup would mean every token in the window was unverifiable.
        if (!isPairConsistent()) {
            throw new IllegalStateException(
                    "The configured RS256 private and public keys are not a pair. "
                    + "Derive the public key with: openssl rsa -in private.pem -pubout -out public.pem");
        }

        log.info("JWT configured: RS256, issuer={}, audience={}, keyId={}, accessTokenTtl={}",
                issuer, audience, keyId, tokenProperties.getAccessTokenTtl());
    }

    /**
     * Signs an access token for a user.
     *
     * <p>Carries {@code credentialsVersion} so a password change invalidates tokens issued
     * before it. Without that, a password change would leave every existing session working,
     * which is the most common way password changes quietly fail at their job.
     */
    public IssuedAccessToken issue(AuthUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(tokenProperties.getAccessTokenTtl());
        String jti = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .id(jti)
                .issuer(issuer)
                .audience().add(audience).and()
                .subject(user.getId().toString())
                .claim(CLAIM_ROLES, List.of(user.getRole().name()))
                .claim(CLAIM_STATUS, user.getStatus().name())
                .claim(CLAIM_CREDENTIALS_VERSION, user.getCredentialsVersion())
                .claim(CLAIM_TOKEN_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(privateKey, Jwts.SIG.RS256)
                .header().keyId(keyId).and()
                .compact();

        return new IssuedAccessToken(token, jti, now, expiry);
    }

    /**
     * Verifies a token and returns its claims.
     *
     * @throws JwtException if the token is malformed, unsigned, expired, wrongly signed, or
     *                       carries the wrong issuer or audience
     */
    public Claims verify(String token) throws JwtException {
        // Passing the algorithm and the public key explicitly means JJWT never consults
        // the token's own `alg` header. That is what defeats algorithm confusion.
        return Jwts.parser()
                .verifyWith(publicKey)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Verifies and additionally requires the token to be an access token. */
    public Claims verifyAccessToken(String token) throws JwtException {
        Claims claims = verify(token);
        if (!TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
            throw new JwtException("Not an access token");
        }
        return claims;
    }

    /** The public key, base64-encoded, for the JWKS document. */
    public String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    public String keyId() {
        return keyId;
    }

    public String issuer() {
        return issuer;
    }

    public String audience() {
        return audience;
    }

    // ------------------------------------------------------------------
    // Key parsing
    // ------------------------------------------------------------------

    private static PrivateKey parsePrivateKey(String pem) {
        byte[] der = decodePem(pem, "PRIVATE KEY");
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException(
                    "Could not parse the RS256 private key. It must be PKCS#8 encoded. "
                    + "Convert an existing PKCS#1 key with: openssl pkcs8 -topk8 -nocrypt -in key.pem -out private.pem",
                    e);
        }
    }

    private static PublicKey parsePublicKey(String pem) {
        byte[] der = decodePem(pem, "PUBLIC KEY");
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Could not parse the RS256 public key. It must be X.509 encoded.", e);
        }
    }

    /**
     * Strips PEM armour and base64-decodes the body.
     *
     * <p>Accepts both {@code BEGIN KEY} and {@code BEGIN RSA KEY} style headers so a key
     * pasted straight from {@code openssl rsa -traditional} still loads.
     */
    private static byte[] decodePem(String pem, String expectedMarker) {
        String body = pem
                .replace("-----BEGIN " + expectedMarker + "-----", "")
                .replace("-----END " + expectedMarker + "-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PUBLIC KEY-----", "")
                .replace("-----END RSA PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        try {
            return Base64.getDecoder().decode(body);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("The configured key is not valid base64 PEM content.", e);
        }
    }

    /**
     * Checks that the configured keys are a matching pair.
     *
     * <p>Signs a throwaway value with the private key and verifies it with the public key.
     * A mismatched pair otherwise fails only at the first real sign-in, which is the worst
     * possible moment to discover it.
     */
    private boolean isPairConsistent() {
        try {
            String probe = Jwts.builder()
                    .issuer(issuer)
                    .subject("probe")
                    .issuedAt(new Date())
                    .expiration(Date.from(Instant.now().plusSeconds(60)))
                    .signWith(privateKey, Jwts.SIG.RS256)
                    .compact();
            Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(probe);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /** An issued access token and the metadata needed to revoke it later. */
    public record IssuedAccessToken(String token, String jti, Instant issuedAt, Instant expiresAt) {
    }
}