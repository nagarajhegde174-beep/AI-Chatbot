# chat-service — NexaAI Chat Service

**Status: Implemented (Phase 3).**

Chat turn orchestrator, conversation manager, chat history, and multiple sessions.

---

## Boundary

| | |
|---|---|
| **Port** | 8083 |
| **Database** | `nexa_chat` (role `nexa_chat`) |
| **Builds from** | its own directory; no aggregator POM |
| **Base package** | `com.nexaai.chat` |
| **Phase** | 3 |

## Responsibility

Conversations, messages, chat history, multiple sessions, and AI integration boundary.

## Does

- Conversation CRUD with user isolation and ownership check on every request
- Multiple sessions and archiving/unarchiving
- Search across conversation titles and message content
- Storing user messages and AI response placeholder integration boundary
- Message turn regeneration foundation
- Message edit and resend foundation
- Message feedback (thumbs up / thumbs down + comments)
- Conversation export (JSON, Markdown, Text)
- Admin auditing routes (`/api/v1/admin/chat/conversations/**`)
- Boundary assertion: `GET /internal/v1/chat/info`

## Does not

- Access User Service database directly (`RULES.md` §3). Uses REST (`UserServiceClient`)
- Access Auth Service database
- Store plaintext credentials

## Owns

`nexa_chat`: `conversation`, `message`, `message_citation`, `conversation_model_history`.
Migrations under `src/main/resources/db/migration`.