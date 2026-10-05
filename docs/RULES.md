# RULES.md — Binding Development Rules

These are **rules**, not suggestions. Section numbers are cited by CI checks, by code review and
by the other documents in this folder, so they are stable: never renumber a section, only add
new ones at the end.

Each rule below states the prohibition, then **why** it exists, then how it is enforced. A rule
that cannot be checked is a wish.

Current phase: **Phase 1 - Auth Service**, complete. Phase plan:
[`ARCHITECTURE.md`](ARCHITECTURE.md) §14.

---

## 1. No monolith

**Rule.** The backend is exactly eight independent Spring Boot applications. There is no
`backend/pom.xml`. There is no ninth application and no tenth.

**Why.** A monolith is easy to start and hard to split later, because every shortcut taken in
the first month becomes load-bearing. Declaring the boundary on day one keeps the decision
reversible and cheap.

**Enforcement.**
- No aggregator `pom.xml` anywhere under `backend/`.
- Each service is built from its own directory: `cd backend/<service> && mvn verify`.
- CI builds one job per service, in its own working directory.

---

## 2. No fake microservices, and no shared business logic

**Rule.** A service that cannot be built, run, scaled and failed independently is not a
microservice. Forbidden:

- One Spring Boot application with modules, packages or profiles standing in for services.
- A shared library module that other services depend on for *business* logic.
- A service `pom.xml` depending on another NexaAI service.
- A service reaching into another service's source tree at build time.
- Placeholder controllers, repositories or services added to make a service "look done".

**Why.** The most common failure mode is a codebase that is called microservices but has one
shared jar of domain logic. Every service then deploys together, so the split is cosmetic and
the coupling is invisible.

**Enforcement.**
- CI asserts every service `pom.xml` has a `<parent>` from Maven Central with **no**
  `<relativePath>`, and no `<modules>` block.
- CI asserts no service POM declares another NexaAI service as a dependency.
- Contract types shared between services are duplicated deliberately, per
  [`SERVICE_CONTRACTS.md`](SERVICE_CONTRACTS.md) §3, because a shared DTO jar is a shared
  business model.

**Allowed sharing.** Only genuinely technical, business-neutral concerns: a logging starter, a
test fixture base class, a Docker base image. Anything that encodes a NexaAI business decision
is prohibited, because that is the point at which a shared module starts owning the domain.

---

## 3. One database and one role per service

**Rule.** Each service owns exactly one PostgreSQL database, and connects to it with a role
granted on that database and no other.

| Service | Database | Role |
|---|---|---|
| api-gateway | *none* | *none* |
| auth-service | `nexa_auth` | `nexa_auth` |
| user-service | `nexa_user` | `nexa_user` |
| chat-service | `nexa_chat` | `nexa_chat` |
| ai-service | *none* | *none* |
| document-service | `nexa_document` | `nexa_document` |
| rag-service | `nexa_rag` | `nexa_rag` |
| subscription-service | `nexa_subscription` | `nexa_subscription` |

**Never:** a service reading, writing or migrating another service's database. Not for a
report, not for a join, not "just for now".

**Why.** Data ownership is what makes a service independently deployable. The moment one
service queries another's tables, they ship together, and the split is over.

**Enforcement.**
- `infrastructure/docker/postgres/init/01-create-databases.sql` creates one database and one
  role per service and revokes `PUBLIC`.
- `infrastructure/docker/postgres/verify/02-verify-ownership.sql` asserts, using
  `has_database_privilege()`, that no service role can reach another service's database.
- CI asserts the set of `nexa_*` database names appearing in a service's sources is either
  empty or exactly that service's own database.
- Each service owns its Flyway migrations. Nobody edits another service's schema, not even by
  hand.

---

## 4. Kafka runs in KRaft. ZooKeeper is prohibited.

**Rule.** Kafka runs in KRaft mode only. There is no ZooKeeper container, no
`KAFKA_ZOOKEEPER_CONNECT`, no `ZOOKEEPER_*` variable, no ZooKeeper client dependency, and no
`kafka.admin.ZkAdminClient` usage.

**Why.** ZooKeeper is a second stateful system with its own failure modes, its own memory
profile and its own operational story. KRaft removed the need for it in Kafka 4. Keeping it
"in case" means carrying a distributed system that nothing uses.

**Enforcement.**
- CI greps code and configuration for `zookeeper`, ignoring comment lines and Markdown so the
  rule can be *named* in the very place it would be violated.
- CI asserts `KAFKA_PROCESS_ROLES` and `KAFKA_CONTROLLER_QUORUM_VOTERS` are present in
  `infrastructure/docker-compose.yml`.
- `infrastructure/docker/kafka/README.md` documents the topology.

**REST versus Kafka.** Synchronous request/response (service APIs, gateway routing) uses REST.
Asynchronous work (events, background processing, analytics, document processing, payment
notifications) uses Kafka. Choosing wrongly is a design error, not a style preference: a Kafka
topic is not an RPC channel and an HTTP call is not a queue.

---

## 5. Bootstrap and SCSS only. Tailwind is prohibited.

**Rule.** The frontend is React + Vite + TypeScript + Bootstrap 5 + SCSS, with Lucide icons.
Prohibited: Tailwind, Next.js, Vue, Angular.

**Why.** Tailwind generates its design system as utility classes, which means the visual
language lives in markup and drifts per developer. Bootstrap plus a themed SCSS layer keeps
the design system in one reviewable place.

**Enforcement.**
- CI greps `package.json`, `src/` and `index.html` for `tailwind`.
- CI asserts `bootstrap` and `sass` are declared dependencies. A Tailwind-free project that is
  also Bootstrap-free is simply not wired to the design system.

---

## 6. No secrets in source

**Rule.** Credentials come from environment variables or a secret store. Never from source.

**Never.**
- A committed `.env`. Only `.env.example` with empty placeholders.
- An API key, signing secret, database password or payment secret in any tracked file.
- **Any AI provider key in React.** Vite inlines every `VITE_*` variable into the bundle, so
  anything prefixed `VITE_` is public. This is why the frontend has no key handling at all.
- A key printed to a log, an exception message or a health endpoint.

**Why.** Git history is permanent and public. A leaked key must be treated as compromised,
and rotation is the only real remedy.

**Enforcement.**
- CI greps tracked files for credential-shaped strings: `sk-…`, `AIza…`, `rzp_live_…`,
  `ghp_…`.
- CI asserts no `.env` file is tracked.
- CI asserts every `.env.example` contains only empty or obviously-local placeholder values.
- CI greps the built frontend bundle for credentials.

---

## 7. Redis is never the system of record

**Rule.** PostgreSQL is the primary relational database; pgvector is used for vector search.
Redis is a cache, a rate-limit counter store, a denylist and a short-lived lock holder.

**Rule.** Losing every Redis key must degrade performance, never correctness.

**Why.** Redis has no durability guarantee configured here, so any fact stored only in Redis is
a fact that can vanish silently.

**Enforcement.** No persistence is enabled in `infrastructure/docker/redis/redis.conf`, so the
constraint is enforced by the configuration rather than by discipline.

---

## 8. Exactly two roles: USER and ADMIN

**Rule.** The application has exactly two roles.

**Prohibited roles:** `SUPER_ADMIN`, `MODERATOR`, `SUPPORT`, `OWNER`, or any other invented
tier.

**Why.** Every extra role multiplies the number of authorisation checks that must be written
and tested. Two roles are enough for the product in [`PRD.md`](PRD.md) and keep the
authorisation matrix small enough to verify by hand.

**Enforcement.** CI asserts the set of role identifiers in the source is a subset of
`USER` and `ADMIN`.

---

## 9. Razorpay TEST MODE ONLY

**Rule.** Payment integration is test mode only. The service refuses to start if the mode is
anything other than `test`.

**Never.** Real money. A live-mode variable. A live key. Production mode "temporarily" enabled
to check something.

**Why.** This is an educational project. A live key in a public repository is an incident, not
a convenience.

**Enforcement.**
- There is deliberately **no** live-mode variable anywhere in this repository.
- CI fails if `rzp_live_` appears in any tracked file.
- `RAZORPAY_MODE` is pinned to `test` in `infrastructure/docker-compose.yml`.

---

## 10. Phase discipline

**Rule.** Implement only the phase explicitly requested. Do not build ahead. The phase plan is
recorded in [`ARCHITECTURE.md`](ARCHITECTURE.md) §14.

**Specifically.** No placeholder business logic, no stub controllers standing in for a future
feature, no speculative schema. A planned service directory may be empty; an empty directory
with a README is honest, a directory full of fake code is not.

**Why.** Work built ahead of its phase is work built without its requirements, and it is
usually wrong in ways that are expensive to delete later.

**Enforcement.** CI is phase-aware. It discovers which services actually exist and validates
only those. It never requires a future service to compile, and it never builds an image for a
service that has no Dockerfile.

---

## 11. CI must not hide failures

**Rule.**

- Never skip a check, add `--exit-zero`, or relax an assertion to make a pipeline green.
- Never catch and swallow an exception without recording it.
- A CI job that finds nothing to do says so explicitly in its output. It does not report a
  pass that implies work was verified.
- Infrastructure checks must assert, not warn. A warning nobody reads is not a control.

**Why.** A green pipeline that skipped the real work is worse than a red one, because it
removes the signal while appearing to keep it.

**Enforcement.** This document is the standard the reviewer applies to any change that
weakens a workflow.

---

## 12. Roles of the documentation

**Rule.** Documentation is source code, and it is kept in the same change as the code.

**Rule.** The project contains exactly seven documents, and no others:

[`PRD.md`](PRD.md) · [`ARCHITECTURE.md`](ARCHITECTURE.md) · [`RULES.md`](RULES.md) ·
[`SERVICE_CONTRACTS.md`](SERVICE_CONTRACTS.md) · [`SECURITY.md`](SECURITY.md) ·
[`TEST_PLAN.md`](TEST_PLAN.md) · [`MEMORY.md`](MEMORY.md)

**No** `DESIGN.md`, `TASKS.md`, `DECISIONS.md`, `ROADMAP.md`, `NOTES.md`, `CHANGELOG.md`,
`API.md`, `DATABASE.md` or `IMPLEMENTATION.md`. Progress, decisions and rationale live inside the
seven, in their appropriate section. Adding an eighth document to avoid editing the right one is
the failure this rule prevents.

**There is no `DESIGN.md`.** Frontend architecture, the visual system and the design tokens live in
[`ARCHITECTURE.md`](ARCHITECTURE.md) §9. A separate design document duplicated that section and then
drifted from it, which is the exact problem this rule exists to prevent.

**`MEMORY.md` is one of the seven.** It is the short, always-current statement of what is true
now: what exists, what does not, and which rules are not to be broken. It is deliberately brief
and is not a place for detail — detail belongs in the document that owns the subject.

- A change to a REST or Kafka contract updates [`SERVICE_CONTRACTS.md`](SERVICE_CONTRACTS.md).
- A change of architectural direction updates [`ARCHITECTURE.md`](ARCHITECTURE.md) §13, and any
  new prohibition updates this file.
- Phase status updates [`ARCHITECTURE.md`](ARCHITECTURE.md) §14 and §17.
- Test findings update [`TEST_PLAN.md`](TEST_PLAN.md).
- Secrets never appear in documentation, not even as "the real value".

**Never** ignore the `docs/` directory. It is tracked, and CI fails the build if a bare `docs/`
line appears in `.gitignore`, or if any of the seven is missing.