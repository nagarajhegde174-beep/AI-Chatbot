package com.nexaai.user.security;

import com.nexaai.user.config.UserProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Verifies the RS256 access tokens that auth-service issued.
 *
 * <p><strong>Verification only.</strong> This service has no signing key and cannot mint a
 * token. There is deliberately no {@code privateKey} property: a service that cannot sign
 * cannot escalate itself.
 *
 * <p><strong>Why it verifies rather than calling auth-service.</strong> Making a synchronous
 * network call on every request would put auth-service on the critical path of the entire
 * platform and turn its availability into everyone's availability. The token is self-contained
 * precisely so that verifying it needs no round trip
 * ({@code docs/ARCHITECTURE.md} §13, decision 014).
 *
 * <p><strong>Algorithm pinning.</strong> Only {@code RS256} is accepted. The algorithm is
 * supplied here, never read from the token, so {@code alg: none} and an HS256 token signed
 * with the public key are both rejected.
 */
@Component
public class JwtVerifier {

    private static final Logger log = LoggerFactory.getLogger(JwtVerifier.class);

    private static final String CLAIM_ROLES = "roles";

    private final PublicKey publicKey;
    private final String issuer;
    private final String audience;
    private final String keyId;

    public JwtVerifier(UserProperties properties) {
        UserProperties.Jwt jwt = properties.getJwt();

        if (jwt.getPublicKey() == null || jwt.getPublicKey().isBlank()) {
            throw new IllegalStateException(
                    "No verification key configured. Set NEXA_USER_JWT_PUBLIC_KEY with the PEM-encoded "
                    + "RS256 public key matching auth-service's signing key. User Service verifies "
                    + "tokens only and is issued none, so failing here is the correct behaviour.");
        }

        this.publicKey = parsePublicKey(jwt.getPublicKey());
        this.issuer = jwt.getIssuer();
        this.audience = jwt.getAudience();
        this.keyId = jwt.getKeyId();

        log.info("JWT verification configured: RS256, issuer={}, audience={}, keyId={}",
                issuer, audience, keyId);
    }

    /**
     * Verifies a token and returns its claims.
     *
     * @throws io.jsonwebtoken.JwtException if the token is malformed, wrongly signed, expired,
     *                                       or carries the wrong issuer or audience
     */
    public Claims verify(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** The stable auth-service user id from a verified token. */
    public UUID subjectOf(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    /** Whether a verified token carries the ADMIN role. */
    public boolean isAdmin(Claims claims) {
        Object roles = claims.get(CLAIM_ROLES);
        if (roles instanceof Iterable<?> iterable) {
            for (Object role : iterable) {
                if (role != null && "ADMIN".equals(role.toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    public String issuer() {
        return issuer;
    }

    public String audience() {
        return audience;
    }

    public String keyId() {
        return keyId;
    }

    private static PublicKey parsePublicKey(String pem) {
        String body = pem
                .replaceAll("-----BEGIN [A-Z ]*PUBLIC KEY-----", "")
                .replaceAll("-----END [A-Z ]*PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        try {
            byte[] der = Base64.getDecoder().decode(body);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not parse the configured RS256 public key.", e);
        }
    }
}