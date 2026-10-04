package com.nexaai.auth.support;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Generates throwaway RS256 key pairs for tests.
 *
 * <p><strong>Generated, never committed.</strong> A test key checked into the repository is a
 * real key that outlives the test, and "it is only used in tests" is the sentence that
 * precedes every leaked key. Generating a fresh pair per call also means each test exercises
 * the real sign-and-verify path rather than a stub.
 */
public final class TestKeys {

    private TestKeys() {
    }

    /** One key pair, in PEM and parsed form. */
    public record TestKeyMaterial(String privateKeyPem, String publicKeyPem,
                                  PrivateKey privateKey, PublicKey publicKey) {
    }

    /** A fresh, unrelated key pair. */
    public static TestKeyMaterial material() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048, new SecureRandom());
            KeyPair pair = generator.generateKeyPair();

            return new TestKeyMaterial(
                    toPem("PRIVATE KEY", pair.getPrivate().getEncoded()),
                    toPem("PUBLIC KEY", pair.getPublic().getEncoded()),
                    parsePrivate(pair.getPrivate().getEncoded()),
                    parsePublic(pair.getPublic().getEncoded()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA is required by the platform but is unavailable", e);
        }
    }

    private static String toPem(String marker, byte[] der) {
        String body = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der);
        return "-----BEGIN " + marker + "-----\n" + body + "\n-----END " + marker + "-----\n";
    }

    private static PrivateKey parsePrivate(byte[] der) {
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse the generated test private key", e);
        }
    }

    private static PublicKey parsePublic(byte[] der) {
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse the generated test public key", e);
        }
    }
}