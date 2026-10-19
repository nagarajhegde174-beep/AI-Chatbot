package com.nexaai.chat.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ConversationAccessDeniedException extends ChatException {

    public ConversationAccessDeniedException(UUID conversationId) {
        super("CONVERSATION_ACCESS_DENIED", "Access denied to conversation: " + conversationId, HttpStatus.FORBIDDEN);
    }
}
