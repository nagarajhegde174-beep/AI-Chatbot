# ARCHITECTURE.md — NexaAI System Architecture

This document describes how NexaAI is built. What it must never become is defined in
[`RULES.md`](RULES.md). API and event details are in
[`SERVICE_CONTRACTS.md`](SERVICE_CONTRACTS.md).

**Status: designed, not implemented.** Phase 0 delivers this architecture and the repository
foundation. Service directories exist but are empty by design (`RULES.md` §10).

---

## 1. Microservices architecture

### 1.1 The shape

```
                          ┌──────────────┐
   browser  ──HTTPS──▶    │    NGINX     │  TLS, rate limits, static assets, SSE
                          │  (edge)      │
                          └──────┬───────┘
                                 │ /api/** only
                          ┌──────▼───────┐
                          │ API GATEWAY  │  8080  authn at the edge, routing,
                          │              │        correlation id, no business logic
                          └──────┬───────┘
              ┌──────────┬───────┼───────┬───────────┬────────────┐
              ▼          ▼       ▼       ▼           ▼            ▼
        auth-service user   chat    document   rag-service  subscription
          8081      8082   8083    8085        8086         8087
              │          │       │        │           │              │
              │          │       └────────┴──▶ ai-service 8084 ──────┘
              │          │                  (stateless inference)
              │          │                        │
        ┌─────┴──────────┴─────┐          ┌───────┴────────┐
        │      PostgreSQL      │          │  Kafka (KRaft) │
        │  one DB + role each  │          │  + Redis cache │
        └──────────────────────┘          └────────────────┘
```

### 1.2 Eight services

| # | Service | Port | Owns | Responsibility |
|---|---|:--:|---|---|
| 1 | `api-gateway` | 8080 | — | Public entry point. Token validation, routing, correlation ids, rate limiting, CORS. No business logic. |
| 2 | `auth-service` | 8081 | `nexa_auth` | Credentials, password hashing, JWT issuance and refresh, token revocation, account lock state. The only service that ever sees a password. |
| 3 | `user-service` | 8082 | `nexa_user` | Profile, preferences, theme, notification settings. User administration for ADMIN. Learns of accounts from `auth.user.registered.v1`. |
| 4 | `chat-service` | 8083 | `nexa_chat` | Conversations, messages, the memory window, streamed answer relay, per-user quota enforcement before spending. |
| 5 | `ai-service` | 8084 | — | **Stateless.** Multi-model LLM inference and streaming through Spring AI. Holds model-provider credentials. Knows nothing of users, conversations or documents. |
| 6 | `document-service` | 8085 | `nexa_document` | Upload, text extraction, chunking. Publishes chunk events. Never embeds. |
| 7 | `rag-service` | 8086 | `nexa_rag` | Embeddings, pgvector storage, similarity retrieval with per-user scoping. The only service that loads pgvector. |
| 8 | `subscription-service` | 8087 | `nexa_subscription` | Plan catalogue, entitlements, usage aggregation, Razorpay test orders and webhooks. |

Each is an independent application with its own `pom.xml`, `application.yml`, source tree, tests
and Dockerfile. There is no aggregator POM (`RULES.md` §1).

### 1.3 Why these eight

The split follows the axes along which the product must change independently:

- **Security boundary.** Auth is separate because credential handling has a different threat
  model, a different audit requirement, and a different change cadence than chat.
- **Statelessness.** AI inference is separate and holds no data, so it scales horizontally on
  nothing, and a provider outage is isolated to one service.
- **Cost shape.** Embedding and retrieval are separate from generation, because they are
  called at completely different frequencies and have completely different costs.
- **Ownership.** Document storage and vector storage are separate, because they have different
  lifecycles: a document is deleted by the user, a vector is a derived artefact.

A split that does not buy independent scaling, independent deployment or a real security
boundary is decoration (`RULES.md` §2).

---

## 2. Communication

### 2.1 REST — synchronous

Used for request/response: gateway routing, service-to-service calls, SSE streaming, and
webhooks.

Rules:
- The **only** public URL space is the gateway. The frontend never calls a service port.
- Internal service-to-service calls use HTTP between container names (`http://chat-service:8083`),
  configured per service via its own `*_SERVICE_URL` property.
- Every versioned endpoint is `/api/v1/...` from the client's perspective and `/internal/v1/...`
  between services.
- `GET /internal/v1/<service>/info` reports each service's own boundary. It exists so a
  boundary violation is visible rather than assumed.
- Timeouts are mandatory on every internal call. An unbounded call is a cascading failure.
- Correlation ids propagate `X-Correlation-Id` across every hop.

### 2.2 Kafka — asynchronous

Used for events, background work, analytics, document processing and payment notification.

Rules:
- Events are facts in the past tense: `chat.message.completed.v1`, not `send-message.v1`.
- Producers never depend on consumers. A consumer being down loses no data.
- Consumers must be idempotent, because Kafka gives at-least-once delivery. Deduplicate on an
  event id, not on business state.
- Schema evolution is additive only. A field is never removed or retyped; it is deprecated and
  retained.
- Each topic has exactly one owning service that produces it. A consumer never writes to a
  topic it does not own.

Full topic list, payloads and ownership: [`SERVICE_CONTRACTS.md`](SERVICE_CONTRACTS.md) §10.

### 2.3 Choosing between them

| Situation | Mechanism |
|---|---|
| The caller needs an answer to continue | REST |
| A streamed answer to the browser | REST / SSE |
| The work does not need to finish before responding | Kafka |
| Notifying others that something happened | Kafka |
| A payment provider calling us | REST webhook |
| Analytics, audit, notifications | Kafka |

**A common error is using Kafka as an RPC channel** (request, block, wait for a reply topic).
If the caller needs the result, it is REST.

---

## 3. Database ownership

### 3.1 One database and one role per service

| Service | Database | Role | Notes |
|---|---|---|---|
| api-gateway | *none* | *none* | Stateless by design |
| auth-service | `nexa_auth` | `nexa_auth` | Credentials, refresh tokens, revocation |
| user-service | `nexa_user` | `nexa_user` | Profile, preferences |
| chat-service | `nexa_chat` | `nexa_chat` | Conversations, messages |
| ai-service | *none* | *none* | Stateless by design |
| document-service | `nexa_document` | `nexa_document` | Document metadata + binary storage |
| rag-service | `nexa_rag` | `nexa_rag` | Chunks, vectors, pgvector |
| subscription-service | `nexa_subscription` | `nexa_subscription` | Plans, entitlements, usage, payments |

**No service reads another service's database** (`RULES.md` §3). Enforced twice: by PostgreSQL
grants, and by CI asserting the `nexa_*` names appearing in a service's sources are either
empty or exactly its own.

### 3.2 The two stateless services

`api-gateway` and `ai-service` own no database. This is a deliberate design statement, not an
optimisation: they must be trivially horizontally scalable, and a datasource is a dependency
that makes that harder to reason about. CI fails the build if either declares a persistence
starter or a datasource.

### 3.3 Migrations

Each service owns its Flyway migrations under `backend/<service>/src/main/resources/db/migration`.
A service migrates its own database at startup and nobody else's, ever.

### 3.4 Cross-service data needs

When one service needs another's data, it does not query it. It either:

1. **Calls** the owning service over REST, or
2. **Keeps its own copy**, derived from a Kafka event.

Duplication here is intentional. A denormalised read model that is rebuilt from events is a
feature; a cross-service join is a coupling bug.

---

## 4. Redis

Redis is a **cache, rate-limit counter store, token denylist and short-lived lock holder**.
It is never the system of record (`RULES.md` §7).

| Use | Key shape | TTL | If lost |
|---|---|---|---|
| Refresh-token denylist | `auth:deny:<jti>` | token lifetime + margin | Session survives revocation until expiry |
| Rate-limit counters | `rl:<scope>:<subject>` | window | Limit resets early |
| Model catalogue cache | `user:models` | minutes | Recomputed from DB |
| Profile cache | `user:profile:<id>` | minutes | Re-read from User Service |
| Short-lived lock | `lock:doc:<id>` | seconds | Operation retried |

`infrastructure/docker/redis/redis.conf` enables **no persistence**. That is the enforcement
mechanism: if something important were stored in Redis, the absence of durability would make it
a bug quickly rather than quietly.

**Keyspace notifications are not used.** Invalidation is driven by the Kafka event that caused
it, so there is exactly one message bus to reason about.

---

## 5. Kafka and KRaft

Kafka runs in **KRaft mode only**. ZooKeeper is prohibited (`RULES.md` §4).

### 5.1 Topology

| | |
|---|---|
| Node | `kafka-1`, combined `broker,controller` |
| Broker id | `1` |
| Controller quorum voters | `1@kafka:29093` |
| Internal listener | `kafka:29092` — used by services |
| External listener | `localhost:9092` — host tooling only |
| Cluster id | Fixed for local development |

### 5.2 Single-node caveat

Replication factors are `1` locally. A production deployment needs at least three brokers and
replication factor `3` for the offsets topic and the transaction state log. This is recorded in
[`SECURITY.md`](SECURITY.md) and in `infrastructure/docker/kafka/README.md` rather than left
implicit.

### 5.3 Consumer groups

One consumer group per service per concern, named `<service>-<concern>`, e.g.
`subscription-usage-aggregation`. Grouping is what gives independent scaling: adding a replica
of `subscription-service` splits partitions across the group without duplicate aggregate
writes, provided the consumer is idempotent.

### 5.4 Why Kraft matters here

KRaft removes a whole distributed system (ZooKeeper) from the operational surface: no separate
memory tuning, no separate failover, no separate version matrix. For a project with one broker
in development, that is the difference between one thing to run and two.

---

## 6. AI architecture

Spring AI is the **core** AI integration layer. No service below it may call a provider SDK
directly.

```
chat-service ──REST/SSE──▶ ai-service ──▶ Spring AI ChatClient
                                              ├── OpenAI      (chat + embeddings)
                                              ├── Google Gemini (chat + embeddings)
                                              └── Groq / LLaMA (chat)
```

### 6.1 The AI Service is stateless

It receives a fully-built request — system prompt, messages, model identifier, parameters — and
returns tokens. It holds:

- no user records
- no conversation records
- no document content
- no database of any kind

Consequences, all intended:

- It scales horizontally with no session affinity.
- It can be restarted with no data loss, because it has none.
- Provider credentials exist in exactly one service, not eight.

### 6.2 Provider credentials

Read from environment variables (`OPENAI_API_KEY`, `GEMINI_API_KEY`, `GROQ_API_KEY`) in the
**AI Service only**, plus embeddings keys in RAG Service. Never in source, never logged, never
returned by any endpoint (`RULES.md` §6).

### 6.3 Streaming

Tokens are streamed from the provider through the AI Service over SSE to Chat Service and on to
the browser. Every hop must disable buffering, or the user sees nothing and then everything
(`infrastructure/docker/nginx/nexaai.conf`).

### 6.4 Failure handling

Provider errors are classified, not passed through raw:

| Class | Handling |
|---|---|
| Auth / configuration | Fail fast at startup. A missing key is not a runtime error. |
| Rate limited | Retry with backoff, then a user-visible message. Never a 500. |
| Timeout | Retry once, then abort the stream cleanly and keep the user input. |
| Content filtered | Specific message. Never retried. |
| Provider outage | Circuit breaker opens; fail fast with a clear message. |

A half-streamed answer must be persisted as an interrupted assistant message, not a complete
one.

---

## 7. RAG architecture

```
document-service                rag-service
─────────────────               ───────────────────────────────
upload                          (owns everything here)
  │ extract text
  ├─ publish document.chunked.v1 ──▶ embed chunks (Spring AI)
  │                                  └─▶ store vector + text ─▶ pgvector
  │
  └─ never embeds. Ever.

chat-service ──REST──▶ rag-service.retrieve(question, userId, documentIds?)
                         1. embed the question
                         2. similarity search, SCOPED BY userId
                         3. return top-k passages with document + chunk ids
```

### 7.1 Rules

1. **pgvector only in `nexa_rag`.** No other NexaAI database loads the extension.
2. **Tenant isolation is part of the query.** `WHERE user_id = :userId` is in the SQL itself.
   Filtering after retrieval is a data leak.
3. **Document Service never embeds.** It produces text chunks. RAG Service embeds. Embedding is
   a retrieval concern.
4. **Chunks carry their document id and chunk index**, so a citation is always possible.
5. **Deletions are derived.** Deleting a document deletes its vectors, driven by
   `document.deleted.v1`, so the vector store cannot outlive its source.

### 7.2 Chunking

Text is split on structure first (headings, paragraphs) and only then on size, with overlap.
Splitting purely by character count destroys the sentence boundaries that make a retrieved
passage readable.

### 7.3 Retrieval shape

`embedding vector(<dim>)` with an HNSW index for approximate nearest neighbour, plus a
`user_id` column indexed alongside, because the tenant filter must be applied before the
similarity search, not after.

---

## 8. Payment architecture

Razorpay, **test mode only** (`RULES.md` §9). Subscription Service is the only service that
knows Razorpay exists.

```
browser ──▶ gateway ──▶ subscription-service.createOrder   (server-side, test mode)
                             │  returns order id + public TEST key id
                             ▼
                       Razorpay test checkout UI
                             │
                             ▼
                       Razorpay ──webhook──▶ subscription-service
                                                 1. verify signature (constant time)
                                                 2. idempotently record the payment
                                                 3. grant entitlements
                                                 4. publish subscription.activated.v1
```

### 8.1 Rules

- **No live mode.** There is no live-mode variable anywhere in this repository. The service
  refuses to start unless the mode is `test`.
- **Order creation is server-side.** The amount and currency come from the plan in the
  database, never from the client.
- **The webhook is the source of truth** for payment state, not the browser's redirect. A user
  closing the tab must not lose a payment.
- **Signatures are verified in constant time** before any payload is trusted.
- **Webhooks are idempotent**, keyed on the provider's payment id, because providers retry.
- **The public test key id may reach the browser.** The key secret and webhook secret may not.

---

## 9. Frontend architecture

React + Vite + TypeScript + Bootstrap 5 + SCSS, Lucide icons. No Tailwind, no Next.js, no Vue,
no Angular (`RULES.md` §5).

### 9.1 Structure

```
frontend/nexa-ai-web/src/
├── main.tsx              entry point; imports Bootstrap JS once
├── App.tsx               shell and routing
├── styles/
│   ├── _tokens.scss      design tokens: colour, space, radius, type
│   ├── _theme.scss       light / dark / system themes
│   ├── _app.scss         component styles
│   └── main.scss         imports Bootstrap, then the layers above
├── api/                  one typed client, no ad-hoc fetch in components
├── features/             one folder per bounded area: auth, chat, documents, rag, subscription, admin
├── components/           shared presentational components
└── hooks/
```

### 9.2 Rules

- **One API client.** Components never call `fetch` directly. Centralising means auth headers,
  error mapping, correlation ids and timeouts are handled once.
- **No secrets, ever.** Vite inlines every `VITE_*` variable into the bundle. The frontend has
  no API keys and cannot have any.
- **Tokens are held in memory** with refresh on load. `localStorage` is acceptable only if
  documented, and never for anything else.
- **Server-Sent Events** are consumed with `fetch` and a `ReadableStream`, **not** with
  `EventSource`. `EventSource` is GET-only, and sending a chat message is a POST with a body.
  Every workaround for that — base64 the prompt into a query string, or open a second GET once the
  POST has created the message — puts a credential or a prompt somewhere that is easy to log by
  accident. It is still not a WebSocket: the transport is one-directional, and the response body is
  a `text/event-stream` parsed frame by frame.
  Two consequences worth knowing: `fetch` needs an `AbortController` for **stop**, and the frame
  parser must handle frames split across network chunks, because packet boundaries and SSE frame
  boundaries do not agree.
- **Heavy, optional rendering is loaded on demand.** KaTeX (maths) and highlight.js (syntax
  highlighting) together are ~132 kB gzipped, more than everything else in the application, and the
  majority of answers contain neither a formula nor a code block. Both are separate chunks fetched
  the first time a message actually needs them. The chunk boundary lives in `React.lazy` and
  `import()` calls in the source — **not** in bundler group patterns, because a dependency rename
  silently breaks a pattern-based boundary and nothing fails.
- **Accessibility is part of the definition of done**: semantic elements, visible focus, labelled
  controls, contrast that passes AA. `prefers-reduced-motion` is honoured for the streaming
  indicator.

### 9.2a The bundle budget

Two caps, **both enforced** by `frontend/nexa-ai-web/scripts/check-bundle-budget.mjs`:

| Cap | Limit | What it measures |
|---|:--:|---|
| **Initial** | 200 kB gzip | The static import closure of the HTML entry point — what a browser downloads before the page is usable |
| **Total** | 320 kB gzip | Every JavaScript chunk, including ones fetched only on demand |

Both are needed. An initial-only cap lets the codebase grow without limit; a total-only cap punishes
deferring a heavy dependency most users never need, which is the opposite of what a budget is for.
The total is a **ceiling, not a target**, and it is lowered whenever the total falls.

The check reads Vite's build manifest (`build.manifest: true`) and traverses `imports`, never
`dynamicImports`. Traversal is by manifest **key**, not by output path — the two differ, and
resolving on the wrong one reports a near-zero payload and passes.

**Why this replaced a simpler check.** The previous version summed every `.js` file in `dist` and
compared that to the 200 kB *initial* budget. It measured a different quantity than the budget
describes, and it made code-splitting strictly worse: moving code into a chunk changed nothing for
the check while genuinely improving the number a user experiences. A check that penalises the
technique it exists to encourage gets satisfied by deleting the feature.

The replacement is **two** checks rather than one, and both have been verified to fail when their
cap is lowered below the measured value, and to fail loudly when the manifest is missing rather than
reporting zero. A budget check that cannot fail is not a check.
### 9.3 Theme

Bootstrap's `data-bs-theme` attribute on `<html>` drives light, dark and system. The token layer
overrides Bootstrap's CSS variables, so components use Bootstrap classes and still follow the
theme. See §9.3 of this document.

---

## 10. Deployment architecture

### 10.1 Local

Docker Compose with profiles, so a backend developer pays for infrastructure only, and a
frontend developer needs nothing but the gateway.

| Profile | Brings up |
|---|---|
| `infra` | PostgreSQL + pgvector, Redis, Kafka (KRaft) |
| `backend` | the eight services |
| `frontend` | the built frontend |
| *(none)* | everything |

Ports: frontend `8088`, edge `8089`, gateway `8080`, services `8081`–`8087`, PostgreSQL `5432`,
Redis `6379`, Kafka `9092` external / `29092` internal.

### 10.2 Containers

One image per service, each built from its own directory. `docker build backend/chat-service`
cannot compile `user-service`, because the sibling service is not in the build context. That
property is the point.

### 10.3 Production expectations

Phase 0 builds a local development topology. A real deployment additionally requires:

- at least three Kafka brokers, replication factor `3`
- managed PostgreSQL with automated backups and point-in-time recovery
- TLS everywhere, HSTS at the edge
- secrets from a secret store, never from images
- secrets rotation, and a documented rotation procedure
- horizontal scaling on the stateless services first (`api-gateway`, `ai-service`)
- observability: metrics, structured logs, tracing across the correlation id

These are tracked in Phase 11 and [`SECURITY.md`](SECURITY.md).

### 10.4 Scaling

| Service | Scales on | Notes |
|---|---|---|
| api-gateway | request rate | stateless |
| ai-service | concurrent generations | stateless; provider rate limits are the real ceiling |
| rag-service | query rate | the heavy CPU/IO service under load |
| chat-service | active streams | long-lived SSE connections need connection headroom |
| auth-service | login rate | rarely the bottleneck |
| others | request rate | — |

---

## 11. Observability

- **Correlation id** generated at the gateway if absent, propagated to every service, returned
  to the client in a response header, and attached to every log line and every Kafka event.
- **Structured logs**, JSON in any deployed environment, one line per request.
- **Actuator health** per service, with liveness and readiness separated so a slow database
  does not cause a restart loop.
- **No prompt or document content in logs.** Content is the user's data; logs are widely
  readable.
- **No credentials in logs**, ever, including on exception paths where a configuration value is
  easy to print by accident.

---

## 12. What is deliberately not here

- No service mesh. Eight services do not need one, and it would add a failure mode without
  removing one.
- No shared library. See [`RULES.md`](RULES.md) §2.
- No Redis as a database. See §4.
- No ZooKeeper. See §5.
- No aggregator build. See §1.2.

---

## 13. Why the architecture is shaped this way

Each entry is a decision that constrains everything else, with the reasoning and the unwelcome
consequence. A decision without its consequence recorded is a preference, not a decision.

| # | Decision | Reasoning | Consequence accepted |
|:--:|---|---|---|
| 001 | Eight independent Spring Boot applications | The split follows the axes along which the product must change independently: a security boundary, statelessness, cost shape and data ownership | Eight deployments, eight sets of logs and health checks. Cross-service features need a network call and an eventual-consistency story |
| 002 | One database and one role per service | Isolation enforced by PostgreSQL, not by discipline | Six databases on one instance, so a host failure takes everything down. No cross-service joins: a report spanning services is assembled from events |
| 003 | Kafka in KRaft mode | ZooKeeper is a second stateful system that nothing uses | Single-node replication factors locally; production needs three brokers |
| 004 | REST for synchronous, Kafka for asynchronous | The deciding question is whether the caller needs an answer to proceed | Two consistency models. Consumers must be idempotent, because Kafka is at-least-once |
| 005 | Redis is a cache only | Losing every key must degrade performance, never correctness | Every read needs a database fallback, so there is more code |
| 006 | Spring AI is the sole AI layer | Provider quirks contained in one module | The lowest common denominator is available; provider-specific features need an escape hatch |
| 007 | AI Service is stateless | Scales with no affinity; provider credentials exist in one place only | The caller must assemble the full prompt, so Chat Service must know each model's context size |
| 008 | pgvector owned solely by RAG Service | One owner for the vector store | Changing embedding models means re-embedding every document |
| 009 | Razorpay test mode only | No live key can be committed by accident | The live-mode integration path is never exercised |
| 010 | Exactly two roles | A small authorisation matrix small enough to verify by hand | An ADMIN sees billing they arguably should not; audit is the mitigation |
| 011 | Bootstrap 5 + SCSS, no Tailwind | The design language stays in one reviewable file | Bootstrap needs real restyling to look designed |
| 012 | No aggregator POM | Makes service independence structural, not conventional | The Spring Boot version is repeated eight times: an eight-line change, cheaper than the coupling |
| 013 | Duplicate DTOs, no contract jar | A shared jar is how a disguised monolith forms | The same field is written twice. Contract tests catch divergence |
| 014 | Gateway authenticates, services authorise | An internal network is not a trust boundary | Verification runs twice; negligible for RS256, but a real cost |
| 015 | Planned service directories stay empty | Nothing in the repository pretends to work | The structure is asserted by rule checks rather than demonstrated by a build |
| 016 | Phase-aware CI | A red build must always mean a real failure | CI discovers services rather than listing them, so it is slightly more complex |
| 017 | Memory is a bounded visible window | Inspectable beats sophisticated for a grounded-answer product | Very long conversations lose early context, which is surfaced rather than hidden |
| 018 | No service mesh | Disproportionate at eight services | No automatic mTLS between services; every service configures its own timeouts |
| 019 | Per-service Flyway migrations | The data owner owns its schema | No coordinated migration across services, so cross-service changes must be backward-compatible |
| 020 | Consumer-driven topic creation | The producer is accountable for its schema | Requires CREATE ACLs for producers |
| 021 | SSE rather than WebSocket | Unidirectional stream; plain HTTP | Every proxy hop must disable buffering, or tokens arrive all at once at the end |

---

## 14. Implementation phases

Services are built in dependency order. Only the current phase is implemented; nothing is built
ahead.

| Phase | Scope | Status |
|:--:|---|:--:|
| 0 | Repository, architecture, infrastructure, CI, frontend shell | Complete |
| 1 | Auth Service: registration, sign-in, tokens, RBAC, Google OAuth | Complete |
| 2 | User Service + API Gateway: profile, preferences, administration, routing, edge auth | Complete |
| 3 | Chat Service + frontend foundation: conversations, messages, feedback, export | Complete |
| 4 | AI Service: Spring AI providers, streaming. Completes the `ChatGenerationPort` seam | Complete |
| 5 | Chat: memory window, message persistence of generated replies | Not started |
| 6 | Document Service: upload, extraction, chunking | Not started |
| 7 | RAG Service: embeddings, pgvector, retrieval | Not started |
| 8 | RAG integration: grounded answers, citations | Not started |
| 9 | Subscription Service: plans, entitlements, Razorpay test orders | Not started |
| 10 | Frontend product UI: chat, documents, admin | Not started |
| 11 | Security and operations: hardening, observability, performance | Not started |
| 12 | Delivery: documentation, release, deployment | Not started |

---

## 15. Technology versions

Resolved against live registries, not assumed.

| Component | Version | Note |
|---|---|---|
| Java | 21 (LTS) | |
| Spring Boot | 4.1.1 | Latest 4.x GA. 4.2.x is still a milestone |
| Spring Security | 7.1.1 | See the API changes in §16 |
| PostgreSQL | 17 | pgvector build |
| Redis | 7 | No persistence, by design |
| Kafka | 4 (KRaft) | No ZooKeeper |
| JJWT | 0.12.6 | RS256 |
| springdoc-openapi | 2.8.13 | |
| Bouncy Castle | 1.81 | Argon2id |
| React / Vite | 19 / 8 | |
| TypeScript | 6.0 (strict) | |
| Bootstrap / Sass | 5.3 / 1.105 | |
| Lucide | 0.499 | |

---

## 16. Spring Boot 4 and Spring Security 7 migration notes

Both are new major versions. These APIs moved or were removed, and each was found by compiling
rather than by reading:

| Change | Consequence |
|---|---|
| `AntPathRequestMatcher` **removed** | Replaced by `PathPatternRequestMatcher`, with the argument order **reversed**: `(HttpMethod, pattern)` |
| `HttpSecurity.disable()` and `AbstractHttpConfigurer::disable` **removed** | Not needed. Declaring an explicit `SecurityFilterChain` makes Spring Boot's defaults back off, so form login, basic auth and logout are absent unless invoked |
| Jackson 2 replaced by **Jackson 3** (`tools.jackson`) | `ObjectMapper` moved packages. Spring Boot 4 no longer auto-configures a Jackson 2 mapper |
| Flyway auto-configuration moved to `spring-boot-flyway` | Without it migrations are **silently not run**: no error, an empty database, then Hibernate reporting every table missing |
| `@AutoConfigureMockMvc` moved to `spring-boot-webmvc-test` | Same failure mode: the annotation is simply absent |

## 16a. Spring Cloud Gateway 5 findings

Gateway was introduced in Phase 2. Four things about it cost time, and all four fail in ways that
look like something else. They are recorded here because the next person to touch the gateway will
hit at least one of them.

| Finding | Symptom when got wrong |
|---|---|
| The properties prefix is **`spring.cloud.gateway.server.webflux`**, not `spring.cloud.gateway` | The old prefix is *silently ignored*. The application starts, health is green, there are **zero routes**, and every request 404s at the gateway |
| `spring-boot-starter-oauth2-resource-server` must **not** be a dependency | It auto-configures a security chain that runs before the gateway's `GlobalFilter`s and rejects everything in its own format. Every request gets an **empty-body 401**, and the gateway's own verification never runs |
| The reactive starter was renamed | `spring-cloud-starter-gateway` (4.x) → `spring-cloud-starter-gateway-server-webflux` (5.x). The old name resolves to a version incompatible with Boot 4 |
| `StripPrefix` is not a substitute for `RewritePath` | `StripPrefix=1` on `/api/auth/login` yields `/auth/login`. Auth Service answers on `/api/v1/auth/**`, so every auth route 404s **at the upstream**, which reads like a broken service rather than a broken route |

**Release train.** Gateway 5 pairs with Spring Cloud `2025.1.x`, resolved from Maven Central
rather than assumed. Spring Cloud and Spring Boot versions must move together; guessing produces a
runtime failure that presents as a code bug.

**A gotcha worth remembering in any codebase.** `ServerWebExchange.getAttribute` is declared
`<T> T getAttribute(String key)`. Passing its result directly into `String.valueOf(...)` makes
javac infer `T = char[]`, because `valueOf(char[])` is a more specific overload than
`valueOf(Object)`. It compiles, and it throws `ClassCastException` at runtime. Assign to `Object`
first.

---

## 16b. Spring AI 2.0.1 findings

Every item here was found by the service failing to start or a test failing. Each is a property of
Spring AI 2.0.1, not a preference, and each one is silent — the symptom is always "it works,
except…".

### 16b.1 `spring.ai.model.chat` is single-valued and inverts on absence

Both the OpenAI and the Google Gemini auto-configurations are annotated:

```java
@ConditionalOnProperty(name = "spring.ai.model.chat",
                       havingValue = "openai",     // or "google-genai"
                       matchIfMissing = true)
```

`matchIfMissing = true` reads as "enable this when the operator has not chosen a provider". It
does the opposite. **Absent property activates both**, and each then throws because no api-key is
set:

```
Incomplete Google GenAI configuration: Provide 'api-key' for Gemini API or 'project-id' ...
```

So merely having the starters on the classpath means the process cannot start on a machine that has
never heard of Google. Setting the property to a value that matches neither provider (`none`)
disables both, and the service then starts with every provider reporting itself unavailable — which
is the required behaviour here, because **a provider with no credential must be a supported state,
not a boot failure.**

`spring.ai.model.chat` also holds exactly one provider name, not a list. Serving three providers
from one process is therefore not something that property can do, which is why `nexa.ai.providers`
exists as a per-provider map that names its own Spring AI bean.

### 16b.2 The non-chat auto-configurations demand credentials too

`spring-ai-starter-model-openai` contributes six auto-configurations. Only `OpenAiChat*` is gated by
`spring.ai.model.chat`; the embedding, image, audio-speech, audio-transcription and moderation ones
activate on their own properties with `matchIfMissing = true` and each fails startup wanting a
credential this service has no use for:

```
At least one credential source must be specified: credential (apiKey), workloadIdentity, or adminApiKey
```

They are excluded explicitly in `application.yml` via `spring.autoconfigure.exclude`. That is not
tidiness — it is what lets the service start with no keys at all.

### 16b.3 The chat API lives in `spring-ai-model`, reached through `spring-ai-client-chat`

`Prompt`, `ChatResponse`, `ChatModel`, `StreamingChatModel`, the message types and `Usage` are in
`spring-ai-model`. The starters reach it only transitively, through `spring-ai-client-chat`, so
`spring-ai-client-chat` is declared as a direct dependency. Compiling against a
transitively-present API and losing it on a dependency bump is a break that arrives in a release
rather than in a review.

### 16b.4 There is no Groq starter

Groq speaks the OpenAI wire protocol. It is served by the OpenAI integration with a different base
URL and a different credential, which is why the configuration looks like two OpenAI providers when
only one of them is OpenAI.

### 16b.5 Constructor shapes differ from Spring AI 1.x

`OpenAiChatModel.Builder` takes `options(OpenAiChatOptions)`, not `defaultOptions(...)`, and
requires a `com.openai.client.OpenAIClient`. `Prompt.Builder` has no `options(...)` method — options
are supplied to the `Prompt` constructor. Both were found by compiling; neither is discoverable from
the documentation.

## 16c. Generation is wired through a stream, not through `ChatGenerationPort`

The streamed path is complete end to end: browser → Chat Service → AI Service → Spring AI → provider.
The blocking `ChatGenerationPort` still resolves to `DisabledChatGenerationPort`.

The reason is a deliberate choice, not an oversight. `ChatGenerationPort.generate(...)` takes a
conversation, a message and a placeholder, and has no authenticated caller on its signature — it is
called from `MessageService` with no token in scope. Forwarding the user's token would mean either
storing the raw token on the security context, which `AuthenticatedCaller.getCredentials()`
deliberately refuses to do, or widening the port's contract to take a credential, which would put a
bearer token into a method signature that the append-only message history would eventually be asked
to serialise.

The correct fix is a **service credential**: Auth Service issues a token with a service identity
that names the user it is acting for, and Chat Service forwards that instead of the user's own
token. That is an impersonation flow and a token-lifetime decision, so it belongs to its own
change rather than being smuggled in here.

What *was* built for it: `CallTokenHolder`, a request-scoped holder the JWT filter populates and
clears in a `finally`, used only by the streaming client. It is deliberately **not** a field on
`AuthenticatedCaller`, because the authentication object travels through the security context, the
audit trail and log statements, and the principal must not be able to read its own credential.

---

| Service | Port | Status |
|---|:--:|---|
| auth-service | 8081 | **Implemented.** 87 tests pass against real PostgreSQL 17 |
| user-service | 8082 | **Implemented.** 120 tests pass against real PostgreSQL 17 |
| api-gateway | 8080 | **Implemented.** 51 tests pass, over real HTTP |
| chat-service | 8083 | **Implemented.** 92 tests pass against real PostgreSQL 17 |
| ai-service | 8084 | **Implemented.** 119 tests. Stateless — owns no database |
| document-service | 8085 | Not started |
| rag-service | 8086 | Not started |
| subscription-service | 8087 | Not started |

**469 tests pass across the five implemented services:** auth 87, user 120, api-gateway 51,
chat 92, ai 119.

**Verified for auth-service:** registration, sign-in, RS256 tokens in HTTP-only cookies, refresh
rotation with reuse detection, logout, email verification, password reset and change, Google OAuth,
a transactional outbox, CSRF protection, and database isolation proven in both directions.

**Verified for user-service:** self-service profile/preferences/status with no route that names a
user, administrative listing/search/filter/detail, activate/suspend/deactivate with an
append-only audit trail, the profile created by consuming `auth.user.registered.v1`, and event
replay proven not to duplicate a profile. Isolation from Auth Service's database is proven by
connecting as the application role and being refused, not by reading the source.

**Verified for api-gateway:** routing with prefix rewriting, token verification at the edge, the
strip-then-set behaviour of the identity headers, refusal of `alg: none` and of the
algorithm-confusion attack, correlation-id sanitisation, CORS allow-listing, and JSON errors from
the gateway itself. Every routing assertion is made against a recording upstream over real HTTP.

**Verified for chat-service:** create, list, get, rename, delete, archive and restore, search by
title or message text, send, regenerate, edit-and-resend, feedback, and export as Markdown or
JSON. User isolation proven by asserting another user's conversation returns a 404 byte-identical
to one that does not exist, and that an ADMIN can read metadata but is given no route to any
user's message content. Database isolation proven by connecting as the application role and being
refused.

**Verified for ai-service:** model selection (default, named, case-insensitive, unknown is a 400
listing the real names), same-provider fallback, opt-in cross-provider fallback, bounded retry with
capped backoff, provider-failure classification, token-limit enforcement, refusal of a
temperature a model does not support, availability with a reason for every unavailable provider,
provider health, in-memory usage, and Server-Sent Events streaming asserted against the wire
format. Authorization proven with forged, unsigned (`alg: none`), algorithm-confused, expired,
wrong-issuer and wrong-audience tokens, and with an assertion that no response body ever contains
a provider credential.

**Not yet wired:** the blocking `ChatGenerationPort` in Chat Service still resolves to
`DisabledChatGenerationPort`, so a sent message leaves a `PENDING` placeholder. The streamed path
IS wired end to end (browser → Chat Service → AI Service). The reason is in §16c — closing the
blocking path properly needs a service credential rather than relaying the user's own token, and
that is a decision, not a detail.

**Verified for the frontend:** it compiles, type-checks under `strict`, lints, and builds; the
theme tokens and Bootstrap variable overrides reach the built CSS; no credential appears in the
bundle. Routing, shell, auth pages, chat layout, sidebar, conversation list, message components,
loading/error/empty states, the responsive drawer and the theme foundation are all in place and
type-checked.

**Not yet wired:** AI generation. The `ChatGenerationPort` seam exists with no implementation, so
a sent message creates a PENDING placeholder and it stays PENDING � which is the honest state, not
a fabricated reply and not a false error.

**Not yet wired:** Kafka publishing (auth-service's outbox is written but no broker was available;
user-service's listener is gated behind `NEXA_USER_EVENT_ENABLED` and has never seen a real
message), email delivery, Redis, and the Subscription Service call, which is behind an interface
that reports "unavailable" rather than zero until Phase 9 exists.

**Not yet verified:** the Compose stack has never been started, because Docker was never available
in the development environment. The Compose file is validated by parsing and review only. No
container image has ever been built for any service.