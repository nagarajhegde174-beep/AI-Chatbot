# ai-service — NOT IMPLEMENTED

**Status: planned. This directory contains no code.**

Phase 0 creates the service boundaries. `ai-service` is implemented in **Phase 4**
([`../../docs/TASKS.md`](../../docs/TASKS.md)).

Nothing here is a placeholder controller or a stub
([`../../docs/DECISIONS.md`](../../docs/DECISIONS.md) ADR-015, [`../../docs/RULES.md`](../../docs/RULES.md) §10).

---

## Boundary

| | |
|---|---|
| **Port** | 8084 |
| **Database** | **none. This service is stateless.** |
| **Builds from** | its own directory; no aggregator POM |
| **Base package** | `com.nexaai.ai` |
| **Phase** | 4 |

## Stateless by design

This service owns **no database and no role**. It holds no user, no conversation and no document.
It receives a fully-built request and returns tokens.

That is a deliberate statement, not an optimisation
([`../../docs/DECISIONS.md`](../../docs/DECISIONS.md) ADR-007):

- It scales horizontally with no session affinity.
- It restarts with no data loss, because it has none.
- Provider credentials exist here and nowhere else.
- Conversation data never comes near the component holding the keys.

**CI fails the build if this service declares a datasource or a persistence starter**
([`../../docs/RULES.md`](../../docs/RULES.md) §3.2).

## Responsibility

Multi-model LLM inference and streaming, through Spring AI.

## Does

- Reach OpenAI, Google Gemini and Groq/LLaMA through **Spring AI only**. No provider SDK
  directly ([`../../docs/DECISIONS.md`](../../docs/DECISIONS.md) ADR-006)
- Stream tokens over SSE
- Classify provider errors: rate limited, timeout, filtered, outage
- Circuit-break per provider
- Read provider keys from the environment, and **fail at startup** if a configured provider has
  no key
- Publish `ai.inference.completed.v1` with token counts for metering

## Does not

- Store anything. No database, no volume, no file
- Know what a conversation is
- Know what a user is. The caller supplies a fully-built prompt
- Expose a public route. The catalogue reaches the browser through the gateway
- Log prompt content or an API key, on any path including exception paths

## Contracts

[`../../docs/SERVICE_CONTRACTS.md`](../../docs/SERVICE_CONTRACTS.md) §8.

`maxOutputTokens` is **required**. A missing bound is an unbounded bill.

## Dependencies

Spring Boot, Spring AI (`ChatClient`), Kafka, WebFlux for streaming. **No** JDBC, **no** JPA,
**no** Flyway, **no** provider SDK.

## Before implementing

Read [`../../docs/RULES.md`](../../docs/RULES.md), then
[`../../docs/SERVICE_CONTRACTS.md`](../../docs/SERVICE_CONTRACTS.md) §8, then
[`../../docs/ARCHITECTURE.md`](../../docs/ARCHITECTURE.md) §6.

Spring AI 2.x is a new major version. Confirm the starter names and the autoconfiguration for
each provider before building on it ([`../../docs/MEMORY.md`](../../docs/MEMORY.md) §6.3).