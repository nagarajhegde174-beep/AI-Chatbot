# MEMORY.md — Project Memory for AI Coding Assistants

**Read this first.** Concise context for any assistant picking up NexaAI. For detail, see the
seven project documents.

---

## 1. Identity and purpose

**NexaAI** is an AI chat SaaS. Users upload documents, ask questions, and receive answers
grounded in those documents **with citations**, from a model they choose, in a conversation that
remembers earlier turns.

- Product: [`PRD.md`](PRD.md)
- Architecture: [`ARCHITECTURE.md`](ARCHITECTURE.md)
- Rules: [`RULES.md`](RULES.md)
- Contracts: [`SERVICE_CONTRACTS.md`](SERVICE_CONTRACTS.md)
- Security: [`SECURITY.md`](SECURITY.md)
- Testing: [`TEST_PLAN.md`](TEST_PLAN.md)
- Frontend: [`DESIGN.md`](DESIGN.md)

---

## 2. Architecture — genuine microservices

**Eight independent Spring Boot applications.** Not a monolith, and not eight folders of fake
microservices. Each has its own `pom.xml`, port, configuration, source tree, tests and
Dockerfile.

| Service | Port | Database | Responsibility |
|---|:--:|---|---|
| `api-gateway` | 8080 | *none* | Public entry. Token validation, routing, correlation ids, rate limits |
| `auth-service` | 8081 | `nexa_auth` | Credentials, tokens, account status |
| `user-service` | 8082 | `nexa_user` | Profile, preferences, administration |
| `chat-service` | 8083 | `nexa_chat` | Conversations, messages, memory, SSE relay |
| `ai-service` | 8084 | *none* | Stateless multi-model inference |
| `document-service` | 8085 | `nexa_document` | Upload, extraction, chunking |
| `rag-service` | 8086 | `nexa_rag` | Embeddings, pgvector retrieval |
| `subscription-service` | 8087 | `nexa_subscription` | Plans, entitlements, Razorpay test orders |

**There is no `backend/pom.xml`.** Each service is its own Maven build. No service may depend on
another, share a business-logic module, or read another service's database.

---

## 3. Technology stack

Java 21 · Spring Boot 4.1.1 · Spring Security 7.1.1 · Spring Data JPA · Hibernate · Flyway ·
PostgreSQL 17 (+ pgvector, owned **only** by RAG Service) · Redis 7 (cache only) · Kafka 4 in
**KRaft** mode · JJWT (RS256) · springdoc-openapi · React 19 · Vite · TypeScript (strict) ·
Bootstrap 5.3 · SCSS · Lucide.

**Never assume a version.** Resolve it against the live registry. Spring Boot 4 and Spring
Security 7 moved or removed APIs; see [`ARCHITECTURE.md`](ARCHITECTURE.md) §16.

---

## 4. Non-negotiable rules

These are enforced in CI, not merely documented. Full text in [`RULES.md`](RULES.md).

1. **No monolith.** Eight independent Spring Boot applications.
2. **No fake microservices.** No placeholder controllers or stub business logic. A planned
   service directory stays empty and says so.
3. **No shared database access.** One database and one role per service, enforced by PostgreSQL
   grants.
4. **No shared business-logic module.** DTOs are duplicated deliberately; the shared artefact is
   a document, not a jar.
5. **Kafka MUST use KRaft. NO ZooKeeper** anywhere.
6. **REST** for synchronous, **Kafka** for asynchronous.
7. **Spring AI** is the only AI integration layer. No provider SDK called directly.
8. **NO Tailwind.** Bootstrap 5 + SCSS. Also no Next.js, Vue or Angular.
9. **No secrets in source.** Environment variables only. **Never** an AI provider key in React.
10. **Razorpay TEST MODE ONLY.** No live-mode variable may exist anywhere.
11. **Exactly two roles:** `USER` and `ADMIN`. No `SUPER_ADMIN`, `MODERATOR` or `SUPPORT`.
12. **CI must not hide failures.** No skipped checks, no `--exit-zero`.

---

## 5. Progress

| Phase | Scope | Status |
|:--:|---|:--:|
| 0 | Foundation: repo, architecture, infrastructure, CI, frontend shell | Complete |
| 1 | Auth Service: registration, sign-in, tokens, RBAC, Google OAuth | Complete |
| 2 | User Service: profile, preferences, administration | **Not started** |
| 3 | API Gateway hardening, token validation, JWKS, login UI | Not started |
| 4 | AI Service: Spring AI providers, streaming | Not started |
| 5 | Chat Service: conversations, memory, SSE relay | Not started |
| 6 | Document Service: upload, extraction, chunking | Not started |
| 7 | RAG Service: embeddings, pgvector, retrieval | Not started |
| 8 | RAG integration: grounded answers, citations | Not started |
| 9 | Subscription Service: plans, entitlements, test orders | Not started |
| 10 | Frontend product UI | Not started |
| 11 | Security and operations | Not started |
| 12 | Delivery | Not started |

**Current phase: 1 complete, 2 not started.**

Built: `auth-service` (87 tests passing against real PostgreSQL 17). The other seven services
hold only a README each.

---

## 6. Decisions that must persist

- **Security state written in the same transaction as the rejection that causes it is state that
  never persists.** Lockout counters and token revocations need their own `REQUIRES_NEW`
  boundary in a separate bean. Two real defects came from this.
- **Re-check account status on every identity-resolution path.** Two separate suspension bypasses
  came from adding a sign-in path and forgetting to re-check.
- **Verify JWT algorithms, never trust the token header.** Pin `RS256`; reject `alg: none` and
  HS256-with-the-public-key.
- **An isolation test against a superuser is not an isolation test.** Service roles must be
  ordinary users, with a separate admin role.
- **A check that fails on correct code gets switched off.** Rule checks carry self-tests proving
  they still catch a real violation.
- **Tokens live in HTTP-only cookies, so CSRF protection is mandatory** — it would not be for a
  bearer-header API.
- **Avoid user enumeration in both response and timing.** An unknown email must cost a real
  hash verification.
- **Documentation is source code** and lives in the same change as the code.

---

## 7. Phase boundaries — non-negotiable

**Implement only the phase the user has explicitly authorised. Nothing else.**

Do not:

- implement the next phase, or any phase not explicitly requested
- create business logic, endpoints, tables, migrations, controllers or services for a future phase
- add a future phase's dependencies or configuration
- modify the architecture in anticipation of a future phase
- infer the next phase from this document, the phase table, or any roadmap text

**After completing a phase: validate it, report the result, then STOP and WAIT.**

The next phase begins **only** when the user provides its implementation prompt explicitly.
Roadmap text such as "Phase 2: User Service" is planning information, never authorisation to
build it.

Preserve all legitimate work from earlier phases. If accidental future-phase work is created,
revert **only** that.

Run **one** build at a time. Never start a second build against the same service while one is
running — concurrent builds corrupt `target/` and produce failures that look like code defects.

Report failures honestly. Do not weaken a check, skip a test, or fake a result to obtain a green
build.