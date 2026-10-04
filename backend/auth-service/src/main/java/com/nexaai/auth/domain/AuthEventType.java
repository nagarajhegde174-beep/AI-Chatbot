package com.nexaai.auth.domain;

/** Authentication events recorded for lockout decisions and the audit trail. */
public enum AuthEventType {
    REGISTER,
    LOGIN,
    LOGIN_FAILED,
    LOGOUT,
    TOKEN_REFRESH,
    /** A revoked refresh token was presented: the signature of a stolen token. */
    TOKEN_REUSE_DETECTED,
    PASSWORD_CHANGED,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_COMPLETED,
    EMAIL_VERIFIED,
    GOOGLE_LINKED,
    ACCOUNT_SUSPENDED,
    ACCOUNT_REINSTATED
}