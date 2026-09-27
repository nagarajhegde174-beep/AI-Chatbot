# NexaAI Auth Service

| | |
|---|---|
| Artifact | `com.nexaai:auth-service` |
| Package | `com.nexaai.auth` |
| Default port | `8081` |
| Database | `nexa_auth` |
| Produces | `auth.user.registered.v1`, `auth.user.disabled.v1`, `auth.user.password-changed.v1` |
| Swagger UI | `http://localhost:8081/swagger-ui.html` |
| Health | `http://localhost:8081/actuator/health` |

## Responsibility

Identity: registration, credentials, password rules, account status, refresh-token rotation
and access-token issuance. This is the **only** service that stores or verifies a password.

## Hard boundaries

- Connects to **one** database only (`nexa_auth`), with the `nexa_auth` role. It never reads or
  writes a database owned by another service.
- It does **not** own the user profile. A new account is announced with
  `auth.user.registered.v1` and the User Service provisions the profile, so a user can sign in
  immediately without the profile write being on the critical path.
- Tokens are verified locally by every other service using the RS256 public key, so there is no
  shared signing secret to distribute and no introspection call to make.
- Independent Spring Boot application: own `pom.xml`, own configuration, own port, own source
  tree, own tests, own Docker image.
- No shared Java library module. Types crossing a boundary are duplicated on both sides on
  purpose (docs/DECISIONS.md ADR-0006).

## Build

```bash
mvn -B clean verify
```

## Phase status

Phase 0 only. The service currently exposes a single internal boundary endpoint,
`GET /internal/v1/auth/info`, plus Actuator and Swagger. Registration, login, refresh,
logout, password reset, persistence and token issuance land in Phase 2, together with the
Flyway migrations and JPA model for `nexa_auth`.
