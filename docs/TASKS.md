# TASKS.md — Phase Plan and Status

**Current phase: Phase 0 — Foundation. COMPLETE.**
Next phase: **Phase 1 — Auth Service.**

Phases are sequential and gated. Only the current phase is implemented
([`RULES.md`](RULES.md) §10). After each phase: build, test, fix genuine errors, update the
docs, update this file and [`MEMORY.md`](MEMORY.md), report, stop.

---

## Status legend

| | Meaning |
|---|---|
| ✅ | Complete and verified |
| 🔄 | In progress |
| ⬜ | Not started |
| ❌ | Blocked |

---

## Phase plan

| Phase | Name | Scope | Status |
|:--:|---|---|:--:|
| 0 | Foundation | Repository, architecture, docs, infrastructure, CI | ✅ |
| 1 | Auth Service | Registration, login, JWT, roles | ⬜ |
| 2 | User Service | Profile, preferences, administration | ⬜ |
| 3 | Gateway hardening + auth UI | Edge policies, login/registration screens | ⬜ |
| 4 | AI Service | Spring AI providers, streaming, credentials | ⬜ |
| 5 | Chat Service | Conversations, messages, memory, SSE relay | ⬜ |
| 6 | Document Service | Upload, extraction, chunking | ⬜ |
| 7 | RAG Service | Embeddings, pgvector, retrieval | ⬜ |
| 8 | RAG integration | Grounded answers, citations, "I don't know" | ⬜ |
| 9 | Subscription Service | Plans, entitlements, Razorpay test orders | ⬜ |
| 10 | Frontend product UI | Chat, documents, admin dashboards, theming | ⬜ |
| 11 | Security and operations | Hardening, observability, performance, audit | ⬜ |
| 12 | Delivery | Final documentation, release, deployment | ⬜ |

---

## Phase 0 — Foundation ✅

**Goal.** Establish the architecture, the boundaries, the documentation and the buildable
foundation. **No business functionality.**

### Completed

**Documentation** (`docs/`)
- [x] `PRD.md` — purpose, users, USER and ADMIN functionality, AI chat, multi-model, memory,
      documents, RAG, subscriptions, usage, analytics, security
- [x] `ARCHITECTURE.md` — microservices, responsibilities, communication, database ownership,
      Redis, Kafka/KRaft, AI, RAG, payments, frontend, deployment
- [x] `RULES.md` — monolith, fake microservices, shared database access, ZooKeeper, Tailwind,
      secrets — each prohibition with rationale and enforcement
- [x] `DESIGN.md` — frontend architecture and visual system
- [x] `SERVICE_CONTRACTS.md` — boundaries, REST contracts, DTO boundaries, Kafka ownership
- [x] `TEST_PLAN.md` — testing strategy per phase, isolation matrix
- [x] `SECURITY.md` — authn, authz, secrets, rate limiting, file security, payments, audit
- [x] `DECISIONS.md` — architectural decisions with consequences
- [x] `TASKS.md` — this file
- [x] `MEMORY.md` — project context and state

**Repository**
- [x] Eight service directories under `backend/`, each with a README stating its boundary,
      port, database and planned responsibility — **empty of code by design**
- [x] `frontend/nexa-ai-web/` — Vite + React + TypeScript + Bootstrap + SCSS shell that
      type-checks, lints and builds
- [x] `infrastructure/` — Docker Compose, PostgreSQL + pgvector init and verification SQL,
      Redis config, Kafka KRaft notes, NGINX edge configuration
- [x] `.github/workflows/` — phase-aware CI
- [x] `README.md`, `.gitignore`

**Infrastructure**
- [x] PostgreSQL + pgvector, one database and one role per service, `PUBLIC` revoked
- [x] A verification script that asserts isolation using `has_database_privilege()`
- [x] Redis with no persistence, so "not the system of record" is enforced by config
- [x] Kafka in KRaft mode, no ZooKeeper anywhere
- [x] NGINX: security headers, per-concern rate limits, unbuffered SSE
- [x] `.env.example` with empty placeholders only

**CI**
- [x] Rule checks that run on every push
- [x] Frontend lint, type-check and build
- [x] Service-aware backend job: discovers which services exist and builds only those
- [x] Phase-aware: never requires a future service to compile, never builds an absent image

### Explicitly NOT done (by rule)
- No Java application, controller, repository or business logic in any service
  ([`RULES.md`](RULES.md) §10)
- No Dockerfile for a service that does not exist yet
- No database schema or migrations
- No auth, chat, AI, document, RAG or subscription functionality

### Validation actually performed

| Check | Result |
|---|---|
| All 14 rule checks (R1–R14), run locally against the real tree | **Pass** |
| `npm install` | 39 packages, no errors |
| `npm run lint` (oxlint, 117 rules) | 0 warnings, 0 errors |
| `npm run build` (`tsc -b` + `vite build`) | Succeeds |
| Design tokens present in built CSS | Pass — the theme pipeline actually applies |
| No credential in the built bundle | Pass |
| JS bundle size | 89.6 kB gzipped, within the 200 kB budget |
| YAML structure of 3 workflows + Compose | Pass |
| Compose declares no unimplemented service | Pass |
| `git check-ignore docs/RULES.md` | Not ignored — docs are tracked |

### Bugs found by validating the checks themselves

Running the rule checks against the real repository — rather than assuming they worked — found
five genuine defects. All are fixed.

| Bug | Consequence had it shipped |
|---|---|
| `.gitignore` contained a bare `docs/` line | **All ten documents silently excluded from the repository.** The files existed on disk and CI would have reported green. Found by R13. |
| R6 matched application prose stating the prohibition | The ZooKeeper check would have failed on `App.tsx`'s own text, i.e. a check that fails on correct code, which gets switched off |
| R10 matched its own pattern inside `rules.yml` | Every run would have failed on the workflow file containing the rule |
| R11 recursed into `node_modules` | The check took over two minutes, long enough that people skip it |
| R11's `[^t]` pattern backtracked | `RAZORPAY_MODE: test` was wrongly flagged as a violation. A check that fails on correct code gets disabled |

R6 and R11 now carry **self-tests** that assert each pattern still catches a real violation
before the check is allowed to pass. R11 additionally asserts it does *not* flag the correct
configuration, because proving the negative case is the half that matters.

### Known issues after Phase 0
- Docker is not running in the development environment, so the Compose stack and the database
  isolation script have **not** been executed. The Compose file is validated by
  `docker compose config` parsing and by review only. See [`MEMORY.md`](MEMORY.md) §6.1.
- No service has a `pom.xml`, so `mvn verify` has nothing to run. `backend.yml` reports this
  explicitly rather than passing silently.

---

## Phase 1 — Auth Service ⬜

**Goal.** Registration, login, logout, token lifecycle, roles. The first genuinely functional
service, and the template for how a service is built.

### Scope
- [ ] `backend/auth-service/` Spring Boot application on port 8081
- [ ] `pom.xml`, `application.yml`, Dockerfile, own README
- [ ] Flyway migrations for `nexa_auth`: user credentials, refresh tokens, revocation
- [ ] Registration: email + password, Argon2id or bcrypt hashing, never plaintext
- [ ] Login with uniform failure responses (no user-enumeration)
- [ ] RS256 access tokens, short-lived (15 min), with `iss`, `aud`, `sub`, `roles`, `jti`
- [ ] Rotating refresh tokens, stored hashed
- [ ] Refresh and logout with revocation
- [ ] Exactly two roles: `USER`, `ADMIN` ([`RULES.md`](RULES.md) §8)
- [ ] `GET /internal/v1/auth/info` boundary endpoint
- [ ] Publish `auth.user.registered.v1` on registration
- [ ] Account lockout after repeated failures
- [ ] Tests: unit, slice, and an integration test against a real PostgreSQL
- [ ] Dockerfile, built only once the service exists
- [ ] Rate limiting on auth endpoints
- [ ] Update docs and `MEMORY.md`

### Out of scope for Phase 1
- User profile (Phase 2)
- Gateway token validation (Phase 3)
- Any login UI (Phase 3)
- Admin user management (Phase 2)

### Acceptance criteria
1. A user can register and log in, and receives an access and refresh token.
2. A wrong password and an unknown email produce an **identical** response and timing.
3. No password appears in any log, any database column in plaintext, or any response.
4. A tampered or expired token is rejected with 401.
5. A revoked refresh token cannot be exchanged.
6. `auth-service` passes `mvn verify` from its own directory.
7. No service other than `auth-service` can read `nexa_auth`.

---

## Phase 2 — User Service ⬜

**Goal.** Profile, preferences, and user administration.

### Scope
- [ ] `backend/user-service/` on port 8082, owning `nexa_user`
- [ ] Flyway migrations: profile, preferences, account status
- [ ] Consume `auth.user.registered.v1` to create the profile — **no password ever arrives**
- [ ] `GET`/`PATCH` own profile; preferences include theme
- [ ] `GET /api/v1/admin/users`, `PATCH /api/v1/admin/users/{id}/status`
- [ ] Suspend and reinstate, audited
- [ ] Ownership enforced server-side on every request
- [ ] Redis profile cache with event-driven invalidation
- [ ] Tests, Dockerfile, documentation

### Acceptance criteria
1. A profile exists for every registered user, created from the event.
2. No credential of any kind exists in `nexa_user`.
3. A USER cannot read or modify another user's profile, even with a known id.
4. Only an ADMIN can list users or change account status.
5. Suspending a user is reflected in auth within one token lifetime.

---

## Phase 3 — Gateway hardening and auth UI ⬜

**Goal.** Make the edge real, and give the user a way to sign in.

### Scope
- [ ] Gateway: RS256 verification, JWKS endpoint, route table for all services
- [ ] Correlation id generation and propagation
- [ ] Rate limiting, body size limits, timeouts, circuit breakers per route
- [ ] CORS restricted to configured origins
- [ ] `/internal/**` and `/actuator/**` never reachable externally
- [ ] Frontend: sign in, register, token store, refresh on load, route guards
- [ ] Frontend: theme switcher, light/dark/system
- [ ] Nginx CSP now that the real policy is known
- [ ] Accessibility pass on the auth screens

### Acceptance criteria
1. An unsigned, tampered or expired token never reaches a service.
2. A correlation id is present on every response and every log line.
3. An ADMIN-only route rejects a USER token at the gateway, before the service is called.
4. The frontend refreshes an expired session without the user noticing.
5. Signing out clears state on both sides.

---

## Phase 4 — AI Service ⬜

**Goal.** Stateless multi-model inference through Spring AI.

### Scope
- [ ] `backend/ai-service/` on port 8084, **no database**
- [ ] Spring AI with OpenAI, Google Gemini, Groq/LLaMA
- [ ] Credentials from the environment; fail fast at startup if a provider is unconfigured
- [ ] `GET /api/v1/models` catalogue, cached
- [ ] `POST /internal/v1/ai/completions` — non-streaming
- [ ] `POST /internal/v1/ai/stream` — SSE, token by token
- [ ] Error classification: rate limit, timeout, filtered, outage
- [ ] Circuit breaker per provider
- [ ] Publish `ai.inference.completed.v1` with token counts for metering
- [ ] Prompts and credentials never logged
- [ ] Tests, Dockerfile, documentation

### Acceptance criteria
1. All three providers answer, streaming and non-streaming.
2. The service starts and passes a request with **no** database configured.
3. A provider outage produces a specific error, not a stack trace.
4. No prompt text and no API key appears in any log.
5. The service scales to two instances with no session affinity.

---

## Phase 5 — Chat Service ⬜

**Goal.** Conversations, messages, memory, streamed answer relay.

### Scope
- [ ] `backend/chat-service/` on port 8083, owning `nexa_chat`
- [ ] Flyway migrations: conversations, messages, model binding
- [ ] CRUD for conversations with per-user ownership checks
- [ ] `POST /api/v1/chat/{id}/messages` — SSE relay from AI Service
- [ ] Memory window bounded by **token count**, built from visible history only
- [ ] Persist the assistant message; mark an interrupted stream as interrupted
- [ ] Quota enforcement **before** spending
- [ ] Publish `chat.message.completed.v1`
- [ ] Rate limiting and concurrent-stream limit per user
- [ ] Tests, Dockerfile, documentation

### Acceptance criteria
1. A user sees tokens as they are produced, not all at once.
2. A USER cannot read another user's conversation, even with a known id.
3. The memory window never exceeds the model's context size.
4. A stream interrupted by a provider failure is stored as incomplete, never as a whole answer.
5. The quota is checked before the provider is called.

---

## Phase 6 — Document Service ⬜

**Goal.** Upload, extraction, chunking.

### Scope
- [ ] `backend/document-service/` on port 8085, owning `nexa_document`
- [ ] Flyway migrations; states `UPLOADING → PROCESSING → READY | FAILED`
- [ ] Multipart upload: PDF, text, Markdown, DOCX
- [ ] Validation by **content**, not extension; size limits per plan
- [ ] Storage outside the web root, random filenames, never served directly
- [ ] Text extraction and structure-aware chunking with overlap
- [ ] Publish `document.chunked.v1` and `document.deleted.v1`
- [ ] No embedding here ([`ARCHITECTURE.md`](ARCHITECTURE.md) §7.1)
- [ ] Tests, Dockerfile, documentation

### Acceptance criteria
1. A renamed executable with a PDF extension is rejected.
2. A file that is too large is rejected before it is fully buffered.
3. Uploaded content is never reachable by a predictable URL or by another user.
4. Processing is asynchronous, and a failure reports a plain-language reason.
5. Chunks preserve sentence boundaries.

---

## Phase 7 — RAG Service ⬜

**Goal.** Embeddings and pgvector retrieval.

### Scope
- [ ] `backend/rag-service/` on port 8086, owning `nexa_rag`; **pgvector only here**
- [ ] Flyway migrations: chunks, vectors, HNSW index
- [ ] Consume `document.chunked.v1`, embed, store
- [ ] Consume `document.deleted.v1`, delete derived vectors
- [ ] `POST /internal/v1/rag/retrieve` — embed, similarity search, top-k
- [ ] Tenant scoping **inside the query**
- [ ] Idempotent consumers, deduplicated on event id
- [ ] Tests against a real PostgreSQL with pgvector, Dockerfile, documentation

### Acceptance criteria
1. A retrieval for user A can never return a passage belonging to user B, verified by test.
2. Replaying the same event does not duplicate vectors.
3. Deleting a document removes its vectors.
4. `pgvector` exists in `nexa_rag` and in no other database.
5. Retrieval returns the document id and chunk index for every passage.

---

## Phase 8 — RAG integration ⬜

**Goal.** Grounded answers with citations, in the chat flow.

### Scope
- [ ] Chat Service calls RAG Service before generating
- [ ] Citations on every grounded answer ([`PRD.md`](PRD.md) §7.3)
- [ ] "Not in your documents" behaviour when retrieval finds nothing
- [ ] Per-conversation RAG toggle
- [ ] Selecting the passages used in the prompt, visible to the user
- [ ] Tests, documentation

### Acceptance criteria
1. Every grounded answer carries at least one citation.
2. With RAG on and nothing relevant, the assistant says so instead of inventing.
3. An answer from general knowledge is labelled as such.
4. Turning RAG off produces an ordinary chat answer.

---

## Phase 9 — Subscription Service ⬜

**Goal.** Plans, entitlements, metering, Razorpay **test mode only**.

### Scope
- [ ] `backend/subscription-service/` on port 8087, owning `nexa_subscription`
- [ ] Flyway migrations: plans, subscriptions, entitlements, usage, payments
- [ ] Plan catalogue CRUD (ADMIN) with token, document and storage allowances
- [ ] Server-side order creation from the plan in the database
- [ ] Razorpay test checkout; **the service refuses to start in any mode but `test`**
- [ ] Webhook with constant-time signature verification and idempotent processing
- [ ] Consume `ai.inference.completed.v1`; aggregate usage
- [ ] Entitlement check endpoint other services call
- [ ] Cancellation and downgrade at period end
- [ ] Tests, Dockerfile, documentation

### Acceptance criteria
1. The service **refuses to start** with a mode other than `test`.
2. The amount comes from the database, never from the client.
3. A webhook with a bad signature changes nothing.
4. The same webhook delivered twice grants one subscription.
5. Closing the browser tab mid-payment still activates the subscription.
6. No live-mode variable exists anywhere in the repository.

---

## Phase 10 — Frontend product UI ⬜

**Goal.** The screens users actually use.

### Scope
- [ ] Chat interface: streaming, citations, regenerate, stop
- [ ] Document library: upload, progress, states, delete
- [ ] Usage and plan view
- [ ] Razorpay test checkout
- [ ] Admin: overview, users, models, plans, analytics, audit log
- [ ] Loading, empty and error states everywhere ([`DESIGN.md`](DESIGN.md) §7)
- [ ] Accessibility pass, WCAG AA in both themes
- [ ] Performance budget met ([`DESIGN.md`](DESIGN.md) §8)
- [ ] Nginx CSP matching the real application

### Acceptance criteria
1. Streaming is visibly token-by-token through every proxy hop.
2. Every list has designed loading, empty and error states.
3. Contrast passes AA in both themes; keyboard navigation reaches every control.
4. The built bundle contains no credential.

---

## Phase 11 — Security and operations ⬜

**Goal.** Harden and operate it.

### Scope
- [ ] Security headers and CSP at the edge
- [ ] Rate limits tuned and reviewed
- [ ] Audit log for every admin action and authentication event, immutable
- [ ] Metrics, structured logs, distributed tracing on the correlation id
- [ ] Dependency and container scanning
- [ ] Load tests: streaming concurrency, retrieval latency
- [ ] Backup and restore verified, not just configured
- [ ] Secret rotation procedure, documented and rehearsed
- [ ] Production Kafka topology: 3 brokers, replication factor 3
- [ ] Security review against [`SECURITY.md`](SECURITY.md)

### Acceptance criteria
1. No open critical finding.
2. Restore from backup is demonstrated, not assumed.
3. A secret can be rotated with documented downtime.
4. Tracing follows one request across all services by correlation id.

---

## Phase 12 — Delivery ⬜

**Goal.** Release readiness.

### Scope
- [ ] Documentation matches the code, line by line
- [ ] Every contract in [`SERVICE_CONTRACTS.md`](SERVICE_CONTRACTS.md) matches reality
- [ ] Postman collection, OpenAPI, deployment runbook
- [ ] Final rule-compliance review against [`RULES.md`](RULES.md)
- [ ] Tag and release notes

### Acceptance criteria
1. No documented endpoint that does not exist, and none that exists but is undocumented.
2. A fresh clone builds and runs from documented instructions alone.
3. CI is green on every rule check, and every check genuinely runs.

---

## Progress log

| Date | Phase | Summary |
|---|:--:|---|
| 2026-10-02 | 0 | Foundation created. Ten documents, eight service directories, frontend shell, Docker infrastructure, phase-aware CI. No business functionality, by rule. |