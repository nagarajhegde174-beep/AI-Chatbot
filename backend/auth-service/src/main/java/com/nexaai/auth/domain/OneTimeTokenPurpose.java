package com.nexaai.auth.domain;

/** What a one-time token authorises. Prevents a verification token being replayed as a reset. */
public enum OneTimeTokenPurpose {
    /** Confirms control of an email address. */
    EMAIL_VERIFICATION,
    /** Authorises a password reset. */
    PASSWORD_RESET
}