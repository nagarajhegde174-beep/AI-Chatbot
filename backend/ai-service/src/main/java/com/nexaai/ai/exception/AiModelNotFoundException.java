package com.nexaai.ai.exception;

public class AiModelNotFoundException extends AiException {
    public AiModelNotFoundException(String model, String provider) {
        super("AI_MODEL_NOT_FOUND", 404, false,
                "Model '" + model + "' is not available for provider " + provider + ".");
    }
}
