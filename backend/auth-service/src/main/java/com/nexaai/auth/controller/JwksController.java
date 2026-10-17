package com.nexaai.auth.controller;

import com.nexaai.auth.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "JWKS", description = "Public JWK Set endpoint for RS256 token verification")
public class JwksController {

    private final JwtTokenProvider tokenProvider;

    public JwksController(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @GetMapping("/.well-known/jwks.json")
    @Operation(summary = "Get JSON Web Key Set (JWKS)", description = "Exposes RS256 public keys for downstream token verifiers.")
    public ResponseEntity<Map<String, Object>> getJwks() {
        RSAPublicKey publicKey = tokenProvider.getPublicKey();
        String n = Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getModulus().toByteArray());
        String e = Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getPublicExponent().toByteArray());

        Map<String, Object> jwk = Map.of(
                "kty", "RSA",
                "alg", "RS256",
                "use", "sig",
                "kid", tokenProvider.getKeyId(),
                "n", n,
                "e", e
        );

        return ResponseEntity.ok(Map.of("keys", List.of(jwk)));
    }
}
