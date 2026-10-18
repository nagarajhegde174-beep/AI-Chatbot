package com.nexaai.gateway.security;

import com.nexaai.gateway.config.JwtVerifierProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

/**
 * Verifies RS256 JWT access tokens at the API Gateway edge.
 *
 * Rules (SERVICE_CONTRACTS.md §5.3, RULES.md §10):
 * - alg must be RS256 (JJWT verifyWith(PublicKey) enforces this; alg:none and HS256 are rejected)
 * - iss must be nexa-auth-service
 * - aud must include nexaai-web
 * - exp must be in the future
 * - status must be ACTIVE
 */
@Component
public class JwtVerifier {

    private static final Logger log = LoggerFactory.getLogger(JwtVerifier.class);

    private final JwtVerifierProperties props;
    private PublicKey publicKey;

    public JwtVerifier(JwtVerifierProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        if (props.getPublicKey() != null && !props.getPublicKey().isBlank()) {
            this.publicKey = loadPemPublicKey(props.getPublicKey());
            log.info("JwtVerifier: loaded RS256 public key from configuration");
        } else {
            log.info("JwtVerifier: no public key configured; will require injection for tests or JWKS lookup");
        }
    }

    public Claims validateAndGetClaims(String token) {
        if (publicKey == null) {
            throw new JwtVerificationException("GATEWAY_AUTH_UNAVAILABLE", "Token verification is not configured on Gateway.");
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .requireIssuer(props.getIssuer())
                    .requireAudience(props.getAudience())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String status = claims.get("status", String.class);
            if (!"ACTIVE".equals(status)) {
                throw new JwtAccountSuspendedException("Account status is " + status + "; access denied.");
            }
            return claims;
        } catch (JwtVerificationException e) {
            throw e;
        } catch (JwtException | IllegalArgumentException e) {
            throw new JwtVerificationException("GATEWAY_INVALID_TOKEN", "Invalid, expired, or malformed JWT token.");
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(Claims claims) {
        List<String> roles = claims.get("roles", List.class);
        return roles != null ? roles : List.of("USER");
    }

    public String getUserId(Claims claims) {
        return claims.getSubject();
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    private PublicKey loadPemPublicKey(String pem) {
        try {
            String stripped = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replace("-----BEGIN RSA PUBLIC KEY-----", "")
                    .replace("-----END RSA PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] decoded = Base64.getDecoder().decode(stripped);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load RSA public key: " + e.getMessage(), e);
        }
    }

    public static class JwtVerificationException extends RuntimeException {
        private final String code;
        public JwtVerificationException(String code, String message) {
            super(message);
            this.code = code;
        }
        public String getCode() {
            return code;
        }
    }

    public static class JwtAccountSuspendedException extends JwtVerificationException {
        public JwtAccountSuspendedException(String message) {
            super("GATEWAY_ACCOUNT_SUSPENDED", message);
        }
    }
}
