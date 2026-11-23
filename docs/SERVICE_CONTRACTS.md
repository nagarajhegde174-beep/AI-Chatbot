# Service Contracts & API Specifications — NexaAI

## 1. Standard Event Envelope (Kafka)

All asynchronous Kafka events conform to a standard, schema-versioned JSON envelope:

```json
{
  "eventId": "3c90c3cc-0d44-4b50-ac6f-8700938f2b1a",
  "eventType": "document.uploaded.v1",
  "timestamp": "2026-11-23T07:15:00Z",
  "correlationId": "corr-f81d4fae-7dec-11d0-a765-00a0c91e6bf6",
  "version": "1.0",
  "producer": "document-service",
  "payload": {}
}
```

---

## 2. Kafka Topics & Event Payloads

### 2.1 Topic: `auth.events`
- **`user.registered.v1`**:
  ```json
  {
    "userId": "usr_998124",
    "email": "user@example.com",
    "role": "USER",
    "verificationToken": "tok_abc123"
  }
  ```
- **`password.reset.requested.v1`**:
  ```json
  {
    "userId": "usr_998124",
    "email": "user@example.com",
    "otpCode": "847291",
    "expiresAt": "2026-11-23T07:25:00Z"
  }
  ```

### 2.2 Topic: `document.events`
- **`document.uploaded.v1`**:
  ```json
  {
    "documentId": "doc_44812",
    "userId": "usr_998124",
    "filename": "annual_financial_report.pdf",
    "mimeType": "application/pdf",
    "fileSize": 1420580,
    "rawText": "Extracted text content..."
  }
  ```
- **`document.deleted.v1`**:
  ```json
  {
    "documentId": "doc_44812",
    "userId": "usr_998124"
  }
  ```

### 2.3 Topic: `rag.events`
- **`document.indexed.v1`**:
  ```json
  {
    "documentId": "doc_44812",
    "userId": "usr_998124",
    "totalChunks": 24,
    "vectorDimension": 1536,
    "status": "READY"
  }
  ```
- **`indexing.failed.v1`**:
  ```json
  {
    "documentId": "doc_44812",
    "userId": "usr_998124",
    "errorMessage": "Embedding provider timeout"
  }
  ```

### 2.4 Topic: `usage.events`
- **`token.consumed.v1`**:
  ```json
  {
    "userId": "usr_998124",
    "model": "gpt-4o",
    "promptTokens": 128,
    "completionTokens": 384,
    "totalTokens": 512,
    "latencyMs": 412
  }
  ```

---

## 3. Major REST API Contracts

### 3.1 Authentication (`auth-service`)
- `POST /api/v1/auth/register`
  - Request: `{ "email": "user@example.com", "password": "SecurePassword123!" }`
  - Response (201): `{ "message": "Verification email sent", "userId": "usr_998124" }`
- `POST /api/v1/auth/login`
  - Request: `{ "email": "user@example.com", "password": "SecurePassword123!" }`
  - Response (200): `{ "accessToken": "eyJhbGciOi...", "tokenType": "Bearer", "expiresIn": 900 }`
  - Sets cookie: `refreshToken=xyz; HttpOnly; Secure; SameSite=Strict`
- `POST /api/v1/auth/refresh`
  - Reads `refreshToken` cookie.
  - Response (200): `{ "accessToken": "eyJhbGciOi...", "expiresIn": 900 }`
- `POST /api/v1/auth/logout`
  - Invalidates token in Redis; clears refresh cookie.

### 3.2 User Management (`user-service`)
- `GET /api/v1/users/me` -> Current authenticated user profile.
- `PUT /api/v1/users/me/password` -> Update password.
- `GET /api/v1/admin/users` (ADMIN only) -> Paginated user list.

### 3.3 Chat & Conversations (`chat-service`)
- `POST /api/v1/chat/conversations`
  - Request: `{ "title": "Quarterly Analysis", "model": "gpt-4o" }`
  - Response (201): `{ "conversationId": "conv_123", "title": "Quarterly Analysis", "createdAt": "..." }`
- `GET /api/v1/chat/conversations` -> List user's conversations.
- `POST /api/v1/chat/conversations/{id}/messages`
  - Request: `{ "content": "Summarize the uploaded PDF", "enableRag": true, "tools": ["calculator"] }`
  - Response (200 / SSE): Streams tokens via Server-Sent Events.

### 3.4 Workflow Builder (`chat-service`)
- `GET /api/v1/workflows` -> List user workflows.
- `POST /api/v1/workflows` -> Save workflow DAG definition.
- `POST /api/v1/workflows/{id}/execute` -> Trigger workflow execution.
- `GET /api/v1/workflows/{id}/runs/{runId}` -> Inspect step-by-step execution logs and output.

### 3.5 AI Model Provider Integration (`ai-service`)
- `GET /api/v1/models` -> List available AI models and provider statuses.
- `POST /api/v1/models/generate` -> Non-streaming text completion.
- `POST /api/v1/models/stream` -> WebFlux SSE token streaming.

### 3.6 Document Management (`document-service`)
- `POST /api/v1/documents/upload`
  - Multipart form (`file`), max 25MB.
  - Response (202): `{ "documentId": "doc_44812", "status": "PROCESSING", "filename": "report.pdf" }`
- `GET /api/v1/documents` -> List user's documents.
- `DELETE /api/v1/documents/{id}` -> Soft-delete document.

### 3.7 RAG Semantic Retrieval (`rag-service`)
- `POST /api/v1/rag/retrieve`
  - Request: `{ "query": "What is our Q3 net margin?", "topK": 5, "documentIds": ["doc_44812"] }`
  - Response (200):
    ```json
    {
      "chunks": [
        {
          "chunkId": "chk_81",
          "documentId": "doc_44812",
          "content": "Net margin for Q3 reached 18.4%...",
          "similarityScore": 0.892,
          "pageNumber": 14
        }
      ]
    }
    ```

### 3.8 Subscriptions & Billing (`subscription-service`)
- `GET /api/v1/plans` -> Available subscription tiers.
- `POST /api/v1/subscriptions/checkout`
  - Request: `{ "planId": "plan_pro_monthly" }`
  - Response (200): `{ "orderId": "order_test_99812", "amount": 1900, "currency": "USD", "keyId": "rzp_test_..." }`
- `POST /api/v1/subscriptions/webhook`
  - Header: `X-Razorpay-Signature: ...`
  - Handles Razorpay payment webhook events idempotently.

---

## 4. Standard Error Envelope

All error responses across all services follow RFC 7807 Problem Details:

```json
{
  "timestamp": "2026-11-23T07:15:00.123Z",
  "status": 404,
  "error": "Not Found",
  "message": "Conversation with ID 'conv_99' not found",
  "path": "/api/v1/chat/conversations/conv_99",
  "correlationId": "corr-f81d4fae-7dec-11d0-a765-00a0c91e6bf6"
}
```
