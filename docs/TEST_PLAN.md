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
[`ARCHITECTURE.md`](ARCHITECTURE.md) §14 pass.

### Phase 0 — Foundation
- [x] Architecture rule checks R1–R15
- [x] `docker compose config` parses
- [x] Frontend lint, type-check and production build
- [ ] Compose stack actually starts *(blocked: no Docker daemon in the environment — see
      [`ARCHITECTURE.md`](ARCHITECTURE.md) §17)*
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

### Phase 2 — User Service + API Gateway

**User Service. All done — 120 tests.**

- [x] Unit: profile updates, preference validation, the status lifecycle
- [x] Integration: profile created from the event, exactly once
- [x] **Isolation: user A cannot read, update or delete user B's profile**
- [x] **Authorisation: a USER token is rejected on every `/api/v1/admin/**` route**
- [x] Idempotency: the same registration event replayed creates one profile
- [x] **Isolation: the `nexa_user` role cannot connect to another service's database at all**
- [x] Schema: `nexa_user` contains no credential column of any kind
- [x] A self-service request carrying `role`, `accountStatus` or `email` does not change them
- [x] A suspension without a reason is refused
- [x] `DEACTIVATED` is terminal: reactivation is refused
- [x] The caller's own status history omits the acting administrator; the admin view keeps it
- [x] Usage reports "unavailable", never zero, while Subscription Service is absent

**API Gateway. All done — 49 tests.**

- [x] Routing and prefix rewriting, asserted against a recording upstream over real HTTP
- [x] Unsigned, tampered, expired, wrong-issuer, wrong-audience and foreign-key tokens rejected
- [x] **Algorithm confusion: an HS256 token signed with the RSA public key grants nothing**
- [x] **A client-supplied `X-User-*` header is stripped even when a valid token is also sent**
- [x] **The bearer token is not forwarded to the upstream**
- [x] Correlation id present on every response, propagated, and sanitised if not a UUID
- [x] CORS allows a listed origin and refuses an unlisted one
- [x] Unroutable paths return a JSON 404, not the framework's HTML error page
- [x] Auth Service's `/internal/**` is not routed
- [ ] `/internal/**` and `/actuator/**` unreachable externally — **partly done.** Auth Service's
      internal surface is not routed and other actuator endpoints are closed; `/actuator/health`
      is deliberately public for the container healthcheck.

**Not done in Phase 2, carried to Phase 3:** rate limits, request-size limits, a service timeout
producing a 504 rather than a hang, JWKS, and `/.well-known/jwks.json`.

### Phase 3 - Chat Service + frontend foundation

**Chat Service. All done - 92 tests.**

- [x] Unit: conversation lifecycle, message lifecycle, title derivation, feedback
- [x] Integration: each of the fourteen required capabilities over HTTP against real PostgreSQL
- [x] **Isolation: user A cannot read, rename, archive, delete, send into, search into or export
      user B''s conversation**
- [x] **Isolation: user A cannot edit, regenerate, rate or delete user B''s message**
- [x] **No existence oracle: the 404 for another user''s conversation is byte-identical to the 404
      for an id that does not exist**
- [x] **Isolation: the `nexa_chat` role cannot connect to another service''s database at all**
- [x] Schema: `nexa_chat` contains no credential column
- [x] A send leaves the assistant placeholder `PENDING`, not `FAILED` and not fabricated content
- [x] Edit and regenerate supersede rather than overwrite
- [x] Search finds a conversation that has no messages yet
- [x] Search escapes `%` and `_`, so a wildcard query cannot match everything
- [x] The default listing excludes archived; archived has its own route
- [x] Feedback re-rating updates the existing row rather than adding a second
- [x] The export filename is the UUID, never the title
- [ ] **ADMIN can read another user''s message content** - deliberately NOT implemented. Metadata
      only. There is no such route, and adding one is out of scope without an audited,
      time-boxed support-access feature
- [ ] SSE streaming - Phase 5

**Frontend foundation. Built; not covered by automated tests.**

- [x] Compiles, type-checks under `strict`, lints with 0 errors, builds
- [x] Theme tokens and Bootstrap variable overrides reach the built CSS bundle
- [x] No credential in `dist/`
- [x] JS bundle 110 kB gzipped, within the 200 kB budget
- [x] Routing, shell, auth pages, chat layout, sidebar, conversation list, message components,
      loading/error/empty states, responsive drawer and theme foundation all present
- [ ] Component tests and E2E sign-in flow - **not built.** This is a real gap, recorded here
      rather than implied. The frontend has no test runner configured in this phase.
### Phase 4 - AI Service

**119 tests pass. `mvn clean verify` green. No database, no provider credential, no network.**

Routing and model selection, unit and over HTTP:

- [x] An absent model name selects the configured default
- [x] A known name selects that model; selection is case-insensitive
- [x] **An unknown name is a 400 listing the real names**, never a silent substitution of the default
- [x] A request reaches the provider that owns the chosen model
- [x] A successful call is not reported as a fallback
- [x] The model that **actually** answered is named in the response, with `fallbackUsed`
- [x] `GET /models` lists every model, available or not, with a reason when unavailable

Fallback:

- [x] Falls back to another model on the **same** provider
- [x] **A provider outage is not rescued by a sibling model** — the honest limit, asserted explicitly
- [x] Does **not** cross to a different provider by default
- [x] Crosses to another provider when the model opts in
- [x] A rejected request is neither retried nor fallen back from
- [x] `allowFallback: false` is honoured
- [x] Every candidate unavailable gives a 503 naming each reason
- [x] Fallback re-validates against the fallback model's own limits

Retry and failure handling:

- [x] A retryable failure is retried up to the limit, asserted by call count
- [x] A non-retryable failure is **not** retried
- [x] An exhausted retry gives up on that model and falls back
- [x] Backoff grows exponentially and is capped
- [x] Classification: 401, 429, context-length, 400, timeout and unknown are each mapped correctly
- [x] A provider exception message never contains the prompt
- [x] Three consecutive retryable failures mark a provider unhealthy
- [x] **A rejected request does not mark a provider unhealthy** — it is up, and taking it out of rotation is wrong
- [x] A success clears an unhealthy state

Limits and parameters:

- [x] Temperature is **refused** for a model that does not support it
- [x] Temperature out of range is refused
- [x] A supported temperature reaches the provider
- [x] An absent temperature is passed as null, not as a guessed default
- [x] `maxOutputTokens` above the model's limit is refused
- [x] An over-long message is refused **before** any provider is contacted

Streaming, asserted on the wire format:

- [x] `meta`, then tokens, then `done`, in that order
- [x] Every chunk arrives as its own `token` event
- [x] `meta` is valid JSON; `done` carries the same request id
- [x] A quote or newline in a token does not break the framing
- [x] An unavailable provider yields one `error` event, not a truncated stream
- [x] A mid-stream fault becomes an `error` event and **no** `done`
- [x] `error` carries `retryable`, and it is false when retrying cannot help
- [x] An unknown model and a blank message are refused before a stream opens
- [x] A completed stream is recorded as streamed usage

Authorization:

- [x] Every route returns 401 without a token
- [x] Forged, **unsigned (`alg: none`)**, **algorithm-confused**, expired, wrong-issuer and
      wrong-audience tokens are each rejected
- [x] A valid user token is accepted — Chat Service forwards the caller's own token
- [x] An ADMIN token confers nothing extra; there is no admin-only route
- [x] Health is public and reveals no configuration detail; other actuator endpoints are guarded
- [x] **No response body contains the provider credential**, and no availability reason does

Statelessness and configuration:

- [x] The service starts and serves with **no datasource, no provider key and no network**
- [x] A misspelled provider key is refused at startup rather than silently meaning nothing
- [x] Usage records carry no prompt text and no user identity

**Not covered, recorded rather than implied:**

- [ ] **No live provider call has ever been made.** There are no credentials and no network in this
      environment, so every provider interaction is exercised against a stub Spring AI `ChatModel`.
      The translation layer, routing, failure classification and framing are all verified; the
      providers themselves are not.
- [ ] **A circuit breaker is not implemented.** Health tracking marks a provider unhealthy for 30 s
      after three consecutive retryable failures and recovers on its own. That is a cooldown, not a
      breaker with half-open probing, and the plan's wording overstated it.
- [ ] **No Kafka topic is produced.** Usage is in memory and served over HTTP, because the service
      owns no database. `UsageSink` is the seam a later phase replaces.
- [ ] **No blocking generation from Chat Service.** `ChatGenerationPort` still resolves to
      `DisabledChatGenerationPort`; see `ARCHITECTURE.md` §16c for why, and why the right fix is a
      service credential rather than relaying the user's token.

**Frontend streaming. Built; not covered by automated tests.**

- [x] Type-checks under `strict`, lints with 0 errors, builds
- [x] SSE read over `fetch` with a hand-written frame parser, because frames split across network
      chunks and a parser that assumes otherwise drops or duplicates text depending on timing
- [x] Progressive rendering: plain text while tokens arrive, rendered once on completion
- [x] Markdown, GFM tables, KaTeX maths, highlighted code blocks, per-block copy
- [x] Stop aborts the request and **keeps** the text that arrived
- [x] Retry re-opens the stream only, and is offered only when the server said retrying could help
- [x] `prefers-reduced-motion` honoured
- [x] No credential in `dist/`
- [x] **No provider endpoint is reachable from the browser** — the only credential it can send is a
      cookie, and AI Service has no gateway route
- [x] **KaTeX and highlight.js loaded on demand.** ~132 kB gzipped that most users never need
- [x] **Initial JavaScript 156.7 kB gzipped**, within the 200 kB budget; total 288.5 kB within the
      320 kB ceiling. The previous check summed every chunk against the *initial* budget, so it
      could only be satisfied by deleting the feature -- see ARCHITECTURE.md 9.2a
- [ ] **Component tests and E2E** — still not built. There is no frontend test runner, so the
      streaming parser and the Markdown renderer are verified by inspection and by type-checking
      only. This is the same gap recorded in Phase 3 and it is now larger.

### Phase 5 - Chat memory window
- [ ] The window actually sent to a model is the last N turns, not the whole history
- [ ] A token estimate is recorded and a too-large window is refused, not silently truncated
- [ ] A generated reply is persisted on the message row, so a reload shows the same text
- [ ] A `PENDING` placeholder stuck from a crash is reconciled rather than shown forever
- [ ] Generation through `ChatGenerationPort` using a **service credential** (`ARCHITECTURE.md` §16c)
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

## 7.1 Implemented in Phase 1 (auth-service)

All of the following are **implemented and tested**, not merely planned. 87 tests pass against
real PostgreSQL 17.

| Control | Test |
|---|---|
| No plaintext password stored | `registersAccount` asserts the stored hash differs from the submitted password |
| Argon2id, never reversible | Single `PasswordEncoder` bean, so every code path can verify |
| No user enumeration, response | `noUserEnumeration` asserts identical `code` and `message` |
| No user enumeration, **timing** | `burnTimeOnMissingUser` performs a real Argon2 verification against a dummy hash for an unknown email |
| Token integrity | `rejectsExpired`, `rejectsWrongAudience`, `rejectsWrongIssuer`, `rejectsGarbage` |
| Algorithm confusion | `rejectsAlgNone`, `rejectsAlgorithmConfusion` |
| Foreign key rejected | `rejectsForeignSignature` |
| Mismatched key pair refused at startup | `mismatchedPairFailsFast` |
| Refresh token hashed at rest | `resetTokenIsStoredHashed` |
| Refresh reuse detection | `detectsTokenReuse`: the rotated token dies with its family |
| Password change revokes sessions | `changeRevokesOtherSessions`, `resetRevokesAllSessions` |
| Change requires the current password | `changeRequiresCurrentPassword` |
| One-time tokens, single use | `verificationTokenIsSingleUse`, `resetTokenIsSingleUse` |
| Purpose separation | `verificationTokenCannotResetPassword` |
| Generic reset response | `forgotPasswordDoesNotEnumerate` |
| Reset clears lockout | `resetClearsLockout` |
| Account lockout | `locksAfterRepeatedFailures` |
| **Suspension not bypassable** | `oldLinkDoesNotResurrectSuspendedAccount`, `suspendedAccountIsRefused` |
| Google: unverified email refused | `refusesUnverifiedGoogleEmail` |
| Google: no password ever stored | `createsAccountOnFirstSignIn` asserts `passwordHash` is null |
| Google: subject beats a changed email | `sameSubjectIgnoresChangedEmail` |
| **Database isolation** | `DatabaseOwnershipIntegrationTest`, verified in both directions |
| CSRF protection on | `SecurityConfig`; logout requires a token |
| No credential in any DTO | `noCredentialInAnyDto` reflects over the response records |
| Secrets absent from source | CI R9, with a PEM pattern requiring a body, not just a header |

### Tests that exist to prove the tests work

Three checks were written to fail if their own subject regresses:

| Test | Prevents |
|---|---|
| `DatabaseOwnershipIntegrationTest` self-verified by granting and revoking `CONNECT` | A green build while isolation is actually broken |
| `rules.yml` R6 / R11 self-tests | Prohibition checks that silently stop matching |
| `rules.yml` R10 self-test | Flagging a role named only in a prohibition comment |

### 7.2 Defects found by testing, and what they teach

Four real defects were found in Phase 1. **None was visible by reading the code.** Each was
found by an integration test asserting a specific property, which is the argument for
integration tests over coverage percentages.

| Defect | Consequence had it shipped |
|---|---|
| `verifyEmail()` permitted `SUSPENDED → ACTIVE` | Anyone holding a stale verification link could **lift an administrator's suspension** |
| Google sign-in skipped the account status check | A **suspended user could sign in through Google** while locked out of password sign-in |
| The failed-login counter was written in the transaction that then threw | It was **rolled back by the rejection**, so lockout never engaged and accounts could be brute-forced indefinitely |
| The refresh-token family revocation was written in the transaction that then threw | It was **rolled back by the error**, so detecting a stolen token and then not revoking it was a silent no-op |

**Two lessons worth carrying forward.**

1. **A status check must be applied after every identity resolution path**, not just the obvious
   one. Two separate bypasses came from adding a new sign-in path and not re-checking state.
2. **Security state written in the same transaction as the rejection it causes is state that
   never persists.** Anything that must outlive a failed request — a lockout counter, a
   revocation — needs its own `REQUIRES_NEW` boundary in a separate bean.

### 7.2b Defects found in Phase 2, and what they teach

Every one of these was invisible to inspection. Each was found by a test asserting a specific
property, which is the whole argument for writing tests that state what must be true rather than
tests that execute what exists.

| Defect | Consequence had it shipped |
|---|---|
| `AuthenticatedCaller.getPrincipal()` returned `this` | `AbstractAuthenticationToken.getName()` sees an `AuthenticatedPrincipal` and calls `getName()` on it, which is itself → **`StackOverflowError` on every authenticated request** |
| An unmatched URL fell through to the catch-all `Exception` handler | Every typo, stale bookmark and scanner probe returned **500 instead of 404**, sending operators hunting a fault that did not exist |
| The correlation filter validated the inbound id but forwarded the original header | The attacker's string still reached **every downstream service's logs**. Sanitising the response while leaving the request dirty is not sanitising |
| The gateway used `StripPrefix=1` for `/api/auth/**` | Yields `/auth/login`; Auth Service answers on `/api/v1/auth/**`, so **every auth route 404s at the upstream** — which reads like a broken service, not a broken route |
| `spring-boot-starter-oauth2-resource-server` was on the gateway classpath | Its auto-configured chain ran **before** the gateway's filters and rejected everything with an **empty-body 401**, so the gateway 401'd valid requests and its own verification never executed |
| Gateway routes were configured under `spring.cloud.gateway.*` | Gateway 5 renamed the prefix. The old one is **silently ignored**: the app starts, health is green, there are **zero routes** |
| `String.valueOf(exchange.getAttribute(...))` in the gateway | `getAttribute` is `<T> T`, so javac infers `T = char[]` and binds the `valueOf(char[])` overload. Compiles; **throws `ClassCastException` at runtime**, so every rejection returned **500 instead of 401** |
| `pref_updated_at` and `updated_at` both mapped to `updated_at` | The embedded preferences and the profile collided on one column. Caught by `ddl-auto: validate`, which is why that setting is load-bearing |
| `preferences.updated_at` check constraint listed `'light','dark','system'` while the enum stores `LIGHT,DARK,SYSTEM` | **The first insert of a preference would have failed.** Found by writing the constraint out and reading it against the enum |

**Three lessons worth carrying forward.**

1. **Assert on what crossed the wire, not on what the component believes it did.** A gateway
   that strips a header internally while forwarding it intact passes any assertion made against
   its own state. The recording upstream exists for exactly this.
2. **A silent-ignore failure mode is the expensive kind.** The gateway prefix and the
   resource-server starter both produced a running, healthy application that answered every
   request wrongly. Neither raised an error. Configuration that is *accepted but not applied* is
   worse than configuration that is rejected.
3. **Read the constraint and the enum side by side.** `CHECK (theme IN ('light','dark','system'))`
   beside an `enum` that serialises to `LIGHT` is a defect that no compiler finds and that only
   surfaces on the first real write.

### 7.2c Defects found in Phase 4, and what they teach

Phase 4 produced **the highest count of silent-failure defects of any phase**, and that is the
finding. Every one of these produced a *running, healthy application that answered every request
wrongly*, or a stream that looked fine and delivered nothing.

| Defect | Consequence had it shipped |
|---|---|
| `spring.ai.model.chat` was left unset | Both provider auto-configurations activate on `matchIfMissing = true` when the property is **absent**, then each throws wanting a key. **The service cannot start on a machine with no credentials** — inverting the whole "no key is a supported state" design |
| OpenAI's embedding/image/audio/moderation auto-configurations were left enabled | Each activates on its own properties and fails startup demanding a credential this service has **no use for**. Same outcome, different route |
| Provider map keys were looked up as `"OPENAI"` while YAML writes `openai:` | Spring binds map keys **exactly as written**, so every provider reported "not configured". The service started, was healthy, and could never call anything |
| `SpringAiProviderClient` was annotated `@Component` with a `ProviderKind` constructor parameter | Component scanning cannot supply it: **the context failed to start**, in a way that only an integration test would surface |
| `"…" + "…%d}".formatted(x)` — concatenation *before* `.formatted` | `.formatted` binds to the adjacent literal only, so two placeholders got one argument. **Every stream ended in `error` instead of `done`** after delivering the complete answer |
| The `meta` event's JSON ended with a trailing comma | Invalid JSON in the **first frame of every stream** |
| SSE frames were relayed line by line instead of re-assembled | `event:` and `data:` are **one frame**. Forwarding them as two sends produces two malformed events and a browser that receives neither |
| The JWT filter did not run on the async dispatch | Streaming responses arrived unauthenticated and Spring Security **refused an endpoint the caller had already been admitted to** — with the response half-written, so the failure could not even be reported |
| A `pom.xml` comment mentioned `datasource` and `Flyway` on continuation lines | CI rule R5 strips only comment lines **starting** with a marker, so a wrapped XML comment **fails its own rule** and would have broken every build |
| The test `application.yml` shadowed the production one | Spring Boot loads **one resource per location and never merges them**. The tests exercised a configuration that **ships to nobody**, and every regression in the real file would have passed CI |
| `ProviderHealth` is a process-wide singleton | Test pollution, but the real finding is that **state leaks between unrelated tests** and the resulting failures look like routing bugs rather than test pollution |
| The bundle check summed **every** chunk in `dist` and compared it to the **initial** 200 kB budget | It measured a different quantity than the budget describes, and made code-splitting **strictly worse**: deferring code changed nothing for the check while genuinely improving what a user downloads. The only way to satisfy it was to delete the feature |
| A hand-written `advancedChunks` group matched `/remark-\|rehype-/` into the "math" chunk | It swallowed `remark-gfm`, a **static** dependency of the plain renderer, so the maths chunk was back on the critical path despite being dynamically imported. The 88 kB the lazy load was supposed to remove was still being downloaded |
| The budget script traversed the manifest by output path instead of manifest key | Vite keys chunks by name (`_math-abc.js`) and emits them at a path (`assets/math-abc.js`). Resolving on the wrong one finds nothing, reports a **near-zero payload, and passes** |

**Seven lessons worth carrying forward.**

1. **`matchIfMissing = true` reads backwards.** It does not mean "enable when the operator has not
   chosen"; on a provider selector it means *absent configuration activates everything*. Every
   third-party auto-configuration has to be read, not trusted.
2. **A library that fails startup when an optional credential is absent is unusable in a service
   where that credential is legitimately optional.** The fix is not to supply a dummy key; it is to
   exclude the auto-configurations and say why.
3. **Assert on the wire format, not on the return value.** Three of the defects above are
   invisible to a test that calls the method and inspects what comes back. They exist only in the
   bytes.
4. **Configuration keys are case-sensitive and preserved exactly as written.** Anything read from a
   `Map<String, …>` bound from YAML needs normalising, and an assertion that a provider is
   "configured" is what catches it.
5. **A test configuration file that replaces production configuration is worse than no test.** It
   reports green while testing something that does not ship. Overlay a profile; never shadow.
6. **A check that measures a different quantity than its budget describes is worse than no check.**
   The bundle check summed every chunk and compared it to an *initial-load* budget, so the only way
   to make it pass was to remove the feature it was protecting. When a check fails, read what it
   measures before deciding whether the code or the number is wrong.
7. **A budget check that cannot fail is not a check.** Two of the defects above produce a *passing*
   check. The replacement script was verified to fail when each cap is lowered below the measured
   value, and to fail loudly when its input is missing -- because the previous generation of this bug
   was a measurement that silently reported zero.

### 7.2d The one that testing did not catch, and should have

`JwtVerifier.subjectOf` parses the token subject as a UUID and throws `IllegalArgumentException` on
anything else — which the filter catches and turns into "no authentication". The test tokens used
subjects like `"nexa-user"`, so **every valid-token test was silently asserting a 401 and calling it
success** until the assertion on the expected status was read properly.

The fix was to make the test tokens realistic. The durable lesson: **a token fixture that is less
realistic than production will quietly turn a success assertion into a failure assertion**, because
both are `status == X` and only one of them is what you meant. Test data has to satisfy the same
constraints production data does, and the negative-path tests here would have failed loudly while
the positive ones passed for the wrong reason.

---
### 7.3 CI rule checks are themselves tested

The architecture rule checks were run against the real repository rather than assumed correct.
That found **five defects in the checks during Phase 0 and three more in Phase 1**, mostly of one
kind: a check that fires on correct code.

| Defect | Consequence |
|---|---|
| `.gitignore` contained a bare `docs/` line | **Every document silently excluded from the repository** while still present on disk, and CI reporting green |
| R6 matched application prose stating the prohibition | The ZooKeeper check would fail on the text that forbids it |
| R10 matched its own pattern inside the workflow file | Every run would fail on the file containing the rule |
| R10 flagged an enum's own prohibition comment | Prose forbidding a role was read as using one |
| R11 recursed into `node_modules` | Took over two minutes, long enough that people skip it |
| R11's `[^t]` pattern backtracked and matched a space | The legal `RAZORPAY_MODE: test` was flagged |
| R9 flagged a PEM header literal | A key *parser* was mistaken for a leaked key |
| R4 flagged the isolation test | The test that must name every foreign database was failed for naming them |

The consistent lesson: **a check that fails on correct code is a check that gets switched off.**
Each prohibition check now carries a self-test proving it still catches a real violation, and the
rule that exclusion globs must actually match the file they intend to exclude.

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
7. Documentation and [`ARCHITECTURE.md`](ARCHITECTURE.md) §14 and §17 are updated.
8. Genuine failures are fixed, not suppressed.
9. Remaining issues are reported honestly.
