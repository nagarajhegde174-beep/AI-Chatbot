-- NexaAI Document Service Schema (Phase 7)
-- Owns: document, document_storage, document_chunk

CREATE TABLE IF NOT EXISTS document (
    id                  UUID PRIMARY KEY,
    user_id             UUID NOT NULL,
    title               VARCHAR(255) NOT NULL,
    original_filename   VARCHAR(255) NOT NULL,
    content_type        VARCHAR(100) NOT NULL,
    size_bytes          BIGINT NOT NULL,
    status              VARCHAR(50) NOT NULL DEFAULT 'UPLOADING',
    failure_reason      TEXT,
    chunk_count         INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_document_user_id ON document(user_id);
CREATE INDEX IF NOT EXISTS idx_document_status ON document(status);
CREATE INDEX IF NOT EXISTS idx_document_created_at ON document(created_at DESC);

CREATE TABLE IF NOT EXISTS document_storage (
    id                  UUID PRIMARY KEY,
    document_id         UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    storage_path        VARCHAR(500) NOT NULL,
    checksum_sha256     VARCHAR(64) NOT NULL,
    stored_bytes        BIGINT NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_doc_storage_doc_id ON document_storage(document_id);

CREATE TABLE IF NOT EXISTS document_chunk (
    id                  UUID PRIMARY KEY,
    document_id         UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    chunk_index         INTEGER NOT NULL,
    text                TEXT NOT NULL,
    character_count     INTEGER NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_doc_chunk_doc_idx ON document_chunk(document_id, chunk_index);
