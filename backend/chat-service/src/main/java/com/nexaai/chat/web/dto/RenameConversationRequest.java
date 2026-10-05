package com.nexaai.chat.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A rename.
 *
 * <p>The title must be present and non-blank: renaming a conversation to nothing is not a rename,
 * it is a delete with extra steps and no confirmation.
 */
public record RenameConversationRequest(
        @NotBlank(message = "title is required")
        @Size(max = 200, message = "title must be at most 200 characters")
        String title) {
}