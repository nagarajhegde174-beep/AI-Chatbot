package com.nexaai.ai.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A generation request.
 *
 * <p><strong>There is deliberately no {@code systemMessage} field.</strong> A caller who could
 * set the system message would be instructing the model directly, which is prompt injection with
 * an HTTP parameter. System instructions come from this service, if at all.
 *
 * @param model            optional. Absent means the configured default, named explicitly so
 *                         behaviour does not change when the catalog is reordered
 * @param temperature      optional, and refused for a model that does not support it. Sent to a
 *                         model that ignores it, it would appear to work while having no effect
 * @param maxOutputTokens  optional, clamped to the model's limit and the platform ceiling
 */
public record GenerateRequest(
        @Size(max = 128)
        String model,

        @NotBlank(message = "message is required and must not be blank")
        @Size(max = 320_000, message = "message must be at most 320000 characters")
        String message,

        @Size(max = 32, message = "conversationId must be at most 32 characters")
        String conversationId,

        @Min(0)
        @Max(2)
        Double temperature,

        @Min(1)
        @Max(8192)
        Integer maxOutputTokens) {

    public String modelOrNull() {
        return (model == null || model.isBlank()) ? null : model;
    }
}
