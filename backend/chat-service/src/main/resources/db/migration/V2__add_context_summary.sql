-- Phase 5: Conversation Memory
-- Adds context_summary to persist the rolling compressed summary
-- and summary_message_seq to track which messages have been summarized.

ALTER TABLE conversation
    ADD COLUMN IF NOT EXISTS context_summary       TEXT,
    ADD COLUMN IF NOT EXISTS summary_message_seq   INTEGER NOT NULL DEFAULT 0;
