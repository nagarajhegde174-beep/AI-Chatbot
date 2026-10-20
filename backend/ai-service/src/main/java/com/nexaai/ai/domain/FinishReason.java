package com.nexaai.ai.domain;

/**
 * Finish reason returned by the provider after token generation ends.
 */
public enum FinishReason {
    STOP,
    LENGTH,
    CONTENT_FILTER,
    ERROR,
    INCOMPLETE
}
