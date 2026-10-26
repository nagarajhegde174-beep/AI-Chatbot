package com.nexaai.document.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class DocumentNotFoundException extends DocumentException {

    public DocumentNotFoundException(UUID documentId) {
        super("DOCUMENT_NOT_FOUND", "Document not found.", HttpStatus.NOT_FOUND);
    }
}
