package com.nexaai.ai.security;

import com.nexaai.ai.config.AiProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
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
 * Verifies the RS256 access tokens that Auth Service issued.
 *
 * <p><strong>Verification only.</strong> This service holds a public key and has no signing key,
 * so it cannot mint a token. There is deliberately no {@code privateKey} property.
 *
 * <p><strong>Duplicated from the other services on purpose.</strong> A shared jar would make every
 * service depend on one artifact, which is the disguised-monolith path ({@code docs/RULES.md} §2).
 *
 * <p><strong>Algorithm pinning.</strong> The algorithm is supplied here, never read from the
 * token, which is what rejects {@code alg: none} and an HS256 token signed with the public key.
 */
@Component
public class JwtVerifier {

    private static final Logger log = LoggerFactory.getLogger(JwtVerifier.class);

    private static final String CLAIM_ROLES = "roles";

    private final PublicKey publicKey;
    private final String issuer;
    private final String audience;

    public JwtVerifier(AiProperties properties) {
        AiProperties.Jwt jwt = properties.getJwt();

        if (jwt.getPublicKey() == null || jwt.getPublicKey().isBlank()) {
            throw new IllegalStateException(
                    "No verification key configured. Set NEXA_AI_JWT_PUBLIC_KEY with the "
                    + "PEM-encoded RS256 public key matching Auth Service's signing key. Chat "
                    + "Service verifies tokens and is issued none, so failing here is correct.");
        }

        this.publicKey = parsePublicKey(jwt.getPublicKey());
        this.issuer = jwt.getIssuer();
        this.audience = jwt.getAudience();

        log.info("JWT verification configured: RS256, issuer={}, audience={}", issuer, audience);
    }

    /**
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

    /** The stable auth-service user id. The key every ownership check uses. */
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