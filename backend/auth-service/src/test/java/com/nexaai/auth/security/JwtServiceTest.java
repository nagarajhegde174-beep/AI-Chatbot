package com.nexaai.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexaai.auth.config.AuthProperties;
import com.nexaai.auth.domain.AuthUser;
import com.nexaai.auth.domain.Role;
import com.nexaai.auth.support.TestKeys;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * JWT issuance and verification, including the attacks the design exists to defeat.
 *
 * <p>These are the tests that matter most in this service: a verifier that is subtly wrong
 * accepts tokens it should refuse, and nothing else in the system would notice.
 */
class JwtServiceTest {

    private static AuthProperties propertiesWith(TestKeys.TestKeyMaterial material) {
        AuthProperties properties = new AuthProperties();
        properties.getJwt().setPrivateKey(material.privateKeyPem());
        properties.getJwt().setPublicKey(material.publicKeyPem());
        properties.getJwt().setIssuer("nexa-auth-service");
        properties.getJwt().setAudience("nexaai-web");
        return properties;
    }

    private static AuthUser aUser() {
        AuthUser user = AuthUser.register("ada@example.com", "hash", "Ada");
        user.verifyEmail();
        return user;
    }

    @Nested
    @DisplayName("issuance")
    class Issuance {

        @Test
        @DisplayName("a signed token verifies and carries the expected claims")
        void issuesAndVerifies() {
            TestKeys.TestKeyMaterial material = TestKeys.material();
            JwtService jwt = new JwtService(propertiesWith(material));

            var issued = jwt.issue(aUser());
            Claims claims = jwt.verify(issued.token());

            assertThat(claims.getSubject()).isNotBlank();
            assertThat(claims.getIssuer()).isEqualTo("nexa-auth-service");
            assertThat(claims.getAudience()).contains("nexaai-web");
            assertThat(claims.getId()).isEqualTo(issued.jti());
            assertThat(claims.get("roles", List.class)).containsExactly("USER");
            assertThat(claims.get("status", String.class)).isEqualTo("ACTIVE");
            assertThat(claims.getExpiration()).isAfter(new Date());
        }

        @Test
        @DisplayName("the credentials version travels with the token")
        void carriesCredentialsVersion() {
            TestKeys.TestKeyMaterial material = TestKeys.material();
            JwtService jwt = new JwtService(propertiesWith(material));
            AuthUser user = aUser();

            Claims before = jwt.verify(jwt.issue(user).token());
            user.changePasswordHash("new-hash");
            Claims after = jwt.verify(jwt.issue(user).token());

            // Without this claim, changing a password would not invalidate existing sessions.
            assertThat(after.get("cv", Integer.class)).isGreaterThan(before.get("cv", Integer.class));
        }

        @Test
        @DisplayName("the header names the algorithm RS256")
        void algorithmIsRs256() {
            TestKeys.TestKeyMaterial material = TestKeys.material();
            JwtService jwt = new JwtService(propertiesWith(material));

            String token = jwt.issue(aUser()).token();
            String header = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[0]));

            assertThat(header).contains("RS256");
        }
    }

    @Nested
    @DisplayName("rejection")
    class Rejection {

        @Test
        @DisplayName("a token signed with a different key is rejected")
        void rejectsForeignSignature() {
            TestKeys.TestKeyMaterial mine = TestKeys.material();
            TestKeys.TestKeyMaterial theirs = TestKeys.material();

            JwtService issuer = new JwtService(propertiesWith(theirs));
            JwtService verifier = new JwtService(propertiesWith(mine));

            String token = issuer.issue(aUser()).token();

            assertThatThrownBy(() -> verifier.verify(token)).isNotNull();
        }

        @Test
        @DisplayName("alg=none is rejected")
        void rejectsAlgNone() {
            // The classic JWT bypass. A verifier that trusts the token's own alg header can be
            // handed an unsigned token and accept it.
            TestKeys.TestKeyMaterial material = TestKeys.material();
            JwtService jwt = new JwtService(propertiesWith(material));

            String unsigned = Jwts.builder()
                    .subject("9f1c7b2e-4a3d-4c8f-b1e2-5d7a9c3e0f14")
                    .issuer("nexa-auth-service")
                    .audience().add("nexaai-web").and()
                    .expiration(Date.from(java.time.Instant.now().plusSeconds(600)))
                    .compact();

            assertThatThrownBy(() -> jwt.verify(unsigned)).isNotNull();
        }

        @Test
        @DisplayName("an HS256 token signed with the public key is rejected")
        void rejectsAlgorithmConfusion() {
            // The public key is public. If the verifier would accept HMAC, anyone could forge
            // tokens with it. Pinning the algorithm is what prevents this.
            TestKeys.TestKeyMaterial material = TestKeys.material();
            JwtService jwt = new JwtService(propertiesWith(material));

            var hmacKey = new SecretKeySpec(material.publicKey().getEncoded(), "HmacSHA256");
            String forged = Jwts.builder()
                    .subject("9f1c7b2e-4a3d-4c8f-b1e2-5d7a9c3e0f14")
                    .issuer("nexa-auth-service")
                    .audience().add("nexaai-web").and()
                    .expiration(Date.from(java.time.Instant.now().plusSeconds(600)))
                    .signWith(hmacKey, Jwts.SIG.HS256)
                    .compact();

            assertThatThrownBy(() -> jwt.verify(forged)).isNotNull();
        }

        @Test
        @DisplayName("an expired token is rejected")
        void rejectsExpired() {
            TestKeys.TestKeyMaterial material = TestKeys.material();
            AuthProperties properties = propertiesWith(material);
            properties.getToken().setAccessTokenTtl(Duration.ofSeconds(-1));
            JwtService jwt = new JwtService(properties);

            assertThatThrownBy(() -> jwt.verify(jwt.issue(aUser()).token())).isNotNull();
        }

        @Test
        @DisplayName("a token with the wrong audience is rejected")
        void rejectsWrongAudience() {
            TestKeys.TestKeyMaterial material = TestKeys.material();
            JwtService jwt = new JwtService(propertiesWith(material));

            String foreign = Jwts.builder()
                    .issuer("nexa-auth-service")
                    .audience().add("some-other-app").and()
                    .subject("9f1c7b2e-4a3d-4c8f-b1e2-5d7a9c3e0f14")
                    .expiration(Date.from(java.time.Instant.now().plusSeconds(600)))
                    .signWith(material.privateKey(), Jwts.SIG.RS256)
                    .compact();

            assertThatThrownBy(() -> jwt.verify(foreign)).isNotNull();
        }

        @Test
        @DisplayName("a token with the wrong issuer is rejected")
        void rejectsWrongIssuer() {
            TestKeys.TestKeyMaterial material = TestKeys.material();
            JwtService jwt = new JwtService(propertiesWith(material));

            String foreign = Jwts.builder()
                    .issuer("someone-else")
                    .audience().add("nexaai-web").and()
                    .subject("9f1c7b2e-4a3d-4c8f-b1e2-5d7a9c3e0f14")
                    .expiration(Date.from(java.time.Instant.now().plusSeconds(600)))
                    .signWith(material.privateKey(), Jwts.SIG.RS256)
                    .compact();

            assertThatThrownBy(() -> jwt.verify(foreign)).isNotNull();
        }

        @Test
        @DisplayName("a garbled token is rejected")
        void rejectsGarbage() {
            TestKeys.TestKeyMaterial material = TestKeys.material();
            JwtService jwt = new JwtService(propertiesWith(material));

            assertThatThrownBy(() -> jwt.verify("not-a-jwt")).isNotNull();
        }
    }

    @Nested
    @DisplayName("startup safety")
    class StartupSafety {

        @Test
        @DisplayName("a missing private key fails at startup, not at first sign-in")
        void missingKeyFailsFast() {
            // A missing signing key is a deployment error. Discovering it on the first request
            // would mean every sign-in failing in production, not a clean startup failure.
            AuthProperties properties = new AuthProperties();
            properties.getJwt().setPrivateKey("");
            properties.getJwt().setPublicKey("x");

            assertThatThrownBy(() -> new JwtService(properties))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("private key");
        }

        @Test
        @DisplayName("a mismatched key pair fails at startup")
        void mismatchedPairFailsFast() {
            AuthProperties properties = new AuthProperties();
            properties.getJwt().setPrivateKey(TestKeys.material().privateKeyPem());
            properties.getJwt().setPublicKey(TestKeys.material().publicKeyPem());

            // Two unrelated pairs. Without the startup probe this would only fail at the first
            // real sign-in, which is the worst moment to discover it.
            assertThatThrownBy(() -> new JwtService(properties))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not a pair");
        }

        @Test
        @DisplayName("a malformed key is reported clearly")
        void malformedKeyIsReported() {
            AuthProperties properties = new AuthProperties();
            properties.getJwt().setPrivateKey("not a pem at all");
            properties.getJwt().setPublicKey(TestKeys.material().publicKeyPem());

            assertThatThrownBy(() -> new JwtService(properties)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("refresh token hashing")
    class RefreshHashing {

        @Test
        @DisplayName("hashing is deterministic so a token can be looked up")
        void hashIsDeterministic() {
            assertThat(RefreshTokenService.hash("abc")).isEqualTo(RefreshTokenService.hash("abc"));
            assertThat(RefreshTokenService.hash("abc")).isNotEqualTo(RefreshTokenService.hash("abd"));
            assertThat(RefreshTokenService.hash("abc")).hasSize(64);
        }

        @Test
        @DisplayName("the hash never equals the token")
        void hashDiffersFromToken() {
            // Storing the hash rather than the token is what makes a database dump useless for
            // session hijacking.
            String token = "a-very-secret-refresh-token";
            assertThat(RefreshTokenService.hash(token)).isNotEqualTo(token);
        }
    }
}
