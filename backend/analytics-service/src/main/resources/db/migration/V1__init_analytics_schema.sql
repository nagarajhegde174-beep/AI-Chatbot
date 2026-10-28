-- NexaAI Analytics & Usage Schema (Phase 9)
-- Owns: processed_event, ai_request_metric, user_activity_read_model, document_read_model, conversation_query_log, model_registry_state

CREATE TABLE IF NOT EXISTS processed_event (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS ai_request_metric (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    conversation_id UUID,
    message_id UUID,
    provider VARCHAR(50) NOT NULL,
    model VARCHAR(100) NOT NULL,
    prompt_tokens BIGINT NOT NULL DEFAULT 0,
    completion_tokens BIGINT NOT NULL DEFAULT 0,
    total_tokens BIGINT NOT NULL DEFAULT 0,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    estimated_cost_usd NUMERIC(12, 6) NOT NULL DEFAULT 0.0,
    status VARCHAR(50) NOT NULL,
    error_code VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ai_metric_user ON ai_request_metric(user_id);
CREATE INDEX IF NOT EXISTS idx_ai_metric_created ON ai_request_metric(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_metric_model ON ai_request_metric(model);
CREATE INDEX IF NOT EXISTS idx_ai_metric_provider ON ai_request_metric(provider);

CREATE TABLE IF NOT EXISTS user_activity_read_model (
    user_id UUID PRIMARY KEY,
    email VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    subscription_tier VARCHAR(50) NOT NULL DEFAULT 'FREE',
    token_quota BIGINT NOT NULL DEFAULT 100000,
    tokens_used BIGINT NOT NULL DEFAULT 0,
    total_requests BIGINT NOT NULL DEFAULT 0,
    last_login_at TIMESTAMP WITH TIME ZONE,
    last_login_ip VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_user_read_status ON user_activity_read_model(status);
CREATE INDEX IF NOT EXISTS idx_user_read_tier ON user_activity_read_model(subscription_tier);

CREATE TABLE IF NOT EXISTS document_read_model (
    document_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    file_size_bytes BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL,
    chunk_count INT NOT NULL DEFAULT 0,
    embedding_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_doc_read_user ON document_read_model(user_id);
CREATE INDEX IF NOT EXISTS idx_doc_read_status ON document_read_model(status);

CREATE TABLE IF NOT EXISTS conversation_query_log (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    conversation_id UUID NOT NULL,
    model VARCHAR(100),
    tokens INT NOT NULL DEFAULT 0,
    latency_ms INT NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_conv_log_user ON conversation_query_log(user_id);
CREATE INDEX IF NOT EXISTS idx_conv_log_created ON conversation_query_log(created_at DESC);

CREATE TABLE IF NOT EXISTS model_registry_state (
    model_name VARCHAR(100) PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    is_default BOOLEAN NOT NULL DEFAULT false,
    total_requests BIGINT NOT NULL DEFAULT 0,
    total_tokens BIGINT NOT NULL DEFAULT 0,
    total_errors BIGINT NOT NULL DEFAULT 0,
    last_used_at TIMESTAMP WITH TIME ZONE
);
