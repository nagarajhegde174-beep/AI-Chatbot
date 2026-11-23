# Project Memory & Knowledge Base — NexaAI

## 1. Project Context & Mission
NexaAI is a multi-model AI Chat SaaS platform featuring an Agentic Workflow Builder, RAG document search, and Razorpay Test Mode subscriptions. It was constructed with strict architectural discipline: 8 independent Spring Boot backend microservices + 1 API Gateway, React 18 TypeScript frontend with Bootstrap 5 (no Tailwind), Apache Kafka with KRaft (zero ZooKeeper), and isolated PostgreSQL databases per service.

---

## 2. Infrastructure & Runtime Reference

### 2.1 Service & Port Map
- `edge` (NGINX Reverse Proxy): `8089` (Public entry point)
- `api-gateway`: `8080` (Internal ingress, JWT verification, routing)
- `auth-service`: `8081` (Database: `nexa_auth`)
- `user-service`: `8082` (Database: `nexa_user`)
- `chat-service`: `8083` (Database: `nexa_chat`)
- `ai-service`: `8084` (Stateless; Spring AI multi-model routing)
- `document-service`: `8085` (Database: `nexa_document`)
- `rag-service`: `8086` (Database: `nexa_rag` + `pgvector`)
- `subscription-service`: `8087` (Database: `nexa_subscription`)
- `analytics-service`: `8088` (Database: `nexa_analytics`)
- `frontend` (`nexa-ai-web`): `5173` (Local dev), `80` (Docker container)
- `postgres` (`pgvector/pgvector:pg17`): `5432`
- `redis` (`redis:7-alpine`): `6379`
- `kafka` (`apache/kafka:4.1.0` in KRaft mode): `9092` (Host), `29092` (Docker internal)

---

## 3. Core Architectural Rules (Enforced by CI)
1. **Aggregator POM Prohibited**: Every backend service builds independently. Sibling relativePath parent POMs are banned.
2. **Zero ZooKeeper**: Kafka runs natively in KRaft mode (`broker,controller`). Any ZooKeeper reference fails CI.
3. **No Cross-Service Database Access**: Each service has exclusive ownership over its database. `pgvector` exists only in `nexa_rag`.
4. **Tailwind Prohibited**: The frontend uses Bootstrap 5 + SCSS exclusively.
5. **Secrets Hygiene**: Real credentials must never be committed. `.env` is gitignored; `.env.example` has only empty placeholders.
6. **Strict RBAC**: Exactly two roles: `ROLE_USER` and `ROLE_ADMIN`.
7. **Razorpay Test Mode**: Only `rzp_test_*` credentials; `RAZORPAY_MODE: test`. Live payments are strictly blocked.
8. **Stateless Services**: `ai-service` and `api-gateway` have zero database dependencies.

---

## 4. Phase History
- **Phase 0**: Architecture guardrails, Docker Compose, CI rule checks R1–R14, project documentation.
- **Phase 1**: `auth-service` (RS256 JWT, bcrypt, registration, verification, login, password reset).
- **Phase 2**: `user-service` (User profiles, password update, admin user management).
- **Phase 3**: `api-gateway` (Spring Cloud Gateway, JWT verification filter, route mapping, rate limiting).
- **Phase 4**: `ai-service` (Stateless multi-model routing via Spring AI: OpenAI, Gemini, Groq, Ollama; SSE streaming).
- **Phase 5**: `chat-service` (Conversations, message persistence, context memory, streaming integration).
- **Phase 6**: `document-service` (Multipart upload, 25MB ceiling, text extraction, Kafka `document.events`).
- **Phase 7**: `rag-service` (Semantic chunking, vector embedding generation, `pgvector` HNSW index similarity search).
- **Phase 8**: `analytics-service` (Kafka consumption of usage/chat events, administrative telemetry).
- **Phase 9**: `subscription-service` (Plans, Razorpay Test Mode checkout, HMAC webhook verification, idempotency).
- **Phase 10**: `nexa-ai-web` (React 18 + TS + Bootstrap 5 + SCSS, dark/light theme, chat interface, auth flows).
- **Phase 11**: Workflow Builder & Agentic AI (Visual DAG canvas, Spring AI tool calling, server-side permissions).
- **Phase 12**: Final Integration (Docker Compose multi-service wiring, OpenAPI 3.0 specs, end-to-end verification, complete documentation).
