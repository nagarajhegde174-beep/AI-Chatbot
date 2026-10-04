package com.nexaai.auth.security;

import com.nexaai.auth.config.AuthProperties;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * The single {@link PasswordEncoder} for the service.
 *
 * <p>Having exactly one encoder bean means a hash written by one code path can be verified by
 * every other. Two encoder beans is the classic way a service ends up unable to log in its
 * own users.
 *
 * <p>Argon2id is the default because it is memory-hard: it resists GPU cracking, which bcrypt
 * does not ({@code docs/SECURITY.md} section 2.1).
 */
@Component
public class PasswordEncoderConfig {

    private static final Logger log = LoggerFactory.getLogger(PasswordEncoderConfig.class);

    /** Argon2 version 1.3, the encoding version for Argon2id (0x13 == 19). */
    private static final int ARGON2_VERSION_1_3 = 0x13;

    /** 16 bytes of salt. The encoder generates a fresh salt per hash; this is only its length. */
    private static final int SALT_LENGTH_BYTES = 16;

    private final PasswordEncoder encoder;
    private final String algorithm;

    public PasswordEncoderConfig(AuthProperties properties) {
        String requested = properties.getPassword().getEncoder();
        this.algorithm = requested;

        switch (requested.toLowerCase()) {
            case "argon2" -> this.encoder = buildArgon2(properties);
            case "bcrypt" -> {
                this.encoder = new BCryptPasswordEncoder(12);
                log.info("Password hashing: bcrypt at cost 12");
            }
            default -> throw new IllegalStateException(
                    "Unsupported password encoder '%s'. Use 'argon2' or 'bcrypt'.".formatted(requested));
        }
    }

    private static PasswordEncoder buildArgon2(AuthProperties properties) {
        AuthProperties.Password config = properties.getPassword();

        // Leaving the parameters null uses Spring Security's defaults, which track current
        // OWASP guidance. Pinning them here would silently rot as hardware improves, and a
        // pinned cost that is too low is worse than no explicit value at all.
        if (config.getArgon2Memory() == null
                && config.getArgon2Iterations() == null
                && config.getArgon2Parallelism() == null) {
            log.info("Password hashing: Argon2id with Spring Security default parameters");
            return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        }

        int memory = config.getArgon2Memory() == null ? 65536 : config.getArgon2Memory();
        int iterations = config.getArgon2Iterations() == null ? 3 : config.getArgon2Iterations();
        int parallelism = config.getArgon2Parallelism() == null ? 1 : config.getArgon2Parallelism();

        log.info("Password hashing: Argon2id with explicit parameters (memory={} KiB, iterations={}, parallelism={})",
                memory, iterations, parallelism);

        // Constructor order is (iterations, memory, parallelism, encodingVersion, saltLength).
        // Spring Security 7 exposes only the 5-argument form; the ENCODING_VERSION constant
        // older code referenced is gone, so the value is named here with its meaning.
        return new Argon2PasswordEncoder(iterations, memory, parallelism,
                ARGON2_VERSION_1_3, SALT_LENGTH_BYTES);
    }

    public PasswordEncoder encoder() {
        return encoder;
    }

    /** Exposed as a bean so Spring Security can autowire it into the filter chain. */
    @org.springframework.context.annotation.Bean
    public PasswordEncoder passwordEncoder() {
        return encoder;
    }

    public String algorithm() {
        return algorithm;
    }
}