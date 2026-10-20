package com.nexaai.ai.exception;

public class AiProviderException extends AiException {
    public AiProviderException(String provider, Throwable cause) {
        super("AI_PROVIDER_ERROR", 502, true,
                "Provider " + provider + " returned an error.", cause);
    }
}
