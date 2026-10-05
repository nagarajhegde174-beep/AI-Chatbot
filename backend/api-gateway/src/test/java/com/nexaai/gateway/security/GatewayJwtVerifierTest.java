package com.nexaai.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexaai.gateway.config.GatewayProperties;
import com.nexaai.gateway.support.GatewayTestTokens;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The verifier on its own, with no HTTP and no Spring context.
 *
 * <p>Exists so a failure in the edge tests points at the verifier or at the plumbing, rather
 * than leaving both suspected.
 */
class GatewayJwtVerifierTest {

    private static GatewayJwtVerifier verifierFor(String publicKeyPem) {
        GatewayProperties properties = new GatewayProperties();
        properties.getJwt().setPublicKey(publicKeyPem);
        properties.getJwt().setIssuer("nexa-auth-service");
        properties.getJwt().setAudience("nexaai-web");
        return new GatewayJwtVerifier(properties);
    }

    private static GatewayJwtVerifier verifier() {
        return verifierFor(GatewayTestTokens.publicKeyPem());
    }

    @Test
    @DisplayName("accepts a token it signed the key for")
    void acceptsValidToken() {
        UUID subject = UUID.randomUUID();
        Claims claims = verifier().verify(GatewayTestTokens.userToken(subject));

        assertThat(claims.getSubject()).isEqualTo(subject.toString());
    }

    @Test
    @DisplayName("extracts the roles")
    void extractsRoles() {
        GatewayJwtVerifier verifier = verifier();

        assertThat(verifier.rolesOf(verifier.verify(GatewayTestTokens.adminToken())))
                .contains("ADMIN");
        assertThat(verifier.isAdmin(verifier.verify(GatewayTestTokens.adminToken()))).isTrue();
        assertThat(verifier.isAdmin(verifier.verify(GatewayTestTokens.userToken()))).isFalse();
    }

    @Test
    @DisplayName("refuses every unusable token")
    void refusesBadTokens() {
        GatewayJwtVerifier verifier = verifier();

        for (String token : new String[]{
                GatewayTestTokens.expiredToken(),
                GatewayTestTokens.wrongIssuerToken(),
                GatewayTestTokens.wrongAudienceToken(),
                GatewayTestTokens.unsignedToken(),
                GatewayTestTokens.algorithmConfusionToken(),
                GatewayTestTokens.foreignKeyToken(),
                GatewayTestTokens.garbageToken()}) {
            assertThatThrownBy(() -> verifier.verify(token))
                    .as("must refuse: %s", token.substring(0, Math.min(20, token.length())))
                    .isInstanceOf(JwtException.class);
        }
    }

    @Test
    @DisplayName("refuses to start without a verification key")
    void refusesToStartWithoutAKey() {
        // Failing at startup is the correct behaviour. The alternative is a gateway that
        // starts and then accepts whatever it is given.
        assertThatThrownBy(() -> verifierFor(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NEXA_GATEWAY_JWT_PUBLIC_KEY");

        assertThatThrownBy(() -> verifierFor("   "))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("refuses to start on an unparseable key")
    void refusesToStartOnAnUnparseableKey() {
        assertThatThrownBy(() -> verifierFor("-----BEGIN PUBLIC KEY-----\nnope\n"
                + "-----END PUBLIC KEY-----"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RS256 public key");
    }
}