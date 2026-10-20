package com.nexaai.ai.exception;

public class AiNotConfiguredException extends AiException {
    public AiNotConfiguredException(String provider) {
        super("AI_NOT_CONFIGURED", 503, false,
                "Provider " + provider + " is not configured or disabled.");
    }
}
