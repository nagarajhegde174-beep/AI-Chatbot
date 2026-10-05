# chat-service

**Status: implemented (Phase 3).** 92 tests pass against a real PostgreSQL 17.

---

## Boundary

| | |
|---|---|
| **Port** | 8083 |
| **Database** | `nexa_chat` (role `nexa_chat`), schema `nexa_chat` |
| **Builds from** | its own directory; no aggregator POM |
| **Base package** | `com.nexaai.chat` |
| **Kafka group** | `chat-events` (its own; listener absent by default) |

## Conversations are the sessions

There is **no separate session table**. A session *is* a conversation, and a second table would
let the two disagree about what exists.

## What it owns

Conversations, messages, chat history, feedback.

## What it does not have

- **No credential of any kind.** No password column, no hash, no token. Asserted against the live
  schema by `DatabaseIsolationTest`.
- **No route to User Service's database.** The role is granted on `nexa_chat` alone, so a
  cross-service query fails with a permission error rather than quietly succeeding.
  Where a user detail is needed it crosses HTTP behind `UserDirectory`.
- **No self-registration, no login, no token issuance.** It verifies the RS256 access token with
  the **public key only** and has no signing key, so it cannot mint a token for itself.

## Security model

**No route accepts a user identifier.** Conversations and messages are addressed by their own id
and scoped to the caller by the *query*, not by a check after loading. There is no parameter
through which one user could ask for another's conversations — the guarantee is structural, not a
convention.

**Another user's conversation returns 404, never 403.** A 403 confirms the conversation exists,
which is a reliable oracle for discovering real ids. `UserIsolationTest` asserts the 404 body is
byte-identical to the one for an id that does not exist.

**Administrative access is metadata-only.** An ADMIN can list and search across all users and see
owner, title, timestamps, message count and status. **An ADMIN cannot read messages and cannot
export a conversation.** There is no route in this service that exposes another user's message
content.

That is a decision, not a gap. Message content is private data, and a role that can read every
conversation without limit is a surveillance capability no dashboard justifies. Where an operator
genuinely must read content, that needs a separate audited, time-boxed support-access feature with
a recorded reason — not this route, and not built in this phase.

## Routes

### Self-service — no user id anywhere

| Method | Path | |
|---|---|---|
| POST | `/api/v1/conversations` | create (body may be empty) |
| GET | `/api/v1/conversations` | **active** conversations |
| GET | `/api/v1/conversations/archived` | archived conversations |
| GET | `/api/v1/conversations/search?q=` | by title **or** message text |
| GET | `/api/v1/conversations/{id}` | one |
| PATCH | `/api/v1/conversations/{id}` | rename |
| PATCH | `/api/v1/conversations/{id}/model` | pin or unpin the model |
| POST | `/api/v1/conversations/{id}/archive` | |
| POST | `/api/v1/conversations/{id}/restore` | |
| DELETE | `/api/v1/conversations/{id}` | soft delete |
| GET | `/api/v1/conversations/{id}/messages` | history, oldest first |
| POST | `/api/v1/conversations/{id}/messages` | **send** |
| GET | `/api/v1/conversations/{id}/export` | Markdown or JSON |
| POST | `/api/v1/messages/{id}/regenerate` | **regenerate** |
| PUT | `/api/v1/messages/{id}` | **edit and resend** |
| DELETE | `/api/v1/messages/{id}` | delete one message |
| PUT | `/api/v1/messages/{id}/feedback` | **rate** |
| DELETE | `/api/v1/messages/{id}/feedback` | clear a rating |

### Administrative — requires `ADMIN`, metadata only

`GET /api/v1/admin/conversations`, `/counts`, `/feedback-totals`, `/{id}`.

## Design decisions worth knowing

**History is append-only.** Editing a user message writes a *new* row and supersedes the old one;
regenerating does the same in reverse. Nothing is ever overwritten in place. The intuitive
implementation mutates the row, which destroys the only record of what was originally said — and
since showing alternatives is the entire point of edit and regenerate, an implementation that
deletes the previous attempt cannot implement them correctly. Superseded rows stay in the table and
out of the default history view.

**Archive is not delete.** Archive is "get this out of my sidebar"; delete removes history. Only
one of those should be hard. Conversation delete is soft-deleted so an audit trail and any derived
data keep resolving. Message-level delete is hard, because a retracted single message has nothing
referencing it that should outlive the retraction.

**The AI response placeholder is written before generation is attempted.** If the row were created
afterwards, an interrupted stream, a crashed process or a timeout would leave the user with a
question, no reply, and no evidence anything happened. With it, the conversation always shows a
reply slot and a later phase can reconcile rows stuck in `PENDING`.

**Generation is off, so placeholders stay `PENDING`.** Not `FAILED`: nothing failed, nothing was
attempted, and a `FAILED` badge on every reply would be a lie. Not fabricated content either. The
user sees that a reply is expected and has not arrived.

**Search uses a LEFT JOIN.** An inner join silently excludes conversations with no messages yet —
which is the state a conversation is in the moment it is created, before the first message. A user
who creates, renames and then searches must find it.

**Search escapes LIKE wildcards.** An unescaped `%` from the caller matches everything, so
"search for %" would return every conversation and the endpoint would silently stop being a filter.

**A title is derived from the first message only, and only while untitled**, so a later message
cannot overwrite a name the user chose.

**Feedback is one row per message**, and re-rating updates it. Two contradictory rows make every
read ambiguous. `created_at` does not move, so it records when sentiment first arrived.

**The export filename is the UUID, never the title.** A title is user-supplied text; putting it in
`Content-Disposition` is how a newline becomes header injection.

## Configuration

| Variable | |
|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | |
| `NEXA_CHAT_JWT_PUBLIC_KEY` | **required.** PEM-encoded RS256 public key |
| `NEXA_CHAT_JWT_ISSUER` / `_AUDIENCE` | default `nexa-auth-service` / `nexaai-web` |
| `NEXA_CHAT_MAX_PAGE_SIZE` | default 100 |
| `NEXA_CHAT_MAX_MESSAGE_LENGTH` | default 32000 |
| `NEXA_CHAT_GENERATION_ENABLED` | **default false.** No AI Service yet |
| `NEXA_CHAT_DEFAULT_MODEL` | used when nothing is pinned |
| `NEXA_CHAT_EVENT_ENABLED` | default false; the listener bean is absent |
| `NEXA_CHAT_USER_SERVICE_ENABLED` | default false |

## Build and test

```bash
cd backend/chat-service
mvn -B -ntp clean verify
```

Needs a real PostgreSQL: Docker (Testcontainers) or `NEXA_TEST_PG_URL`.

```bash
export NEXA_TEST_PG_URL=jdbc:postgresql://127.0.0.1:5434/nexa_chat
export NEXA_TEST_PG_USER=nexa_chat
export NEXA_TEST_PG_PASSWORD=nexa_chat_local_pw
```

**H2 is never used.** A repository test against an in-memory database cannot test the property
this service depends on most — that it cannot see another service's tables — because that is
enforced by PostgreSQL grants.

## Not wired

- **AI generation.** The `ChatGenerationPort` seam exists and has no implementation.
- **Kafka publishing.** Nothing is produced; no topics are consumed.
- **The User Service call.** `UserDirectory` is built and disabled. Every operation here is scoped
  to the calling user, and the auth-service user id is already in the verified token, so there is
  no need to ask who the user is.
- **Streaming (SSE).** Not in this phase; the placeholder is the foundation a stream will complete.