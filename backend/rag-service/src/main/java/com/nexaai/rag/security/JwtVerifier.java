package com.nexaai.rag.security;

import com.nexaai.rag.config.JwtVerifierProperties;
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
            log.info("JwtVerifier: no public key configured — using fallback/test mode");
        }
    }

    public Claims validateAndGetClaims(String token) {
        if (publicKey == null) {
            throw new RuntimeException("Token verification is not configured.");
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
            if (status != null && !"ACTIVE".equals(status)) {
                throw new RuntimeException("Account is not active.");
            }
            return claims;
        } catch (JwtException e) {
            throw new RuntimeException("Invalid or expired token.", e);
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

    public void setPublicKey(PublicKey key) {
        this.publicKey = key;
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
            throw new IllegalStateException("Failed to load JWT public key from config: " + e.getMessage(), e);
        }
    }
}
