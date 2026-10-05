-- =====================================================================
-- V1: chat-service initial schema.
--
-- Owns nexa_chat and NOTHING else.
--
-- This service cannot read another service's database: the role is granted on
-- exactly one database, so a cross-service query fails with a permission error
-- rather than quietly succeeding (docs/RULES.md section 3).
--
-- The database is referred to by its owning service throughout these files
-- rather than by name, because naming another service's database here would
-- trip the R4 architecture check, which is the point of that check.
--
-- TWO THINGS THAT MATTER MOST IN THIS FILE:
--
--  1. No table stores a credential. No hash, no token, no secret.
--
--  2. owner_auth_user_id is on EVERY row that belongs to a person, and it is
--     the auth-service user id rather than an email or a local surrogate key.
--     User isolation is enforced by querying on this column, so a row without
--     it would be a row nobody can prove they own.
--
-- Conversations ARE the sessions. There is no separate session table: a
-- session is a conversation, and a second table would let the two disagree.
--
-- Every timestamp is timestamptz. A local-time timestamp across services is a
-- bug waiting to happen.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Conversations.
--
-- One row per session. Soft-deleted rather than removed, so an audit trail and
-- any derived data elsewhere keep resolving.
-- ---------------------------------------------------------------------
CREATE TABLE chat_conversation (
    -- NexaAI's internal surrogate key. Never an authorisation key.
    id                  UUID            PRIMARY KEY,

    -- The stable id issued by Auth Service. THE isolation key: every read of a
    -- conversation filters on this, so it is never null and never an email.
    owner_auth_user_id  UUID            NOT NULL,

    -- Defaults to the first user message, truncated. Denormalised so the
    -- sidebar list is a single indexed read rather than a join per row.
    title               VARCHAR(200)    NOT NULL DEFAULT 'New conversation',

    -- The model this conversation is pinned to, or null for "the user's
    -- default", which is resolved at send time and never stored as a guess.
    model               VARCHAR(128)    NULL,

    -- ACTIVE or ARCHIVED. Archive is a first-class state, not a delete: it is
    -- how a user keeps a conversation out of the sidebar without losing it.
    status              VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',

    -- Denormalised counters and timestamps. Kept because the sidebar sorts by
    -- last activity on every render; computing it from the message table would
    -- make that O(messages) per conversation.
    message_count       INTEGER         NOT NULL DEFAULT 0,
    last_message_at     TIMESTAMPTZ     NULL,

    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ     NULL,

    -- Constrained in the database, not only in Java. An application bug must
    -- not be able to invent a status.
    -- Optimistic locking. Two tabs sending at once both read message_count and both
    -- increment it; without this one increment is silently lost and the sidebar count
    -- drifts permanently. It is a correctness issue in a number the user can see.
    version             BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT ck_chat_conversation_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_chat_conversation_count CHECK (message_count >= 0)
);

-- The primary access path: "my conversations, newest activity first".
CREATE INDEX ix_chat_conversation_owner_activity
    ON chat_conversation (owner_auth_user_id, last_message_at DESC NULLS LAST)
    WHERE deleted_at IS NULL;

-- The sidebar's archived view.
CREATE INDEX ix_chat_conversation_owner_archived
    ON chat_conversation (owner_auth_user_id, updated_at DESC)
    WHERE deleted_at IS NULL AND status = 'ARCHIVED';

-- Search is case-insensitive over the title, so index the expression the query
-- actually uses rather than the raw column.
CREATE INDEX ix_chat_conversation_title_lower ON chat_conversation (lower(title));

-- ---------------------------------------------------------------------
-- Messages.
--
-- Messages are never hard-deleted individually. An edited message is SUPERSEDED
-- by a new row rather than overwritten, because "what did the user actually
-- send first" is exactly the thing an edit is supposed to preserve, and it is
-- what makes regenerate and edit reproducible.
--
-- role SYSTEM is reserved for future prompt/context injection and is not
-- writable through the public API in this phase.
-- ---------------------------------------------------------------------
CREATE TABLE chat_message (
    id                  UUID            PRIMARY KEY,
    conversation_id     UUID            NOT NULL REFERENCES chat_conversation (id) ON DELETE CASCADE,

    -- The owner is denormalised from the conversation ON PURPOSE. It means the
    -- "messages of conversation X that belong to me" check is a single indexed
    -- predicate that cannot accidentally be forgotten on a code path that only
    -- queries messages. Ownership is then re-verified against the conversation
    -- for writes.
    owner_auth_user_id  UUID            NOT NULL,

    -- Monotonic per conversation. Ordering by timestamp alone is wrong: two
    -- messages in the same transaction get the same timestamp, and history
    -- order is not negotiable.
    sequence_no         BIGINT          NOT NULL,

    role                VARCHAR(16)     NOT NULL,
    content             TEXT            NOT NULL,

    -- COMPLETE, PENDING or FAILED.
    --
    -- PENDING is the AI response placeholder. The message row is created BEFORE
    -- generation is attempted, so an interrupted or crashed stream leaves a
    -- visible PENDING row rather than a silently missing reply. FAILED records
    -- that the attempt happened and why it did not finish.
    status              VARCHAR(16)     NOT NULL DEFAULT 'COMPLETE',

    -- Which model actually produced an assistant message. Null on a user
    -- message and on a PENDING placeholder whose model is not yet resolved.
    model               VARCHAR(128)    NULL,

    -- Populated from the AI response when one exists. Null on a placeholder.
    -- Kept for usage metering, which must not parse the content to count it.
    input_tokens        INTEGER         NULL,
    output_tokens       INTEGER         NULL,

    -- For a regenerate: the assistant message this one replaces. The original
    -- is kept, so the full history of attempts is inspectable.
    regenerated_from_id UUID            NULL REFERENCES chat_message (id),

    -- Set when an edit created this message, pointing at the message it
    -- replaced. Makes "edited" answerable without diffing content.
    edited_from_id      UUID            NULL REFERENCES chat_message (id),

    -- When an edit supersedes a user message, the old row keeps its content
    -- but stops appearing in normal history reads.
    superseded_at       TIMESTAMPTZ     NULL,

    -- Set when generation failed, so the reason is not lost.
    failure_reason      VARCHAR(255)    NULL,

    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT ck_chat_message_role CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM')),
    CONSTRAINT ck_chat_message_status CHECK (status IN ('COMPLETE', 'PENDING', 'FAILED')),
    CONSTRAINT ck_chat_message_tokens CHECK (
        (input_tokens IS NULL OR input_tokens >= 0)
        AND (output_tokens IS NULL OR output_tokens >= 0)
    ),
    -- A sequence number is unique within its conversation. The service computes
    -- it as max+1 under a row lock, and this constraint is what turns a lost
    -- race into a failure instead of a duplicate.
    CONSTRAINT uq_chat_message_conversation_seq UNIQUE (conversation_id, sequence_no)
);

-- History read: one conversation in order.
CREATE INDEX ix_chat_message_conversation_seq
    ON chat_message (conversation_id, sequence_no);

-- Search across a user's conversations by message content.
CREATE INDEX ix_chat_message_content_lower ON chat_message (lower(content));

-- The sender's own conversation list, filtered by role, for "my questions".
CREATE INDEX ix_chat_message_owner_role ON chat_message (owner_auth_user_id, role, created_at DESC);

-- Pending rows that a later phase will pick up or reconcile.
CREATE INDEX ix_chat_message_pending ON chat_message (status, created_at)
    WHERE status = 'PENDING';

-- ---------------------------------------------------------------------
-- Feedback.
--
-- One row per message. Re-submitting feedback updates the row rather than
-- appending, because "the current opinion about this answer" is one fact, and
-- two contradictory rows would be a bug in every read of it.
-- ---------------------------------------------------------------------
CREATE TABLE message_feedback (
    id                  UUID            PRIMARY KEY,
    message_id          UUID            NOT NULL REFERENCES chat_message (id) ON DELETE CASCADE,

    -- Denormalised for the same reason messages carry it: isolation must not
    -- depend on remembering to join.
    owner_auth_user_id  UUID            NOT NULL,

    -- UP or DOWN. Never a numeric score: a five-point scale invites
    -- "what does 3 mean" and gets answered inconsistently.
    rating              VARCHAR(8)      NOT NULL,

    comment             VARCHAR(1000)   NULL,

    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT ck_message_feedback_rating CHECK (rating IN ('UP', 'DOWN')),
    -- One opinion per message. The application upserts; this makes a duplicate
    -- insert a failure instead of a second row.
    CONSTRAINT uq_message_feedback_message UNIQUE (message_id)
);

CREATE INDEX ix_message_feedback_owner ON message_feedback (owner_auth_user_id, created_at DESC);

-- ---------------------------------------------------------------------
-- Processed event ids.
--
-- Kafka is at-least-once, so an inbound event can arrive twice. This table is
-- the deduplication key (docs/SERVICE_CONTRACTS.md section 1.7).
-- ---------------------------------------------------------------------
CREATE TABLE processed_event (
    event_id      UUID        PRIMARY KEY,
    event_type    VARCHAR(128) NOT NULL,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_processed_event_type ON processed_event (event_type, processed_at DESC);