package com.nexaai.auth.domain;

import java.util.Locale;

/**
 * Normalises email addresses so uniqueness is a real guarantee.
 *
 * <p>The domain part is lower-cased; the local part is left as typed apart from trimming.
 *
 * <p>Lower-casing the domain is safe because domains are case-insensitive by definition
 * (RFC 1035). Lower-casing the local part is <em>not</em> strictly safe: RFC 5321 allows it
 * to be case-sensitive. It is done anyway because every mainstream provider treats it
 * case-insensitively, and a user who registers {@code User@x.com} and cannot later sign in
 * as {@code user@x.com} is a worse outcome than the theoretical ambiguity.
 */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}