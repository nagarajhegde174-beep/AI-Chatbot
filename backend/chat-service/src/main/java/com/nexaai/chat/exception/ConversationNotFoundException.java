package com.nexaai.chat.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ConversationNotFoundException extends ChatException {

    public ConversationNotFoundException(UUID id) {
        super("CONVERSATION_NOT_FOUND", "Conversation not found with id: " + id, HttpStatus.NOT_FOUND);
    }
}
