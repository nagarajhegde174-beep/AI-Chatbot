package com.nexaai.chat.domain;

/**
 * Whether a conversation is in the sidebar or put away.
 *
 * <p>Archive is deliberately <em>not</em> delete. Deleting removes the user's history, which is
 * a destructive action for a feature whose real purpose is "get this out of my sidebar". The two
 * need to be separately reversible, and only one of them is.
 */
public enum ConversationStatus {
    ACTIVE,
    ARCHIVED;

    public boolean isArchived() {
        return this == ARCHIVED;
    }
}