package com.nexaai.ai.exception;

public class AiTimeoutException extends AiException {
    public AiTimeoutException(String provider, Throwable cause) {
        super("AI_TIMEOUT", 504, true,
                "Provider " + provider + " did not respond within the deadline.", cause);
    }
}
