<div align="center">

# NexaAI

**Multi-model AI chat, grounded in your own documents.**

An AI chat SaaS built as **genuine microservices** — eight independent Spring Boot
applications, each owning its own data, communicating over REST and Kafka.

</div>

---

## Status

**Phase 0 — complete.** Architecture, documentation, service boundaries, infrastructure and a
buildable frontend toolchain.

**No business functionality exists yet, by rule.** The eight service directories under
`backend/` are empty apart from a README stating each boundary
([`docs/RULES.md`](docs/RULES.md) §10, [`docs/DECISIONS.md`](docs/DECISIONS.md) ADR-015).

| | |
|---|---|
| Backend | Java 21 · Spring Boot 4.x · Maven — **planned, no code yet** |
| AI | Spring AI 2.x · OpenAI · Google Gemini · Groq/LLaMA — **planned** |
| Data | PostgreSQL 17 + pgvector · Redis cache — **configured, not yet run** |
| Messaging | Apache Kafka 4 in **KRaft** mode, **no ZooKeeper** — **configured, not yet run** |
| Payments | Razorpay **TEST MODE ONLY** |
| Frontend | React 19 · Vite · TypeScript strict · Bootstrap 5 · SCSS · Lucide — **working** |
| Delivery | Git · GitHub Actions (phase-aware) |

---

## Services

Eight independent applications. Each has its own directory, port, configuration, source tree,
tests and Dockerfile. **No service reads another service's database.**

| Service | Port | Responsibility | Database | Phase |
|---|:--:|---|---|:--:|
| [api-gateway](backend/api-gateway) | 8080 | Public entry point, routing edge | *none* | 3 |
| [auth-service](backend/auth-service) | 8081 | Credentials, tokens, account status | `nexa_auth` | 1 |
| [user-service](backend/user-service) | 8082 | Profile, preferences, administration | `nexa_user` | 2 |
| [chat-service](backend/chat-service) | 8083 | Conversations, memory, streamed relay | `nexa_chat` | 5 |
| [ai-service](backend/ai-service) | 8084 | Stateless multi-model inference | *none* | 4 |
| [document-service](backend/document-service) | 8085 | Upload, extraction, chunking | `nexa_document` | 6 |
| [rag-service](backend/rag-service) | 8086 | Embeddings and pgvector retrieval | `nexa_rag` | 7 |
| [subscription-service](backend/subscription-service) | 8087 | Plans, entitlements, test orders | `nexa_subscription` | 9 |

Each service reports its own boundary at `GET /internal/v1/<service>/info` once implemented, and
the gateway reports its routes at `GET /internal/v1/gateway/info`.

---

## Documentation

| Document | Contents |
|---|---|
| [docs/PRD.md](docs/PRD.md) | Purpose, users, USER and ADMIN functionality, AI chat, multi-model, memory, documents, RAG, subscriptions, usage, analytics, security |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Service responsibilities, REST and Kafka communication, data ownership, Redis, RAG, AI, payments, frontend, deployment |
| [docs/RULES.md](docs/RULES.md) | Binding development rules and prohibitions |
| [docs/DESIGN.md](docs/DESIGN.md) | Frontend architecture and visual system |
| [docs/SERVICE_CONTRACTS.md](docs/SERVICE_CONTRACTS.md) | REST endpoints, DTO boundaries, Kafka topics and event ownership |
| [docs/TASKS.md](docs/TASKS.md) | The Phase 0–12 plan and current status |
| [docs/TEST_PLAN.md](docs/TEST_PLAN.md) | Test layers, per-phase plan, isolation and security matrices |
| [docs/SECURITY.md](docs/SECURITY.md) | Threat model, controls, secret handling, known gaps |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Architectural decision records with reasoning and consequences |
| [docs/MEMORY.md](docs/MEMORY.md) | **Read this first.** Current state, what exists, what to do next |

---

## Hard rules

These are enforced in CI, not merely documented. See [`docs/RULES.md`](docs/RULES.md).

1. **No monolith.** Eight independent Spring Boot applications. There is deliberately **no
   `backend/pom.xml`**.
2. **No fake microservices.** No placeholder controllers or stub business logic. A planned
   service directory is empty, and says so.
3. **No shared database access.** One database and one role per service, enforced by PostgreSQL
   grants and verified by a script.
4. **No shared business-logic module.** Contract types are duplicated deliberately; the shared
   artefact is a document, not a jar.
5. **No ZooKeeper.** Kafka runs in KRaft mode.
6. **No Tailwind.** Bootstrap 5 and SCSS only.
7. **No secrets in source.** Environment variables only, and **no AI provider key in React**.
8. **Razorpay test mode only.** No live-mode variable exists anywhere in this repository.
9. **Exactly two roles.** `USER` and `ADMIN`. No `SUPER_ADMIN`, no `MODERATOR`, no `SUPPORT`.
10. **CI must not hide failures.** Nothing is skipped, and a job with nothing to do says so.

---

## Getting started

### Prerequisites

Java 21, Maven 3.9+, Node 20+ (22 in CI), Docker with Compose v2.

### Run the infrastructure

```bash
cd infrastructure
cp .env.example .env          # local placeholders; real secrets stay out of git
docker compose --profile infra up -d
```

Brings up PostgreSQL (with pgvector), Redis and Kafka in KRaft mode, with one database and one
role per service.

Verify the isolation guarantee — **every row must read `ok`**:

```bash
docker compose --profile infra exec -T postgres \
  psql -U nexa_admin -d nexa_admin -f /verify/02-verify-ownership.sql
```

### Run the frontend

```bash
cd frontend/nexa-ai-web
npm install
npm run dev            # http://localhost:5173, /api proxied to the gateway
```

### Build a service

Each service builds **independently**, from its own directory:

```bash
cd backend/<service> && mvn -B clean verify
```

No service exists yet, so this has nothing to run today. `backend.yml` discovers services
automatically as they appear and needs no change.

### Ports

Frontend `5173` (dev) / `8088` (container), edge `8089`, gateway `8080`, services `8081`–`8087`,
PostgreSQL `5432`, Redis `6379`, Kafka `9092` external / `29092` internal.

---

## Repository layout

```
NexaAI/
├── docs/                      ten documents: PRD, architecture, rules, design, contracts,
│                              tasks, tests, security, decisions, memory
├── backend/
│   ├── api-gateway/           8080  routing edge
│   ├── auth-service/          8081  identity and tokens
│   ├── user-service/          8082  profile and administration
│   ├── chat-service/          8083  conversations, memory, streaming relay
│   ├── ai-service/            8084  stateless multi-model inference
│   ├── document-service/      8085  upload, extraction, chunking
│   ├── rag-service/           8086  embeddings and pgvector retrieval
│   └── subscription-service/  8087  plans, metering, Razorpay test mode
├── frontend/
│   └── nexa-ai-web/           React + Vite + TypeScript + Bootstrap + SCSS
├── infrastructure/
│   ├── docker/
│   │   ├── postgres/          per-service databases and roles, plus a verification script
│   │   ├── redis/             cache configuration
│   │   ├── kafka/             KRaft notes
│   │   └── nginx/             edge proxy and SSE configuration
│   └── docker-compose.yml
├── .github/workflows/         phase-aware CI
├── README.md
└── .gitignore
```

There is deliberately **no `backend/pom.xml`**. Each service is its own build, which is what
makes the microservices claim verifiable rather than aspirational.

---

## How a chat answer will flow

Not yet implemented. This is the contract Phase 5 builds to, from
[`docs/SERVICE_CONTRACTS.md`](docs/SERVICE_CONTRACTS.md) §7.

```
browser ──▶ NGINX ──▶ api-gateway ──▶ chat-service
                                          │  1. verify ownership
                                          │  2. check entitlement before spending
                                          │  3. persist the user message
                                          │  4. build the memory window
                                          ▼
                            rag-service ◀── 5. retrieve context (REST, if RAG is on)
                                          ▼
                            ai-service  ◀── 6. stream completion (REST/SSE)
                                          │  7. relay each token to the browser
                                          ▼
                                     browser
                                          │
   chat-service  8. persist the assistant message, publish chat.message.completed.v1
   ai-service    9. publish ai.inference.completed.v1 for metering
```

Each arrow is an explicit, versioned contract.

---

## Known gaps

Phase 0 is a foundation, and it is honest about what has not been proven.

| Gap | Detail |
|---|---|
| **Docker unavailable** | The Compose stack, both SQL scripts, `redis.conf` and `nexaai.conf` have **never been run**. The Compose file is validated by parsing only. See [`docs/MEMORY.md`](docs/MEMORY.md) §6.1. |
| **No backend build** | No `pom.xml`, so there is nothing for `mvn verify` to do. `backend.yml` reports this explicitly rather than passing silently. |
| **Versions unverified** | Spring Boot and Spring AI versions are **not** resolved against Maven Central. Phase 1 must pin real, existing versions. |
| **Documentation ahead of code** | The contracts specify endpoints and schemas that do not exist. Treat them as a specification to validate, not as proven truth. |
| **No security control implemented** | Every control in [`docs/SECURITY.md`](docs/SECURITY.md) is a specification. Fourteen gaps are catalogued in §13. |

### Validated in Phase 0

All 14 architecture rule checks pass against the real tree, the frontend installs, lints,
type-checks and builds (89.6 kB gzipped, within budget), the theme reaches the built CSS, and the
bundle contains no credential. The ZooKeeper and payment checks include self-tests that prove
they still catch real violations.

Validating the checks themselves found five defects, including a `.gitignore` line that would
have excluded all ten documents from the repository. All are fixed; see
[`docs/MEMORY.md`](docs/MEMORY.md) §5.1.

---

## Contributing

1. Read [`docs/RULES.md`](docs/RULES.md) first. It is not advisory.
2. Work only in the current phase of [`docs/TASKS.md`](docs/TASKS.md).
3. Inspect existing code before changing it, and preserve correct implementation.
4. A contract change updates [`docs/SERVICE_CONTRACTS.md`](docs/SERVICE_CONTRACTS.md) in the
   same change. A change of direction adds an entry to [`docs/DECISIONS.md`](docs/DECISIONS.md).
5. After every phase: build, test, fix genuine errors, update the docs, update
   `docs/MEMORY.md` and `docs/TASKS.md`, report what was completed and what remains, then stop.
6. Never hide a failure to make CI green. A red pipeline that means something is worth more than
   a green one that does not.

---

## Licence

Educational project. Razorpay is **test mode only**; no real money is ever processed.