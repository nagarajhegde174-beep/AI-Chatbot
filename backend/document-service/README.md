# NexaAI Document Service

| | |
|---|---|
| Artifact | `com.nexaai:document-service` |
| Package | `com.nexaai.document` |
| Default port | `8085` |
| Database | `nexa_document` |
| Produces | `"document.uploaded.v1", "document.chunked.v1"` |
| Swagger UI | `http://localhost:8085/swagger-ui.html` |
| Health | `http://localhost:8085/actuator/health` |

## Responsibility

File upload, text extraction, chunking and document metadata lifecycle.

## Hard boundaries

- This service connects to **one** database only (`nexa_document`). It never reads or writes a
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
`GET /internal/v1/document/info`, plus Actuator and Swagger. Business endpoints, persistence
and integrations are delivered in the phases listed in `docs/TASKS.md`.