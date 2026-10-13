-- =====================================================================
-- Conversation memory.
--
-- One row per conversation, holding the COMPRESSED form of the older turns.
-- The verbatim messages are NOT here and are never removed: this table is a
-- derived index over them, not a second copy of the record.
--
-- That distinction is the whole design. A conversation's messages are
-- append-only and are the only authoritative history. Memory is something
-- Chat Service builds to answer "what did we already cover", so it can be
-- dropped and rebuilt at any time without losing a single message.
--
-- WHY ONE ROW PER CONVERSATION AND NOT PER USER
--
-- Memory is conversation-specific, so it is keyed by conversation. That
-- makes cross-conversation leakage structurally impossible rather than
-- merely guarded: a join that forgot the owner predicate still could not
-- reach another conversation's row. owner_auth_user_id is carried anyway,
-- and every read filters on it, because a row whose owner does not match
-- the conversation's owner is a bug worth failing on rather than reading.
--
-- WHY THE SUMMARY IS NULLABLE
--
-- A conversation with no memory yet has no row at all, so a nullable
-- summary on an existing row means "we had memory and it was cleared".
-- The two states are different: one is "nothing to remember yet", the
-- other is "the user asked us to forget", and only the second should
-- cause the next send to rebuild from the full history.
-- =====================================================================

CREATE TABLE conversation_memory (
    id                            UUID            PRIMARY KEY,
    conversation_id               UUID            NOT NULL UNIQUE
                                  REFERENCES chat_conversation (id) ON DELETE CASCADE,
    owner_auth_user_id            UUID            NOT NULL,

    -- The compressed older turns. NULL after a clear; absent row before any
    -- summarisation at all.
    summary                       TEXT            NULL,

    -- Every message with a sequence at or below this is covered by `summary`
    -- and no longer needs to be sent verbatim. Zero means nothing is covered.
    summarised_through_sequence   BIGINT          NOT NULL DEFAULT 0,

    -- How many turns the summary covers. Kept separately from the sequence
    -- so a caller can report "remembering 40 earlier turns" without walking
    -- the message table, and so an emptied range is visible rather than
    -- indistinguishable from "never summarised".
    summarised_turn_count         INTEGER         NOT NULL DEFAULT 0,

    -- When the summary was last rebuilt, and how many tokens it was judged
    -- to cost. The latter is what decides whether another pass is needed
    -- without re-measuring the string.
    summarised_at                 TIMESTAMPTZ     NULL,
    summary_estimated_tokens      INTEGER         NULL,

    -- Incremented on every write. Summarisation and clearing both race with
    -- an in-flight send, and a lost update here means a conversation that
    -- silently stops remembering itself.
    version                       BIGINT          NOT NULL DEFAULT 0,

    created_at                    TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                    TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT ck_conversation_memory_owner
        CHECK (owner_auth_user_id IS NOT NULL),
    CONSTRAINT ck_conversation_memory_sequence
        CHECK (summarised_through_sequence >= 0),
    CONSTRAINT ck_conversation_memory_turns
        CHECK (summarised_turn_count >= 0),
    -- A summary and the range it covers are written together, so one without
    -- the other is always a bug rather than a state worth representing.
    CONSTRAINT ck_conversation_memory_summary_pair
        CHECK ((summary IS NULL) = (summarised_turn_count = 0))
);

-- Reads are always "this conversation's memory", by conversation id.
CREATE INDEX ix_conversation_memory_owner
ON conversation_memory (owner_auth_user_id);

-- =====================================================================
--  NOTHING IS DELETED WHEN A CONVERSATION IS CLEARED.
--
--  Clearing memory resets `summary` and rewinds
--  `summarised_through_sequence` to zero. Messages stay exactly as they
--  are. "Forget what we discussed" and "delete the discussion" are
--  different requests, and conflating them would either lose the user's
--  own words or fail to actually forget anything.
-- =====================================================================