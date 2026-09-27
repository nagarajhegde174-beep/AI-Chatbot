# NexaAI API Gateway

| | |
|---|---|
| Artifact | `com.nexaai:api-gateway` |
| Package | `com.nexaai.gateway` |
| Default port | `8080` |
| Database | none - the gateway owns no data |
| Stack | Spring Cloud Gateway 5.0.x (reactive / WebFlux) |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| Health | `http://localhost:8080/actuator/health` |

## Responsibility

Single public entry point. Terminate the client connection, apply cross-cutting HTTP policy
and forward each request to **exactly one** owning service.

## What this service must never do

- Hold a database, a schema or a migration.
- Contain business rules. If logic appears here, it belongs in the service that owns the data.
- Call another service service-to-service. The gateway only forwards to its direct downstream.
- Strip or rewrite the incoming path prefix, so each service keeps full ownership of its own
  URL namespace.

## Routes

| Path prefix | Forwarded to | Service port |
|---|---|---|
| `/api/v1/auth/**` | auth-service | 8081 |
| `/api/v1/users/**`, `/api/v1/admin/users/**` | user-service | 8082 |
| `/api/v1/chat/**` | chat-service | 8083 |
| `/api/v1/ai/**` | ai-service | 8084 |
| `/api/v1/documents/**` | document-service | 8085 |
| `/api/v1/rag/**` | rag-service | 8086 |
| `/api/v1/subscriptions/**`, `/api/v1/admin/plans/**` | subscription-service | 8087 |

Upstream URLs are environment variables (`AUTH_SERVICE_URL`, ...), so the same image runs
locally and inside Docker Compose.

## Build

```bash
mvn -B clean verify
```

## Phase status

Phase 0 only: routing, Actuator, Swagger and a route-shape test. Token verification, rate
limiting and the internal-path allow-list are Phase 2 work, see `docs/TASKS.md`.
