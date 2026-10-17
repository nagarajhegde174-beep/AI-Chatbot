package com.nexaai.auth.dto;

import com.nexaai.auth.dto.request.ChangePasswordRequest;
import com.nexaai.auth.dto.request.ForgotPasswordRequest;
import com.nexaai.auth.dto.request.LoginRequest;
import com.nexaai.auth.dto.request.RegisterRequest;
import com.nexaai.auth.dto.request.ResetPasswordRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationRulesTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testValidRegisterRequest() {
        RegisterRequest req = new RegisterRequest("valid.user@example.com", "Password123!", "Ada Lovelace");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(req);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testInvalidEmailInRegisterRequest() {
        RegisterRequest req = new RegisterRequest("not-an-email", "Password123!", "Ada");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());
    }

    @Test
    void testWeakPasswordFailsRegister() {
        // Missing uppercase and special char
        RegisterRequest req = new RegisterRequest("valid@example.com", "weakpass1", "Ada");
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty());

        // Too short (< 8 chars)
        RegisterRequest req2 = new RegisterRequest("valid@example.com", "P@ss1", "Ada");
        Set<ConstraintViolation<RegisterRequest>> violations2 = validator.validate(req2);
        assertFalse(violations2.isEmpty());
    }

    @Test
    void testLoginRequestValidation() {
        LoginRequest valid = new LoginRequest("user@example.com", "any-password");
        assertTrue(validator.validate(valid).isEmpty());

        LoginRequest invalidEmail = new LoginRequest("invalid-email", "any-password");
        assertFalse(validator.validate(invalidEmail).isEmpty());

        LoginRequest blankPassword = new LoginRequest("user@example.com", "");
        assertFalse(validator.validate(blankPassword).isEmpty());
    }

    @Test
    void testForgotPasswordValidation() {
        ForgotPasswordRequest valid = new ForgotPasswordRequest("user@example.com");
        assertTrue(validator.validate(valid).isEmpty());

        ForgotPasswordRequest invalid = new ForgotPasswordRequest("not-an-email");
        assertFalse(validator.validate(invalid).isEmpty());
    }

    @Test
    void testResetPasswordValidation() {
        ResetPasswordRequest valid = new ResetPasswordRequest("token-123", "NewPassword123!");
        assertTrue(validator.validate(valid).isEmpty());

        ResetPasswordRequest weak = new ResetPasswordRequest("token-123", "weak");
        assertFalse(validator.validate(weak).isEmpty());
    }

    @Test
    void testChangePasswordValidation() {
        ChangePasswordRequest valid = new ChangePasswordRequest("OldPass123!", "NewP@ssw0rd123!");
        assertTrue(validator.validate(valid).isEmpty());

        ChangePasswordRequest weak = new ChangePasswordRequest("OldPass123!", "weak");
        assertFalse(validator.validate(weak).isEmpty());
    }
}
