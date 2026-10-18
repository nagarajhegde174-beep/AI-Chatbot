-- User profile table
-- Populated by consuming auth.user.registered.v1 from auth-service.
-- Never contains passwords, hashes, or raw tokens.
CREATE TABLE IF NOT EXISTS user_profile (
    id              UUID        PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    display_name    VARCHAR(100),
    avatar_url      VARCHAR(500),
    account_status  VARCHAR(50)  NOT NULL DEFAULT 'PENDING_VERIFICATION',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_user_profile_email        ON user_profile(email);
CREATE INDEX IF NOT EXISTS idx_user_profile_status       ON user_profile(account_status);

-- User preferences (1:1 with user_profile)
CREATE TABLE IF NOT EXISTS user_preferences (
    user_id              UUID PRIMARY KEY REFERENCES user_profile(id) ON DELETE CASCADE,
    theme                VARCHAR(20)  NOT NULL DEFAULT 'system',
    default_model        VARCHAR(100),
    rag_enabled_default  BOOLEAN      NOT NULL DEFAULT true,
    stream_responses     BOOLEAN      NOT NULL DEFAULT true,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Processed Kafka events for idempotent consumption
CREATE TABLE IF NOT EXISTS processed_event (
    event_id   VARCHAR(100) PRIMARY KEY,
    topic      VARCHAR(200) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_processed_event_processed_at ON processed_event(processed_at);
