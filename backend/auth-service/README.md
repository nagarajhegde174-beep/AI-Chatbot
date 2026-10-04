# auth-service — IMPLEMENTED

**Phase 1. Status: built and tested. 87 tests pass against real PostgreSQL 17.**

Credentials, tokens and account status. Owns `nexa_auth` and nothing else. This is the only
service in NexaAI that ever handles a password.

```bash
cd backend/auth-service
mvn -B -ntp clean verify     # compile, test, package — from this directory alone
```

---

## Boundary

| | |
|---|---|
| **Port** | 8081 |
| **Database** | `nexa_auth` (role `nexa_auth`) |
| **Builds from** | its own directory; parent is `spring-boot-starter-parent` from Maven Central |
| **Base package** | `com.nexaai.auth` |
| **Stack** | Java 21 · Spring Boot 4.1.1 · Spring Security 7.1.1 · Spring Data JPA · Hibernate · Flyway · PostgreSQL 17 · JJWT (RS256) · springdoc-openapi |
| **Phase** | 1 |

---

## What it implements

| Flow | Endpoint | Notes |
|---|---|---|
| Register | `POST /api/v1/auth/register` | Argon2id hash, duplicate check, **no tokens returned** |
| Login | `POST /api/v1/auth/login` | Tokens set as HTTP-only cookies, absent from the body |
| Refresh | `POST /api/v1/auth/refresh` | Rotates both; reuse revokes the family |
| Logout | `POST /api/v1/auth/logout` | Revokes refresh, denylists the access token |
| Logout all | `POST /api/v1/auth/logout-all` | Revokes every session |
| Me | `GET /api/v1/auth/me` | Current principal |
| Verify email | `POST /api/v1/auth/email/verify`, `/email/verify-link` | Single use, idempotent success |
| Forgot password | `POST /api/v1/auth/password/forgot` | **Generic response**, never enumerates |
| Reset password | `POST /api/v1/auth/password/reset` | Single use, revokes all sessions, clears lockout |
| Change password | `POST /api/v1/auth/password/change` | Requires the current password |
| Google sign-in | `/oauth2/authorization/google` → callback | Issues **our** tokens, never Google's |
| Introspect | `POST /internal/v1/auth/introspect` | Service-to-service only |
| Boundary | `GET /internal/v1/auth/info` | Declares `nexa_auth` as its only database |
| OpenAPI | `/v3/api-docs`, `/swagger-ui.html` | |

### RBAC

Exactly two roles: `USER` and `ADMIN` ([`../../docs/RULES.md`](../../docs/RULES.md) §8). Enforced
three ways: a Java enum, a `CHECK` constraint on `auth_user.role`, and a CI rule.

---

## Security decisions worth knowing

| Decision | Why |
|---|---|
| **RS256, algorithm pinned** | Asymmetric signing, so verifiers hold only the public key. `alg: none` and HS256-with-the-public-key are both rejected. A verifier that reads `alg` from the token it is verifying is trusting its input. |
| **Tokens in HTTP-only cookies** | Unreadable by script, so XSS becomes a nuisance rather than credential theft. The cost is that CSRF protection is mandatory, and it is enabled. |
| **Refresh token scoped to `/api/v1/auth/refresh`** | It is not attached to every other request. |
| **Refresh tokens stored hashed** | A database dump must not yield a usable session. |
| **Reuse detection** | Presenting a rotated token means it was copied. The whole family is revoked. |
| **Credentials version in the token** | A password change invalidates tokens issued before it. |
| **Unknown email costs a real Argon2 verification** | Otherwise response time is a reliable enumeration oracle. |
| **Suspension checked per request, from the database** | Immediate effect, rather than after the 15-minute token lifetime. |

---

## Two bugs the tests caught

Recorded because both were invisible by inspection and would have shipped.

1. **Suspension bypass.** `verifyEmail()` asked `status.canTransitionTo(ACTIVE)`, which is true for
   `SUSPENDED`. Anyone holding a stale verification link could click it and lift an
   administrator's suspension. Now only `PENDING_VERIFICATION` activates.
2. **Google sign-in skipped the status check.** `resolveAccount` returned on a subject match
   without checking status, so a suspended user could sign in through Google while being locked
   out of password sign-in.

A third pair were transaction bugs: the failed-login counter and the token-reuse revocation were
both **rolled back by the rejection that wrote them**, so lockout never engaged and stolen tokens
stayed usable. Both now commit independently ([`../../docs/ARCHITECTURE.md`](../../docs/ARCHITECTURE.md)).

---

## Configuration

Every value is an environment variable. Nothing security-relevant has a default: a missing signing
key fails at startup, which is the correct time to find out.

| Variable | Purpose |
|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | `nexa_auth` only |
| `NEXA_AUTH_JWT_PRIVATE_KEY` / `_PUBLIC_KEY` | PEM-encoded RS256 pair. **Required.** |
| `NEXA_AUTH_JWT_ISSUER` / `_AUDIENCE` / `_KEY_ID` | Claim values. Default to the documented values. |
| `NEXA_AUTH_ACCESS_TOKEN_TTL` | Default `PT15M` |
| `NEXA_AUTH_REFRESH_TOKEN_TTL` | Default `P30D` |
| `NEXA_AUTH_COOKIE_SECURE` | **Must be `true` outside local development.** Default `true`. |
| `NEXA_AUTH_COOKIE_SAME_SITE` | Default `Strict` |
| `NEXA_AUTH_PASSWORD_ENCODER` | `argon2` (default) or `bcrypt` |
| `NEXA_AUTH_MAX_FAILED_LOGINS` / `_LOCK_DURATION` | Default 5 attempts, 15 minutes |
| `NEXA_AUTH_GOOGLE_ENABLED` / `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Off unless enabled |
| `NEXA_AUTH_FRONTEND_BASE_URL` | For email links and the post-login redirect |

Generate a key pair:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem
openssl rsa -in private.pem -pubout -out public.pem
```

---

## Tests

| Suite | Count | Covers |
|---|:--:|---|
| `AuthUserTest` | 12 | Account state machine, lockout, transitions |
| `OneTimeTokenTest` | 12 | Expiry, single use, attempt counting |
| `JwtServiceTest` | 15 | Issuance, and the attacks RS256 + pinning defeat |
| `AuthFlowIntegrationTest` | 11 | Register, sign-in, cookies, refresh, reuse, logout |
| `PasswordFlowIntegrationTest` | 15 | Verification, reset, change |
| `GoogleOAuthServiceTest` | 10 | Creation, linking, refusals |
| `DatabaseOwnershipIntegrationTest` | 7 | This service cannot reach another service's database |
| **Total** | **87** | |

Integration tests run against **real PostgreSQL**, never H2 — H2 accepts syntax PostgreSQL rejects
and has none of the constraints the isolation depends on. They use Testcontainers where a Docker
daemon exists, or `NEXA_TEST_PG_URL` where it does not, and **fail loudly** if neither is available
rather than passing without having run.

The isolation test was verified in both directions: granting `CONNECT` on another service's
database makes it fail, revoking makes it pass. A test that cannot fail is worse than no test.

---

## Not implemented

Deliberately out of scope for Phase 1 ([`../../docs/ARCHITECTURE.md`](../../docs/ARCHITECTURE.md)):

- User profiles — User Service, Phase 2
- Admin user management endpoints — Phase 2
- Gateway token validation and the JWKS endpoint — Phase 3
- Kafka publishing — the **outbox is written** and the publisher runs, but no broker was available
  to verify against
- Email delivery — links are published as domain events; no mail transport exists yet
- Multi-factor authentication