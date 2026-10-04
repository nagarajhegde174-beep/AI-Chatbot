# PRD.md — Product Requirements

NexaAI is an AI chat SaaS. A user brings their own documents, asks questions, and gets answers
grounded in those documents, from a model they choose.

This document states **what the product does**. How it is built is
[`ARCHITECTURE.md`](ARCHITECTURE.md); what it is forbidden from being is [`RULES.md`](RULES.md).

Phases are tracked in [`ARCHITECTURE.md`](ARCHITECTURE.md) §14.

---

## 1. Purpose

### 1.1 The problem

General AI chat tools answer from a model's training data. That is the wrong answer when the
question is about *your* material: a contract clause, a policy document, a manual, a set of
notes, a codebase's README.

Search is not enough either, because it returns documents and makes the user do the reading.

### 1.2 The product

NexaAI lets a user:

1. Upload documents.
2. Ask questions in a normal chat interface.
3. Receive answers **grounded in those documents**, with citations back to the source.
4. Continue the conversation, where the AI **remembers** what was said earlier.
5. Choose which AI model answers, per conversation.
6. Stay inside a plan with a clear, visible usage allowance.

### 1.3 Non-goals

- Not a general-purpose autonomous agent. No tool calling, no code execution.
- Not a team collaboration suite. No shared documents, no comments, no presence.
- Not a fine-tuning platform. Retrieval, not training.
- Not a document editor. Documents are read, never modified.

### 1.4 Explicit non-goals worth stating

- **No image or audio generation.** Out of scope entirely.
- **No voice interface.** Text only.
- **No self-hosted model hosting.** NexaAI calls hosted providers through Spring AI.

---

## 2. Target users

### 2.1 USER

The paying customer. Wants answers from their own material without building infrastructure.

Typical: a professional, a student, a small team lead, someone reading a large PDF.

**Needs.**
- Sign up and sign in quickly.
- Upload PDFs and documents.
- Ask questions and get grounded answers with citations.
- Hold a conversation that remembers context.
- Switch model when a question is hard or cheap.
- See remaining usage before hitting a limit, not after.
- Delete their data and cancel.

### 2.2 ADMIN

The operator of the service. Not a customer.

**Responsibilities.**
- Manage the catalogue of available AI models and their limits.
- View platform usage and analytics.
- Review and moderate user accounts.
- Manage subscription plans and pricing.
- Observe system health, failures and cost.

**Explicitly not** a customer support agent role. See [`RULES.md`](RULES.md) §8: there are
exactly two roles.

---

## 3. Roles and permissions

The application has **exactly two roles** (`RULES.md` §8).

| Capability | USER | ADMIN |
|---|:--:|:--:|
| Register, log in, log out | yes | yes |
| Read and update own profile | yes | yes |
| Upload, list, delete own documents | yes | — |
| Create, rename, delete own conversations | yes | — |
| Send messages, stream answers | yes | — |
| Choose the model for a conversation | yes | yes |
| Read own usage and plan | yes | — |
| Create a Razorpay test order, subscribe, cancel | yes | — |
| List all users | — | yes |
| Suspend / reinstate a user account | — | yes |
| List and edit AI model catalogue | — | yes |
| List and edit subscription plans | — | yes |
| View platform-wide usage analytics | — | yes |
| Read system health and failure records | — | yes |

**The important line.** A USER may only ever read or modify **their own** resources. Not "the
resource if you know the id". Ownership is checked server-side on every single request, and the
check is not optional.

---

## 4. Feature: multi-model AI chat

### 4.1 Providers

Three provider families are supported, all through **Spring AI** (`RULES.md`, AI section of
[`ARCHITECTURE.md`](ARCHITECTURE.md)).

| Provider | Used for | Notes |
|---|---|---|
| OpenAI | chat, embeddings | strongest embedding quality available to us |
| Google Gemini | chat, embeddings | long context |
| Groq / LLaMA | chat | fastest; used for short, cheap answers |

### 4.2 Choosing a model

A conversation is bound to one model. Changing it mid-conversation is allowed and explicitly
resets the working memory window, because the new model has not seen the old context. The UI
warns before the change takes effect.

### 4.3 Streaming

Answers stream token by token over Server-Sent Events. A non-streaming answer is not acceptable
for a user-facing chat: perceived latency is the product.

### 4.4 Graceful failure

If a provider fails, the user sees a specific, actionable message and keeps their input. The
conversation is never left in a state where a half-written message looks complete.

---

## 5. Feature: conversation memory

### 5.1 What "memory" means here

Memory is **the visible conversation history plus a bounded recent window sent to the model**.
It is not a summarisation system and not a vector memory store. That is a deliberate limit:
invisible memory that changes answers unpredictably is worse than no memory.

### 5.2 Requirements

- The model receives the last *N* turns of the conversation, plus a system prompt, plus any
  retrieved document context.
- The window is bounded by token count, not by message count, because models differ in
  context size.
- The window is built from what the **user** can see. No hidden history, no injected turns.
- Truncation is visible: if older turns fall outside the window, the answer may say so.

### 5.3 Why it belongs to Chat Service

Chat Service owns conversations, so it owns the memory window. The AI Service is stateless and
knows nothing about any conversation (`ARCHITECTURE.md` §4).

---

## 6. Feature: documents

### 6.1 Upload

- Formats: PDF, plain text, Markdown, DOCX.
- Size limit per document and total volume per plan.
- Upload is **multipart**, and the browser must never receive a direct storage URL.

### 6.2 Processing

Extraction and chunking are background work, not a request. The user uploads, sees
`PROCESSING`, and the document becomes `READY` or `FAILED` asynchronously.

### 6.3 States

`UPLOADING → PROCESSING → READY | FAILED`

A `FAILED` document states the reason in plain language and offers a retry. It never silently
disappears.

### 6.4 Privacy

Documents are private to their owner. They are not used for training, not shared, and not
readable by another user under any circumstance.

---

## 7. Feature: RAG

### 7.1 What the user experiences

They ask a question. They get an answer built from the passages of their documents that are
relevant, with citations. If their documents do not contain the answer, the system says so
rather than inventing one.

### 7.2 Pipeline

```
upload → extract text → chunk → embed → store vector (pgvector)
                                             ↓
question → embed → similarity search → top-k passages → answer + citations
```

### 7.3 Grounding requirement

**An answer with retrieved context must cite its sources.** A citation is a document id plus
the chunk the text came from. An uncited grounded answer is a defect.

### 7.4 Saying "I don't know"

When retrieval returns nothing relevant, the assistant states that the answer is not in the
user's documents and answers from general knowledge *labelled as such*. This is the single
most important behaviour for trustworthiness.

### 7.5 Retrieval is scoped per user

A similarity search must never return a passage belonging to another user. Tenant isolation
is part of the query, not a filter applied afterwards.

---

## 8. Feature: subscriptions

### 8.1 Plans

A catalogue of plans. Each plan has a monthly message allowance, a document quota, a storage
quota, and the model tiers available. Entitlements, not plan names, are what the system checks.

### 8.2 Payments

Razorpay, **test mode only** (`RULES.md` §9). The flow is: create order server-side, complete
it in the Razorpay test UI, receive a webhook, grant entitlements.

### 8.3 Entitlements are server-side

The frontend may *display* a limit. It may never *decide* one. Every quota check happens in the
owning service against the database.

### 8.4 Cancellation

A user can cancel. Entitlement is not revoked retroactively for messages already sent, and the
plan degrades to free tier at period end.

---

## 9. Feature: usage metering

Every LLM call records provider, model, token counts and latency. This is needed for three
reasons, all of them real:

1. Enforcing the plan allowance.
2. Telling the user what they have used.
3. Knowing what the platform costs.

Usage events are produced asynchronously, so metering never sits in the user's request path.

---

## 10. Feature: analytics

### 10.1 For the user

Messages sent, documents uploaded, current usage against the plan. Nothing about other users,
nothing about revenue.

### 10.2 For the admin

Per-provider and per-model call volume, token totals, latency percentiles, error rates, and
cost. Aggregate numbers, not prompt content. Seeing user prompts requires a separate,
explicitly granted capability that this product does not have.

---

## 11. Security requirements

The full model is in [`SECURITY.md`](SECURITY.md). The product-level requirements:

| Area | Requirement |
|---|---|
| Passwords | Hashed with a memory-hard adaptive algorithm. Never stored, logged or emailed in plaintext. |
| Sessions | Short-lived signed access token plus a rotating refresh token stored hashed. |
| Authorisation | Enforced server-side per request. Never trusted from the client. |
| Transport | TLS everywhere in any non-local environment. HSTS. |
| Secrets | Environment variables only. Never in source, never in the frontend (`RULES.md` §6). |
| Rate limiting | Per IP at the edge, per user in each service. |
| Uploads | Validated by content, not by extension. Size-limited. Stored outside the web root. Never executed. |
| Payments | Test mode only. Webhook signatures verified. |
| Audit | Every admin action and every authentication event recorded, immutable. |
| Data deletion | A user can delete their documents and cancel; retention is documented. |

---

## 12. Success criteria

Phase-by-phase acceptance criteria live in [`TEST_PLAN.md`](TEST_PLAN.md) and
[`TEST_PLAN.md`](TEST_PLAN.md). Product-level, the product is successful when:

1. A user uploads a document and asks about it, and gets a correct cited answer.
2. A multi-turn conversation stays coherent.
3. No user can ever see another user's document, conversation or usage.
4. Usage limits are accurate to the message.
5. The frontend bundle contains no secret of any kind.
6. Each service can be stopped without taking the others down.