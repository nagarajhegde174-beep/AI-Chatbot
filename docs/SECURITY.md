# Security Architecture & Policies — NexaAI

## 1. Security Principles
NexaAI implements strict defense-in-depth principles across transport, identity, compute, data storage, and external provider boundaries.

---

## 2. Authentication & Credential Security

### 2.1 Password Hashing & Storage
- Passwords are encrypted using **bcrypt** with a work factor of 12.
- Plaintext passwords are never logged, persisted, or returned in API responses.

### 2.2 Asymmetric JWT Tokens (RS256)
- **Token Architecture**:
  - `auth-service` signs JWTs using an **RS256 private key**.
  - `api-gateway` and downstream services verify JWT signatures using the corresponding **RS256 public key**.
  - Private key material is never shared with downstream services.
- **Lifetimes**:
  - Access Token: 15 minutes (stateless, short-lived).
  - Refresh Token: 7 days (stored in HTTP-only, Secure, SameSite=Strict cookies).
- **Revocation**:
  - Logout places the token's `jti` (JWT ID) into Redis with a TTL matching the token's remaining lifetime.
  - The API Gateway checks the Redis denylist on every incoming request.

---

## 3. Role-Based Access Control (RBAC)
- Exactly two system roles exist:
  - `ROLE_USER`: Standard tenant permissions (manage own conversations, documents, workflows, and subscriptions).
  - `ROLE_ADMIN`: Administrative permissions (view system telemetry, manage all users, inspect system audit logs).
- **Enforcement**:
  - Gateway pre-filters `/api/v1/admin/**` routes.
  - Method-level security annotations (`@PreAuthorize("hasRole('ADMIN')")`) enforce authorization at the service controller layer.
  - User isolation: All user resource queries (`conversations`, `documents`, `workflows`) filter strictly by the authenticated `userId` extracted from the verified JWT claims. Users can never view or mutate foreign tenant data.

---

## 4. Network Security & Ingress Filtering

### 4.1 Reverse Proxy & Gateway Defense
- **NGINX Edge**:
  - Restricts payload sizes (`client_max_body_size 25m`).
  - Hides internal service headers (`server_tokens off`).
  - Blocks external access to internal paths: `/internal/**` and `/actuator/**` return `404 Not Found`.
- **Security Headers**:
  - `X-Content-Type-Options: nosniff`
  - `X-Frame-Options: DENY`
  - `Referrer-Policy: strict-origin-when-cross-origin`
  - `Permissions-Policy: camera=(), microphone=(), geolocation=()`
  - `Cross-Origin-Opener-Policy: same-origin`

### 4.2 Cross-Origin Resource Sharing (CORS)
- Explicit allowed origins (`http://localhost:5173`, `http://localhost:3000`).
- Disallows wildcard origins (`*`) when credentials are included (`allowCredentials: true`).

---

## 5. Rate Limiting & Denial-of-Service (DoS) Protection
- **Multi-Zone NGINX Rate Limiting**:
  - `auth` zone: 10 requests/minute per IP (burst 5) for login/register/reset endpoints.
  - `chat` zone: 60 requests/minute per IP (burst 20) for message streaming.
  - `api` zone: 300 requests/minute per IP (burst 60) for general API endpoints.
- **Application-Level Redis Rate Limiter**:
  - Distributed token bucket enforced per user/IP at the API Gateway.
  - Returns `429 Too Many Requests` with standard `Retry-After` header.

---

## 6. Document & Upload Security
- **MIME Type Validation**: Multi-layer inspection validating both HTTP Content-Type headers and actual magic bytes via Apache Tika.
- **Storage Sanitization**:
  - Files are renamed with UUIDs upon receipt (e.g., `d19f4a56-4b2a-4a2e-839e-e380f72f8832.pdf`). Original filenames are sanitized before being saved as metadata.
  - Local upload directory permissions are restricted to the application service user.
- **Maximum Size**: Hard ceiling of 25MB enforced at NGINX, Gateway, and Document Service.

---

## 7. Database Isolation & Least Privilege
- Separate database per service (`nexa_auth`, `nexa_user`, `nexa_chat`, `nexa_document`, `nexa_rag`, `nexa_subscription`, `nexa_analytics`).
- Each service connects with its own dedicated role (`nexa_auth_user`, `nexa_user_user`, etc.).
- Default PostgreSQL `PUBLIC` schema permissions are revoked. Services are granted privileges exclusively over their own database.
- Cross-service database network connections are blocked.

---

## 8. Payment Security (Razorpay Test Mode)
- **Strict Test Mode**: All keys must be `rzp_test_*`. Real live transactions are prohibited.
- **Cryptographic Signature Verification**:
  - Webhooks verified using HMAC-SHA256 with `RAZORPAY_WEBHOOK_SECRET`.
  - Signature mismatch immediately returns `400 Bad Request` and halts processing.
- **Idempotency Protection**:
  - Webhook delivery IDs are recorded in Redis and PostgreSQL unique constraints to prevent double-charging or duplicate provisioning.

---

## 9. Agentic AI Tool Safety
- AI agents cannot execute arbitrary shell code or raw SQL queries.
- Tool invocations are verified server-side against tenant boundaries.
- Destructive operations require explicit human confirmation.
- Execution loop circuit breaker limits agent reasoning to a maximum of 5 iterations.
