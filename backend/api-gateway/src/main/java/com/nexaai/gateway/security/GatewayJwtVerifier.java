package com.nexaai.gateway.security;

import com.nexaai.gateway.config.GatewayProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Verifies the RS256 access tokens that auth-service issued.
 *
 * <p><strong>Verification only.</strong> This component holds a public key and has no signing
 * key, so it cannot mint a token. That is deliberate and is the reason the gateway is not a
 * second authentication authority.
 *
 * <p><strong>Duplicated from auth-service and user-service on purpose.</strong> The same ~40
 * lines now exist in three services. A shared jar would make all three depend on one artifact,
 * which is the disguised-monolith path this architecture exists to avoid
 * ({@code docs/RULES.md} §2). Verification code is the least harmful thing to duplicate: it
 * has no state and no database.
 *
 * <p><strong>Algorithm pinning.</strong> The algorithm is supplied here, never read from the
 * token. That is what rejects {@code alg: none}, and it is what rejects an HS256 token signed
 * with the RSA public key — the algorithm-confusion attack, which would otherwise let an
 * attacker mint an ADMIN token.
 */
@Component
public class GatewayJwtVerifier {

    private static final Logger log = LoggerFactory.getLogger(GatewayJwtVerifier.class);

    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_EMAIL = "email";

    private final PublicKey publicKey;
    private final String issuer;
    private final String audience;

    public GatewayJwtVerifier(GatewayProperties properties) {
        GatewayProperties.Jwt jwt = properties.getJwt();

        if (jwt.getPublicKey() == null || jwt.getPublicKey().isBlank()) {
            throw new IllegalStateException(
                    "No verification key configured. Set NEXA_GATEWAY_JWT_PUBLIC_KEY with the "
                    + "PEM-encoded RS256 public key matching auth-service's signing key. The "
                    + "gateway verifies tokens and is issued none, so failing here is correct.");
        }

        this.publicKey = parsePublicKey(jwt.getPublicKey());
        this.issuer = jwt.getIssuer();
        this.audience = jwt.getAudience();

        log.info("JWT verification configured: RS256, issuer={}, audience={}", issuer, audience);
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

    public String emailOf(Claims claims) {
        return claims.get(CLAIM_EMAIL, String.class);
    }

    /** The roles the verified token carries. */
    public List<String> rolesOf(Claims claims) {
        Object roles = claims.get(CLAIM_ROLES);
        if (!(roles instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object role : iterable) {
            if (role != null) {
                result.add(role.toString());
            }
        }
        return result;
    }

    public boolean isAdmin(Claims claims) {
        return rolesOf(claims).contains("ADMIN");
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