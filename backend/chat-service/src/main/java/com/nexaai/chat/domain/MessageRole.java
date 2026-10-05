package com.nexaai.chat.domain;

/** Who produced a message. */
public enum MessageRole {
    /** Written by the account holder. */
    USER,
    /** Produced by the model. Created as a PENDING placeholder before generation runs. */
    ASSISTANT,
    /**
     * Reserved for future context and prompt injection.
     *
     * <p>Not writable through the public API in this phase. A caller who could inject a SYSTEM
     * message could place text the model treats as instructions, which is prompt injection with
     * an HTTP request.
     */
    SYSTEM
}