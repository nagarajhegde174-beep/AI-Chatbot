# Architectural Decision Records (ADRs) — NexaAI

This document records the key architectural choices, rationales, and trade-offs made during the design and implementation of NexaAI.

---

### ADR-001: Independent Microservices Without Aggregator POM
- **Status**: Accepted
- **Context**: Monorepos frequently fall victim to the "distributed monolith" anti-pattern when an aggregator POM or shared parent is used to build all services together.
- **Decision**: Each service in `backend/` has its own autonomous `pom.xml` inheriting from `spring-boot-starter-parent` from Maven Central. Cross-service dependencies and aggregator POMs are strictly forbidden.
- **Consequences**: Services can be built, tested, and containerized completely independently. Contract DTOs are replicated intentionally.

---

### ADR-002: Apache Kafka with KRaft Mode (Zero ZooKeeper)
- **Status**: Accepted
- **Context**: Historically, Kafka required an external ZooKeeper ensemble for quorum and metadata management, adding operational complexity and potential split-brain issues.
- **Decision**: Kafka 4.1.0 is deployed in KRaft (Kafka Raft) combined broker-controller mode (`KAFKA_PROCESS_ROLES: broker,controller`). ZooKeeper is strictly prohibited.
- **Consequences**: Simplified container orchestration, faster failover, unified metadata storage, reduced memory footprint.

---

### ADR-003: Database per Service with PostgreSQL 17
- **Status**: Accepted
- **Context**: Shared databases violate microservice encapsulation, causing tight schema coupling and concurrency bottlenecks.
- **Decision**: Each service owns its dedicated PostgreSQL database on isolated credentials (`nexa_auth`, `nexa_user`, `nexa_chat`, `nexa_document`, `nexa_rag`, `nexa_subscription`, `nexa_analytics`).
- **Consequences**: No cross-service SQL joins. Data sharing occurs exclusively via REST APIs or Kafka events.

---

### ADR-004: Redis Strictly as Ephemeral Cache and Rate Limiter
- **Status**: Accepted
- **Context**: Using Redis as a primary data store risks data loss during restarts if persistence is misconfigured.
- **Decision**: Redis operates strictly as an ephemeral cache, distributed rate-limiter, and JWT denylist. Data persistence (`save`) is disabled.
- **Consequences**: If Redis fails or restarts, system state is reconstructed from PostgreSQL and Kafka without data loss.

---

### ADR-005: PostgreSQL + pgvector for Semantic Search
- **Status**: Accepted
- **Context**: Dedicated vector databases (Pinecone, Milvus, Weaviate) introduce additional infrastructure dependencies and billing costs.
- **Decision**: `rag-service` uses PostgreSQL 17 with the `pgvector` extension and HNSW indexing for vector embeddings.
- **Consequences**: Operational consistency with the existing PostgreSQL stack, transactional guarantees, zero extra vector database infrastructure to maintain.

---

### ADR-006: Separation of Document Service and RAG Service
- **Status**: Accepted
- **Context**: Combining file upload management and vector embedding generation couples I/O-heavy file operations with compute-heavy ML chunking and embedding.
- **Decision**: `document-service` manages uploads, metadata, and raw text extraction. `rag-service` manages chunking, embedding generation, and vector retrieval. They communicate asynchronously via Kafka topics.
- **Consequences**: Scalability isolation: `rag-service` can be scaled independently during heavy embedding workloads without affecting upload latency.

---

### ADR-007: Stateless AI Service with Spring AI Multi-Model Routing
- **Status**: Accepted
- **Context**: AI provider APIs change frequently and require different client libraries.
- **Decision**: `ai-service` encapsulates all LLM provider interactions (OpenAI, Gemini, Groq, Ollama) via Spring AI 1.0.0-M6. It is completely stateless with zero database connections.
- **Consequences**: Any LLM provider update or fallback logic is isolated within `ai-service`.

---

### ADR-008: Ingress Routing via Spring Cloud Gateway & NGINX Edge
- **Status**: Accepted
- **Context**: Exposing backend microservices directly to the internet creates severe security risks and CORS complexity.
- **Decision**: A two-tier ingress model: NGINX edge reverse proxy terminates SSL, serves static assets, and handles initial IP rate limiting; Spring Cloud Gateway handles JWT validation, dynamic routing, and correlation ID injection.
- **Consequences**: Defense in depth. Downstream services are invisible to the public internet.

---

### ADR-009: Razorpay Test Mode Only
- **Status**: Accepted
- **Context**: Accidental exposure of live payment credentials or testing with real funds during development must be prevented.
- **Decision**: Subscriptions are integrated with Razorpay in Test Mode (`rzp_test_*`). Live keys (`rzp_live_*`) are blocked by automated CI rules.
- **Consequences**: Secure and predictable sandbox testing of payment verification, subscription activation, and webhooks.

---

### ADR-010: Frontend Framework — Bootstrap 5 and SCSS Only (No Tailwind)
- **Status**: Accepted
- **Context**: The project specification requires a clean, accessible, consistent UI design system using Bootstrap 5 and SCSS variables.
- **Decision**: React 18 + TypeScript + Bootstrap 5 + SCSS. Tailwind CSS is prohibited by automated linting and CI checks.
- **Consequences**: Predictable styling via custom SCSS tokens; no class bloat; rapid responsive prototyping.

---

### ADR-011: Asymmetric RS256 JWT Authentication
- **Status**: Accepted
- **Context**: Symmetric HS256 JWT requires sharing the secret key across all microservices, increasing the blast radius of a credential leak.
- **Decision**: `auth-service` signs tokens using an RSA private key. The API Gateway and downstream services verify tokens using only the RSA public key.
- **Consequences**: High cryptographic security; downstream services cannot forge access tokens.

---

### ADR-012: In-Service DTO Duplication over Shared Library JARs
- **Status**: Accepted
- **Context**: A common `nexa-common.jar` library creates tight compile-time coupling and version lock-in across microservices.
- **Decision**: Shared contracts are documented in `docs/SERVICE_CONTRACTS.md` and their DTO classes are intentionally duplicated in each service.
- **Consequences**: Complete microservice independence. Changes to one service do not force recompilation of others.

---

### ADR-013: Server-Sent Events (SSE) for Real-Time Streaming
- **Status**: Accepted
- **Context**: Token-by-token LLM output requires streaming. WebSockets introduce stateful connection management and proxy overhead.
- **Decision**: Chat streaming uses HTTP Server-Sent Events (`text/event-stream`). Proxy buffering is disabled at NGINX and Gateway.
- **Consequences**: Standard HTTP compatibility, automatic browser reconnection, minimal server overhead.

---

### ADR-014: Visual Workflow Builder DAG Engine
- **Status**: Accepted
- **Context**: Users need multi-step automation without introducing heavyweight BPMN engines (like Camunda) that bloat the footprint.
- **Decision**: A custom lightweight DAG workflow executor in `chat-service` executing trigger -> action -> action -> result nodes with JSON state persistence.
- **Consequences**: Fast, understandable execution flow tailored to AI workflows.

---

### ADR-015: Spring AI Tool Calling with Server-Side Safety Boundaries
- **Status**: Accepted
- **Context**: AI agents require external capabilities (math, document search, weather) without gaining dangerous unrestricted system access.
- **Decision**: Spring AI function calling with explicit registered tool definitions. Tool invocations run through server-side permission checks.
- **Consequences**: Autonomous agentic reasoning with strict tenant isolation and no raw SQL or shell execution risk.

---

### ADR-016: Phase-Aware CI/CD Rule Verification
- **Status**: Accepted
- **Context**: Monolithic CI pipelines fail when early phases do not yet have all services implemented.
- **Decision**: GitHub Actions matrix discovering implemented services dynamically and running dedicated architecture rule checks (`rules.yml`).
- **Consequences**: Honest build reporting across all development stages.
