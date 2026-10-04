

-- ---------------------------------------------------------------------
-- Users. The ONLY table that holds a credential.
--
-- Role and status are stored as VARCHAR with a CHECK constraint rather than as
-- foreign keys into lookup tables.
--
-- Why: the enum in Java is the source of truth, so a lookup table would add a
-- JOIN on every read and every write to model something the code already
-- knows. The CHECK keeps the real guarantee, namely that the database itself
-- refuses a third role (docs/RULES.md section 8), without the join.
--
-- An earlier draft used SMALLINT foreign keys into `role` and `account_status`
-- tables. Hibernate's schema validation caught the mismatch against
-- @Enumerated(EnumType.STRING) mappings, and the simpler schema is the better
-- one, so it was changed rather than worked around.
-- ---------------------------------------------------------------------
CREATE TABLE auth_user (
    id                 UUID            PRIMARY KEY,

    -- Stored lower-cased and trimmed, so "User@Example.com" and
    -- "user@example.com" cannot become two accounts. Uniqueness is therefore
    -- a genuine duplicate-email guarantee rather than a case-sensitivity
    -- accident.
    email              VARCHAR(320)    NOT NULL UNIQUE,
    email_verified     BOOLEAN         NOT NULL DEFAULT FALSE,

    -- Null for accounts created through Google OAuth, which never has a
    -- password. A NOT NULL password column would force a fake hash to exist
    -- for those accounts, which is a much worse outcome.
    password_hash      VARCHAR(255)    NULL,

    display_name       VARCHAR(120)    NOT NULL,

    -- Bumped whenever the password changes. JWTs issued before the bump are
    -- rejected by comparing issuedAt against it. Without this, a password
    -- change would not invalidate existing sessions.
    credentials_version INTEGER        NOT NULL DEFAULT 1,

    status              VARCHAR(32)      NOT NULL DEFAULT 'PENDING_VERIFICATION',
    role               VARCHAR(32)      NOT NULL DEFAULT 'USER',

    -- Google linkage. subject is Google's stable user id.
    google_subject     VARCHAR(255)    NULL UNIQUE,

    failed_login_count INTEGER         NOT NULL DEFAULT 0,
    locked_until       TIMESTAMPTZ     NULL,
    last_login_at      TIMESTAMPTZ     NULL,

    created_at         TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ     NOT NULL DEFAULT now(),

    -- A locally-registered account MUST have a password hash. A Google-only
    -- account MUST NOT. This constraint makes "a password exists but should
    -- not" or "no password exists but the account expects one" impossible to
    -- persist, whatever the calling code believes.
    CONSTRAINT ck_auth_user_credentials CHECK (
        (google_subject IS NULL     AND password_hash IS NOT NULL) OR
        (google_subject IS NOT NULL AND password_hash IS NULL) OR
        (google_subject IS NOT NULL AND password_hash IS NOT NULL)
    ),

    -- An email that has been verified through Google must be marked verified.
    CONSTRAINT ck_auth_user_email_verified CHECK (NOT email_verified OR google_subject IS NOT NULL OR password_hash IS NOT NULL)
);

-- The database refuses a third role and a fifth status. Application-level
-- validation is a second, independent check, not the only one.
ALTER TABLE auth_user ADD CONSTRAINT ck_auth_user_role_allowed
    CHECK (role IN ('USER', 'ADMIN'));

ALTER TABLE auth_user ADD CONSTRAINT ck_auth_user_status_allowed
    CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DEACTIVATED'));

-- Login and the duplicate-email check both look up by email.
CREATE INDEX ix_auth_user_email_lower ON auth_user (lower(email));
-- Account listing and suspension filters.
CREATE INDEX ix_auth_user_status ON auth_user (status);
CREATE INDEX ix_auth_user_role ON auth_user (role);

-- ---------------------------------------------------------------------
-- Refresh tokens, stored HASHED.
--
-- family_id groups a rotation chain. Presenting an already-rotated token is
-- the signal that a token was stolen, because the legitimate holder would
-- always use the newest one. The service then revokes the entire family.
-- ---------------------------------------------------------------------
CREATE TABLE refresh_token (
    id          UUID        PRIMARY KEY,
    family_id   UUID        NOT NULL,

    -- SHA-256 of the token, hex encoded. Not the token itself.
    token_hash  VARCHAR(64) NOT NULL UNIQUE,

    user_id     UUID        NOT NULL REFERENCES auth_user (id) ON DELETE CASCADE,

    issued_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ NULL,
    -- Where the token came from. Recorded so an auditor can tell a password
    -- session from an OAuth session without inferring it.
    source      VARCHAR(16) NOT NULL DEFAULT 'PASSWORD',

    CONSTRAINT ck_refresh_token_source CHECK (source IN ('PASSWORD', 'GOOGLE'))
);

CREATE INDEX ix_refresh_token_user   ON refresh_token (user_id);
CREATE INDEX ix_refresh_token_family ON refresh_token (family_id);
CREATE INDEX ix_refresh_token_active ON refresh_token (token_hash) WHERE revoked_at IS NULL;

-- ---------------------------------------------------------------------
-- Revoked access-token ids, kept until the token would have expired anyway.
--
-- Redis holds this in production for speed (docs/ARCHITECTURE.md section 4).
-- The table is the durable record, so a Redis flush cannot silently un-revoke
-- a token.
-- ---------------------------------------------------------------------
CREATE TABLE token_denylist (
    jti         VARCHAR(64)  PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES auth_user (id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    reason      VARCHAR(64)  NOT NULL
);

CREATE INDEX ix_token_denylist_expiry ON token_denylist (expires_at);

-- ---------------------------------------------------------------------
-- Authentication events, for lockout decisions and for the audit trail.
--
-- IP and user agent are recorded because credential-stuffing detection needs
-- them. Neither is a secret, and neither is prompt or document content.
-- ---------------------------------------------------------------------
CREATE TABLE auth_event (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      UUID         NULL REFERENCES auth_user (id) ON DELETE SET NULL,
    event_type   VARCHAR(32)  NOT NULL,
    success      BOOLEAN      NOT NULL,
    ip_address   VARCHAR(64)  NULL,
    user_agent   VARCHAR(512) NULL,
    occurred_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_auth_event_type CHECK (event_type IN (
        'REGISTER', 'LOGIN', 'LOGIN_FAILED', 'LOGOUT', 'TOKEN_REFRESH',
        'TOKEN_REUSE_DETECTED', 'PASSWORD_CHANGED', 'PASSWORD_RESET_REQUESTED',
        'PASSWORD_RESET_COMPLETED', 'EMAIL_VERIFIED', 'GOOGLE_LINKED',
        'ACCOUNT_SUSPENDED', 'ACCOUNT_REINSTATED'
    ))
);

CREATE INDEX ix_auth_event_user      ON auth_event (user_id, occurred_at DESC);
CREATE INDEX ix_auth_event_type_time ON auth_event (event_type, occurred_at DESC);
-- Lockout counts recent failures; this index makes that a bounded scan.
CREATE INDEX ix_auth_event_failures  ON auth_event (event_type, user_id, occurred_at DESC) WHERE success = FALSE;

-- ---------------------------------------------------------------------
-- One-time tokens: email verification and password reset.
--
-- Stored as a SHA-256 hash, so read access to this table is not enough to
-- verify anyone's email or reset anyone's password.
--
-- purpose separates the two flows so a verification token can never be
-- replayed as a password reset.
-- ---------------------------------------------------------------------
CREATE TABLE one_time_token (
    id           UUID        PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES auth_user (id) ON DELETE CASCADE,

    token_hash   VARCHAR(64) NOT NULL,
    purpose      VARCHAR(32) NOT NULL,

    issued_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ NOT NULL,
    consumed_at  TIMESTAMPTZ NULL,

    -- How many times this token has been tried. A correct token is consumed on
    -- first use; an incorrect one increments this. A high count on one token
    -- is a brute-force signal.
    attempt_count INTEGER    NOT NULL DEFAULT 0,

    CONSTRAINT ck_one_time_token_purpose CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
    -- One live token per purpose per user. A newer request invalidates the
    -- older one, so a reset link sent yesterday cannot work after a new
    -- request today.
    CONSTRAINT uq_one_time_token_live UNIQUE (user_id, purpose, consumed_at)
);

CREATE INDEX ix_one_time_token_hash   ON one_time_token (token_hash);
CREATE INDEX ix_one_time_token_expiry ON one_time_token (expires_at);

-- ---------------------------------------------------------------------
-- Outbound event log.
--
-- auth.user.registered.v1 is produced to Kafka (docs/SERVICE_CONTRACTS.md
-- section 12). This table is the transactional outbox: the account row and the
-- event are committed together, so the event cannot be lost by a crash between
-- the two, and cannot claim an account that was rolled back.
--
-- It exists in Phase 1 because auth-service OWNS the account-creation fact. It
-- is not a shared business-logic module; it is this service's own durability.
-- ---------------------------------------------------------------------
CREATE TABLE outbox_event (
    id             UUID        PRIMARY KEY,
    topic          VARCHAR(128) NOT NULL,
    event_type     VARCHAR(128) NOT NULL,
    -- version is part of the Kafka topic name, and is the schema version.
    event_version  INTEGER     NOT NULL DEFAULT 1,
    aggregate_id   UUID        NULL,
    payload        JSONB       NOT NULL,
    correlation_id VARCHAR(64) NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Null until published. A non-null value means the broker accepted it.
    published_at   TIMESTAMPTZ NULL,
    attempts       INTEGER     NOT NULL DEFAULT 0,
    last_error     VARCHAR(512) NULL
);

CREATE INDEX ix_outbox_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;