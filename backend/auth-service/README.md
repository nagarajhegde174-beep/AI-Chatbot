# auth-service

**Status: Implemented (Phase 1).**

Auth Service is an independent Spring Boot application managing credentials, authentication, token issuance and revocation, email verification, password reset, and Google OAuth.

---

## Boundary

| | |
|---|---|
| **Port** | 8081 |
| **Database** | `nexa_auth` (role `nexa_auth`) |
| **Builds from** | its own directory; no aggregator POM |
| **Base package** | `com.nexaai.auth` |
| **Phase** | 1 |

## Responsibility

Credentials, password hashing, JWT issuance and refresh, token revocation, account status.

**This is the only service that ever handles a password.** No password hash, and no token, ever
leaves this service in any DTO ([`../../docs/SERVICE_CONTRACTS.md`](../../docs/SERVICE_CONTRACTS.md) §3.4).

## Features

- **Registration**: Email & password validation, duplicate email prevention (409 Conflict), BCrypt password hashing, verification token generation.
- **Login**: Email/password authentication, dummy hash verification for constant-time responses to prevent enumeration, RS256 JWT access token, rotating refresh token, HTTP-only secure cookie.
- **Logout & Logout-All**: Revocation of refresh tokens and token denylisting for access token `jti`, cookie clearance.
- **Email Verification**: Token expiration, verification endpoint (`POST` & `GET`), safe activation to `ACTIVE`.
- **Forgot Password**: Secure token and OTP, generic responses to prevent user enumeration, 15-minute expiration, one-time use.
- **Reset Password**: Token verification, secure hashing of new password, invalidation of existing refresh sessions.
- **Change Password**: Old password verification, secure hashing of new password.
- **RBAC**: Exactly two roles: `USER` and `ADMIN`.
- **Google OAuth**: OAuth 2.0 / OpenID Connect, Continue with Google, verified-email account linking, never storing Google passwords.
- **OpenAPI / Swagger**: Documentation available at `/swagger-ui.html` and `/v3/api-docs`.

## Database Ownership

`nexa_auth`: `auth_user`, `refresh_token`, `token_denylist`, `login_attempt`,
`email_verification`, `password_reset`. Flyway migrations under
`src/main/resources/db/migration`, owned and applied by this service alone.

## Running

```bash
cd backend/auth-service
mvn clean verify
mvn spring-boot:run
```