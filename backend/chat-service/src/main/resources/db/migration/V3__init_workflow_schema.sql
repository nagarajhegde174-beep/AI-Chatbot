-- NexaAI Workflow Builder Schema (Phase 11)
-- Owns: workflow, workflow_execution

CREATE TABLE IF NOT EXISTS workflow (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    trigger_type    VARCHAR(50) NOT NULL DEFAULT 'MANUAL',
    nodes_json      TEXT NOT NULL,
    edges_json      TEXT NOT NULL,
    is_enabled      BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_workflow_user_id ON workflow(user_id);
CREATE INDEX IF NOT EXISTS idx_workflow_updated_at ON workflow(updated_at DESC);

CREATE TABLE IF NOT EXISTS workflow_execution (
    id                      UUID PRIMARY KEY,
    workflow_id             UUID NOT NULL REFERENCES workflow(id) ON DELETE CASCADE,
    user_id                 UUID NOT NULL,
    status                  VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    current_step_index      INTEGER NOT NULL DEFAULT 0,
    input_payload           TEXT,
    output_payload          TEXT,
    step_logs_json          TEXT,
    error_message           TEXT,
    requires_approval       BOOLEAN NOT NULL DEFAULT false,
    pending_approval_node_id VARCHAR(100),
    started_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at            TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_workflow_exec_workflow_id ON workflow_execution(workflow_id);
CREATE INDEX IF NOT EXISTS idx_workflow_exec_user_id ON workflow_execution(user_id);
CREATE INDEX IF NOT EXISTS idx_workflow_exec_started_at ON workflow_execution(started_at DESC);
