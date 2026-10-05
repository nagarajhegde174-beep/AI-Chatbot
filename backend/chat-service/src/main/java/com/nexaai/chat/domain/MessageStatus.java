package com.nexaai.chat.domain;

/**
 * Where a message is in its lifecycle.
 *
 * <p>The existence of PENDING is the important design decision. The assistant row is created
 * <em>before</em> generation is attempted. Without it, an interrupted stream, a crashed process or
 * a killed pod leaves the user with a question and no reply and no indication that anything
 * happened. With it, the UI can render "still thinking", and a later phase can reconcile rows
 * stuck in PENDING.
 */
public enum MessageStatus {
    /** Content is final. */
    COMPLETE,
    /** Placeholder written, generation not yet finished. */
    PENDING,
    /** Generation was attempted and did not finish. The reason is recorded. */
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETE || this == FAILED;
    }
}