package com.nexaai.chat.domain;

import java.util.Locale;

/**
 * Builds a conversation title from its first user message.
 *
 * <p>Duplicated in spirit from the email normaliser in the other services: a shared helper
 * module would make several services depend on one artifact, which is the disguised-monolith
 * path ({@code docs/RULES.md} §2). A twenty-line rule duplicated is a far smaller problem than
 * a shared jar.
 */
public final class TitleFromFirstMessage {

    /** Titles are capped by the column, so truncate on a boundary rather than mid-word. */
    private static final int MAX_LENGTH = 80;

    private TitleFromFirstMessage() {
    }

    /**
     * Derives a title from the first line of a message.
     *
     * <p>Never throws and never returns null. A conversation must always have a usable title,
     * because the sidebar renders it before any message exists.
     */
    public static String derive(String messageContent) {
        String fallback = "New conversation";

        if (messageContent == null || messageContent.isBlank()) {
            return fallback;
        }

        // First non-blank line only. A pasted paragraph should not become the title.
        String firstLine = null;
        for (String line : messageContent.split("\\R")) {
            if (!line.isBlank()) {
                firstLine = line.trim();
                break;
            }
        }
        if (firstLine == null) {
            return fallback;
        }

        // Collapse internal whitespace so a wrapped sentence reads as one line.
        String collapsed = firstLine.replaceAll("\\s+", " ").trim();
        if (collapsed.isEmpty()) {
            return fallback;
        }

        if (collapsed.length() <= MAX_LENGTH) {
            return collapsed;
        }

        String clipped = collapsed.substring(0, MAX_LENGTH);
        int lastSpace = clipped.lastIndexOf(' ');
        // Only respect a word boundary if it is not so early that the result is useless.
        if (lastSpace > MAX_LENGTH / 2) {
            clipped = clipped.substring(0, lastSpace);
        }
        return clipped + "…";
    }

    /** Normalises a user-supplied rename. Blank falls back to the derived default. */
    public static String sanitise(String requested, String current) {
        if (requested == null || requested.isBlank()) {
            return current;
        }
        String collapsed = requested.replaceAll("\\s+", " ").trim();
        if (collapsed.isEmpty()) {
            return current;
        }
        return collapsed.length() > 200 ? collapsed.substring(0, 200) : collapsed;
    }

    /** Lower-cases for search predicates. Explicit so the index and the query agree. */
    public static String normaliseForSearch(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}