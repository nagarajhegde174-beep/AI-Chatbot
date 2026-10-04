# user-service — NOT IMPLEMENTED

**Status: planned. This directory contains no code.**

Phase 0 creates the service boundaries. `user-service` is implemented in **Phase 2**
([`../../docs/ARCHITECTURE.md`](../../docs/ARCHITECTURE.md)).

Nothing here is a placeholder controller or a stub
([`../../docs/ARCHITECTURE.md`](../../docs/ARCHITECTURE.md) ADR-015, [`../../docs/RULES.md`](../../docs/RULES.md) §10).

---

## Boundary

| | |
|---|---|
| **Port** | 8082 |
| **Database** | `nexa_user` (role `nexa_user`) |
| **Builds from** | its own directory; no aggregator POM |
| **Base package** | `com.nexaai.user` |
| **Phase** | 2 |

## Responsibility

Profile, preferences, theme, and user administration for `ADMIN`.

## Does

- Read and update a user's own profile
- Store preferences, including the UI theme
- Provide the admin surface: list users, suspend and reinstate
- Consume `auth.user.registered.v1` and create the profile
- Publish `user.profile.updated.v1`, `user.status.changed.v1`, `user.account.deleted.v1`
- Cache profiles in Redis, invalidated by the event that changed them

## Does not

- **Store a password, hash or token.** No credential ever reaches this service. It learns of an
  account only from the registration event
- Validate tokens. The gateway and Auth Service do
- Read `nexa_auth` directly, or any other service's database

## Owns

`nexa_user`: `user_profile`, `user_preference`, `account_status_history`.
Migrations under `src/main/resources/db/migration`, owned by this service alone.

## Contracts

[`../../docs/SERVICE_CONTRACTS.md`](../../docs/SERVICE_CONTRACTS.md) §6.

## Dependencies

Spring Boot, Spring Data JPA, Spring Cache, Kafka, Redis, PostgreSQL driver.

## Before implementing

Read [`../../docs/RULES.md`](../../docs/RULES.md), then
[`../../docs/SERVICE_CONTRACTS.md`](../../docs/SERVICE_CONTRACTS.md) §6.

Ownership must be enforced server-side on **every** request. A known id is not authorisation
([`../../docs/SECURITY.md`](../../docs/SECURITY.md) §4.2).