package com.nexaai.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHashingTest {

    private BCryptPasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(12);
    }

    @Test
    void testPasswordIsHashedWithSalt() {
        String rawPassword = "SecurePassword123!";
        String hash1 = passwordEncoder.encode(rawPassword);
        String hash2 = passwordEncoder.encode(rawPassword);

        assertNotNull(hash1);
        assertNotNull(hash2);
        // BCrypt uses random salts, so two hashes of the same password must differ
        assertNotEquals(hash1, hash2);
        assertNotEquals(rawPassword, hash1);
        assertTrue(hash1.startsWith("$2a$12$") || hash1.startsWith("$2b$12$"));
    }

    @Test
    void testPasswordMatches() {
        String rawPassword = "SecurePassword123!";
        String hash = passwordEncoder.encode(rawPassword);

        assertTrue(passwordEncoder.matches(rawPassword, hash));
        assertFalse(passwordEncoder.matches("WrongPassword123!", hash));
        assertFalse(passwordEncoder.matches("", hash));
    }
}
