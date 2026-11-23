# System Architecture — NexaAI

## 1. High-Level Architecture Overview

NexaAI is architected as an event-driven, decoupled microservices ecosystem. It explicitly avoids distributed monolith anti-patterns: each backend service is an autonomous Spring Boot 3 application with its own repository directory, its own `pom.xml`, its own independent lifecycle, and its own dedicated relational database.

```
                      +-----------------------------+
                      |   Browser / Mobile Client   |
                      +--------------+--------------+
                                     |
                                     v
                 +-------------------+-------------------+
                 |    NGINX Edge Reverse Proxy (8089)    |
                 |     (SSL Termination, Rate Limit,     |
                 |      Static SPA Assets, SSE Pass)     |
                 +-------------------+-------------------+
                                     |
                                     v
                 +-------------------+-------------------+
                 |     Spring Cloud API Gateway (8080)   |
                 |      (JWT Verification, Routing,      |
                 |       CORS, Request Correlation)      |
                 +-------------------+-------------------+
                                     |
       +------------+------------+---+--------+------------+------------+
       |            |            |            |            |            |
       v            v            v            v            v            v
+------------+ +----------+ +----------+ +----------+ +----------+ +----------+
|auth-service| |user-svc  | |chat-svc  | |doc-svc   | |sub-svc   | |analytics |
|   (8081)   | |  (8082)  | |  (8083)  | |  (8085)  | |  (8087)  | |  (8088)  |
+-----+------+ +----+-----+ +----+-----+ +----+-----+ +----+-----+ +----+-----+
      |             |            |            |            |            |
      v             v            v            v            v            v
 (nexa_auth)   (nexa_user)  (nexa_chat)  (nexa_doc)   (nexa_sub)   (nexa_ana)
      |             |            |            |            |            |
      +-------------+------------+------+-----+------------+------------+
                                        |
                 +----------------------+----------------------+
                 |          Apache Kafka (KRaft Mode)          |
                 |    auth.events, chat.events, doc.events,    |
                 |    rag.events, usage.events                 |
                 +-----------+----------------------+----------+
                             |                      |
                             v                      v
                      +--------------+       +--------------+
                      |  rag-service |       |  ai-service  |
                      |    (8086)    |       |    (8084)    |
                      +------+-------+       +------+-------+
                             |                      |
                             v                      v
                         (nexa_rag)            (Stateless)
                         [pgvector]          [Spring AI Tools]
                                             OpenAI/Gemini/Groq
```

---

## 2. Microservice Boundaries & Port Map

| Service | Port | Database | Primary Responsibility |
|---|:---:|---|---|
| **`edge` (NGINX)** | 8089 | *none* | Edge reverse proxy, SSL termination, static SPA host, rate-limiting buffer bypass. |
| **`api-gateway`** | 8080 | *none* | Central ingress routing, JWT token signature validation, correlation ID injection. |
| **`auth-service`** | 8081 | `nexa_auth` | User authentication, registration, password hashing (bcrypt), token issuance (RS256 JWT). |
| **`user-service`** | 8082 | `nexa_user` | User profiles, account settings, role permissions (`USER`, `ADMIN`), admin user management. |
| **`chat-service`** | 8083 | `nexa_chat` | Conversation lifecycle, message persistence, Agentic Workflow Builder execution engine. |
| **`ai-service`** | 8084 | *none (stateless)* | Spring AI provider abstractions (OpenAI, Gemini, Groq, Ollama), tool execution, model fallback. |
| **`document-service`** | 8085 | `nexa_document` | Document uploads (PDF/TXT/DOCX), metadata storage, raw text extraction, Kafka publishing. |
| **`rag-service`** | 8086 | `nexa_rag` | Document chunking, vector embedding generation, `pgvector` HNSW index similarity search. |
| **`subscription-service`** | 8087 | `nexa_subscription` | Plan catalog, Razorpay Test Mode billing, payment webhook verification, subscription status. |
| **`analytics-service`** | 8088 | `nexa_analytics` | Token usage telemetry, API call aggregation, administrative reporting dashboards. |
| **`nexa-ai-web`** | 5173 | *none* | React 18, TypeScript, Vite, Bootstrap 5 + SCSS Single Page Application. |

---

## 3. Communication Patterns

### 3.1 Synchronous Communication (REST / HTTP)
- **Client to Backend**: Client communicates exclusively with `NGINX` (port 8089 in production, 8080 directly via Gateway in local development). All API endpoints reside under `/api/v1/**`.
- **Inter-Service REST Calls**:
  - `chat-service` calls `ai-service:8084/api/v1/models/generate` or `/api/v1/models/stream` for LLM inference.
  - `chat-service` calls `rag-service:8086/api/v1/rag/retrieve` for grounded context retrieval during document chat.
  - Synchronous timeouts are strictly enforced (connect: 5s, read: 30s-600s for streaming).

### 3.2 Asynchronous Communication (Apache Kafka with KRaft)
- **Zero ZooKeeper Architecture**: Kafka runs version 4.1.0 using the **Kafka Raft (KRaft)** metadata mode (`KAFKA_PROCESS_ROLES: broker,controller`). ZooKeeper is completely eliminated.
- **Event Decoupling**:
  - `document.events`: Emitted by `document-service` upon file upload; consumed by `rag-service` to trigger asynchronous chunking and vector embedding.
  - `rag.events`: Emitted by `rag-service` upon indexing completion or failure; consumed by `document-service` to update document processing status.
  - `chat.events`: Emitted by `chat-service` on message exchange; consumed by `analytics-service` for token telemetry.
  - `usage.events`: Emitted across services to track quota consumption per user.

### 3.3 Ephemeral Caching & Rate Limiting (Redis)
- Redis 7-Alpine operates strictly as an ephemeral cache and operational coordinator.
- **Persistence Off**: Data persistence is disabled (`save ""` in redis.conf); Redis is NEVER used as a primary system of record.
- **Use Cases**:
  - JWT denylist (revoked tokens with TTL matching token expiration).
  - Distributed IP and per-user token bucket rate limiting.
  - Payment webhook idempotency locks.

---

## 4. Database Ownership & Isolation
- Each service owns a distinct PostgreSQL database on a dedicated user role.
- **Strict Prohibition**: Cross-service database queries, shared connection pools, or direct table joins across service boundaries are strictly prohibited.
- **pgvector Exclusivity**: The `pgvector` extension is enabled and loaded **ONLY** on the `nexa_rag` database. No other service interacts with vector data structures.

---

## 5. Agentic AI & Tool Calling Architecture
- The `ai-service` integrates Spring AI Function Calling, exposing registered tools:
  - `calculator`: Deterministic math evaluation.
  - `documentSearch`: Retrieval tool querying `rag-service` for authenticated tenant vector data.
  - `databaseSearch`: Controlled query execution against sanitized read-only tenant views.
  - `weather`: Third-party meteorological queries.
  - `fileAnalyzer`: Document schema inspection.
- **Agent Loop**: Iterative prompt evaluation up to a maximum of 5 loops, preventing runaway token consumption. Destructive actions require client confirmation approval.

---

## 6. Frontend Architecture
- Built on **React 18 + TypeScript + Vite**.
- **UI Framework**: Strictly **Bootstrap 5 + SCSS**. Tailwind CSS is strictly banned across the entire codebase.
- **State Management & Routing**: React Context API, React Router DOM v6, Axios interceptors for automatic JWT refresh on 401 Unauthorized.
