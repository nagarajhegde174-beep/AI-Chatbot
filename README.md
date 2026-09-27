<div align="center">

# NexaAI

**Multi-model AI chat, grounded in your own documents.**

An intermediate-level AI Chat SaaS platform built as **genuine microservices** — eight
independent Spring Boot applications, each owning its own data, communicating over REST and
Kafka.

</div>

---

## Status

**Phase 0 — complete.** Architecture, documentation, service boundaries and a buildable
skeleton. No business feature is implemented yet, by design
([`docs/RULES.md`](docs/RULES.md) §7.7).

| | |
|---|---|
| Backend | Java 21 · Spring Boot 4.1.1 · Spring MVC · Spring Security · Spring Data JPA · Hibernate · Maven |
| AI | Spring AI 2.0.1 · OpenAI · Google Gemini · Groq/LLaMA · streaming · conversation memory |
| Data | PostgreSQL 17 + pgvector · Redis cache |
| Messaging | Apache Kafka 4 (KRaft, **no ZooKeeper**) |
| Payments | Razorpay **TEST MODE ONLY** |
| Frontend | React 19 · Vite 8 · TypeScript strict · Bootstrap 5 · SCSS · light / dark / system |
| Infrastructure | Docker · Docker Compose · NGINX |
| Delivery | Git · GitHub Actions · Swagger/OpenAPI · Postman |

---

## Services

Eight independent applications. Each has its own `pom.xml`, port, configuration, source tree,
tests and Docker image. **No service reads another service's database.**

| Service | Port | Responsibility | Database |
|---|---|---|---|
| [api-gateway](backend/api-gateway) | 8080 | Public entry point and routing edge | none |
| [auth-service](backend/auth-service) | 8081 | Credentials, tokens, account status | `nexa_auth` |
| [user-service](backend/user-service) | 8082 | Profile, preferences, user administration | `nexa_user` |
| [chat-service](backend/chat-service) | 8083 | Conversations, memory, streamed answer relay | `nexa_chat` |
| [ai-service](backend/ai-service) | 8084 | Stateless multi-model LLM inference | **none — stateless** |
| [document-service](backend/document-service) | 8085 | Upload, text extraction, chunking | `nexa_document` |
| [rag-service](backend/rag-service) | 8086 | Embeddings and pgvector retrieval | `nexa_rag` |
| [subscription-service](backend/subscription-service) | 8087 | Plans, entitlements, Razorpay test orders | `nexa_subscription` |

Every service reports its own boundary at `GET /internal/v1/<service>/info`, and the gateway
reports the services it routes to at `GET /internal/v1/gateway/info`.

---

## Documentation

| Document | Contents |
|---|---|
| [docs/PRD.md](docs/PRD.md) | Purpose, users, journeys, and all product requirements |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Service responsibilities, REST and Kafka communication, data ownership, Redis, RAG, AI, payments, frontend, Docker |
| [docs/RULES.md](docs/RULES.md) | Binding development rules and prohibitions |
| [docs/DESIGN.md](docs/DESIGN.md) | Branding, theming, responsive layout, chat UI, admin UI |
| [docs/SERVICE_CONTRACTS.md](docs/SERVICE_CONTRACTS.md) | REST endpoints, DTOs, Kafka topics, event schemas |
| [docs/TASKS.md](docs/TASKS.md) | The Phase 0–12 plan and current status |
| [docs/TEST_PLAN.md](docs/TEST_PLAN.md) | Test layers, per-phase plan, security and isolation matrices |
| [docs/SECURITY.md](docs/SECURITY.md) | Threat model, controls, secret handling, known gaps |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Architecture decision records with reasoning and consequences |
| [docs/MEMORY.md](docs/MEMORY.md) | Current state, what exists, and what to do next |

---

## Hard rules

These are enforced in CI, not merely documented. See [`docs/RULES.md`](docs/RULES.md).

1. **No monolith.** Eight independent Spring Boot applications, never one app with modules.
2. **No fake microservices.** Each service builds, runs, scales and fails on its own.
3. **No shared database access.** One database and one role per service, enforced by
   PostgreSQL grants.
4. **No ZooKeeper.** Kafka runs in KRaft mode.
5. **No Tailwind.** Bootstrap 5 and SCSS only.
6. **No secrets in source.** Environment variables only; `.env.example` with empty placeholders.
7. **Razorpay test mode only.** The service refuses to start otherwise.

---

## Getting started

### Prerequisites

Java 21, Maven 3.9+, Node 20+, Docker with Compose v2.

### Run the infrastructure

```bash
cd infrastructure
cp .env.example .env          # local placeholders; real secrets stay out of git
docker compose --profile infra up -d
```

Brings up PostgreSQL (with pgvector), Redis and Kafka in KRaft mode, with one database and one
role per service.

### Build a service

Each service builds **independently**, from its own directory:

```bash
mvn -B clean verify            # in backend/<service>/
```

Or all eight:

```bash
for s in api-gateway auth-service user-service chat-service ai-service document-service rag-service subscription-service; do
  (cd "backend/$s" && mvn -B clean verify) || exit 1
done
```

### Run the frontend

```bash
cd frontend/nexa-ai-web
npm install
npm run dev                    # http://localhost:5173, /api proxied to the gateway
```

### Run everything

```bash
docker compose -f infrastructure/docker-compose.yml up -d          # all
docker compose -f infrastructure/docker-compose.yml --profile backend up -d   # services only
```

Ports: frontend `8088`, gateway `8080`, services `8081`–`8087`, PostgreSQL `5432`, Redis
`6379`, Kafka `9092`.

---

## Repository layout

```
NexaAI/
├── docs/                      PRD, architecture, rules, design, contracts, tests, security, decisions, memory
├── backend/
│   ├── api-gateway/           8080  reactive Spring Cloud Gateway
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
│   │   ├── postgres/          per-service databases and roles
│   │   ├── redis/             cache configuration
│   │   ├── kafka/             KRaft notes
│   │   └── nginx/             edge proxy and SSE configuration
│   └── docker-compose.yml
├── .github/workflows/         per-service CI, frontend CI, Docker build
├── README.md
└── .gitignore
```

There is deliberately **no `backend/pom.xml`**. Each service is its own build, which is what
makes the microservices claim verifiable rather than aspirational.

---

## How a chat answer flows

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

Each arrow is an explicit, versioned contract in
[`docs/SERVICE_CONTRACTS.md`](docs/SERVICE_CONTRACTS.md).

---

## Contributing

1. Read [`docs/RULES.md`](docs/RULES.md) first.
2. Work only in the current phase of [`docs/TASKS.md`](docs/TASKS.md).
3. After every phase: build, test, fix, update the docs, update `docs/MEMORY.md` and
   `docs/TASKS.md`, then report what was completed and what remains.
4. A change to a contract updates `docs/SERVICE_CONTRACTS.md` in the same change; a change of
   direction adds an ADR.

---

## Licence

Educational project.
