package com.nexaai.ai.exception;

public class AiRateLimitedException extends AiException {
    public AiRateLimitedException(String provider, Throwable cause) {
        super("AI_RATE_LIMITED", 429, true,
                "Provider " + provider + " is rate limiting this request. Retry with backoff.", cause);
    }
}
