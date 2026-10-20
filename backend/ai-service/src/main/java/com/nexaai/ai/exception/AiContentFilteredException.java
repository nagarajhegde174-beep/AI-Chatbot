package com.nexaai.ai.exception;

public class AiContentFilteredException extends AiException {
    public AiContentFilteredException(String provider) {
        super("AI_CONTENT_FILTERED", 422, false,
                "Provider " + provider + " refused the content.");
    }
}
