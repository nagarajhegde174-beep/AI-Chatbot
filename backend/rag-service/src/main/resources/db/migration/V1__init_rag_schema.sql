-- NexaAI RAG Service Schema (Phase 7)
-- Owns: document_embedding (pgvector), processed_event
-- pgvector is loaded ONLY here. No other database loads the extension.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS document_embedding (
    id                  UUID PRIMARY KEY,
    user_id             UUID NOT NULL,
    document_id         UUID NOT NULL,
    document_title      VARCHAR(255),
    chunk_index         INTEGER NOT NULL,
    text                TEXT NOT NULL,
    embedding           vector(1536),
    model               VARCHAR(100) NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_embedding_user_doc ON document_embedding(user_id, document_id);
CREATE INDEX IF NOT EXISTS idx_embedding_user_id ON document_embedding(user_id);

-- HNSW index for cosine distance similarity search
CREATE INDEX IF NOT EXISTS idx_embedding_hnsw_cosine ON document_embedding USING hnsw (embedding vector_cosine_ops);

-- Idempotency table for event deduplication
CREATE TABLE IF NOT EXISTS processed_event (
    event_id            UUID PRIMARY KEY,
    event_type          VARCHAR(100) NOT NULL,
    processed_at        TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_processed_event_at ON processed_event(processed_at DESC);
