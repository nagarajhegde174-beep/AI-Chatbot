-- =====================================================================
-- NexaAI local development bootstrap.
--
-- Creates one database AND one role per service. The role is granted on
-- exactly one database, so a service that tries to read another service's
-- data fails with a permission error instead of quietly succeeding.
--
-- This is the mechanism that enforces the rule in docs/RULES.md section 3.
-- There is no role that can see more than one service's tables.
--
-- The AI Service and the API Gateway own no database and therefore have no
-- role here at all.
-- =====================================================================

\set ON_ERROR_STOP on

-- ---------------------------------------------------------------------
-- Roles
-- ---------------------------------------------------------------------
-- Passwords are placeholders for local development only. Real environments
-- inject credentials from the deployment pipeline, never from this file.
-- ---------------------------------------------------------------------

CREATE ROLE nexa_auth            LOGIN PASSWORD 'nexa_auth_local_pw';
CREATE ROLE nexa_user            LOGIN PASSWORD 'nexa_user_local_pw';
CREATE ROLE nexa_chat            LOGIN PASSWORD 'nexa_chat_local_pw';
CREATE ROLE nexa_document        LOGIN PASSWORD 'nexa_document_local_pw';
CREATE ROLE nexa_rag             LOGIN PASSWORD 'nexa_rag_local_pw';
CREATE ROLE nexa_subscription    LOGIN PASSWORD 'nexa_subscription_local_pw';

-- A read-only role used by ad-hoc inspection during development. It still
-- cannot cross a service boundary, because no role may.
CREATE ROLE nexa_readonly        LOGIN PASSWORD 'nexa_readonly_local_pw';

-- ---------------------------------------------------------------------
-- Databases, each owned by exactly one service role
-- ---------------------------------------------------------------------

CREATE DATABASE nexa_auth         OWNER nexa_auth;
CREATE DATABASE nexa_user         OWNER nexa_user;
CREATE DATABASE nexa_chat         OWNER nexa_chat;
CREATE DATABASE nexa_document     OWNER nexa_document;
CREATE DATABASE nexa_rag          OWNER nexa_rag;
CREATE DATABASE nexa_subscription OWNER nexa_subscription;

-- ---------------------------------------------------------------------
-- Lock the doors
-- ---------------------------------------------------------------------
-- By default a role can connect to any database it has been granted. Revoke
-- the PUBLIC grant so only the owner of a database can reach it, then give
-- the explicit grants back one service at a time.
-- ---------------------------------------------------------------------

REVOKE ALL ON DATABASE nexa_auth         FROM PUBLIC;
REVOKE ALL ON DATABASE nexa_user         FROM PUBLIC;
REVOKE ALL ON DATABASE nexa_chat         FROM PUBLIC;
REVOKE ALL ON DATABASE nexa_subscription FROM PUBLIC;
REVOKE CONNECT ON DATABASE nexa_document FROM PUBLIC;
REVOKE CONNECT ON DATABASE nexa_rag      FROM PUBLIC;

-- The document and vector stores are reachable only by their owner service
-- and by the development inspection role.
GRANT CONNECT ON DATABASE nexa_document TO nexa_readonly;
GRANT CONNECT ON DATABASE nexa_rag      TO nexa_readonly;

-- ---------------------------------------------------------------------
-- Per-database grants
-- ---------------------------------------------------------------------
-- Runs inside each database so PUBLIC is revoked on that database's own
-- schemas, not just at the cluster level.
-- ---------------------------------------------------------------------

\connect nexa_auth
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT ALL ON SCHEMA public TO nexa_auth;
ALTER SCHEMA public OWNER TO nexa_auth;

\connect nexa_user
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT ALL ON SCHEMA public TO nexa_user;
ALTER SCHEMA public OWNER TO nexa_user;

\connect nexa_chat
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT ALL ON SCHEMA public TO nexa_chat;
ALTER SCHEMA public OWNER TO nexa_chat;

\connect nexa_document
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT ALL ON SCHEMA public TO nexa_document;
ALTER SCHEMA public OWNER TO nexa_document;
GRANT USAGE ON SCHEMA public TO nexa_readonly;

\connect nexa_rag
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT ALL ON SCHEMA public TO nexa_rag;
ALTER SCHEMA public OWNER TO nexa_rag;
GRANT USAGE ON SCHEMA public TO nexa_readonly;

-- pgvector is loaded ONLY here. No other NexaAI database loads the extension.
CREATE EXTENSION IF NOT EXISTS vector;

\connect nexa_subscription
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT ALL ON SCHEMA public TO nexa_subscription;
ALTER SCHEMA public OWNER TO nexa_subscription;
