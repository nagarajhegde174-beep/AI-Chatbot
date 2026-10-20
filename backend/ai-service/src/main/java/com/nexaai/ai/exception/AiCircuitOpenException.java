package com.nexaai.ai.exception;

public class AiCircuitOpenException extends AiException {
    public AiCircuitOpenException(String provider) {
        super("AI_CIRCUIT_OPEN", 503, true,
                "Provider " + provider + " is currently unavailable. Try again later.");
    }
}
