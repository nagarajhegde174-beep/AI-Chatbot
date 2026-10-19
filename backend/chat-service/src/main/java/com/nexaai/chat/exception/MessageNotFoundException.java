package com.nexaai.chat.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class MessageNotFoundException extends ChatException {

    public MessageNotFoundException(UUID id) {
        super("MESSAGE_NOT_FOUND", "Message not found with id: " + id, HttpStatus.NOT_FOUND);
    }
}
