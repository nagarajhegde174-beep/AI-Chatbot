# NexaAI RAG Service

| | |
|---|---|
| Artifact | `com.nexaai:rag-service` |
| Package | `com.nexaai.rag` |
| Default port | `8086` |
| Database | `nexa_rag (PostgreSQL + pgvector)` |
| Produces | `"document.indexed.v1"` |
| Swagger UI | `http://localhost:8086/swagger-ui.html` |
| Health | `http://localhost:8086/actuator/health` |

## Responsibility

Embedding pipeline, pgvector storage and retrieval of user knowledge bases.

## Hard boundaries

- This service connects to **one** database only (`nexa_rag (PostgreSQL + pgvector)`). It never reads or writes a
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
`GET /internal/v1/rag/info`, plus Actuator and Swagger. Business endpoints, persistence
and integrations are delivered in the phases listed in `docs/TASKS.md`.