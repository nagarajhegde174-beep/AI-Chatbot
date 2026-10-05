package com.nexaai.chat.domain;

/** Thumbs up or down. A binary opinion, not a score. */
public enum FeedbackRating {
    UP,
    DOWN;

    /** The opposite rating, for toggling. */
    public FeedbackRating opposite() {
        return this == UP ? DOWN : UP;
    }
}