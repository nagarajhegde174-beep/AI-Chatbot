# Implementation Tasks & Phase Roadmap — NexaAI

This document tracks all implementation phases of the NexaAI enterprise platform from initial architecture design to final integration.

---

## Phase Breakdown & Status Summary

| Phase | Title | Scope & Key Deliverables | Status |
|:---:|---|---|:---:|
| **0** | Architecture & Guardrails | Multi-service boundaries, Docker Compose, CI rule checks R1–R14, project documentation. | **COMPLETE** |
| **1** | Auth Service | Registration, email verification, password reset, login, RS256 JWT issuance, bcrypt hashing. | **COMPLETE** |
| **2** | User Service | Profile management, password updates, admin RBAC, user status management. | **COMPLETE** |
| **3** | API Gateway | Spring Cloud Gateway, JWT signature verification, CORS, route mapping, rate limiting. | **COMPLETE** |
| **4** | AI Service | Stateless Spring AI multi-model routing (OpenAI, Gemini, Groq, Ollama), model fallback, SSE streaming. | **COMPLETE** |
| **5** | Chat Service | Conversations, messages, contextual history, model routing delegation, Kafka chat events. | **COMPLETE** |
| **6** | Document Service | Multipart upload (PDF, TXT, DOCX), text extraction, metadata storage, `document.events`. | **COMPLETE** |
| **7** | RAG Service | Semantic chunking, vector embeddings, PostgreSQL `pgvector` HNSW index, retrieval API. | **COMPLETE** |
| **8** | Analytics & Admin | Usage tracking, token consumption aggregation, administrative metrics dashboard. | **COMPLETE** |
| **9** | Subscription Service | Plan tiers (Free, Pro, Enterprise), Razorpay Test Mode checkout, webhook HMAC, idempotency. | **COMPLETE** |
| **10** | Web Frontend | React 18, TypeScript, Bootstrap 5 + SCSS, dark/light theme, chat UI, auth flows. | **COMPLETE** |
| **11** | Workflow Builder & Agents | Visual DAG builder, Spring AI tool calling (calc, doc search, db, weather), agent execution engine. | **COMPLETE** |
| **12** | Final Integration | End-to-end integration, Docker Compose verification, OpenAPI docs, CI/CD validation, final docs. | **COMPLETE** |

---

## Detailed Deliverables by Phase

### Phase 1 — Authentication Service
- [x] Autonomous `auth-service` module with Spring Security 6.
- [x] Database `nexa_auth` with Flyway migrations.
- [x] Secure registration with email token verification.
- [x] Password reset with 6-digit OTP expiration.
- [x] Stateless RS256 JWT access token and refresh token rotation.
- [x] Redis token blacklist on logout.

### Phase 2 — User Service
- [x] Autonomous `user-service` module with `nexa_user` database.
- [x] Synchronous user profile REST endpoints.
- [x] Admin endpoints for user status updates and role queries (`USER`, `ADMIN`).
- [x] Kafka consumer for `auth.events` user sync.

### Phase 3 — API Gateway
- [x] Spring Cloud Gateway ingress on port 8080.
- [x] JWT public key signature verification filter.
- [x] Dynamic downstream routing for all microservices.
- [x] Global CORS configuration and correlation ID injection.

### Phase 4 — AI Service
- [x] Stateless multi-model routing with Spring AI 1.0.0-M6.
- [x] Providers: OpenAI, Google Gemini, Groq, Ollama.
- [x] Automatic fallback chain on provider rate limits or errors.
- [x] WebFlux reactive Server-Sent Events (SSE) streaming.

### Phase 5 — Chat Service
- [x] `nexa_chat` database for conversation and message persistence.
- [x] Sliding window context memory management.
- [x] Integration with `ai-service` for streaming generation.
- [x] Kafka publication of `chat.events`.

### Phase 6 — Document Service
- [x] Multi-format upload handling (PDF, TXT, MD, DOCX) with 25MB ceiling.
- [x] Raw text extraction using Apache Tika / PDFBox.
- [x] File metadata storage in `nexa_document`.
- [x] Kafka publication of `document.events`.

### Phase 7 — RAG Service
- [x] Dedicated `nexa_rag` database with `pgvector` extension.
- [x] Recursive semantic text chunking.
- [x] Vector embedding generation.
- [x] Cosine similarity top-k retrieval with document citation metadata.

### Phase 8 — Analytics Service
- [x] Asynchronous Kafka consumer for `usage.events` and `chat.events`.
- [x] Aggregated token metrics, latency percentiles, and active user stats.
- [x] Administrative reporting endpoints.

### Phase 9 — Subscription Service
- [x] Plan management (Free, Pro, Enterprise).
- [x] Razorpay Test Mode order creation.
- [x] HMAC-SHA256 signature verification for webhooks.
- [x] Idempotent webhook handling with Redis deduplication locks.

### Phase 10 — Frontend Web Application
- [x] React 18 + TypeScript + Bootstrap 5 + SCSS.
- [x] Light, Dark, and System theme synchronization.
- [x] Markdown syntax rendering with code copy controls.
- [x] Document upload, RAG chat citation inspection, responsive layouts.

### Phase 11 — Workflow Builder & Agentic AI
- [x] Spring AI tool calling integration (Calculator, DocumentSearch, DatabaseSearch, Weather, FileAnalyzer).
- [x] Visual Workflow Builder canvas with node configuration and DAG execution engine.
- [x] Execution status tracking, error state handling, and retry logic.
- [x] Server-side tool execution permissions and security checks.

### Phase 12 — Final Integration & Polish
- [x] Independent Dockerfiles for all 9 backend services and frontend.
- [x] Comprehensive Docker Compose stack (PostgreSQL + pgvector, Redis, Kafka KRaft, NGINX, Services).
- [x] OpenAPI / Swagger 3.0 documentation across services.
- [x] Clean `.env.example` template without exposed secrets.
- [x] CI/CD workflows for backend, frontend, and architectural rules.
- [x] Complete documentation suite and root `README.md`.
