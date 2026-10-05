package com.nexaai.user.domain;

import java.util.Locale;

/**
 * Email normalisation, matching auth-service exactly.
 *
 * <p>The domain part is lower-cased; the local part is trimmed. This duplicates auth-service's
 * rule on purpose: a user whose email is normalised differently in the two services would get
 * a confusing "email already registered" from one and a "not found" from the other.
 *
 * <p>Duplicating this five-line method is not shared business logic. Sharing it would mean
 * both services depend on the same module, which is the disguised-monolith path
 * ({@code docs/ARCHITECTURE.md} §13, decision 013). A duplicated normalisation rule is a far
 * smaller problem than a shared jar.
 */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String raw) {
        return raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
    }
}