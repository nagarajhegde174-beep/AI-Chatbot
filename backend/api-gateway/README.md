# api-gateway

**Status: implemented (Phase 2).** Builds, runs, and is covered by 49 passing tests that make
real HTTP calls through a recording upstream.

---

## Boundary

| | |
|---|---|
| **Port** | 8080 — the platform's only public port |
| **Database** | **none. This service is stateless.** |
| **Kafka** | **none. The gateway does not consume events.** |
| **Builds from** | its own directory; no aggregator POM |
| **Base package** | `com.nexaai.gateway` |

## What it does

Terminates TLS, authenticates a token, applies edge policy, and routes. That is the whole list.

## What it deliberately does not do

- **No business logic.** A gateway that decides anything about a chat, a document or a
  subscription has become a second, divergent implementation of that service.
- **No auth logic.** It verifies a token; it never issues, refreshes or revokes one.
- **No database.** Not a pool, not a replica, not a cache of somebody's tables.
- **No shared business module.** The JWT verifier here is a deliberate duplicate of the one in
  auth-service and user-service. A shared jar would make all three depend on one artifact,
  which is the disguised-monolith path ([`../../docs/RULES.md`](../../docs/RULES.md) §2).

## Routes

| Public prefix | Upstream | Rewrite |
|---|---|---|
| `/api/auth/**` | auth-service | `/api/auth/x` → `/api/v1/auth/x` |
| `/api/users/**` | user-service | `/api/users/x` → `/api/v1/x` |

So `/api/users/me` → user-service `/api/v1/me`, and `/api/users/admin/users` → `/api/v1/admin/users`.

**`RewritePath`, not `StripPrefix`.** `StripPrefix=1` on `/api/auth/login` yields `/auth/login`,
which auth-service does not serve — it answers on `/api/v1/auth/**`. Stripping a segment only
works when what remains happens to match, which is not a property to rely on.

Auth Service's `/internal/v1/auth/**` is **not** routed. It is the service-to-service surface and
has no business being reachable from the public edge.

## The header-trust boundary

This is the gateway's central security property.

The gateway passes verified identity downstream as `X-User-Id`, `X-User-Email` and
`X-User-Roles`. **Those headers are only trustworthy if they arrive from the gateway.** A client
that can send its own `X-User-Id: <someone else>` turns every identity check downstream into a
suggestion.

So `HeaderSanitizingGlobalFilter` runs first and **removes** `X-User-*`, `X-Authenticated`,
`X-Forwarded-*` and `X-Real-IP`; only then does the authentication filter **set** verified values.
Strip first, set second — never the other way round. Removal is case-insensitive, because HTTP
header names are and a filter matching only the exact case it writes is defeated by
`x-user-id`.

**The bearer token is removed before forwarding.** Upstreams read the identity headers; none of
them needs the credential. Forwarding it would hand every upstream a replayable secret.

## Authentication

`JwtAuthenticationGlobalFilter` verifies RS256 with the **public key only** — there is no
`private-key` property, so the gateway cannot issue a token.

The algorithm is pinned, never read from the token. That rejects `alg: none` and the
algorithm-confusion attack (an HS256 token signed with the RSA public key, which a verifier
reading `alg` from the token would accept as ADMIN).

Public paths bypass verification. The list is explicit configuration, not a heuristic like
"anything containing login" — a pattern match is a way to accidentally expose an endpoint.

## Deliberate omissions

- **No circuit breaker.** A breaker needs a fallback route and a dependency, and an untested
  fallback is worse than an honest 502: it turns an outage into a silent wrong answer.
- **No rate limiting.** Not yet, and not pretended.
- **No request-size limit.** Noted, not implemented.

Each of these needs its own tests when added.

## Two traps that cost real time here

**`spring-boot-starter-oauth2-resource-server` must not be a dependency.** It auto-configures a
security chain that runs *before* the gateway's `GlobalFilter`s and rejects anything it cannot
authenticate in its own format, with an empty 401 body. The symptom is a gateway that 401s every
request including valid ones, while its own verification never runs. Empty-body 401s are the tell.

**The properties prefix is `spring.cloud.gateway.server.webflux`**, not `spring.cloud.gateway`.
Gateway 5 renamed it and the old prefix is *silently ignored* — the application starts, health
is green, there are zero routes, and every request 404s.

If routing breaks, check the prefix first, then `GET /actuator/gateway/routes` (expect 2).

## Configuration

| Variable | |
|---|---|
| `NEXA_GATEWAY_JWT_PUBLIC_KEY` | **required.** PEM-encoded RS256 public key |
| `NEXA_GATEWAY_CORS_ALLOWED_ORIGIN_PATTERNS` | **required.** Comma-separated. Empty stops startup |
| `NEXA_GATEWAY_CORS_ALLOW_CREDENTIALS` | default true (cookie auth) |
| `NEXA_GATEWAY_PUBLIC_PATTERNS` | comma-separated unauthenticated paths |
| `NEXA_AUTH_SERVICE_URL` | default `http://localhost:8081` |
| `NEXA_USER_SERVICE_URL` | default `http://localhost:8082` |
| `NEXA_GATEWAY_CONNECT_TIMEOUT` | default 2000 ms |
| `NEXA_GATEWAY_RESPONSE_TIMEOUT` | default 30 s |

CORS has **no wildcard default**: an empty allow-list stops the application at startup. Defaulting
to `*` publishes a browser-readable API to every origin, and the mistake is invisible until it is
exploited.

## Build and test

```bash
cd backend/api-gateway
mvn -B -ntp clean verify
```

No database needed. Tests route real traffic through a JDK `HttpServer` stub that records exactly
what arrived, so the header-stripping claims are checked against bytes on the wire rather than
against the gateway's own opinion of itself.