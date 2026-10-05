-- =====================================================================
-- V1: user-service initial schema.
--
-- Owns nexa_user and NOTHING else.
--
-- This service cannot read Auth Service's database: the role is granted on
-- exactly one database, so a cross-service query fails with a permission
-- error rather than quietly succeeding (docs/RULES.md section 3).
--
-- The database is referred to by its owning service throughout these files
-- rather than by name, because naming another service's database here would
-- trip the R4 architecture check -- which is the point of that check: it
-- makes a service that reaches sideways into somebody else's data fail the
-- build, including when it only appears in a comment.
--
-- THE MOST IMPORTANT THING IN THIS FILE:
--
--   No table here stores a credential. No password hash, no token, no
--   secret, and no copy of Auth Service's account_status beyond the
--   non-sensitive administrative projection this service needs.
--
-- This service learns that an account EXISTS from the
-- auth.user.registered.v1 event. It never learns the password, and it
-- cannot verify one, because it has no hash to verify against and must
-- not acquire one.
--
-- Design notes that matter:
--
--  * The external auth user id is the stable key. NexaAI's own surrogate
--    id is an internal implementation detail and is never exposed as an
--    identifier the outside world relies on.
--  * Account status here is an ADMINISTRATIVE PROJECTION of the status
--    auth-service owns. Duplicating it is deliberate: it is denormalised
--    read state, not a second source of truth. auth-service remains
--    authoritative and republishes on change.
--  * Every timestamp is timestamptz. A local-time timestamp is a bug
--    waiting to happen across services.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Users.
--
-- The profile. Created by consuming auth.user.registered.v1, which is the
-- ONLY way an account appears here: there is no self-registration in this
-- service. Registration belongs to auth-service (docs/ARCHITECTURE.md
-- section 13, ADR-001).
-- ---------------------------------------------------------------------
CREATE TABLE user_profile (
    -- NexaAI's internal surrogate key.
    id                UUID            PRIMARY KEY,

    -- The stable identifier issued by auth-service. Unique, because one
    -- auth account maps to exactly one profile. A user cannot acquire a
    -- second profile by re-registering with the same email, because the
    -- email itself is also unique.
    auth_user_id      UUID            NOT NULL UNIQUE,

    -- Retained for lookup and display. Authorisation NEVER keys on it: an
    -- email can change, the auth_user_id cannot.
    email             VARCHAR(320)    NOT NULL UNIQUE,

    display_name      VARCHAR(120)    NOT NULL,
    avatar_url        VARCHAR(1024)   NULL,

    -- Administrative projection of auth-service's status. NOT authoritative.
    account_status    VARCHAR(32)     NOT NULL DEFAULT 'PENDING_VERIFICATION',
    role              VARCHAR(32)     NOT NULL DEFAULT 'USER',

    -- Administrative note: why an account was suspended, visible to the
    -- account holder so a suspension is never unexplained.
    status_reason     VARCHAR(255)    NULL,
    status_changed_at TIMESTAMPTZ     NULL,
    status_changed_by UUID            NULL,

    -- Administrative projection, refreshed by subscription-service events.
    -- NULL means "no subscription", which is different from "free plan".
    plan_id           VARCHAR(64)     NULL,
    plan_name         VARCHAR(120)    NULL,

    email_verified    BOOLEAN         NOT NULL DEFAULT FALSE,
    registered_via    VARCHAR(16)     NOT NULL DEFAULT 'PASSWORD',

    created_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    -- Soft delete. A row is retained so an audit trail and a foreign key
    -- from another service's derived data keep resolving.
    deleted_at        TIMESTAMPTZ     NULL,

    -- Status and role are constrained in the database, not only in Java.
    -- An application bug must not be able to invent a role.
    CONSTRAINT ck_user_profile_status_allowed CHECK (
        account_status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DEACTIVATED')
    ),
    CONSTRAINT ck_user_profile_role_allowed CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_user_profile_registered_via CHECK (registered_via IN ('PASSWORD', 'GOOGLE'))
);

-- Admin listing filters and ordering.
CREATE INDEX ix_user_profile_status ON user_profile (account_status);
CREATE INDEX ix_user_profile_role ON user_profile (role);
CREATE INDEX ix_user_profile_created ON user_profile (created_at DESC);
-- Partial index for the common case: active accounts only.
CREATE INDEX ix_user_profile_active ON user_profile (email) WHERE deleted_at IS NULL;
-- Case-insensitive search over name and email.
CREATE INDEX ix_user_profile_email_lower ON user_profile (lower(email));
CREATE INDEX ix_user_profile_name_lower ON user_profile (lower(display_name));

-- ---------------------------------------------------------------------
-- Preferences and settings.
--
-- These live on user_profile rather than in a second table. They are always
-- read and written with the profile, in one request, and there is no
-- independent lifecycle: a preferences row that existed without its profile
-- would be meaningless. An embeddable in JPA maps them as columns of this
-- table, and they are prefixed so they cannot collide with the profile's own
-- updated_at.
--
-- Prefixing matters for a subtler reason too: it makes an accidental write to
-- the wrong group of columns visible in a schema diff instead of silent.
--
-- Preferences remain a SEPARATE API resource from the profile, so a
-- preferences request physically cannot carry a profile field.
-- ---------------------------------------------------------------------
ALTER TABLE user_profile ADD COLUMN pref_theme VARCHAR(16) NOT NULL DEFAULT 'system';
ALTER TABLE user_profile ADD COLUMN pref_locale VARCHAR(16) NOT NULL DEFAULT 'en';
ALTER TABLE user_profile ADD COLUMN pref_default_model VARCHAR(128) NULL;
ALTER TABLE user_profile ADD COLUMN pref_rag_enabled_by_default BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE user_profile ADD COLUMN pref_stream_responses BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE user_profile ADD COLUMN pref_email_notifications BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE user_profile ADD COLUMN pref_product_updates BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE user_profile ADD COLUMN pref_updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- Constrained in the database, not only in Java. Values are the enum NAMES, which is
-- what EnumType.STRING stores. The API speaks lowercase; the conversion happens in the
-- DTO, so the wire format and the stored format are allowed to differ.
ALTER TABLE user_profile ADD CONSTRAINT ck_user_profile_pref_theme
    CHECK (pref_theme IN ('LIGHT', 'DARK', 'SYSTEM'));

-- ---------------------------------------------------------------------
-- Status change history.
--
-- An append-only record of every administrative status change, with the
-- reason and the acting administrator. Audit, not business state: the
-- current status is the column on user_profile.
-- ---------------------------------------------------------------------
CREATE TABLE user_status_history (
    id            UUID        PRIMARY KEY,
    user_profile_id UUID      NOT NULL REFERENCES user_profile (id) ON DELETE CASCADE,
    from_status   VARCHAR(32) NULL,
    to_status     VARCHAR(32) NOT NULL,
    reason        VARCHAR(255) NULL,
    -- The administrator who made the change. NULL for a system change.
    changed_by    UUID        NULL,
    changed_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_user_status_history_to_allowed CHECK (
        to_status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DEACTIVATED')
    )
);

CREATE INDEX ix_user_status_history_user ON user_status_history (user_profile_id, changed_at DESC);

-- ---------------------------------------------------------------------
-- Processed event ids.
--
-- Kafka is at-least-once, so auth.user.registered.v1 can arrive twice. This
-- table is the deduplication key: a replayed event must not create a second
-- profile, a second preference row, or a second history entry
-- (docs/SERVICE_CONTRACTS.md section 12.2).
-- ---------------------------------------------------------------------
CREATE TABLE processed_event (
    event_id      UUID        PRIMARY KEY,
    event_type    VARCHAR(128) NOT NULL,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_processed_event_type ON processed_event (event_type, processed_at DESC);