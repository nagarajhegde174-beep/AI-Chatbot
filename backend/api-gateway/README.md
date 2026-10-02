# api-gateway — NOT IMPLEMENTED

**Status: planned. This directory contains no code.**

Phase 0 creates the service boundaries. `api-gateway` is **implemented in Phase 3**, when token
validation and the route table are wired ([`../../docs/TASKS.md`](../../docs/TASKS.md)).

Nothing here is a placeholder controller or a stub
([`../../docs/DECISIONS.md`](../../docs/DECISIONS.md) ADR-015, [`../../docs/RULES.md`](../../docs/RULES.md) §10).

---

## Boundary

| | |
|---|---|
| **Port** | 8080 |
| **Database** | **none. This service is stateless.** |
| **Kafka** | **none. The gateway does not consume events.** |
| **Builds from** | its own directory; no aggregator POM |
| **Base package** | `com.nexaai.gateway` |
| **Phase** | 3 (skeleton and boundary endpoint may appear earlier if a phase needs routing) |

## Stateless by design

The gateway owns **no database and no role**. It holds no session, no token store and no user
state.

**CI fails the build if this service declares a datasource or a persistence starter**
([`../../docs/RULES.md`](../../docs/RULES.md) §3.2).

## The only public entry point

Every browser request goes through the gateway. **No service port is ever published to the
internet**, and the frontend never calls a service directly. One upstream means edge policy
lives in one place
([`../../docs/ARCHITECTURE.md`](../../docs/ARCHITECTURE.md) §4).

## Does

- Validate the RS256 access token and reject invalid requests **before** routing
- Route by path prefix to the owning service, per the route table in
  [`../../docs/SERVICE_CONTRACTS.md`](../../docs/SERVICE_CONTRACTS.md) §4.2
- Generate `X-Correlation-Id` when absent and propagate it to every hop
- Rate limit per concern, and cap body size
- Apply CORS from a configured allowlist
- Deny `/internal/**` and `/actuator/**` from external access
- Apply timeouts and circuit breakers per downstream service
- Aggregate health for orchestration
- Publish the JWKS document

## Does not

- **Hold business logic.** A route that computes something belongs in the service that owns the
  data
- Be the only authorisation check. Each service authorises independently, because an internal
  network is not a trust boundary ([`../../docs/DECISIONS.md`](../../docs/DECISIONS.md) ADR-014)
- Retry a non-idempotent `POST`. A retried message bills the user twice
- Use `CORS: *` with credentials
- Depend on any other service's code

## Contracts

[`../../docs/SERVICE_CONTRACTS.md`](../../docs/SERVICE_CONTRACTS.md) §4.

## Dependencies

Spring Boot, Spring Cloud Gateway (reactive), Spring Security resource server for JWT
verification, Resilience4j.

**No** JDBC, **no** JPA, **no** Flyway, **no** Kafka client.

## Before implementing

Read [`../../docs/RULES.md`](../../docs/RULES.md), then
[`../../docs/SERVICE_CONTRACTS.md`](../../docs/SERVICE_CONTRACTS.md) §4, then
[`../../docs/DECISIONS.md`](../../docs/DECISIONS.md) ADR-014 and ADR-018.

Algorithm confusion must be rejected: `alg` must be exactly `RS256`, and neither `alg: none` nor
an HS256 token signed with the public key may ever be accepted.