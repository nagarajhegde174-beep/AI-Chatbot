# auth-service — NOT IMPLEMENTED

**Status: planned. This directory contains no code.**

Phase 0 creates the service boundaries. `auth-service` is implemented in **Phase 1**
([`../../docs/TASKS.md`](../../docs/TASKS.md)).

Nothing here is a placeholder controller or a stub. An empty directory with an honest README is
preferable to a skeleton that looks like an implementation
([`../../docs/DECISIONS.md`](../../docs/DECISIONS.md) ADR-015, [`../../docs/RULES.md`](../../docs/RULES.md) §10).

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
leaves this service in any DTO
([`../../docs/SERVICE_CONTRACTS.md`](../../docs/SERVICE_CONTRACTS.md) §3.4).

## Does

- Registration, login, refresh, logout
- Password hashing with Argon2id or bcrypt
- RS256 access tokens (15 min) with `iss`, `aud`, `sub`, `roles`, `jti`
- Rotating refresh tokens, stored hashed
- Revocation, in a denylist with a TTL
- Account lockout after repeated failures
- Publishes `auth.user.registered.v1`

## Does not

- Store a profile. User Service owns that, and learns of an account from the event.
- Manage users. That is User Service's administration surface.
- Validate tokens at the edge. The gateway does that.
- Talk to an AI provider.

## Owns

`nexa_auth`: `auth_user`, `refresh_token`, `token_denylist`, `login_attempt`,
`email_verification`, `password_reset`. Flyway migrations under
`src/main/resources/db/migration`, owned and applied by this service alone.

## Contracts

[`../../docs/SERVICE_CONTRACTS.md`](../../docs/SERVICE_CONTRACTS.md) §5.

## Dependencies

Spring Boot, Spring Security, Spring Data JPA, Spring Validation, Flyway, PostgreSQL driver.
**No** persistence of another service's data, **no** ZooKeeper client, **no** provider SDK.

## Before implementing

Read [`../../docs/RULES.md`](../../docs/RULES.md), then
[`../../docs/SERVICE_CONTRACTS.md`](../../docs/SERVICE_CONTRACTS.md) §5, then
[`../../docs/SECURITY.md`](../../docs/SECURITY.md) §2 and §4.

Pin **real, existing** versions from Maven Central. Verify the Spring Boot 4.x and Spring AI 2.x
annotation packages and starter names rather than assuming them
([`../../docs/MEMORY.md`](../../docs/MEMORY.md) §6.3).