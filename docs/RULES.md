# Architectural Rules & Prohibitions — NexaAI

This document defines the architectural rules and non-negotiable prohibitions governing the NexaAI codebase. Every pull request and build pipeline must strictly comply with these rules.

---

## Section 1: No Aggregator POM
- **Rule**: There is deliberately **no root `pom.xml`** or aggregator POM in the `backend/` directory.
- **Enforcement**:
  - Each backend service resides in `backend/<service>` with its own autonomous `pom.xml`.
  - Every service declares `<parent>` as `org.springframework.boot:spring-boot-starter-parent` directly from Maven Central.
  - Using `<relativePath>` to point to a parent or sibling POM within the repository is strictly forbidden.
- **Rationale**: An aggregator POM creates hidden compilation coupling. Independent POMs guarantee true microservice autonomy.

---

## Section 2: No Cross-Service Code Dependencies
- **Rule**: No backend service may declare a Maven dependency on another service in the repository.
- **Enforcement**:
  - A service POM cannot list another service's `artifactId`.
  - Shared contracts (DTOs, event envelopes) are intentionally duplicated as independent code in each service.
  - The shared artifact is the documented contract in `docs/SERVICE_CONTRACTS.md`, not a shared binary JAR.
- **Rationale**: Shared libraries introduce tight version locking and cascade deployment failures across microservices.

---

## Section 3: No Monolith & No Fake Microservices
- **Rule**: Every microservice must be a genuine, independently runnable application.
- **Enforcement**:
  - Each service directory must contain complete source code, tests, configuration (`application.yml`), and a multi-stage Dockerfile.
  - Each service must have its own assigned port and dedicated entry point (`@SpringBootApplication`).
  - Placeholder stubs or "mock" microservices are prohibited.

---

## Section 4: Apache Kafka with KRaft — ZooKeeper is Strictly Prohibited
- **Rule**: Apache Kafka must operate in **KRaft (Kafka Raft Metadata)** mode.
- **Enforcement**:
  - Kafka version 4.x is configured with `KAFKA_PROCESS_ROLES: broker,controller`.
  - Introducing any ZooKeeper Docker container, configuration, or client dependency (`zookeeper`, `curator-framework`, `zkClient`) immediately fails CI rule check R6.
- **Rationale**: ZooKeeper adds unnecessary operational complexity, state divergence risk, and maintenance overhead.

---

## Section 5: Frontend UI Framework — Bootstrap 5 and SCSS Only (Tailwind Prohibited)
- **Rule**: The frontend web application must use **Bootstrap 5 + SCSS**.
- **Enforcement**:
  - Tailwind CSS (`tailwindcss`, `@tailwind`, `tailwind.config.js`) is strictly prohibited and fails CI check R8.
  - Other alternative frameworks (Next.js, Vue, Angular) are banned; the stack is React 18 with Vite and TypeScript.
  - The design system is implemented via custom SCSS variables overriding Bootstrap defaults (`_theme.scss`).

---

## Section 6: No Committed Credentials or Secrets
- **Rule**: Secret credentials, production tokens, private keys, and real passwords must never be committed to git.
- **Enforcement**:
  - All `.env` files are gitignored.
  - Only `.env.example` is committed to the repository, and it must contain only empty placeholders or local defaults.
  - No strings matching provider API keys (`sk-*`, `AIza*`, `ghp_*`) or private keys (`-----BEGIN PRIVATE KEY-----`) are permitted in tracked files.

---

## Section 7: Exactly Two User Roles (USER and ADMIN)
- **Rule**: The platform's RBAC system permits exactly two roles:
  1. `ROLE_USER` (`USER`)
  2. `ROLE_ADMIN` (`ADMIN`)
- **Enforcement**:
  - Any introduction of extraneous roles (`SUPER_ADMIN`, `MODERATOR`, `OWNER`, `SUPPORT`) fails CI rule check R10.
  - Role checks are enforced server-side using Spring Security method security (`@PreAuthorize("hasRole('ADMIN')")`).

---

## Section 8: Razorpay Test Mode Only — Live Keys Prohibited
- **Rule**: All payment and subscription flows must use **Razorpay Test Mode** only.
- **Enforcement**:
  - Only keys starting with `rzp_test_` are permitted in configuration.
  - The variable `RAZORPAY_MODE` must be explicitly set to `test`.
  - Any occurrence of `rzp_live_*` or variables containing `RAZORPAY_LIVE` fails CI check R11.
  - Real financial transactions are strictly blocked.

---

## Section 9: Database Ownership & Schema Isolation
- **Rule**: Every database belongs to exactly one service.
- **Enforcement**:
  - `nexa_auth` belongs exclusively to `auth-service`.
  - `nexa_user` belongs exclusively to `user-service`.
  - `nexa_chat` belongs exclusively to `chat-service`.
  - `nexa_document` belongs exclusively to `document-service`.
  - `nexa_rag` belongs exclusively to `rag-service`.
  - `nexa_subscription` belongs exclusively to `subscription-service`.
  - `nexa_analytics` belongs exclusively to `analytics-service`.
  - No service may configure connections or execute SQL against a foreign database.
  - `pgvector` is enabled and used **ONLY** in `nexa_rag`.

---

## Section 10: Stateless Services (AI Service & API Gateway)
- **Rule**: `ai-service` and `api-gateway` must remain completely stateless and have **NO relational database**.
- **Enforcement**:
  - Neither service may declare JPA, Hibernate, JDBC, or Flyway dependencies.
  - Neither service may configure a `spring.datasource` or `EntityManagerFactory`.

---

## Section 11: Document Service and RAG Service Separation
- **Rule**: Document storage and RAG vector processing must remain strictly separated into two microservices.
- **Enforcement**:
  - `document-service`: Handles file upload, metadata persistence, storage management, and raw text extraction.
  - `rag-service`: Handles text chunking, embedding generation, vector storage, and similarity retrieval via `pgvector`.
  - Communication between the two is asynchronous via Kafka topics (`document.events` and `rag.events`).

---

## Section 12: Documentation as Source Code
- **Rule**: Technical documentation is a first-class project deliverable and must be tracked in version control.
- **Enforcement**:
  - The `docs/` folder must never be ignored in `.gitignore`.
  - All core architectural and design documentation must remain up-to-date with code changes.
