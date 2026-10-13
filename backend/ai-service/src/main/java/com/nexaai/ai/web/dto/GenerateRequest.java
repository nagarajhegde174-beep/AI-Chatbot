package com.nexaai.ai.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A generation request.
 *
 * <p><strong>Two fields, not one.</strong> {@code message} is the turn being answered;
 * {@code messages} is the context behind it. The server always appends {@code message} as the final
 * turn, so a caller cannot accidentally reorder history, omit the actual question, or send a
 * history whose last turn is an assistant message — all of which produce a confident answer to the
 * wrong question.
 *
 * <p><strong>There is deliberately no {@code systemPrompt} field.</strong> A caller who could set
 * the system instruction would be instructing the model directly, which is prompt injection with an
 * HTTP parameter. System instructions come from this service, if at all.
 *
 * <p><strong>{@code SUMMARY} is a first-class role here.</strong> It carries compressed older
 * context produced by a previous {@code /summarize} call. Treating it as ordinary prose rather than
 * as a distinct role is what makes a compressed conversation read as though the model had simply
 * forgotten the earlier turns, rather than as though it were being told about them.
 *
 * @param model            optional. Absent means the configured default, named explicitly so
 *                         behaviour does not change when the catalog is reordered
 * @param message          the turn being answered. Required, and always the LAST turn sent
 * @param messages         prior context, oldest first. Excludes {@code message}
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

        @Size(max = 200, message = "messages must contain at most 200 entries")
        List<@Valid ContextMessage> messages,

        @Size(max = 32, message = "conversationId must be at most 32 characters")
        String conversationId,

        @Min(0)
        @Max(2)
        Double temperature,

        @Min(1)
        @Max(8192)
        Integer maxOutputTokens) {

    /**
     * The context to send, with the new message last.
     *
     * <p>Blank entries are dropped rather than sent. An empty turn is not "no information", it is
     * a formatting artefact of a history that has been summarised, and sending it produces a model
     * response to nothing.
     */
    /** The requested model, or null when the caller named none. */
    public String modelOrNull() {
        return (model == null || model.isBlank()) ? null : model;
    }

    /** The full turn list to send: prior context, then the new message last. */
    public List<ContextMessage> contextWithMessage() {
        java.util.ArrayList<ContextMessage> context =
                new java.util.ArrayList<>(messages == null ? List.of() : messages);
        context.add(new ContextMessage(ContextMessage.Role.USER, message));
        return context;
    }

    /** Prior context only, which is what token accounting is measured against. */
    public List<ContextMessage> historyOnly() {
        return messages == null ? List.of() : messages;
    }
}