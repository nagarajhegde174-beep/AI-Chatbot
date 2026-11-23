# Test Plan & End-to-End Verification — NexaAI

## 1. Test Strategy Overview

NexaAI employs a defense-in-depth testing strategy across all architectural layers:

```
          +-----------------------------------------+
          |         End-to-End Test Suite           |
          |       (35 Integration Scenarios)        |
          +--------------------+--------------------+
                               |
          +--------------------+--------------------+
          |       Service Integration Tests         |
          |  (@SpringBootTest, Testcontainers, Mock) |
          +--------------------+--------------------+
                               |
          +--------------------+--------------------+
          |        Controller / Slice Tests         |
          |    (@WebMvcTest, @DataJpaTest, MockMvc)  |
          +--------------------+--------------------+
                               |
          +--------------------+--------------------+
          |           Unit Test Suites              |
          |     (JUnit 5, Mockito, AssertJ, Vitest) |
          +-----------------------------------------+
```

---

## 2. The 35 End-to-End Verification Scenarios

| # | Scenario | Expected Behavior & Verification Criteria |
|:---:|---|---|
| **1** | Registration | User submits email/password. User created with status `PENDING_VERIFICATION`, password hashed with bcrypt, verification event published. |
| **2** | Email Verification | Verification token link validated. User status updated to `ACTIVE`. Expired or altered tokens return 400 Bad Request. |
| **3** | Login | Valid credentials return RS256 JWT access token (15m expiration) and HTTP-only refresh token (7d expiration). |
| **4** | Google Login | OAuth2 authorization code exchanged for token. Existing user linked or new user provisioned with `USER` role. |
| **5** | Logout | Refresh token invalidated; access token JTI added to Redis denylist with TTL matching token expiry. |
| **6** | Forgot Password | User enters email; system validates existence and sends 6-digit OTP with 10-minute expiration. |
| **7** | OTP Verification | 6-digit OTP verified against hashed Redis/DB record. Failed attempts throttled (max 3 tries). |
| **8** | Reset Password | Temporary reset token allows updating password. All active sessions invalidated; bcrypt hash updated. |
| **9** | Chat Interaction | User submits message to `chat-service`. Message saved to `nexa_chat`; forwarded to `ai-service` for inference. |
| **10** | Multiple Conversations | User creates, lists, renames, and switches between isolated conversation sessions without cross-contamination. |
| **11** | Streaming (SSE) | Tokens stream in real time via Server-Sent Events (`text/event-stream`). Proxy buffering disabled at NGINX and Gateway. |
| **12** | Model Selection | User switches model (`gpt-4o`, `gemini-1.5-pro`, `llama-3.3-70b`). AI service delegates to appropriate Spring AI provider. |
| **13** | Model Fallback | When primary model fails (HTTP 429/500/timeout), `ai-service` automatically falls back to secondary configured model without dropping chat. |
| **14** | Context Memory | Recent conversation messages assembled into token window. Semantic summarization invoked when context exceeds window. |
| **15** | Document Upload | User uploads PDF/TXT/DOCX up to 25MB. File validated and saved to `nexa_document`. |
| **16** | Text Extraction | Document parsed; plain text extracted and cleaned. Processing status transitions to `EXTRACTED`. |
| **17** | Kafka Document Event | `document-service` emits `document.uploaded.v1` event to Kafka topic `document.events`. |
| **18** | RAG Chunking | `rag-service` consumes document event, performs sliding-window token chunking (e.g., 512 tokens with 64-token overlap). |
| **19** | Embeddings | Chunks sent to embedding model (`text-embedding-3-small` / local) to generate dense vector representations. |
| **20** | pgvector Retrieval | Embeddings stored in `nexa_rag` with HNSW index. Similarity search queries vectors using cosine distance (`<=>`). |
| **21** | Citations | Grounded answers include source metadata: document title, page number, chunk ID, and similarity score. |
| **22** | Subscription Catalog | User queries available tiers (`Free`, `Pro`, `Enterprise`) with token limits and price breakdown. |
| **23** | Razorpay Test Mode | Checkout creates Razorpay order with `rzp_test_*` key. Client opens Razorpay Test modal. |
| **24** | Server-Side Verification | Razorpay signature (`order_id|payment_id`) verified using HMAC-SHA256 before subscription activation. |
| **25** | Webhook Processing | Razorpay webhook (`payment.captured`, `subscription.charged`) received, validated, and processed asynchronously. |
| **26** | Idempotency | Duplicate webhook payloads rejected via Redis idempotency key lock and PostgreSQL unique payment reference constraint. |
| **27** | Refund Flow | Admin or user cancellation updates subscription status to `CANCELLED` and logs audit entry. |
| **28** | Usage Limits | Free tier users capped at quota (e.g., 10 messages/day). Subsequent requests return 429 Too Many Requests with upgrade prompt. |
| **29** | User Dashboard | Authenticated user views active plan, current quota usage, document list, and recent workflow runs. |
| **30** | Admin Dashboard | Admin accesses cross-system telemetry: active user count, system token consumption, model error rates, and RAG status. |
| **31** | Admin RBAC | Regular `USER` accessing `/api/v1/admin/**` receives 403 Forbidden. `ADMIN` receives 200 OK. |
| **32** | Redis Rate Limiting | Rate limiter throttles excessive requests (e.g., >10 auth reqs/min) with `429 Too Many Requests` and `Retry-After` header. |
| **33** | Kafka Events Integrity | All Kafka events conform to standard JSON envelope with `eventId`, `eventType`, `timestamp`, `correlationId`, and `payload`. |
| **34** | Workflow Execution | Visual workflow DAG runs sequentially: trigger -> document input -> summarization -> result. Logs and execution status recorded. |
| **35** | Docker Environment | Entire ecosystem boots via `docker compose up -d` with zero errors, passing healthchecks across all containers. |

---

## 3. Automated Test Execution Commands

### 3.1 Backend Service Tests
Each service executes independently with zero sibling dependencies:

```bash
# Run tests for individual services
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

### 3.2 Frontend Tests & Build Validation
```bash
cd frontend/nexa-ai-web
npm run lint
npm run build
```

### 3.3 Strict Testing Rules
- No `@Disabled` or `@Ignore` annotations in test suites (enforced by CI).
- Every implemented service must contain unit and controller tests.
- All tests must pass before merging to `main`.
