# rag-service — NOT IMPLEMENTED

**Status: planned. This directory contains no code.**

Phase 0 creates the service boundaries. `rag-service` is implemented in **Phase 7**
([`../../docs/TASKS.md`](../../docs/TASKS.md)).

Nothing here is a placeholder controller or a stub
([`../../docs/DECISIONS.md`](../../docs/DECISIONS.md) ADR-015, [`../../docs/RULES.md`](../../docs/RULES.md) §10).

---

## Boundary

| | |
|---|---|
| **Port** | 8086 |
| **Database** | `nexa_rag` (role `nexa_rag`) |
| **Extension** | **pgvector — loaded here and nowhere else** |
| **Builds from** | its own directory; no aggregator POM |
| **Base package** | `com.nexaai.rag` |
| **Phase** | 7 |

## Responsibility

Embeddings and vector retrieval.

This service owns the vector store entirely. One owner, one schema, one migration stream
([`../../docs/DECISIONS.md`](../../docs/DECISIONS.md) ADR-008).

## Does

- Consume `document.chunked.v1`, embed the chunks through Spring AI, store the vectors
- `POST /internal/v1/rag/retrieve`: embed the query, similarity search, return top-k passages
- Scope every retrieval **by `userId` inside the SQL query**, never by filtering results
- Return `documentId` and `chunkIndex` with every passage, so a citation is possible
- Consume `document.deleted.v1` and delete the derived vectors, so the vector store cannot
  outlive its source
- Deduplicate on `eventId`: replaying an event must not duplicate vectors

## Does not

- Load pgvector in any other database. CI asserts this
- Store document metadata or the original file. Document Service owns those
- Serve a passage to a browser. It has no public route
- Expose an unscoped search. `userId` is a required parameter, so a cross-user leak cannot be
  expressed
- Call a chat model. Embeddings only

## Owns

`nexa_rag`: `document_embedding` (`embedding vector(1536)`, HNSW index), `processed_event`.
Migrations under `src/main/resources/db/migration`, owned alone.

## Contracts

[`../../docs/SERVICE_CONTRACTS.md`](../../docs/SERVICE_CONTRACTS.md) §10, including the
tenant-isolation query, which is the most security-relevant SQL in the project.

## Dependencies

Spring Boot, Spring Data JPA, Spring AI embeddings, Kafka, PostgreSQL driver plus **pgvector**.

## Before implementing

Read [`../../docs/RULES.md`](../../docs/RULES.md), then
[`../../docs/SERVICE_CONTRACTS.md`](../../docs/SERVICE_CONTRACTS.md) §10, then
[`../../docs/ARCHITECTURE.md`](../../docs/ARCHITECTURE.md) §7.

Integration tests must run against a **real PostgreSQL with pgvector** (Testcontainers), not an
in-memory substitute. `user_id` inside the query is not an optimisation — filtering after
retrieval is a data leak.