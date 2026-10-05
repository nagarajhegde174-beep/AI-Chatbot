package com.nexaai.chat.web.dto;

import jakarta.validation.constraints.Size;

/**
 * A new conversation.
 *
 * <p>Every field is optional. An empty body creates a conversation titled by its first message,
 * because the frontend's "New chat" button should not have to ask a question before it can
 * create anything.
 */
public record CreateConversationRequest(
        @Size(max = 200, message = "title must be at most 200 characters")
        String title,

        @Size(max = 128, message = "model must be at most 128 characters")
        String model) {

    public String titleOrNull() {
        return (title == null || title.isBlank()) ? null : title;
    }

    public String modelOrNull() {
        return (model == null || model.isBlank()) ? null : model;
    }
}