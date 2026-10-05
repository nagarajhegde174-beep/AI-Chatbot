package com.nexaai.chat.exception;

import java.util.UUID;

/**
 * Thrown when a conversation or message that was expected to exist does not.
 *
 * <p><strong>The message deliberately does not distinguish "does not exist" from "not yours".</strong>
 * The caller gets one identical answer either way, because a different answer for each is a
 * reliable oracle for discovering which conversation ids are real. Both cases return 404.
 */
public class ChatResourceNotFoundException extends RuntimeException {

    /** Which kind of resource, so the error code is specific without leaking existence. */
    public enum Kind {
        CONVERSATION,
        MESSAGE,
        FEEDBACK
    }

    private final Kind kind;
    private final UUID requestedId;

    public ChatResourceNotFoundException(Kind kind, UUID requestedId) {
        super("No " + kind.name().toLowerCase(java.util.Locale.ROOT) + " with id " + requestedId);
        this.kind = kind;
        this.requestedId = requestedId;
    }

    public Kind getKind() {
        return kind;
    }

    public UUID getRequestedId() {
        return requestedId;
    }
}