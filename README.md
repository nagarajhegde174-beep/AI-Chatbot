# NexaAI — Multi-Model AI Chat & Agentic Workflow Platform

[![Backend CI](https://github.com/nexaai/nexaai/actions/workflows/backend.yml/badge.svg)](https://github.com/nexaai/nexaai/actions/workflows/backend.yml)
[![Frontend CI](https://github.com/nexaai/nexaai/actions/workflows/frontend.yml/badge.svg)](https://github.com/nexaai/nexaai/actions/workflows/frontend.yml)
[![Architecture Rules](https://github.com/nexaai/nexaai/actions/workflows/rules.yml/badge.svg)](https://github.com/nexaai/nexaai/actions/workflows/rules.yml)

**NexaAI** is an enterprise-grade Software-as-a-Service (SaaS) platform providing unified access to multiple Large Language Model (LLM) providers, Retrieval-Augmented Generation (RAG) over uploaded user documents, an interactive visual Workflow Builder, autonomous Agentic AI function calling, and subscription billing.

The backend is built as **eight independent Spring Boot 3 microservices** and a central **Spring Cloud API Gateway**. It strictly adheres to pure microservices principles: independent builds, dedicated database ownership, asynchronous event decoupling via Apache Kafka in KRaft mode, and zero monolithic coupling.

---

## Architecture Overview

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

## Microservices Breakdown

| Service | Port | Database | Technology | Responsibility |
|---|:---:|---|---|---|
| **`edge` (NGINX)** | 8089 | *none* | NGINX 1.27 Alpine | Public entry point, SSL termination, static SPA hosting, edge rate-limiting, SSE unbuffered streaming. |
| **`api-gateway`** | 8080 | *none* | Spring Cloud Gateway | Ingress proxy, RS256 JWT public key validation, request correlation, dynamic downstream routing. |
| **`auth-service`** | 8081 | `nexa_auth` | Spring Boot 3, Spring Security 6 | Registration, email verification, password reset, login, RS256 JWT issuance, bcrypt hashing. |
| **`user-service`** | 8082 | `nexa_user` | Spring Boot 3, Spring Data JPA | User profile management, password updates, admin RBAC, user status management. |
| **`chat-service`** | 8083 | `nexa_chat` | Spring Boot 3, Spring Data JPA | Conversations, message history, context memory, visual Workflow Builder execution engine. |
| **`ai-service`** | 8084 | *none* | Spring Boot 3, Spring AI, WebFlux | Stateless multi-model LLM routing (OpenAI, Gemini, Groq, Ollama), model fallback, tool calling. |
| **`document-service`** | 8085 | `nexa_document` | Spring Boot 3, Apache Tika | Document upload (PDF, TXT, DOCX), 25MB ceiling, text extraction, Kafka publishing. |
| **`rag-service`** | 8086 | `nexa_rag` | Spring Boot 3, PostgreSQL `pgvector` | Recursive chunking, embedding generation, HNSW cosine similarity search, citation retrieval. |
| **`subscription-service`** | 8087 | `nexa_subscription` | Spring Boot 3, Razorpay Java SDK | Subscription tiers, Razorpay Test Mode checkout, HMAC-SHA256 webhook processing, idempotency. |
| **`analytics-service`** | 8088 | `nexa_analytics` | Spring Boot 3, Spring Kafka | Token usage aggregation, API telemetry, administrative metrics dashboard. |
| **`nexa-ai-web`** | 5173 / 80 | *none* | React 18, TypeScript, Bootstrap 5 | Single Page Application (SPA), Dark/Light/System theme, real-time chat, visual workflow canvas. |

---

## Technology Stack

- **Backend Framework**: Spring Boot 3.4.x, Java 21 LTS
- **AI Orchestration**: Spring AI 1.0.0-M6 (Multi-provider abstraction, Tool/Function Calling)
- **API Gateway**: Spring Cloud Gateway 2023.x (WebFlux Reactive)
- **Message Broker**: Apache Kafka 4.1.0 in **KRaft Mode** (Zero ZooKeeper)
- **Primary Database**: PostgreSQL 17 with `pgvector` extension
- **Cache & Rate Limiting**: Redis 7-Alpine (strictly ephemeral; never primary DB)
- **Database Migrations**: Flyway
- **Frontend Stack**: React 18, TypeScript, Vite, Bootstrap 5 + SCSS (**Tailwind strictly prohibited**)
- **Reverse Proxy**: NGINX 1.27 Alpine
- **Containerization**: Multi-stage Docker builds, Docker Compose v2
- **CI/CD**: GitHub Actions

---

## Inter-Service Communication

1. **Synchronous (REST / HTTP)**:
   - Used for immediate client-facing queries and low-latency internal orchestration (e.g., `chat-service` calls `ai-service` for LLM inference; `chat-service` calls `rag-service` for vector retrieval).
2. **Asynchronous (Apache Kafka with KRaft)**:
   - Zero ZooKeeper architecture using Kafka's built-in Raft consensus.
   - Decoupled event streams:
     - `auth.events`: User registered, password reset requested.
     - `document.events`: Document uploaded, raw text extracted.
     - `rag.events`: Document indexed, embedding ready.
     - `chat.events`: Message exchanged.
     - `usage.events`: Token consumed, quota incremented.
3. **Database Isolation**:
   - Each microservice exclusively owns its schema and PostgreSQL user.
   - Cross-service database queries or foreign schema joins are strictly forbidden.

---

## RAG Pipeline (Retrieval-Augmented Generation)

```
User Uploads File (PDF/TXT/DOCX)
       │
       ▼
[document-service] ──▶ Extracts Text ──▶ Emits `document.uploaded.v1`
                                                 │ (Kafka)
                                                 ▼
                                          [rag-service]
                                                 │
                                                 ├─▶ Recursive Semantic Chunking
                                                 ├─▶ Generates Vector Embeddings
                                                 └─▶ Stores in PostgreSQL (nexa_rag)
                                                     via `pgvector` HNSW index
                                                         │
User Queries Chat ───────────────────────────────────────┤
       │                                                 │
       ▼                                                 ▼
[chat-service] ──▶ Queries Context ──▶ Cosine Similarity Retrieval Top-K
       │                                                 │
       ▼                                                 ▼
[ai-service] ◀── Grounded Context + Prompt ──────────────┘
       │
       ▼
Streams Response with Citations (Doc title, page number, confidence)
```

---

## Spring AI Multi-Model Routing & Tool Calling

The stateless `ai-service` encapsulates Spring AI to communicate with:
- **OpenAI**: `gpt-4o`, `gpt-4o-mini`
- **Google Gemini**: `gemini-1.5-pro`, `gemini-1.5-flash`
- **Groq**: `llama-3.3-70b-versatile`
- **Ollama**: Local models (`llama3`)

### Agentic AI Tools
Spring AI Tool Calling enables autonomous reasoning loops (max 5 iterations):
- `calculator`: Deterministic mathematical and financial evaluations.
- `documentSearch`: Semantic vector queries against user's permitted documents.
- `databaseSearch`: Controlled, parameterized read-only queries against tenant views.
- `weather`: Meteorological data lookup.
- `fileAnalyzer`: File structure and metadata extraction.

---

## Subscription & Payment Architecture (Razorpay Test Mode)

- **Test Mode Pinned**: Real financial transactions are strictly blocked. All credentials use `rzp_test_*`.
- **Server-Side Verification**: Orders verified via cryptographic HMAC-SHA256 signature matching.
- **Webhook Idempotency**: Redis locks and database unique constraints prevent duplicate processing of payment webhooks.

---

## How to Run Locally

### Prerequisites
- Docker & Docker Compose v2+
- Java 21 LTS & Maven 3.9+
- Node.js 20+ & npm

### 1. Configure Environment
Copy the example environment template:
```bash
cp .env.example .env
```
*(Leave default local values or provide your own local test API keys if testing live LLM providers).*

### 2. Start Full Stack via Docker Compose
To boot all 9 microservices, PostgreSQL, Redis, Kafka (KRaft), NGINX, and Frontend:
```bash
docker compose -f infrastructure/docker-compose.yml up -d
```

### 3. Start Infrastructure Only (for local service debugging)
```bash
docker compose -f infrastructure/docker-compose.yml --profile infra up -d
```

### 4. Run a Microservice Locally in Debug Mode
Each service compiles independently from its own directory:
```bash
cd backend/auth-service
mvn spring-boot:run
```

### 5. Access Endpoints
- **Web Frontend**: [http://localhost:8089](http://localhost:8089) (via NGINX Edge) or [http://localhost:5173](http://localhost:5173) (Vite Dev)
- **API Gateway**: [http://localhost:8080](http://localhost:8080)
- **Auth Service**: [http://localhost:8081](http://localhost:8081)
- **Swagger UI**: Available per service at `http://localhost:<PORT>/swagger-ui.html`

---

## Testing Guide

### Run Backend Unit & Integration Tests
```bash
# Test individual microservices independently
mvn -f backend/auth-service/pom.xml test
mvn -f backend/user-service/pom.xml test
mvn -f backend/api-gateway/pom.xml test
mvn -f backend/ai-service/pom.xml test
mvn -f backend/chat-service/pom.xml test
mvn -f backend/document-service/pom.xml test
mvn -f backend/rag-service/pom.xml test
mvn -f backend/subscription-service/pom.xml test
mvn -f backend/analytics-service/pom.xml test
```

### Run Frontend Lint & Build
```bash
cd frontend/nexa-ai-web
npm run lint
npm run build
```

---

## CI / CD Pipelines (GitHub Actions)

- **`.github/workflows/backend.yml`**: Discovers all implemented microservices, builds effective POMs, runs unit/slice/integration tests in an isolated matrix, verifies zero `@Disabled` tests, and archives JAR artifacts.
- **`.github/workflows/frontend.yml`**: Type-checks TypeScript, lints code, validates production SCSS compilation, verifies total absence of Tailwind CSS, and checks for secret leaks.
- **`.github/workflows/rules.yml`**: Enforces architectural rules (no aggregator POM, no sibling dependencies, KRaft mode, database ownership, exactly two roles: USER and ADMIN, Razorpay Test Mode only).

---

## 🎓 Fresher-Friendly Interview Explanation

> **"How would you explain the NexaAI architecture in a software engineering interview?"**

Here is a simple, structured 2-minute explanation you can speak directly in an interview:

### 1. The High-Level Pitch
*"NexaAI is a cloud-native, multi-model AI platform designed like ChatGPT Plus with custom workflows. Instead of locking users into one AI model like OpenAI, NexaAI lets users chat with GPT-4, Google Gemini, or Groq, upload PDFs to ask questions with exact citations (RAG), and create automated visual workflows (like 'Summarize this document and email the result')."*

### 2. Microservices & Database Ownership
*"To ensure high availability and scalability, we built it as 8 independent Spring Boot microservices behind a Spring Cloud API Gateway and an NGINX reverse proxy. 
A key principle we followed is **Database-per-Service**: Auth Service owns `nexa_auth`, Chat Service owns `nexa_chat`, and Document Service owns `nexa_document`. No service ever connects to another service's database directly. If Chat needs user details, it calls the User API over REST."*

### 3. Kafka & Asynchronous Processing
*"For heavy tasks like document processing, we use **Apache Kafka in modern KRaft mode** without ZooKeeper. When a user uploads a PDF, `document-service` saves the raw file and emits a `document.uploaded` event to Kafka. The `rag-service` consumes that event in the background, breaks the text into chunks, generates vector embeddings, and stores them in PostgreSQL using the `pgvector` extension. This ensures file uploads return immediately to the user without hanging the web server."*

### 4. Real-Time Streaming & AI Resilience
*"For chat responses, we use **Server-Sent Events (SSE)** so tokens stream onto the user's screen in real time with zero buffering. In the `ai-service`, we implemented **model fallback**: if OpenAI returns a rate-limit error (HTTP 429), the service automatically retries the prompt with Google Gemini without the user ever noticing a failure."*

### 5. Frontend & Security
*"The frontend is built with **React, TypeScript, and Bootstrap 5 with SCSS**. We strictly banned Tailwind to keep our design tokens and CSS bundle clean. For security, we use **asymmetric RS256 JWT tokens** where Auth Service signs tokens with a private key, and the Gateway verifies them with a public key. Payment flows use **Razorpay strictly in Test Mode** with cryptographic HMAC webhook validation."*
