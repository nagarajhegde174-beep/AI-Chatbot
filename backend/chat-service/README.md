# chat-service — NOT IMPLEMENTED

**Status: planned. This directory contains no code.**

Phase 0 creates the service boundaries. `chat-service` is implemented in **Phase 5**
([`../../docs/TASKS.md`](../../docs/TASKS.md)).

Nothing here is a placeholder controller or a stub
([`../../docs/DECISIONS.md`](../../docs/DECISIONS.md) ADR-015, [`../../docs/RULES.md`](../../docs/RULES.md) §10).

---

## Boundary

| | |
|---|---|
| **Port** | 8083 |
| **Database** | `nexa_chat` (role `nexa_chat`) |
| **Builds from** | its own directory; no aggregator POM |
| **Base package** | `com.nexaai.chat` |
| **Phase** | 5 |

## Responsibility

Conversations, messages, the memory window, the streamed answer relay, and quota enforcement
before spending.

**This is the orchestrator of a chat turn.** It calls other services; it does not reimplement
them.

## A chat turn

```
1. verify the caller owns the conversation
2. check the entitlement BEFORE spending
3. persist the user message
4. build the memory window (bounded by TOKEN COUNT)
5. if RAG is on: POST to rag-service for context
6. POST to ai-service, streaming (REST/SSE)
7. relay each token to the browser
8. persist the assistant message
9. publish chat.message.completed.v1
```

## Does

- Conversation CRUD, with per-user ownership on every request
- The memory window: visible history plus a bounded recent window, trimmed by token count
- Relay the AI Service's SSE stream to the browser without buffering
- Store citations, so every grounded answer is traceable
- Enforce the message quota **before** calling the provider
- Publish `chat.message.completed.v1`

## Does not

- Call an AI provider directly. Only the AI Service does
- Embed anything. That is RAG Service
- Hold a password or a token
- Buffer the stream. Buffering makes streaming invisible and is the most common bug in this
  service

## Owns

`nexa_chat`: `conversation`, `message`, `message_citation`,
`conversation_model_history`. Migrations under `src/main/resources/db/migration`, owned alone.

## Contracts

[`../../docs/SERVICE_CONTRACTS.md`](../../docs/SERVICE_CONTRACTS.md) §7, including the SSE event
contract.

## Dependencies

Spring Boot, Spring WebFlux or MVC with `SseEmitter`, Spring Data JPA, Kafka, WebClient for
internal calls, PostgreSQL driver.

Every internal call needs a timeout. An unbounded call is a cascading failure
([`../../docs/ARCHITECTURE.md`](../../docs/ARCHITECTURE.md) §2.1).

## Before implementing

Read [`../../docs/RULES.md`](../../docs/RULES.md), then
[`../../docs/SERVICE_CONTRACTS.md`](../../docs/SERVICE_CONTRACTS.md) §7, then
[`../../docs/DECISIONS.md`](../../docs/DECISIONS.md) ADR-017 and ADR-021.

An interrupted stream must be persisted as **incomplete**, never as a whole answer.