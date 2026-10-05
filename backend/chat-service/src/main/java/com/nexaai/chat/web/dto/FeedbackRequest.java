package com.nexaai.chat.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A thumbs up or down, optionally with a comment.
 *
 * <p>Binary on purpose. A one-to-five scale invites the question "what does 3 mean" and gets
 * answered inconsistently, so the data ends up unusable for the thing it was collected for.
 */
public record FeedbackRequest(
        @NotBlank(message = "rating is required and must be UP or DOWN")
        String rating,

        @Size(max = 1000, message = "comment must be at most 1000 characters")
        String comment) {
}