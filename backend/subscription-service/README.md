# NexaAI Subscription Service

| | |
|---|---|
| Artifact | `com.nexaai:subscription-service` |
| Package | `com.nexaai.subscription` |
| Default port | `8087` |
| Database | `nexa_subscription` |
| Produces | `"payment.order.created.v1", "payment.succeeded.v1", "payment.failed.v1"` |
| Swagger UI | `http://localhost:8087/swagger-ui.html` |
| Health | `http://localhost:8087/actuator/health` |

## Responsibility

Plans, entitlements, usage metering and Razorpay TEST-mode order lifecycle.

## Hard boundaries

- This service connects to **one** database only (`nexa_subscription`). It never reads or writes a
  database owned by another service.
- It is an independent Spring Boot application: own `pom.xml`, own configuration, own port,
  own source tree, own tests, own Docker image.
- Synchronous calls to other services go over REST. Asynchronous hand-offs go over Kafka.
- There is no shared Java library module in this repository. Types crossing a service
  boundary are duplicated on both sides on purpose (docs/DECISIONS.md ADR-0006).

## Build

```bash
mvn -B clean verify
```

## Phase status

Phase 0 only. The service currently exposes a single internal boundary endpoint,
`GET /internal/v1/subscription/info`, plus Actuator and Swagger. Business endpoints, persistence
and integrations are delivered in the phases listed in `docs/TASKS.md`.