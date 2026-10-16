# TEST_PLAN.md — Testing Strategy

How NexaAI is tested, what each layer is for, and what must never be skipped.

**Current state: Phase 0.** There is no production code to test yet, because
[`RULES.md`](RULES.md) §10 forbids building ahead. What exists is validated by CI rule checks
and by a real frontend build. This document defines the strategy those tests will follow from
Phase 1.

---

## 1. Principles

1. **A test that cannot fail is worse than no test.** No test may be commented out, skipped by
   default, or wrapped so that failure cannot propagate ([`RULES.md`](RULES.md) §11).
2. **Test the behaviour, not the implementation.** A test that breaks when a private method is
   renamed is a liability.
3. **Isolation is a test.** Cross-service data access is not a code-review opinion; it is an
   assertion.
4. **Security properties are tested, not assumed.** Ownership, authorisation and secret handling
   each get explicit tests.
5. **Real dependencies in integration tests.** A repository test against H2 proves nothing about
   PostgreSQL or pgvector.
6. **Determinism.** No test may depend on wall-clock timing, network timing or test execution
   order.

---

## 2. Layers

| Layer | Scope | Speed | Runs on | Isolates |
|---|---|:--:|---|---|
| Architecture rules | Repository shape and prohibitions | seconds | every push | — |
| Unit | One class, no Spring | ms | every push | nothing |
| Slice | One web or persistence layer | 100s of ms | every push | the other layer |
| Integration | A service against real infrastructure | seconds | every push | other services |
| Contract | Interfaces between services | seconds | every push | implementations |
| Isolation | Cross-service access attempts | seconds | every push | other services |
| End-to-end | The whole system through the gateway | minutes | main, pre-release | nothing |
| Security | The matrix in §7 | seconds–minutes | every push | — |
| Load | Concurrency and latency | minutes | nightly, pre-release | — |

**The layering rule.** A failing unit test points at a class. A failing integration test points
at a service. A failing end-to-end test points at the design. Keeping these distinct is what
makes a red build actionable.

---

## 3. Architecture rule tests (Phase 0, running now)

These run on every push and are the Phase 0 test suite.

| # | Check | Fails if |
|---|---|---|
| R1 | No aggregator POM | Any `backend/pom.xml`, or any `<modules>` block |
| R2 | Independent services | A service POM has a `<relativePath>` parent, or depends on another service |
| R3 | No fake microservices | A service directory contains a `@SpringBootApplication` without a real `application.yml` declaring its port, or contains stub business code before its phase |
| R4 | Database ownership | A service names a `nexa_*` database that is not its own |
| R5 | Stateless services | `api-gateway` or `ai-service` declares a datasource or a persistence starter |
| R6 | No ZooKeeper | `zookeeper` appears in code or configuration, ignoring comments |
| R7 | KRaft | `KAFKA_PROCESS_ROLES` or `KAFKA_CONTROLLER_QUORUM_VOTERS` missing from Compose |
| R8 | No Tailwind | `tailwind` in the frontend, or `bootstrap`/`sass` missing from it |
| R9 | No secrets | A credential-shaped string is committed, a `.env` is tracked, or an `.env.example` holds a real value |
| R10 | Exactly two roles | A role identifier outside `USER` and `ADMIN` appears |
| R11 | Test mode only | `rzp_live_` appears anywhere |
| R12 | Compose is valid | `docker compose config` fails |
| R13 | Docs are tracked | `docs/` is ignored by `.gitignore`, or a required document is missing |
| R14 | Frontend builds | `tsc -b` or `vite build` fails |
| R15 | Phase awareness | CI requires a service or image that does not exist |

**These tests assert. None of them warn.** A check that nobody reads is not a control
([`RULES.md`](RULES.md) §11).

---

## 4. Per-phase test plan

Each phase adds the listed layers. A phase is not done until its criteria in
[`TASKS.md`](TASKS.md) pass.

### Phase 0 — Foundation
- [x] Architecture rule checks R1–R15
- [x] `docker compose config` parses
- [x] Frontend lint, type-check and production build
- [ ] Compose stack actually starts *(blocked: no Docker daemon in the environment — see
      [`MEMORY.md`](MEMORY.md) §6)*
- [ ] Database isolation script actually passes *(same blocker)*

### Phase 1 — Auth Service
- [ ] Unit: password hashing, token claims and signing, validation rules
- [ ] Slice: every auth controller, including failure paths
- [ ] Integration: registration, login, refresh, logout, revocation against real PostgreSQL
- [ ] Security: identical response and timing for unknown email and wrong password
- [ ] Security: no password in any log or any response body
- [ ] Security: tampered, expired and wrongly-signed tokens rejected
- [ ] Contract: `auth.user.registered.v1` schema matches the registered event
- [ ] E2E: register → login → access an authenticated route

### Phase 2 — User Service
- [ ] Unit: profile updates, preference validation
- [ ] Slice: profile and admin controllers
- [ ] Integration: profile created from the event, exactly once
- [ ] **Isolation: user A cannot read, update or delete user B's profile**
- [ ] **Authorisation: a USER token is rejected on every `/api/v1/admin/**` route**
- [ ] Idempotency: the same registration event replayed creates one profile
- [ ] Contract: profile shape matches what Auth and Chat expect

### Phase 3 — Gateway and auth UI
- [ ] Gateway: unsigned, tampered, expired and wrong-issuer tokens rejected at the edge
- [ ] Gateway: correlation id present on every response and propagated to every service
- [ ] Gateway: `/internal/**` and `/actuator/**` unreachable externally
- [ ] Gateway: a service timeout produces a 504, not a hang
- [ ] Gateway: CORS rejects an unlisted origin
- [ ] Frontend: component tests for auth state transitions
- [ ] Frontend: E2E sign in → refresh on expiry → sign out

### Phase 4 — AI Service
- [ ] Unit: provider selection, parameter mapping, error classification
- [ ] Integration: each provider, streaming and non-streaming, against a **recorded** or stubbed
      response — never a live paid call in CI
- [ ] **Statelessness: the service starts and serves with no datasource configured**
- [ ] Security: no prompt text and no API key in any log
- [ ] Resilience: circuit breaker opens after repeated failures and recovers
- [ ] Contract: `ai.inference.completed.v1` carries accurate token counts

### Phase 5 — Chat Service
- [ ] Unit: memory window assembly, token-budget trimming, stream state machine
- [ ] Slice: conversation and message controllers
- [ ] Integration: full message lifecycle with a stubbed AI Service
- [ ] **Isolation: a USER cannot read or write another user's conversation**
- [ ] **Streaming: the first token is relayed before generation completes**
- [ ] Streaming: an interrupted stream is stored as incomplete
- [ ] Quota: the check happens *before* the provider is called
- [ ] Contract: SSE event names and payload shapes match the contract document

### Phase 6 — Document Service
- [ ] Unit: chunking preserves sentence boundaries, size and overlap respected
- [ ] Integration: upload, extract, chunk against real PostgreSQL and real files
- [ ] **File security: a renamed executable with a `.pdf` extension is rejected**
- [ ] File security: an oversized file is rejected before full buffering
- [ ] File security: stored files are not reachable by predictable URL
- [ ] **Isolation: a USER cannot list, read or delete another user's documents**
- [ ] Idempotency: replaying `document.chunked.v1` does not duplicate chunks

### Phase 7 — RAG Service
- [ ] Unit: tenant scoping is in the generated query, not applied afterwards
- [ ] Integration: embed, store, retrieve against **real PostgreSQL with pgvector**
- [ ] **Isolation: retrieval for user A never returns user B's passage** (asserted directly)
- [ ] Deletion: deleting a document removes its vectors
- [ ] Idempotency: replaying an event does not duplicate vectors
- [ ] Contract: every passage carries document id and chunk index
- [ ] Database: `pgvector` exists in `nexa_rag` and in no other database

### Phase 8 — RAG integration
- [ ] Integration: a grounded answer carries at least one citation
- [ ] Behaviour: with nothing relevant retrieved, the assistant says so
- [ ] Behaviour: a general-knowledge answer is labelled as such
- [ ] E2E: upload → ask → cited answer → follow-up question in the same conversation

### Phase 9 — Subscription Service
- [ ] Unit: entitlement resolution, usage aggregation, webhook signature verification
- [ ] Integration: order creation, webhook processing, entitlement grant
- [ ] **The service refuses to start with a mode other than `test`**
- [ ] Payment security: the amount comes from the database, never the client
- [ ] Payment security: a bad webhook signature changes nothing
- [ ] **Idempotency: the same webhook twice grants one subscription**
- [ ] **The browser redirect alone never activates a subscription**; only the webhook does
- [ ] Quota: an exceeded allowance is refused before spending

### Phase 10 — Frontend product UI
- [ ] Component tests per feature
- [ ] E2E: the full user journey, including upload and a cited answer
- [ ] Accessibility: automated axe checks plus manual keyboard and screen-reader passes
- [ ] Theme: contrast passes AA in **both** themes
- [ ] Performance: bundle size, LCP and CLS within budget
- [ ] Security: the built bundle contains no credential

### Phase 11 — Security and operations
- [ ] Full security matrix in §7
- [ ] Load: concurrent streaming users, retrieval latency under load
- [ ] Chaos: a service stopped does not cascade; the gateway degrades honestly
- [ ] Backup: a restore is performed and verified, not merely configured
- [ ] Secret rotation: rehearsed with documented downtime

### Phase 12 — Delivery
- [ ] Documentation matches the code; no documented endpoint is missing
- [ ] A fresh clone builds and runs from the README alone
- [ ] Every contract in [`SERVICE_CONTRACTS.md`](SERVICE_CONTRACTS.md) matches reality

---

## 5. The isolation matrix

The most important tests in the project, because a failure here leaks one user's data to
another. Every row is a real test, in the phase named.

| Actor | Target | Expected | Phase |
|---|---|---|:--:|
| A (`USER`) | user B's profile | 403/404 | 2 |
| A (`USER`) | any `/api/v1/admin/**` | 403 at the gateway | 3 |
| A (`USER`) | user B's conversation | 403/404 | 5 |
| A (`USER`) | user B's document | 403/404 | 6 |
| A (`USER`) | user B's document via a predictable URL | 404 | 6 |
| A (`USER`) | RAG retrieval scoped to B | empty result | 7 |
| A (`USER`) | a suspended account | 401 | 2 |
| anonymous | any authenticated route | 401 | 3 |
| anonymous | `/internal/**`, `/actuator/**` | 404 | 3 |
| any | another service's database | permission denied | 0 |
| `ai-service` | any database | not configured | 4 |
| A (`USER`) | another user's payment webhook | signature rejected | 9 |
| any browser | any AI provider key | not present in the bundle | 10 |

**Note on 404 versus 403.** Returning 403 for a resource that exists leaks its existence.
Return 404 for both "does not exist" and "not yours", unless the caller is an ADMIN.

---

## 6. Test data

- Fixtures are factories, never shared mutable constants.
- Every test gets its own database schema or a clean schema, so tests can run in parallel.
- Passwords in fixtures are obviously fake and never reused anywhere.
- **No real credential, key or card number in any fixture.** Not in a test, not in a comment
  ([`RULES.md`](RULES.md) §6).
- Provider responses are recorded or stubbed. **CI never makes a paid API call.**

---

## 7. Security test matrix

| Area | Test | Phase |
|---|---|:--:|
| Password storage | No plaintext password in any column or log | 1 |
| Password hashing | Cost factor meets the current OWASP guidance | 1 |
| User enumeration | Unknown email and wrong password: identical response **and** timing | 1 |
| Token integrity | Tampered, expired, wrong-issuer and wrong-audience tokens all rejected | 1, 3 |
| Token revocation | A revoked refresh token cannot be exchanged | 1 |
| Algorithm confusion | `alg: none` and an HS256 token signed with the public key are rejected | 3 |
| Authorisation | Every admin route rejects a USER token | 2, 3 |
| Ownership | Every row of the §5 matrix | 2–7 |
| Secret exposure | No credential in source, logs, error messages or the bundle | all |
| Rate limiting | Auth endpoints are limited per IP and per user | 1, 3 |
| File upload | Content-based validation; size limit; stored outside the web root; never executed | 6 |
| Prompt injection | Retrieved document content is treated as data, never as instructions | 8 |
| XSS | Model output is sanitised before rendering | 10 |
| Webhook signatures | Verified in constant time before any payload is trusted | 9 |
| Webhook idempotency | Replay grants nothing extra | 9 |
| Payment amount | Always read from the database, never from the client | 9 |
| Live-mode guard | The service refuses to start outside `test` mode | 9 |
| Audit | Every admin action and auth event is recorded immutably | 11 |
| Secrets in CI | The credential-pattern scan runs on every push | 0 |
| Internal exposure | `/internal/**` unreachable from outside | 3 |

**Prompt injection deserves a note.** A user can upload a document containing text designed to
instruct the model. Retrieved passages must be marked as untrusted data in the prompt, and the
system prompt must state that document content cannot change instructions. This is tested with
an adversarial document, not assumed to be handled.

---

## 8. Test frameworks

| Layer | Tool | Rationale |
|---|---|---|
| Unit and slice | JUnit 5, AssertJ, Mockito | Default in the Spring ecosystem; AssertJ reads better than raw JUnit assertions |
| Integration | Spring Boot Test, Testcontainers | Real PostgreSQL and real Kafka, started per run |
| Database | Testcontainers + PostgreSQL 17 + pgvector | A containerised real server, not an in-memory substitute |
| Kafka | Testcontainers + KRaft broker | Same reason: KRaft, and never ZooKeeper |
| Frontend | Vitest + React Testing Library | Matches the Vite toolchain |
| E2E | Playwright | Real browser, real streaming through the real proxy chain |
| Accessibility | axe-core, plus manual review | Automation catches roughly a third of issues |
| Load | k6 | Scriptable, and it handles SSE |

**Testcontainers is the answer to "how do we test against real infrastructure locally?"** It
starts the real thing, so a test that passes in CI behaves the same as one on a laptop.

---

## 9. CI integration

| Workflow | Runs | Purpose |
|---|---|---|
| `rules.yml` | every push | Architecture rules R1–R13 |
| `frontend.yml` | changes under `frontend/` | Lint, type-check, build, bundle secret scan |
| `backend.yml` | changes under `backend/` | Builds and tests **only the services that exist** |

**Phase awareness.** The backend workflow discovers which services have a `pom.xml` and builds
exactly those. In Phase 0 that set is empty, and the workflow says so explicitly in its output
rather than reporting a pass that implies work was verified
([`RULES.md`](RULES.md) §11, §10).

**Never in CI:** a build of a service that does not exist, an image build for an absent
Dockerfile, `--exit-zero`, a skipped check, or a live paid API call.

---

## 10. Coverage

Coverage is a **guide, not a target**. A number that becomes a target produces tests that execute
lines without asserting behaviour.

Requirements:

- Every business rule in [`PRD.md`](PRD.md) has at least one test that would fail if the rule
  were removed.
- Every row of the §5 isolation matrix has a test.
- Every row of the §7 security matrix has a test.
- Every documented endpoint has a slice test, including its failure paths.
- Every Kafka consumer has an idempotency test.

Where a percentage is reported, it is informational.

---

## 11. What "done" means

A phase is done when:

1. `mvn verify` passes for every service the phase touched.
2. Every applicable row of §4 passes.
3. Every applicable row of §5 and §7 passes.
4. The Phase 0 rule checks still pass — a phase must not break an earlier rule.
5. The frontend type-checks, lints and builds.
6. `docker compose config` succeeds.
7. Documentation, [`TASKS.md`](TASKS.md) and [`MEMORY.md`](MEMORY.md) are updated.
8. Genuine failures are fixed, not suppressed.
9. Remaining issues are reported honestly.