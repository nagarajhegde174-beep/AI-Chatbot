<div align="center">

# NexaAI

**Enterprise Multi-Model AI Chat, RAG & Agentic Workflow SaaS Platform**

Built with a decoupled **Java 21 + Spring Boot 3** microservices architecture, **Spring AI**, **Apache Kafka (KRaft)**, **PostgreSQL + pgvector**, and **React + TypeScript + Bootstrap 5**.

<p>
  <img src="https://img.shields.io/badge/JAVA-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/SPRING_BOOT-3.4-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot 3.4" />
  <img src="https://img.shields.io/badge/SPRING_SECURITY-6-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white" alt="Spring Security" />
  <img src="https://img.shields.io/badge/SPRING_AI-1.0-6DB33F?style=for-the-badge&logo=spring&logoColor=white" alt="Spring AI" />
  <img src="https://img.shields.io/badge/JWT-0.12.6-000000?style=for-the-badge&logo=jsonwebtokens&logoColor=white" alt="JWT" />
  <img src="https://img.shields.io/badge/TYPESCRIPT-6.0-3178C6?style=for-the-badge&logo=typescript&logoColor=white" alt="TypeScript" />
  <img src="https://img.shields.io/badge/REACT-19-20232A?style=for-the-badge&logo=react&logoColor=61DAFB" alt="React 19" />
  <img src="https://img.shields.io/badge/VITE-8.3-646CFF?style=for-the-badge&logo=vite&logoColor=white" alt="Vite" />
  <img src="https://img.shields.io/badge/BOOTSTRAP-5.3-7952B3?style=for-the-badge&logo=bootstrap&logoColor=white" alt="Bootstrap 5" />
  <img src="https://img.shields.io/badge/POSTGRESQL-17-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL 17" />
  <img src="https://img.shields.io/badge/REDIS-7-DC382D?style=for-the-badge&logo=redis&logoColor=white" alt="Redis 7" />
  <img src="https://img.shields.io/badge/KAFKA-4.1_KRaft-231F20?style=for-the-badge&logo=apachekafka&logoColor=white" alt="Kafka" />
  <img src="https://img.shields.io/badge/DOCKER-COMPOSE-2496ED?style=for-the-badge&logo=docker&logoColor=white" alt="Docker Compose" />
  <img src="https://img.shields.io/badge/NGINX-1.27-009639?style=for-the-badge&logo=nginx&logoColor=white" alt="NGINX" />
  <img src="https://img.shields.io/badge/RAZORPAY-1.4.0-0C2451?style=for-the-badge&logo=razorpay&logoColor=white" alt="Razorpay" />
</p>

</div>

---

## Key Features

- **Multi-Model AI Chat & Streaming**: Real-time SSE token streaming across **OpenAI**, **Google Gemini**, **Groq**, and **Ollama** with automatic provider fallback.
- **Document RAG with Citations**: Upload PDF/TXT/DOCX files; asynchronous Kafka chunking, vector embeddings, and cosine similarity search via **PostgreSQL `pgvector`** with exact page citations.
- **Agentic AI & Visual Workflow Builder**: Drag-and-drop DAG workflow builder (`Trigger → Action → Result`) with Spring AI tool calling (Calculator, Document Search, Controlled DB Lookup, Weather, File Analyzer) and human-in-the-loop approvals.
- **Enterprise Auth & RBAC**: RS256 asymmetric JWT authentication, Google OAuth2 login, email verification, OTP password reset, Redis token revocation, and strict `USER` / `ADMIN` role isolation.
- **Subscriptions & Billing**: Tiered SaaS plans (`Free`, `Pro`, `Enterprise`) powered by **Razorpay (Test Mode)** with HMAC-SHA256 webhook verification and idempotency keys.
- **Admin & Usage Analytics**: Real-time token telemetry, quota enforcement, rate limiting (NGINX + Redis), and system health dashboards.

---

## System Architecture

NexaAI follows a strict **Database-per-Service** microservices pattern with **no shared databases**, **no aggregator POM**, and **Kafka KRaft (zero ZooKeeper)**.

```mermaid
flowchart TB
    Client["🌐 Browser / Client"]

    subgraph Frontend["Frontend & Edge (Port 8089 / 5173)"]
        NGINX["NGINX Edge Proxy<br/>(Serves React SPA + Rate Limit + SSE)"]
        React["React 19 + Vite + TypeScript<br/>Bootstrap 5 + SCSS"]
        NGINX -->|"SPA Routing"| React
    end

    Razorpay["💳 Razorpay Test Mode<br/>(External)"]
    LLM["🤖 OpenAI / Gemini / Groq / Ollama<br/>(External)"]

    subgraph Backend["API Gateway & Microservices (Spring Boot 3 / Java 21)"]
        Gateway["Spring Cloud API Gateway (:8080)<br/>RS256 JWT Auth · CORS · Routing"]
        CoreServices["auth-service (:8081) · user-service (:8082)<br/>chat-service (:8083) · document-service (:8085)<br/>rag-service (:8086) · subscription-service (:8087)<br/>analytics-service (:8088)"]
        AIService["ai-service (:8084)<br/>Stateless · Spring AI · Agentic Tools"]
        Gateway --> CoreServices
        CoreServices -->|"REST / SSE"| AIService
    end

    subgraph Data["Data & Messaging"]
        PG[("PostgreSQL 17 + pgvector<br/>Port: 5432<br/>Isolated DB per Service")]
        Redis[("Redis 7<br/>Port: 6379<br/>Cache · Denylist · Rate Limit")]
        Kafka[("Apache Kafka 4.1 (KRaft)<br/>Port: 9092<br/>Zero ZooKeeper")]
    end

    Client -->|"HTTP :8089"| NGINX
    NGINX -->|"Proxy /api/*"| Gateway
    Razorpay -->|"Webhooks"| Gateway
    AIService -->|"Spring AI Inference"| LLM

    CoreServices -->|"Entities / JPA"| PG
    Gateway <-->|"Rate Limiting / Denylist"| Redis
    CoreServices -->|"Publishes Events"| Kafka
    Kafka -->|"Consumes Events"| CoreServices
```

### Microservices Overview

| Service | Port | Database | Core Responsibility |
|---|:---:|---|---|
| **edge (NGINX)** | `8089` | — | Reverse proxy, static SPA host, IP rate limiting, unbuffered SSE |
| **api-gateway** | `8080` | — | Central routing, RS256 JWT validation, CORS, correlation IDs |
| **auth-service** | `8081` | `nexa_auth` | Registration, login, Google OAuth2, OTP reset, RS256 JWT issuance |
| **user-service** | `8082` | `nexa_user` | User profiles, settings, admin user management |
| **chat-service** | `8083` | `nexa_chat` | Conversations, sliding-window memory, DAG workflow execution |
| **ai-service** | `8084` | *Stateless* | Spring AI multi-model routing, fallback, agentic tool calling |
| **document-service** | `8085` | `nexa_document` | File upload (≤25 MB), Apache Tika extraction, Kafka events |
| **rag-service** | `8086` | `nexa_rag` | Chunking, embeddings, `pgvector` HNSW cosine similarity retrieval |
| **subscription-service** | `8087` | `nexa_subscription` | Plans, Razorpay Test Mode checkout, idempotent webhooks |
| **analytics-service** | `8088` | `nexa_analytics` | Kafka usage consumer, token metrics, admin overview |
| **nexa-ai-web** | `5173` | — | React 19 + TypeScript + Bootstrap 5 + SCSS (Light/Dark/System) |

---

## Project Structure

```text
NexaAI/
├── backend/
│   ├── api-gateway/            # Spring Cloud Gateway (JWT filter, routing)
│   ├── auth-service/           # Authentication, JWT, OAuth2, OTP
│   ├── user-service/           # Profiles & admin user controls
│   ├── chat-service/           # Chat, memory & workflow builder engine
│   ├── ai-service/             # Spring AI LLM router & agentic tools
│   ├── document-service/       # File upload & text extraction
│   ├── rag-service/            # Chunking, embeddings & pgvector search
│   ├── subscription-service/   # Plans & Razorpay Test Mode billing
│   └── analytics-service/      # Usage telemetry & admin metrics
├── frontend/
│   └── nexa-ai-web/            # React 19 + TypeScript + Bootstrap 5 + SCSS
├── infrastructure/
│   ├── docker-compose.yml      # Full stack (Postgres, Redis, Kafka KRaft, NGINX, Apps)
│   └── docker/                 # Init SQL scripts, NGINX & Redis configs
└── .github/workflows/          # CI/CD (Backend matrix, Frontend build, Rule checks)
```

---

## Quick Start

### 1. Run Entire Stack with Docker Compose
```bash
docker compose -f infrastructure/docker-compose.yml up -d
```
- **Web App (NGINX Edge)**: `http://localhost:8089`
- **API Gateway**: `http://localhost:8080`
- **Swagger UI (per service)**: `http://localhost:<PORT>/swagger-ui.html`

### 2. Run Locally (Development)
Start infrastructure only (PostgreSQL + pgvector, Redis, Kafka KRaft):
```bash
docker compose -f infrastructure/docker-compose.yml --profile infra up -d
```
Run any backend service independently:
```bash
cd backend/auth-service && mvn spring-boot:run
```
Run the frontend:
```bash
cd frontend/nexa-ai-web && npm install && npm run dev
```

---

## Testing & CI/CD

```bash
# Test any backend microservice independently
mvn -f backend/auth-service/pom.xml test

# Lint, type-check, and build frontend
cd frontend/nexa-ai-web && npm run lint && npm run build
```

- **Backend CI (`.github/workflows/backend.yml`)**: Matrix build and test across all 9 independent services.
- **Frontend CI (`.github/workflows/frontend.yml`)**: TypeScript check, ESLint, SCSS theme verification, bundle secret scan.
- **Architecture Guardrails (`.github/workflows/rules.yml`)**: Enforces zero ZooKeeper, zero Tailwind, database isolation, RBAC (`USER`/`ADMIN`), and Razorpay Test Mode only.
