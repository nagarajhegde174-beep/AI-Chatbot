package com.nexaai.document.exception;

import org.springframework.http.HttpStatus;

public class FileValidationException extends DocumentException {

    public FileValidationException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }

    public static FileValidationException tooLarge(long maxBytes) {
        return new FileValidationException(
                "DOCUMENT_TOO_LARGE",
                "File size exceeds maximum allowed limit of " + (maxBytes / 1024 / 1024) + " MB.",
                HttpStatus.PAYLOAD_TOO_LARGE
        );
    }

    public static FileValidationException unsupportedType(String contentType) {
        return new FileValidationException(
                "DOCUMENT_UNSUPPORTED_TYPE",
                "Unsupported file type: " + contentType + ". Supported types are PDF, DOCX, TXT, Markdown, and CSV.",
                HttpStatus.UNSUPPORTED_MEDIA_TYPE
        );
    }

    public static FileValidationException contentMismatch(String reason) {
        return new FileValidationException(
                "DOCUMENT_CONTENT_MISMATCH",
                "File content does not match its claimed type: " + reason,
                HttpStatus.UNSUPPORTED_MEDIA_TYPE
        );
    }

    public static FileValidationException malformed(String message) {
        return new FileValidationException(
                "DOCUMENT_MALFORMED",
                message,
                HttpStatus.BAD_REQUEST
        );
    }
}
