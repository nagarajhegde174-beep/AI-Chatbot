package com.nexaai.ai;

import com.nexaai.ai.dto.CompletionRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates that the CompletionRequest constraints are correctly declared.
 * Uses the Jakarta Validation API directly — no Spring context needed.
 */
@DisplayName("CompletionRequest — validation constraint tests")
class CompletionRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("Valid request passes all constraints")
    void valid_request_has_no_violations() {
        var req = new CompletionRequest(
                "OPENAI", "gpt-4o-mini", null,
                List.of(new CompletionRequest.CompletionMessage("USER", "Hello")),
                0.7, 2048, UUID.randomUUID().toString()
        );
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    @DisplayName("null maxOutputTokens fails @NotNull")
    void null_max_tokens_fails() {
        var req = new CompletionRequest(
                "OPENAI", "gpt-4o-mini", null,
                List.of(new CompletionRequest.CompletionMessage("USER", "Hello")),
                0.7, null, UUID.randomUUID().toString()
        );
        Set<ConstraintViolation<CompletionRequest>> violations = validator.validate(req);
        assertThat(violations).isNotEmpty();
        assertThat(violations.stream().map(v -> v.getPropertyPath().toString()))
                .contains("maxOutputTokens");
    }

    @Test
    @DisplayName("Empty messages list fails @NotEmpty")
    void empty_messages_fails() {
        var req = new CompletionRequest(
                "OPENAI", "gpt-4o-mini", null,
                Collections.emptyList(),
                0.7, 2048, UUID.randomUUID().toString()
        );
        Set<ConstraintViolation<CompletionRequest>> violations = validator.validate(req);
        assertThat(violations).isNotEmpty();
        assertThat(violations.stream().map(v -> v.getPropertyPath().toString()))
                .contains("messages");
    }

    @Test
    @DisplayName("Blank model fails @NotBlank")
    void blank_model_fails() {
        var req = new CompletionRequest(
                "OPENAI", "  ", null,
                List.of(new CompletionRequest.CompletionMessage("USER", "Hello")),
                null, 2048, UUID.randomUUID().toString()
        );
        Set<ConstraintViolation<CompletionRequest>> violations = validator.validate(req);
        assertThat(violations).isNotEmpty();
        assertThat(violations.stream().map(v -> v.getPropertyPath().toString()))
                .contains("model");
    }

    @Test
    @DisplayName("null requestId fails @NotBlank")
    void null_request_id_fails() {
        var req = new CompletionRequest(
                "OPENAI", "gpt-4o-mini", null,
                List.of(new CompletionRequest.CompletionMessage("USER", "Hello")),
                null, 2048, null
        );
        Set<ConstraintViolation<CompletionRequest>> violations = validator.validate(req);
        assertThat(violations).isNotEmpty();
        assertThat(violations.stream().map(v -> v.getPropertyPath().toString()))
                .contains("requestId");
    }

    @Test
    @DisplayName("maxOutputTokens > 32768 fails @Max")
    void max_tokens_above_limit_fails() {
        var req = new CompletionRequest(
                "OPENAI", "gpt-4o-mini", null,
                List.of(new CompletionRequest.CompletionMessage("USER", "Hello")),
                null, 100_000, UUID.randomUUID().toString()
        );
        Set<ConstraintViolation<CompletionRequest>> violations = validator.validate(req);
        assertThat(violations).isNotEmpty();
        assertThat(violations.stream().map(v -> v.getPropertyPath().toString()))
                .contains("maxOutputTokens");
    }

    @Test
    @DisplayName("temperature above 2 fails @Max")
    void temperature_above_max_fails() {
        var req = new CompletionRequest(
                "OPENAI", "gpt-4o-mini", null,
                List.of(new CompletionRequest.CompletionMessage("USER", "Hello")),
                3.0, 2048, UUID.randomUUID().toString()
        );
        Set<ConstraintViolation<CompletionRequest>> violations = validator.validate(req);
        assertThat(violations).isNotEmpty();
        assertThat(violations.stream().map(v -> v.getPropertyPath().toString()))
                .contains("temperature");
    }
}
