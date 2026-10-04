package com.nexaai.auth.exception;

/**
 * The failures this service raises, each carrying the HTTP status and the stable error code
 * from {@code docs/SERVICE_CONTRACTS.md} section 1.4.
 *
 * <p>A small sealed hierarchy rather than a generic exception with a message, because the
 * distinction that matters here is which of these is safe to tell a caller. Enumeration-safe
 * ones deliberately carry the same message and code whether or not the account exists.
 */
public final class AuthExceptions {

    private AuthExceptions() {
    }

    /** Base type for every error this service raises deliberately. */
    public abstract static class AuthException extends RuntimeException {

        private final String code;

        protected AuthException(String code, String message) {
            super(message);
            this.code = code;
        }

        protected AuthException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    /**
     * Credentials are wrong, or the email is unknown.
     *
     * <p>One type for both, on purpose. Distinguishing them would let an attacker enumerate
     * every registered email address. The same message and the same code are returned either
     * way, and the same amount of work is done either way
     * ({@code docs/SECURITY.md} section 2.1).
     */
    public static class InvalidCredentialsException extends AuthException {

        public InvalidCredentialsException() {
            super("AUTH_INVALID_CREDENTIALS", "Email or password is incorrect.");
        }
    }

    /** The account exists but cannot sign in: unverified, suspended, deactivated or locked. */
    public static class AccountNotActiveException extends AuthException {

        public AccountNotActiveException(String reason) {
            super("AUTH_ACCOUNT_NOT_ACTIVE", reason);
        }
    }

    /** The account is temporarily locked after repeated failures. */
    public static class AccountLockedException extends AuthException {

        public AccountLockedException() {
            super("AUTH_ACCOUNT_LOCKED",
                    "Too many failed sign-in attempts. Try again later or reset your password.");
        }
    }

    /** Registration with an email that already has an account. */
    public static class EmailAlreadyRegisteredException extends AuthException {

        public EmailAlreadyRegisteredException() {
            super("AUTH_EMAIL_ALREADY_REGISTERED", "An account with that email already exists.");
        }
    }

    /** A token is unknown, expired, already used or revoked. */
    public static class InvalidTokenException extends AuthException {

        public InvalidTokenException(String message) {
            super("AUTH_INVALID_TOKEN", message);
        }
    }

    /** A one-time token has already been redeemed. */
    public static class TokenAlreadyConsumedException extends AuthException {

        public TokenAlreadyConsumedException() {
            super("AUTH_TOKEN_ALREADY_CONSUMED", "This link has already been used.");
        }
    }

    /** A one-time token has expired. */
    public static class TokenExpiredException extends AuthException {

        public TokenExpiredException() {
            super("AUTH_TOKEN_EXPIRED", "This link has expired. Please request a new one.");
        }
    }

    /** The supplied password does not match the stored hash. */
    public static class WrongPasswordException extends AuthException {

        public WrongPasswordException() {
            super("AUTH_WRONG_PASSWORD", "The current password is incorrect.");
        }
    }

    /**
     * A refresh token that was already exchanged was presented.
     *
     * <p>The signature of a stolen token: the legitimate holder always uses the newest one.
     * The whole rotation family is revoked in response.
     */
    public static class TokenReuseDetectedException extends AuthException {

        public TokenReuseDetectedException() {
            super("AUTH_TOKEN_REUSE_DETECTED",
                    "This session has been invalidated. Please sign in again.");
        }
    }

    /** A caller is authenticated but not permitted to do this. */
    public static class AccessDeniedException extends AuthException {

        public AccessDeniedException() {
            super("AUTH_ACCESS_DENIED", "You do not have permission to perform this action.");
        }
    }
}