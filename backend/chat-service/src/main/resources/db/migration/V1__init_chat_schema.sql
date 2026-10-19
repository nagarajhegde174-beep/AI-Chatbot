-- NexaAI Chat Service Schema (Phase 3)
-- Owns: conversation, message, message_citation, conversation_model_history

CREATE TABLE IF NOT EXISTS conversation (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL,
    title           VARCHAR(255) NOT NULL,
    model           VARCHAR(100) NOT NULL,
    system_prompt   TEXT,
    rag_enabled     BOOLEAN NOT NULL DEFAULT true,
    is_archived     BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    last_message_at TIMESTAMP WITH TIME ZONE,
    deleted_at      TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_conversation_user_id ON conversation(user_id);
CREATE INDEX IF NOT EXISTS idx_conversation_archived ON conversation(is_archived);
CREATE INDEX IF NOT EXISTS idx_conversation_updated_at ON conversation(updated_at DESC);

CREATE TABLE IF NOT EXISTS message (
    id                  UUID PRIMARY KEY,
    conversation_id     UUID NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
    user_id             UUID NOT NULL,
    role                VARCHAR(20) NOT NULL,
    content             TEXT NOT NULL,
    model               VARCHAR(100),
    status              VARCHAR(30) NOT NULL DEFAULT 'COMPLETED',
    prompt_tokens       INTEGER,
    completion_tokens   INTEGER,
    total_tokens        INTEGER,
    sequence_number     INTEGER NOT NULL,
    feedback            VARCHAR(20),
    feedback_comment    TEXT,
    is_regenerated      BOOLEAN NOT NULL DEFAULT false,
    original_message_id UUID,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_message_conversation ON message(conversation_id, sequence_number ASC);
CREATE INDEX IF NOT EXISTS idx_message_user_id ON message(user_id);

CREATE TABLE IF NOT EXISTS message_citation (
    id              UUID PRIMARY KEY,
    message_id      UUID NOT NULL REFERENCES message(id) ON DELETE CASCADE,
    document_id     VARCHAR(100) NOT NULL,
    title           VARCHAR(255),
    chunk_index     INTEGER,
    excerpt         TEXT
);

CREATE INDEX IF NOT EXISTS idx_citation_message ON message_citation(message_id);

CREATE TABLE IF NOT EXISTS conversation_model_history (
    id              UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,
    previous_model  VARCHAR(100),
    new_model       VARCHAR(100) NOT NULL,
    changed_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    changed_by      UUID NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_model_history_conversation ON conversation_model_history(conversation_id);
