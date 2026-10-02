# document-service — NOT IMPLEMENTED

**Status: planned. This directory contains no code.**

Phase 0 creates the service boundaries. `document-service` is implemented in **Phase 6**
([`../../docs/TASKS.md`](../../docs/TASKS.md)).

Nothing here is a placeholder controller or a stub
([`../../docs/DECISIONS.md`](../../docs/DECISIONS.md) ADR-015, [`../../docs/RULES.md`](../../docs/RULES.md) §10).

---

## Boundary

| | |
|---|---|
| **Port** | 8085 |
| **Database** | `nexa_document` (role `nexa_document`) |
| **Storage** | an external volume, mounted outside the web root |
| **Builds from** | its own directory; no aggregator POM |
| **Base package** | `com.nexaai.document` |
| **Phase** | 6 |

## Responsibility

Upload, text extraction and chunking.

## Does

- Multipart upload of PDF, plain text, Markdown and DOCX
- Validate by **content**, not by extension. The extension is chosen by the uploader; the magic
  bytes are a property of the file
- Enforce size limits before full buffering
- Store files outside the web root under a server-generated random name, never the client's
- Extract text and chunk it on structure first, then size, with overlap
- Publish `document.uploaded.v1`, `document.chunked.v1`, `document.deleted.v1`,
  `document.processing.failed.v1`
- Track state: `UPLOADING → PROCESSING → READY | FAILED`

## Does not

- **Embed. Never.** Embedding is RAG Service's job. This service produces *text chunks*
  ([`../../docs/ARCHITECTURE.md`](../../docs/ARCHITECTURE.md) §7.1)
- Call an AI provider
- Store vectors
- Serve a stored file directly. Content is returned only through an authorised controller
- Use the client filename to build a path

## Owns

`nexa_document`: `document`, `document_chunk`, `document_storage`.
Migrations under `src/main/resources/db/migration`, owned alone.

## Contracts

[`../../docs/SERVICE_CONTRACTS.md`](../../docs/SERVICE_CONTRACTS.md) §9.

## Dependencies

Spring Boot, Spring Data JPA, Kafka, PostgreSQL driver, a PDF/DOCX text extraction library.

## Before implementing

Read [`../../docs/RULES.md`](../../docs/RULES.md), then
[`../../docs/SERVICE_CONTRACTS.md`](../../docs/SERVICE_CONTRACTS.md) §9, then
[`../../docs/SECURITY.md`](../../docs/SECURITY.md) §7 in full.

This is the most security-sensitive service in the system, because it stores and parses
user-supplied files. Extraction must run with no network and no shell access.