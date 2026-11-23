# Product Requirements Document (PRD) — NexaAI

## 1. Executive Summary
**NexaAI** is an enterprise-grade, multi-model AI Chat Software-as-a-Service (SaaS) platform featuring an advanced visual Workflow Builder, Agentic AI function calling, Retrieval-Augmented Generation (RAG) over user documents, and multi-tier subscription billing.

The platform provides a unified interface to leading Large Language Model (LLM) providers (OpenAI, Google Gemini, Groq, local Ollama) with seamless fallback capabilities, citation-backed document question-answering, and autonomous tool-augmented AI agents.

---

## 2. Target Audience & Personas
1. **Individual Professionals & Developers (Free / Pro Tier)**:
   - Need access to multiple top-tier AI models within a single interface.
   - Require persistent chat histories, code generation, and fast streaming responses.
2. **Knowledge Workers & Researchers (Pro Tier)**:
   - Need to upload large PDFs and text documents and chat with them using accurate citations.
   - Require autonomous workflows (e.g., trigger -> parse PDF -> summarize -> notify).
3. **Enterprise Teams & Administrators (Admin Role)**:
   - Require administrative oversight over user registrations, token usage, model telemetry, and RAG document ingestion.
   - Need strict role-based access control (RBAC), tenant data isolation, and comprehensive audit trails.

---

## 3. Core Functional Requirements

### 3.1 Authentication & User Management
- **Registration & Verification**: Email/password registration with tokenized email verification and password reset with 6-digit OTPs.
- **Authentication**: Stateless RS256 JWT access tokens paired with secure HTTP-only refresh tokens.
- **OAuth2 / Social Login**: Google OAuth2 integration with account linking.
- **Role-Based Access Control (RBAC)**: Exactly two system roles: `USER` and `ADMIN`.
- **Session Management**: Instant token revocation via Redis token blacklist upon logout.

### 3.2 AI Chat & Model Orchestration
- **Multi-Model Support**: Direct routing between OpenAI (`gpt-4o`, `gpt-4o-mini`), Google Gemini (`gemini-1.5-pro`, `gemini-1.5-flash`), Groq (`llama-3.3-70b-versatile`), and local Ollama (`llama3`).
- **Resilient Fallback**: Automatic failover to secondary configured models when a provider encounters rate limits or upstream timeouts.
- **Low-Latency Streaming**: Real-time token streaming using Server-Sent Events (SSE) through NGINX and API Gateway with zero proxy buffering.
- **Conversation Memory**: Context window management with token sliding windows and semantic summarization.

### 3.3 Retrieval-Augmented Generation (RAG)
- **Document Ingestion**: Multi-format document parser supporting PDF, TXT, MD, and DOCX up to 25MB.
- **Asynchronous Pipeline**: Event-driven ingestion using Apache Kafka (`document.events` -> `rag.events`).
- **Semantic Chunking**: Configurable token chunking with overlap (e.g., 512 tokens with 64-token overlap).
- **Vector Storage**: High-performance semantic vector search powered by PostgreSQL `pgvector` with HNSW indexing and cosine similarity.
- **Citations & Provenance**: Grounded generation providing exact document names, chunk IDs, and relevance confidence scores.

### 3.4 Agentic AI & Tool Calling
- **Spring AI Tool Calling**: Autonomous multi-turn reasoning with controlled function execution.
- **Permitted Tools**:
  - `CalculatorTool`: Mathematical and financial calculation engine.
  - `DocumentSearchTool`: Controlled semantic search across authenticated user vector stores.
  - `DatabaseQueryTool`: Read-only, parameterized queries against permitted tenant views (no raw SQL execution).
  - `WeatherTool`: Live meteorological data retrieval.
  - `FileAnalyzerTool`: Metadata and content structure inspection.
- **Execution Safeguards**: Server-side tool authorization checks, loop execution limits (max 5 iterations), timeout circuit breakers, and human confirmation approval for destructive operations.

### 3.5 Visual Workflow Builder
- **DAG Workflow Execution**: Visual directed acyclic graph (DAG) builder consisting of Trigger -> Action -> Action -> Result nodes.
- **Node Catalog**:
  - *Trigger Nodes*: Manual trigger, Webhook trigger, Scheduled cron trigger.
  - *Processing Nodes*: Document Input, Text Extraction, Prompt Execution, Document Summarization, RAG Retrieval, Conditional Branching.
  - *Output Nodes*: Final Response, Email/Webhook Notification.
- **State Tracking**: Real-time execution logs, step-by-step audit trails, retry policies, and execution error reporting.

### 3.6 Subscriptions & Payments (Razorpay Test Mode)
- **Tier Structure**: Free ($0/mo, 10 chats/day), Pro ($19/mo, unlimited chats, 50 docs), Enterprise ($99/mo, custom limits).
- **Razorpay Integration**: Pinned strictly to **Razorpay Test Mode** (`rzp_test_*`). Live payment transactions are strictly prohibited.
- **Webhook Verification**: Cryptographic HMAC-SHA256 signature verification for order completion, payment captures, and renewals.
- **Idempotency**: Strict deduplication of payment webhooks via persistent idempotency keys in Redis and PostgreSQL.

---

## 4. Non-Functional Requirements
- **Latency**: TTFT (Time to First Token) < 800ms for chat streaming; vector similarity search < 50ms for top-5 chunks.
- **Scalability**: Stateless microservices horizontally scalable behind NGINX edge proxy and Spring Cloud Gateway.
- **Reliability & Fault Tolerance**: Asynchronous event decoupling via Kafka KRaft cluster; Redis cache-aside fallback.
- **Security**: OWASP Top 10 compliance, CSRF protection, AES-256 encrypted credentials at rest, TLS in transit, database per service.
- **Maintainability**: Independent Maven modules, clean hex/layered architecture, comprehensive OpenAPI 3.0 documentation.
