# MEMORY.md — Project Context and State

The file to read first when picking up NexaAI. It records what exists, what was decided, what is
known to be wrong, and what to do next.

**Last updated:** 2026-10-02, at the end of **Phase 0**.

---

## 1. What NexaAI is

An AI chat SaaS. Users upload documents, ask questions, and get answers grounded in those
documents with citations, from a model they choose, in a conversation that remembers.

- **Product requirements:** [`PRD.md`](PRD.md)
- **System architecture:** [`ARCHITECTURE.md`](ARCHITECTURE.md)
- **Binding rules:** [`RULES.md`](RULES.md)
- **Phase plan:** [`TASKS.md`](TASKS.md)

**It is a genuine microservices project: eight independent Spring Boot applications.** Not a
monolith, and not eight directories of fake microservices. Phase 0 currently has the directories
and nothing else, which is deliberate ([`DECISIONS.md`](DECISIONS.md) ADR-015).

---

## 2. Current state

### What exists

| Area | State |
|---|---|
| `docs/` | **10 documents, complete.** PRD, ARCHITECTURE, RULES, DESIGN, TASKS, TEST_PLAN, SECURITY, DECISIONS, SERVICE_CONTRACTS, MEMORY |
| `backend/` | **8 service directories, empty by design.** Each has a README stating its boundary, port, database and planned responsibility |
| `frontend/nexa-ai-web/` | **Working Vite + React + TypeScript + Bootstrap + SCSS shell.** Lints, type-checks and builds |
| `infrastructure/` | Docker Compose, PostgreSQL + pgvector init and verification SQL, Redis config, Kafka KRaft notes, NGINX edge config, `.env.example` |
| `.github/workflows/` | Phase-aware CI: architecture rules, frontend, backend |
| `README.md`, `.gitignore` | Present |

### What does not exist

**No production code. By rule** ([`RULES.md`](RULES.md) §10).

- No Java application, controller, repository, entity or business logic
- No `pom.xml` for any service
- No Dockerfile for any service
- No database schema or migrations
- No auth, chat, AI, document, RAG or subscription functionality
- No Docker Compose service entry for the eight services, because there are no images to build
- No product UI beyond the shell

---

## 3. Architecture decisions

Full reasoning in [`DECISIONS.md`](DECISIONS.md). The ones that shape everything else:

| # | Decision | One-line reason |
|:--:|---|---|
| 001 | Eight independent Spring Boot services | Split follows real boundaries: security, statelessness, cost, data ownership |
| 002 | One database and one role per service | Isolation enforced by PostgreSQL, not by discipline |
| 003 | Kafka in KRaft mode | ZooKeeper is a second stateful system nothing needs |
| 004 | REST for synchronous, Kafka for asynchronous | Does the caller need an answer? Then REST |
| 005 | Redis is a cache only, persistence disabled | Losing Redis must degrade performance, never correctness |
| 006 | Spring AI is the sole AI integration layer | Provider quirks contained in one place |
| 007 | AI Service is stateless | Scales with no affinity; holds provider keys once |
| 008 | pgvector only in RAG Service | One owner for the vector store |
| 009 | Razorpay test mode only | No live-money variable exists anywhere |
| 010 | Exactly two roles, USER and ADMIN | Small enough to verify the matrix by hand |
| 011 | Bootstrap 5 + SCSS, no Tailwind | The design system lives in one reviewable file |
| 012 | No aggregator POM | Makes service independence structural, not conventional |
| 013 | DTOs duplicated, not shared | A shared jar is how a disguised monolith forms |
| 014 | Gateway authenticates, services authorise | An internal network is not a trust boundary |
| 015 | Empty service directories in Phase 0 | Nothing in the repo pretends to work |
| 016 | Phase-aware CI | A red build always means a real failure |
| 017 | Memory is a bounded visible window | Inspectable beats sophisticated for a grounded-answer product |
| 018 | No service mesh | Disproportionate at eight services |
| 019 | Per-service Flyway migrations | The data owner owns the schema |
| 020 | Consumer-driven topic creation | The producer is accountable for its schema |
| 021 | SSE rather than WebSocket | Unidirectional stream; plain HTTP |

---

## 4. Service boundaries

| Service | Port | Database | Responsibility |
|---|:--:|---|---|
| api-gateway | 8080 | *none* | Public entry. Token validation, routing, correlation ids, rate limits. No business logic |
| auth-service | 8081 | `nexa_auth` | Credentials, tokens, account status. The only service that sees a password |
| user-service | 8082 | `nexa_user` | Profile, preferences, user administration. Learns of accounts from Kafka |
| chat-service | 8083 | `nexa_chat` | Conversations, messages, memory window, streamed answer relay, quota enforcement |
| ai-service | 8084 | *none* | Stateless multi-model inference through Spring AI. Holds provider credentials |
| document-service | 8085 | `nexa_document` | Upload, extraction, chunking. **Never embeds** |
| rag-service | 8086 | `nexa_rag` | Embeddings and pgvector retrieval, tenant-scoped |
| subscription-service | 8087 | `nexa_subscription` | Plans, entitlements, usage, Razorpay test orders |

**Non-negotiables:** no shared database, no shared business-logic module, no ZooKeeper, no
Tailwind, no secrets in source, no AI keys in React, Razorpay test mode only.

---

## 5. Validation performed in Phase 0

Everything below was **executed**, not assumed.

| Check | Result |
|---|---|
| All 14 architecture rule checks, run locally against the real tree | **All pass** |
| R6 and R11 self-tests | **Pass.** Each proves its pattern still catches a real violation; R11 also proves it does not flag correct config |
| Frontend `npm install` | 39 packages, no errors |
| Frontend `npm run lint` (oxlint, 117 rules) | 0 warnings, 0 errors |
| Frontend `npm run build` (`tsc -b` + `vite build`) | Succeeds |
| Design tokens reached the built CSS | Pass — the theme pipeline genuinely applies, not just compiles |
| No credential in the built bundle | Pass |
| JS bundle size | 89.6 kB gzipped, within the 200 kB budget |
| YAML structure of 3 workflows + Compose | Pass |
| Compose declares no unimplemented service | Pass |
| `git check-ignore docs/RULES.md` | Not ignored |
| `docker compose config` | **Parses only** — see §6.1 |

### 5.1 Bugs found by validating the checks themselves

The rule checks were run against the real repository instead of being assumed correct. That
found **five genuine defects**, all now fixed.

| Bug | Consequence had it shipped |
|---|---|
| `.gitignore` contained a bare `docs/` line | **All ten documents silently excluded from the repository.** The files existed on disk and CI reported green. Found by R13. |
| R6 matched application prose that states the prohibition | The ZooKeeper check would fail on `App.tsx`'s own text — a check that fails on correct code, which is exactly the kind that gets switched off |
| R10 matched its own search pattern inside `rules.yml` | Every run would fail on the workflow file that contains the rule |
| R11 recursed into `node_modules` | Took over two minutes, long enough that people skip it |
| R11's `[^t]` pattern backtracked and matched the space | `RAZORPAY_MODE: test` was wrongly flagged. Same failure mode as R6: a check that rejects correct code |

**The lesson worth carrying forward.** Three of these five bugs were checks that would have
*passed a broken system* or *failed a correct one*. Neither is acceptable
([`RULES.md`](RULES.md) §11). R6 and R11 now include self-tests that must succeed before the
check reports success, and R11 asserts both directions: it must catch a live key **and** accept
`RAZORPAY_MODE: test`.

### 5.2 A note on `.gitignore`

The prior `foundation` commit ended its `.gitignore` with `docs/`, which would have excluded
every document in this repository. The same bug reappeared during Phase 0 and was caught by
R13. The line is now removed, and R13 fails the build if it ever returns. **If documentation
disappears from a diff, check `.gitignore` first.**

---

## 6. Known issues and gaps

**Read this before trusting anything above.**

### 6.1 The Compose stack has never been run

The Docker daemon is not available in the development environment. Therefore:

- `infrastructure/docker-compose.yml` has been validated by `docker compose config` **parsing
  and review only** — not by actually starting Postgres, Redis or Kafka.
- `infrastructure/docker/postgres/init/01-create-databases.sql` has **never been executed**.
- `infrastructure/docker/postgres/verify/02-verify-ownership.sql` has **never been executed**, so
  the isolation guarantee is currently argued rather than demonstrated.
- `infrastructure/docker/redis/redis.conf` has **never been loaded**.
- `infrastructure/docker/nginx/nexaai.conf` has **never been loaded**, so its syntax is
  unverified.

**This is the most significant limitation of Phase 0.** The database isolation rule is the
strongest architectural guarantee in the design, and it has not yet been proven at runtime.

**Action:** the first task of Phase 1 is to start the stack and run both SQL scripts, then report
the actual output. If they fail, they are fixed before any feature work.

### 6.2 No backend build exists

No service has a `pom.xml`, so there is nothing for `mvn verify` to do. The CI backend workflow
discovers this, reports it explicitly and does not pretend otherwise. This resolves in Phase 1.

### 6.3 Version choices are unverified against live registries

The architecture specifies Java 21, Spring Boot 4.x, Spring AI 2.x, PostgreSQL 17 + pgvector,
Kafka 4 in KRaft mode, React 19, Vite, Bootstrap 5.3. The frontend versions in `package.json`
are verified by an actual `npm install` and build. **The Spring Boot and Spring AI versions have
not been resolved against Maven Central yet**, because no POM exists. Phase 1 must pin real,
existing versions, and must not assume a version exists.

### 6.4 Spring Boot 4 and Spring AI 2 are new major versions

Both involve real migration risk from the 3.x line. Phase 1 should confirm the annotation
packages, the `spring-boot-starter-*` names, the Spring AI starter names and the OpenAI/Gemini
model autoconfiguration before building on them. This is recorded so it is not discovered
mid-implementation.

### 6.5 Documentation is ahead of implementation

Ten documents specify contracts, schemas, topics and endpoints that do not exist. That is
correct for a foundation phase, but it means **the documents can be wrong**. Phase 1 must treat
[`SERVICE_CONTRACTS.md`](SERVICE_CONTRACTS.md) as a specification to validate against Spring
Security's actual behaviour, not as proven truth. Corrections are expected and belong in the
same commit that finds them.

### 6.6 Secrets are placeholders only

`infrastructure/.env.example` and `infrastructure/docker/postgres/init/01-create-databases.sql`
contain local placeholder passwords. These are development conveniences, not secrets, and are
not usable outside a local machine. Real environments inject credentials from a secret store.

### 6.7 Known security gaps

Fourteen gaps are catalogued in [`SECURITY.md`](SECURITY.md) §13, including no CSP, no code-level
rate limiting, no upload virus scanning, no anomaly detection, and no penetration test. Each has
a closing phase. **No security control is implemented yet; the document is a specification.**

### 6.8 Kafka single-node

Local Kafka runs one broker with replication factor `1`. This is development-only. Production
needs at least three brokers and replication factor `3`, tracked in Phase 11.

---

## 7. Next: Phase 1 — Auth Service

**Do not start anything beyond this list.**

1. **Start the Docker stack and run both SQL scripts.** Report the actual output. This closes
   §6.1 and is the first thing to do, because the isolation guarantee is unproven.
2. Create `backend/auth-service`: `pom.xml` (pin **real** existing versions, §6.3), port 8081,
   `application.yml`, Dockerfile, README.
3. Flyway migrations for `nexa_auth`: credentials, refresh tokens, revocation, login attempts.
4. Registration, login, refresh, logout, per
   [`SERVICE_CONTRACTS.md`](SERVICE_CONTRACTS.md) §5.
5. RS256 access tokens (15 min) and rotating, hashed refresh tokens. Reject `alg: none` and
   algorithm confusion.
6. Exactly two roles: `USER` and `ADMIN`.
7. Identical response **and** timing for unknown email and wrong password.
8. Publish `auth.user.registered.v1`.
9. `GET /internal/v1/auth/info`.
10. Unit, slice and integration tests (Testcontainers PostgreSQL) per
    [`TEST_PLAN.md`](TEST_PLAN.md) §4.
11. Update the docs, `TASKS.md` and this file. Report. Stop.

**Out of scope for Phase 1:** user profile (Phase 2), gateway token validation (Phase 3), login
UI (Phase 3), admin user management (Phase 2).

---

## 8. How to work on this project

1. Read [`RULES.md`](RULES.md) first. It is not advisory.
2. Work only in the current phase of [`TASKS.md`](TASKS.md).
3. Inspect existing code before changing it. Preserve correct implementation.
4. A contract change updates [`SERVICE_CONTRACTS.md`](SERVICE_CONTRACTS.md) **in the same
   change**.
5. A change of direction adds an entry to [`DECISIONS.md`](DECISIONS.md).
6. After every phase: build, test, fix genuine errors, update the docs, update `TASKS.md`,
   update this file, report what was completed and what remains, then **stop**.
7. **Never weaken a check, skip a test or swallow an exception to make CI green**
   ([`RULES.md`](RULES.md) §11). A red pipeline that means something is worth more than a green
   one that does not.

---

## 9. Environment

| Tool | Version | Verified |
|---|---|:--:|
| Java | 21.0.10 (LTS) | yes |
| Maven | 3.9.14 | yes |
| Node.js | 20.20.0 | yes |
| npm | 10.8.2 | yes |
| Docker | **unavailable** | no — see §6.1 |

Target runtime: Java 21, Spring Boot 4.x, Spring AI 2.x, PostgreSQL 17 + pgvector, Redis 7,
Kafka 4 (KRaft), React 19, Vite, TypeScript, Bootstrap 5.3, SCSS.

Ports: frontend `8088`, edge `8089`, gateway `8080`, services `8081`–`8087`, PostgreSQL `5432`,
Redis `6379`, Kafka `9092` external / `29092` internal.

---

## 10. Change log

| Date | Phase | Summary |
|---|:--:|---|
| 2026-10-02 | 0 | Foundation created from an empty working tree. Ten documents, eight empty service directories, working frontend shell, Docker infrastructure (unverified — no Docker), phase-aware CI. All 14 rule checks pass. Five defects found in the checks themselves, all fixed. No production code, by rule. |