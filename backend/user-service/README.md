# user-service

**Status: implemented (Phase 2).** Builds, runs, and is covered by 120 passing tests against a
real PostgreSQL.

---

## Boundary

| | |
|---|---|
| **Port** | 8082 |
| **Database** | `nexa_user` (role `nexa_user`), schema `nexa_user` |
| **Builds from** | its own directory; no aggregator POM |
| **Base package** | `com.nexaai.user` |
| **Consumes** | `auth.user.registered.v1` |
| **Kafka group** | `user-profile-events` (its own; never shared) |

## What it owns

Profiles, preferences, account status, settings, and the administrative view of users.

## What it does not have

- **No credential of any kind.** No password column, no hash, no token, no secret. The
  `auth.user.registered.v1` payload carries none either: a topic is not a place credentials
  belong, and a hash travelling on it would give every consumer one.
- **No route to `nexa_auth`.** The database role is granted on `nexa_user` alone, so a
  cross-service query fails with a permission error rather than quietly succeeding. That is
  asserted by `DatabaseIsolationTest`, which connects as the application role and tries.
- **No self-registration.** A profile appears only because the registration event was consumed.
  Registration belongs to auth-service.

## How it authenticates

Verifies the RS256 access tokens auth-service issued, using the **public key only**. There is no
`private-key` property, so this service cannot mint a token for itself.

Verifying locally rather than calling auth-service on every request is deliberate: the token is
self-contained, and a synchronous call would put auth-service on the critical path of every
request in the platform.

The algorithm is pinned to RS256. `alg: none` and an HS256 token signed with the public key —
the algorithm-confusion attack — are both refused.

## Routes

### Self-service — always about the caller, never a user id

| Method | Path | |
|---|---|---|
| GET | `/api/v1/me` | own profile |
| PATCH | `/api/v1/me` | display name and avatar only |
| GET | `/api/v1/me/preferences` | own preferences |
| PATCH | `/api/v1/me/preferences` | partial update; absent fields unchanged |
| GET | `/api/v1/me/status` | own status and reason |

`PATCH /api/v1/me` accepts **no** `email`, `role`, `accountStatus` or `authUserId` field. Their
absence is the control, not an oversight.

The caller's own status history omits which administrator acted. They need to know a suspension
happened and why; they do not need the operator's identity.

### Administrative — requires `ADMIN`

Users are addressed by **auth-service id**, the platform-wide identity.

| Method | Path | |
|---|---|---|
| GET | `/api/v1/admin/users` | list; filter by status/role, search, page |
| GET | `/api/v1/admin/users/counts` | counts by status |
| GET | `/api/v1/admin/users/{id}` | detail incl. full status history |
| GET | `/api/v1/admin/users/{id}/usage` | subscription and usage |
| POST | `/api/v1/admin/users/{id}/activate` | |
| POST | `/api/v1/admin/users/{id}/suspend` | **reason required** |
| POST | `/api/v1/admin/users/{id}/deactivate` | permanent |
| POST | `/api/v1/admin/users/{id}/status` | general form |

## Design decisions worth knowing

**Status is a projection, not a source of truth.** auth-service owns account status and
republishes changes. The copy here is denormalised read state so the admin view can filter and
explain without a network call per row, and so a blocked user can see why on their own profile.

**`DEACTIVATED` is terminal.** No transition leads out of it. A deactivation that can be undone
is a suspension with extra steps.

**`SUSPENDED → SUSPENDED` is allowed, and is the only self-transition.** So an administrator can
correct a reason without unsuspending first. History is append-only, so the original entry
survives.

**A suspension requires a reason**, which is shown to the account holder. An unexplained block
is a support ticket, not an administrative decision.

**Page size is capped server-side.** An uncapped `size` parameter is a denial of service dressed
as a convenience.

**Usage reports "unavailable", never zero.** Subscription Service is Phase 9. A zero reads as
"this user has used nothing", which is a different and wrong claim.

**Unknown query filters are rejected**, not ignored. Silently dropping a misspelled filter and
returning every user is how a response gets cached and screenshotted.

## Configuration

All from the environment; nothing security-relevant has a default.

| Variable | |
|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL for `nexa_user` |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | the `nexa_user` role |
| `DB_POOL_MAX_SIZE` | default 10 |
| `NEXA_USER_JWT_PUBLIC_KEY` | **required.** PEM-encoded RS256 public key |
| `NEXA_USER_JWT_ISSUER` / `NEXA_USER_JWT_AUDIENCE` | default `nexa-auth-service` / `nexaai-web` |
| `NEXA_USER_EVENT_ENABLED` | default true; the listener bean is absent when false |
| `NEXA_USER_CLIENT_ENABLED` | default false; Subscription Service is Phase 9 |
| `NEXA_KAFKA_BOOTSTRAP_SERVERS` | default `localhost:9092` |
| `NEXA_USER_ADMIN_MAX_PAGE_SIZE` | default 100 |

The service starts and serves traffic with no Kafka broker: the listener bean only exists when
`NEXA_USER_EVENT_ENABLED` is true. Registration is delivered by replay in a fresh environment, so
running without consuming is a supported state.

## Build and test

```bash
cd backend/user-service
mvn -B -ntp clean verify
```

Tests need a real PostgreSQL. Either Docker (Testcontainers) or a cluster supplied through
`NEXA_TEST_PG_URL`. If neither is available the tests **fail with an explanation** rather than
skipping — a green build that ran nothing is worse than a red one
([`../../docs/RULES.md`](../../docs/RULES.md) §11).

```bash
export NEXA_TEST_PG_URL=jdbc:postgresql://127.0.0.1:5434/nexa_user
export NEXA_TEST_PG_USER=nexa_user
export NEXA_TEST_PG_PASSWORD=nexa_user_local_pw
```

**H2 is never used.** A repository test against an in-memory database cannot test the property
this service most depends on — that it cannot see another service's tables — because that is
enforced by PostgreSQL grants.

## OpenAPI

`/v3/api-docs`, `/swagger-ui.html`.