# DECISIONS.md — Architectural Decision Records

Each entry states the decision, the reasoning, the consequences including the unwelcome ones, and
the alternatives that were rejected. A decision without recorded consequences is not a decision;
it is a preference.

Format follows the conventional ADR shape. Entries are append-only: a decision that is reversed
gets a new entry superseding the old one, so the reasoning stays readable.

Related: [`ARCHITECTURE.md`](ARCHITECTURE.md) for the resulting system,
[`RULES.md`](RULES.md) for the binding constraints.

---

## Index

| # | Decision | Status |
|:--:|---|---|
| 001 | Eight independent Spring Boot services | Accepted |
| 002 | PostgreSQL as the primary database, one database per service | Accepted |
| 003 | Kafka in KRaft mode | Accepted |
| 004 | REST for synchronous, Kafka for asynchronous | Accepted |
| 005 | Redis is a cache only, never the system of record | Accepted |
| 006 | Spring AI as the sole AI integration layer | Accepted |
| 007 | AI Service is stateless | Accepted |
| 008 | pgvector, owned solely by RAG Service | Accepted |
| 009 | Razorpay test mode only | Accepted |
| 010 | Exactly two roles: USER and ADMIN | Accepted |
| 011 | React + Vite + TypeScript, Bootstrap 5 + SCSS, no Tailwind | Accepted |
| 012 | No aggregator POM; services are built independently | Accepted |
| 013 | Duplicate DTOs rather than share a contract jar | Accepted |
| 014 | The gateway authenticates; services authorise | Accepted |
| 015 | Empty service directories in Phase 0, no stub code | Accepted |
| 016 | Phase-aware CI | Accepted |
| 017 | Memory is a bounded visible window, not a summarisation system | Accepted |
| 018 | No service mesh | Accepted |
| 019 | Flyway migrations owned per service | Accepted |
| 020 | Consumer-driven topic creation | Accepted |
| 021 | SSE rather than WebSocket for streaming | Accepted |

---

## ADR-001: Eight independent Spring Boot services

**Status.** Accepted.

**Context.** NexaAI must be a genuine microservices application. The obvious alternative is one
application with modules, which is faster to build and far easier to deploy.

**Decision.** Exactly eight independently buildable Spring Boot applications, each in its own
directory with its own `pom.xml`, port, configuration, source tree and tests.

**Reasoning.** The split follows the axes along which the product must change independently:
a security boundary (auth), statelessness (AI inference), cost shape (embedding vs generation)
and data ownership (documents vs vectors). A boundary that does not buy independent scaling,
independent deployment or a real security boundary is decoration.

**Consequences.**
- *Positive:* a service can be restarted, scaled or replaced without touching the others.
  Failure domains are real.
- *Negative:* eight deployments instead of one. Eight sets of logs, health checks and pipelines.
- *Negative:* cross-service features need a network call and an eventual-consistency story.
  A user registering means a REST call plus a Kafka event, not one transaction.
- *Negative:* more upfront infrastructure work before any feature exists.

**Alternatives rejected.**
- *Modular monolith:* explicitly prohibited, and correct to prohibit: the shortcuts taken in
  month one become load-bearing.
- *Twelve or more services:* finer splitting without a real boundary produces chatty services
  and no benefit.
- *Fewer than eight:* merging AI inference into Chat Service would make the provider
  credentials and the rate-limit-sensitive path the same process as conversation storage.

---

## ADR-002: One database and one role per service

**Status.** Accepted.

**Context.** Services could share one PostgreSQL instance and one schema, which is simpler, or
have physically separate instances, which is expensive locally.

**Decision.** One PostgreSQL instance holding one **database** and one **role** per service. A
role is granted on exactly one database. `PUBLIC` is revoked. No service ever reads another's
database.

**Reasoning.** A physically separate server per service is the production end state, but eight
local servers is unacceptable friction, and nothing in the application code knows they share a
host. Database-level isolation gives the architectural guarantee — a cross-service query fails
with a permission error rather than quietly succeeding — without the local cost.

**Consequences.**
- *Positive:* isolation is enforced by PostgreSQL, not by discipline. It is checkable with a
  script, and CI asserts it.
- *Positive:* services can be moved to separate instances by changing a connection string.
- *Negative:* six databases on one instance, so a host failure takes everything down. Accepted
  as a local-development convenience; production separation is a deployment decision.
- *Negative:* no cross-service joins. A report spanning users and subscriptions is assembled in
  the consumer from events, not in SQL.
- *Negative:* migrations are per service, so schema changes cannot be coordinated atomically
  across services.

**Alternatives rejected.**
- *One schema per service in one database:* weaker. A role granted on the database can still
  read any schema it is granted on, and the guarantee weakens to convention.
- *A shared read-only reporting role:* a role that can see two databases defeats the whole
  rule. No such role exists.
- *Per-service database servers:* correct for production, too expensive for development.

---

## ADR-003: Kafka in KRaft mode

**Status.** Accepted.

**Context.** Kafka has historically required ZooKeeper. Kafka 4 can run without it.

**Decision.** KRaft only. No ZooKeeper container, variable, client or dependency. ZooKeeper is
prohibited by rule.

**Reasoning.** ZooKeeper is a second stateful distributed system with its own memory profile,
failover behaviour, version matrix and operational story. Keeping it "in case" means carrying
a system nothing uses. KRaft removes it.

**Consequences.**
- *Positive:* one stateful system instead of two. For a single-broker development setup that is
  the difference between running one thing and running two.
- *Positive:* CI can assert KRaft by configuration, which is unambiguous.
- *Negative:* local replication factors are `1`; production needs three brokers and replication
  factor `3`. This is a real gap, recorded in [`SECURITY.md`](SECURITY.md) §13 and Phase 11.
- *Negative:* no ZooKeeper means no legacy migration path for an existing cluster. Irrelevant
  here, since there is no existing cluster.

**Alternatives rejected.**
- *ZooKeeper:* prohibited, and operationally worse.
- *A managed cloud Kafka:* removes the KRaft learning, but adds a vendor dependency and a cost
  for what is a self-contained educational project.

---

## ADR-004: REST for synchronous, Kafka for asynchronous

**Status.** Accepted.

**Context.** Services must communicate. Using one mechanism for everything is simpler to
explain and wrong in practice.

**Decision.** REST for request/response, including streaming and webhooks. Kafka for events,
background processing, analytics, document processing and payment notification.

**Reasoning.** The deciding question is whether the caller needs an answer to proceed. If yes,
REST. If no, Kafka. Choosing wrongly is not a style preference: a Kafka topic is not an RPC
channel, and an HTTP call is not a queue.

**Consequences.**
- *Positive:* each mechanism is used where it is strong. Events give durability, replay and
  fan-out; REST gives an answer.
- *Negative:* two consistency models to reason about. A REST call is immediately consistent;
  an event is eventually consistent, and code must tolerate the delay.
- *Negative:* consumers must be idempotent, because Kafka delivers at least once.
- *Negative:* debugging spans two tools.

**Alternatives rejected.**
- *REST for everything:* a background document-processing request would have to block a
  connection for the whole extraction.
- *Kafka for everything:* the caller would need request-reply over topics, which is an RPC
  channel built on a log. Far harder to operate and far harder to debug.

---

## ADR-005: Redis is a cache only

**Status.** Accepted.

**Context.** Redis could be the primary store. It is fast, and it supports vectors.

**Decision.** PostgreSQL is the primary relational database; pgvector handles vector search.
Redis is a cache, a rate-limit counter store, a denylist and a short-lived lock holder.
Persistence is disabled.

**Reasoning.** Losing every Redis key must degrade performance, never correctness. Disabling
persistence is how that is enforced rather than merely intended: a fact stored only in Redis
disappears, and the bug surfaces in development rather than in production.

**Consequences.**
- *Positive:* Redis can be flushed, restarted or replaced with no data loss.
- *Positive:* the design is forced towards durable state, which is usually correct.
- *Negative:* every Redis read must have a database fallback, so there is more code.
- *Negative:* slightly slower reads on a cold cache.
- *Negative:* the denylist resets on restart, so a revoked token can live out its remaining
  natural lifetime. Bounded by the 15-minute access token TTL, and accepted.

**Alternatives rejected.**
- *Redis as the primary store:* makes a fast dependency the only copy of the data.
- *Redis persistence enabled:* hides the design error instead of surfacing it.

---

## ADR-006: Spring AI as the sole AI integration layer

**Status.** Accepted.

**Context.** Providers can be called through their own SDKs, or through an abstraction.

**Decision.** Spring AI is the core integration layer. OpenAI, Google Gemini and Groq/LLaMA are
reached only through it. No service may call a provider SDK directly.

**Reasoning.** A provider abstraction that lives in one place means one place to change when a
provider renames a parameter or changes a streaming chunk shape. Without it, provider quirks
leak into Chat Service and into eight services' worth of future code.

**Consequences.**
- *Positive:* provider quirks are contained.
- *Positive:* adding a provider touches one module.
- *Negative:* Spring AI is an abstraction, so the lowest common denominator is available and
  provider-specific features need an escape hatch. The escape hatch is used sparingly.
- *Negative:* a dependency on Spring AI's release cadence.

**Alternatives rejected.**
- *Raw provider SDKs:* quirks leak everywhere.
- *LangChain4j:* a reasonable alternative; Spring AI was chosen for first-class Spring
  integration.

---

## ADR-007: The AI Service is stateless

**Status.** Accepted.

**Context.** Inference could live inside Chat Service.

**Decision.** AI Service owns no database and holds no user, conversation or document data. It
receives a fully-built request and returns tokens.

**Reasoning.** Inference is the expensive, rate-limited, provider-credentials-holding path. If it
is stateless it scales horizontally with no session affinity, restarts with no data loss, and
holds provider credentials in exactly one place rather than eight.

**Consequences.**
- *Positive:* trivial horizontal scaling and a tiny failure domain.
- *Positive:* provider credentials exist once.
- *Positive:* no conversation data is exposed to the component holding the keys.
- *Negative:* the full prompt must be assembled by the caller, so Chat Service must know the
  model's context size and limits. That coupling is real and is handled through the model
  catalogue rather than by sharing code.
- *Negative:* no per-conversation state in the AI Service, so all memory lives in Chat Service.
  Correct, but it makes Chat Service the more complex of the two.

**Alternatives rejected.**
- *Inference inside Chat Service:* couples provider credentials and conversation storage, and
  makes the streaming path harder to scale.

---

## ADR-008: pgvector, owned solely by RAG Service

**Status.** Accepted.

**Context.** Vectors could live in Redis, or in each service's own database.

**Decision.** pgvector, in `nexa_rag`, loaded in no other NexaAI database.

**Reasoning.** The vector store is a derived artefact with one owner. Putting it anywhere else
splits it. Putting it in Redis would make vector search non-durable and violate ADR-005.

**Consequences.**
- *Positive:* retrieval performance is far better than application-side similarity search.
- *Positive:* one database, one owner, one migration stream.
- *Negative:* retrieval needs a network call to RAG Service.
- *Negative:* the embedding dimension is fixed per model, so changing embedding models means
  re-embedding every document. This is a genuine operational cost, accepted.
- *Negative:* the RAG database needs pgvector, which constrains the hosting choice.

**Alternatives rejected.**
- *Vectors in Redis:* rejected by ADR-005.
- *A separate vector database:* a whole additional system for one feature.

---

## ADR-009: Razorpay test mode only

**Status.** Accepted.

**Context.** Razorpay supports test and live modes.

**Decision.** Test mode only. The service refuses to start in any other mode. There is
deliberately no live-mode variable anywhere in the repository.

**Reasoning.** This is an educational project with no commercial intent. A live key committed to
a public repository is an incident. Removing the live-mode variable means the mistake cannot be
made by flipping one value.

**Consequences.**
- *Positive:* real money cannot be processed, by accident or otherwise.
- *Positive:* the payment flow can be demonstrated fully in test mode.
- *Negative:* the live-mode integration path is never exercised. Accepted, and recorded in
  [`SECURITY.md`](SECURITY.md) §13.
- *Negative:* payment testing requires Razorpay test credentials from the developer.

**Alternatives rejected.**
- *Support both modes:* a live-money path in an educational project is a liability.

---

## ADR-010: Exactly two roles

**Status.** Accepted.

**Context.** Role-based designs usually grow a hierarchy.

**Decision.** Exactly `USER` and `ADMIN`. `SUPER_ADMIN`, `MODERATOR` and `SUPPORT` are
prohibited.

**Reasoning.** Every additional role multiplies the authorisation checks that must be written
and tested, and the matrix that must be verified by hand. Two roles cover the product in
[`PRD.md`](PRD.md) §3, and the resulting matrix is small enough to be genuinely complete rather
than nominally present.

**Consequences.**
- *Positive:* a small, verifiable authorisation matrix.
- *Positive:* no "who administers the administrators" question.
- *Negative:* some admin capabilities are coarse. Granularity is expressed through explicit
  admin endpoints instead, which is more code but more explicitness.
- *Negative:* an `ADMIN` who should not see billing can still see billing. Accepted; audit is
  the mitigation.

**Alternatives rejected.**
- *Fine-grained roles:* disproportionate to the product.
- *Permissions instead of roles:* the same problem with more configuration. Roles with explicit
  endpoints are simpler to verify.

---

## ADR-011: React + Vite + TypeScript with Bootstrap 5 and SCSS

**Status.** Accepted.

**Context.** The frontend could be Next.js, Vue or Angular, with Tailwind or Bootstrap.

**Decision.** React + Vite + TypeScript (strict), Bootstrap 5 and SCSS, Lucide icons.
Prohibited: Tailwind, Next.js, Vue, Angular.

**Reasoning.** Tailwind puts the design language in markup, where it drifts per developer.
Bootstrap plus a themed SCSS token layer keeps it in one reviewable file. Next.js adds a server
runtime that owns no data and cannot usefully server-render an authenticated client-side
application.

**Consequences.**
- *Positive:* the visual system is reviewable in one place.
- *Positive:* no server runtime to deploy or secure beyond static files.
- *Negative:* Bootstrap's default look requires genuine restyling to look designed, which is
  real work in Phase 10.
- *Negative:* Bootstrap's JS and React's rendering can fight over the DOM. Mitigated by
  importing only the specific Bootstrap plugins used and driving them from React state.
- *Negative:* No SSR, so first paint depends entirely on the bundle.

**Alternatives rejected.**
- *Tailwind:* rejected because the design system belongs in a stylesheet.
- *Next.js:* rejected because SSR buys nothing here.

---

## ADR-012: No aggregator POM

**Status.** Accepted.

**Context.** Maven projects conventionally have a parent POM with `<modules>` listing siblings.

**Decision.** No `backend/pom.xml`. Each service's parent is `spring-boot-starter-parent` from
Maven Central with no `<relativePath>`. Each service builds from its own directory.

**Reasoning.** An aggregator lets one service depend on another. That is the first step towards
a shared library, which is the first step towards a disguised monolith. Removing the aggregator
makes the boundary structural rather than conventional: a service genuinely cannot build without
its own directory.

**Consequences.**
- *Positive:* the independence claim is verifiable. `cd backend/<service> && mvn verify` works
  with nothing else present.
- *Positive:* CI can build one service in isolation, which is exactly the microservices test.
- *Negative:* the Spring Boot version is repeated eight times. Bumping it means eight edits.
  Accepted: an eight-line change is cheaper than a coupling.
- *Negative:* no shared plugin configuration, so checkstyle and surefire settings are
  duplicated. Accepted for the same reason.

**Alternatives rejected.**
- *An aggregator with `<modules>` but no shared library:* still permits a dependency to be
  added later, and the boundary is only convention.
- *A parent POM outside `backend/`:* still permits the dependency.

---

## ADR-013: Duplicate DTOs rather than share a contract jar

**Status.** Accepted.

**Context.** Two services exchanging a payload need matching types. The obvious DRY solution is
a shared module.

**Decision.** Contract types are deliberately duplicated per service, each shaped to that
service's need. [`SERVICE_CONTRACTS.md`](SERVICE_CONTRACTS.md) is the shared artefact.

**Reasoning.** A shared DTO jar looks harmless and is how a shared business model forms. Once
one jar exists, business logic joins it within a few months, and then the services are not
separate. Documentation duplicates fine; a compiled dependency does not.

**Consequences.**
- *Positive:* services cannot compile against each other's internals.
- *Positive:* each side can evolve independently.
- *Negative:* the same field is written twice. A contract test catches divergence.
- *Negative:* more code, and drift is possible without contract tests. This is a real, accepted
  cost with a named mitigation.

**Alternatives rejected.**
- *A shared contracts module:* rejected. This is the disguised-monolith path
  ([`RULES.md`](RULES.md) §2).
- *A generated client from the provider's OpenAPI document:* acceptable later if contract tests
  prove insufficient. Not now.

---

## ADR-014: The gateway authenticates; services authorise

**Status.** Accepted.

**Context.** Either the edge verifies tokens and services trust it, or every service verifies.

**Decision.** The gateway verifies the token and rejects invalid requests before routing. Each
service independently verifies the token and enforces authorisation and ownership.

**Reasoning.** Edge verification is the fast, correct default: an unsigned request never reaches
a business service. But an internal network is not a trust boundary. A service reachable from
inside the network must not trust the edge blindly, or a single misrouted or spoofed request
becomes a data breach.

**Consequences.**
- *Positive:* invalid requests are rejected cheaply, at the edge.
- *Positive:* a service is individually safe.
- *Negative:* verification runs twice. Negligible for RS256; noted because it is a real cost.
- *Negative:* a signature-key change must reach every service. Handled by publishing a JWKS
  with a key id.

**Alternatives rejected.**
- *Gateway verifies only:* one misconfiguration becomes a full bypass.
- *Services verify only:* invalid traffic reaches business logic.

---

## ADR-015: Empty service directories in Phase 0

**Status.** Accepted.

**Context.** Phase 0 establishes the foundation. It could create a buildable skeleton in each
service, or leave them empty.

**Decision.** Create the eight directories with a README describing each service's boundary,
port, database and planned responsibility. **No Java code, no controllers, no repositories, no
Dockerfiles** until the phase that implements that service.

**Reasoning.** A skeleton with a placeholder controller is not a skeleton: it is code that says
"not implemented" while looking like an implementation. It must later be deleted, and in the
meantime it invites someone to build on it. An empty directory with an honest README is truthful
about the state of the project. This is also what
[`RULES.md`](RULES.md) §10 requires.

**Consequences.**
- *Positive:* nothing in the repository pretends to work.
- *Positive:* a reviewer can see exactly what is and is not implemented.
- *Negative:* `mvn verify` cannot run in Phase 0, so there is no backend build to validate. CI is
  phase-aware and says so explicitly rather than reporting a false pass.
- *Negative:* the structure is asserted by rule checks rather than demonstrated by a build. This
  is a genuine weakness of Phase 0, resolved in Phase 1, and recorded in
  [`MEMORY.md`](MEMORY.md).

**Alternatives rejected.**
- *Full skeleton with an `/info` controller per service:* rejected. The `/info` endpoint idea is
  good and is retained in [`SERVICE_CONTRACTS.md`](SERVICE_CONTRACTS.md) §2, but it belongs to
  the service's own phase, where there is a real service to report on.

---

## ADR-016: Phase-aware CI

**Status.** Accepted.

**Context.** CI can hardcode eight services in a build matrix, or discover what exists.

**Decision.** CI discovers which services have a `pom.xml` and builds exactly those. If none
exist, it reports that explicitly. It never requires a future service to compile and never
builds an image for an absent Dockerfile.

**Reasoning.** A matrix listing services that do not exist fails every run, which trains people
to ignore a red pipeline — and a red pipeline is the signal. A workflow that says "no services
implemented yet" is honest. A workflow that skips a broken service is not
([`RULES.md`](RULES.md) §11).

**Consequences.**
- *Positive:* a red build always means a real failure.
- *Positive:* adding a service needs no CI change.
- *Negative:* a typo in a service directory name would silently exclude it. Mitigated by a rule
  check asserting the expected directory names exist.
- *Negative:* CI is slightly more complex than a static matrix.

**Alternatives rejected.**
- *A static matrix of eight:* guaranteed red until Phase 12, or eight `--exit-zero` steps,
  which is worse.
- *CI disabled until Phase 1:* no architecture enforcement during the most important structural
  phase.

---

## ADR-017: Memory is a bounded visible window

**Status.** Accepted.

**Context.** Conversation memory could be a sliding window, a summarisation system, or a vector
memory.

**Decision.** Memory is the visible conversation history plus a bounded recent window, trimmed
by **token count**, assembled from what the user can already see.

**Reasoning.** Invisible memory — a summary the user cannot inspect, or facts retrieved from a
memory store they cannot see — changes answers unpredictably. For a product whose value is
trustworthy answers, predictable and inspectable beats sophisticated and opaque. Trimming by
tokens rather than messages is necessary because models differ in context size.

**Consequences.**
- *Positive:* every token sent to a model is visible to the user.
- *Positive:* truncation is explainable, and can be stated in the answer.
- *Negative:* very long conversations lose early context. Accepted, and surfaced to the user
  rather than hidden.
- *Negative:* token counting is approximate, and providers count differently. Accepted; the
  window is set conservatively below the model's limit.

**Alternatives rejected.**
- *Summarisation:* hides context and can invent it. Rejected for a grounded-answer product.
- *Vector memory:* implies facts the user never said in this conversation. Rejected for the same
  reason. RAG is for the user's **documents**, which they can see, and is a separate feature.

---

## ADR-018: No service mesh

**Status.** Accepted.

**Context.** A mesh (Istio, Linkerd) is the standard way to get timeouts, retries and telemetry
between services.

**Decision.** No mesh. Timeouts, retries, circuit breakers and correlation ids are handled by the
gateway and by each service's own client configuration.

**Reasoning.** Eight services do not need one. A mesh adds a substantial control plane, its own
failure modes, and a steep debugging cost, in exchange for solving problems a gateway plus
explicit client configuration already solves at this size.

**Consequences.**
- *Positive:* fewer components, fewer failure modes, a simpler local stack.
- *Negative:* no automatic mTLS between services. Accepted: the network is internal, and
  production hardening is Phase 11.
- *Negative:* every service must configure its own timeouts. This is a real per-service
  obligation, and CI checks for it from Phase 3.

**Alternatives rejected.**
- *A mesh:* disproportionate at eight services.
- *Spring Cloud Gateway only, with no client configuration:* timeouts must exist at both ends.

---

## ADR-019: Per-service Flyway migrations

**Status.** Accepted.

**Context.** A central migration tool could manage every schema.

**Decision.** Each service owns its migrations under
`backend/<service>/src/main/resources/db/migration` and applies them at startup.

**Reasoning.** The service that owns the data owns its schema. A central migration runner
requires a component that can alter every database, which contradicts the isolation guarantee in
ADR-002: it would hold credentials to all six databases at once.

**Consequences.**
- *Positive:* schema ownership is unambiguous.
- *Positive:* no component can alter a database it does not own.
- *Negative:* no coordinated migration across services. A cross-service schema change needs a
  backward-compatible sequence, which is the correct discipline anyway.
- *Negative:* the first startup of a service performs migrations, so a slow first request is
  possible.

**Alternatives rejected.**
- *A central migration runner:* it would need every service's credentials. Rejected.
- *Manual DDL:* not reproducible, not reviewable.

---

## ADR-020: Consumer-driven topic creation

**Status.** Accepted.

**Context.** Topics can be created by an external script, or by the producer application.

**Decision.** The service that produces a topic declares it and creates it at startup.

**Reasoning.** The producer is the party accountable for the schema of what it publishes. An
external script is a second place where a schema is described, and it drifts.

**Consequences.**
- *Positive:* the schema and the topic definition live together.
- *Positive:* a new environment needs no manual step.
- *Negative:* requires `CREATE` ACLs for producers, where a locked-down cluster might deny it.
- *Negative:* a change to a topic definition needs a producer deployment. Accepted: topic
  changes should be deliberate.

**Alternatives rejected.**
- *An external provisioning script:* a second source of truth for schemas.

---

## ADR-021: SSE rather than WebSocket for streaming

**Status.** Accepted.

**Context.** Token streaming is server-to-client only.

**Decision.** Server-Sent Events over HTTP.

**Reasoning.** SSE is plain HTTP: it works through ordinary proxies, needs no protocol upgrade,
carries automatic reconnection, and is inspectable with ordinary tooling. WebSocket adds a
persistent bidirectional channel for a unidirectional stream, which is the definition of
unnecessary complexity.

**Consequences.**
- *Positive:* no separate protocol, no upgrade handshake, easy to debug.
- *Positive:* works through NGINX with a correct configuration.
- *Negative:* every proxy hop must disable buffering, or tokens arrive all at once at the end.
  This is configured in `infrastructure/docker/nginx/nexaai.conf` and tested end to end.
- *Negative:* browser connection limits. Each connection occupies a browser slot per origin,
  which constrains concurrency.
- *Negative:* no binary framing. Irrelevant for text tokens.

**Alternatives rejected.**
- *WebSocket:* unjustified for a one-way stream.
- *Polling:* much worse latency for the same information.