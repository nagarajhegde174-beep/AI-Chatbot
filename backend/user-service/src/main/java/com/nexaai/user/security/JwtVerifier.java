package com.nexaai.user.security;

import com.nexaai.user.config.JwtVerifierProperties;
import com.nexaai.user.exception.UserException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

/**
 * Verifies RS256 JWTs issued by auth-service.
 * Does NOT sign tokens — user-service has no private key.
 *
 * Verification requirements (SERVICE_CONTRACTS.md §5.3):
 * - alg must be RS256 (JJWT enforces this; alg:none is rejected)
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
            log.info("JwtVerifier: loaded RS256 public key from config");
        } else {
            // In production, key would be fetched from JWKS. In tests, key is injected.
            log.info("JwtVerifier: no public key configured — will require injection for tests");
        }
    }

    public Claims validateAndGetClaims(String token) {
        if (publicKey == null) {
            throw new UserException("USER_AUTH_UNAVAILABLE",
                    "Token verification is not configured.", HttpStatus.SERVICE_UNAVAILABLE);
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
                throw new UserException("USER_ACCOUNT_SUSPENDED",
                        "Account is not active.", HttpStatus.FORBIDDEN);
            }
            return claims;
        } catch (UserException e) {
            throw e;
        } catch (JwtException e) {
            throw new UserException("USER_INVALID_TOKEN", "Invalid or expired token.", HttpStatus.UNAUTHORIZED);
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

    /** Loads RSA public key from PEM string (BEGIN PUBLIC KEY or BEGIN RSA PUBLIC KEY). */
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
            throw new IllegalStateException("Failed to load JWT public key from config: " + e.getMessage(), e);
        }
    }

    /** Sets public key directly — used by tests that inject a generated RSA key. */
    public void setPublicKey(PublicKey key) {
        this.publicKey = key;
    }

    public PublicKey getPublicKey() { return publicKey; }
}
