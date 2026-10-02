-- =====================================================================
-- NexaAI database ownership verification.
--
-- Proves the rule in docs/RULES.md section 3 actually holds in PostgreSQL,
-- rather than being a convention nobody checks: a service role must NOT be
-- able to CONNECT to another service's database.
--
-- Run as a superuser (the container's POSTGRES_USER, nexa_admin):
--
--   docker compose -f infrastructure/docker-compose.yml exec -T postgres \
--     psql -U nexa_admin -d nexa_admin -f /verify/02-verify-ownership.sql
--
-- DESIGN NOTE, and the reason this file is written the way it is: these
-- checks query privileges with has_database_privilege() instead of attempting
-- a connection and hoping it fails. An earlier draft used the "expected to
-- FAIL" approach against pg_catalog.pg_database, which was wrong: pg_database
-- is a cluster-wide catalog readable by every role, so that query would have
-- SUCCEEDED and reported a false pass. A security check that can silently pass
-- is worse than no check.
--
-- Every assertion below is deterministic and self-describing: the expected
-- value is printed next to the actual value, so a human reading the output can
-- see the claim, not just the verdict.
--
-- STATUS: this script has NOT been executed yet. There was no Docker daemon
-- available when Phase 0 was written. See docs/MEMORY.md section 6.1.
-- =====================================================================

\pset border 2

\echo ''
\echo '== 1. A service role must NOT be able to connect to another service database =='
\echo ''

SELECT r AS service_role,
       'nexa_auth' AS target_database,
       has_database_privilege(r, 'nexa_auth', 'CONNECT') AS can_connect,
       false AS expected,
       CASE WHEN has_database_privilege(r, 'nexa_auth', 'CONNECT')
            THEN 'VIOLATION' ELSE 'ok' END AS verdict
FROM unnest(ARRAY['nexa_user','nexa_chat','nexa_document','nexa_rag','nexa_subscription']) AS r

UNION ALL

SELECT r, 'nexa_user', has_database_privilege(r, 'nexa_user', 'CONNECT'), false,
       CASE WHEN has_database_privilege(r, 'nexa_user', 'CONNECT') THEN 'VIOLATION' ELSE 'ok' END
FROM unnest(ARRAY['nexa_auth','nexa_chat','nexa_document','nexa_rag','nexa_subscription']) AS r

UNION ALL

SELECT r, 'nexa_chat', has_database_privilege(r, 'nexa_chat', 'CONNECT'), false,
       CASE WHEN has_database_privilege(r, 'nexa_chat', 'CONNECT') THEN 'VIOLATION' ELSE 'ok' END
FROM unnest(ARRAY['nexa_auth','nexa_user','nexa_document','nexa_rag','nexa_subscription']) AS r

UNION ALL

SELECT r, 'nexa_document', has_database_privilege(r, 'nexa_document', 'CONNECT'), false,
       CASE WHEN has_database_privilege(r, 'nexa_document', 'CONNECT') THEN 'VIOLATION' ELSE 'ok' END
FROM unnest(ARRAY['nexa_auth','nexa_user','nexa_chat','nexa_rag','nexa_subscription']) AS r

UNION ALL

SELECT r, 'nexa_rag', has_database_privilege(r, 'nexa_rag', 'CONNECT'), false,
       CASE WHEN has_database_privilege(r, 'nexa_rag', 'CONNECT') THEN 'VIOLATION' ELSE 'ok' END
FROM unnest(ARRAY['nexa_auth','nexa_user','nexa_chat','nexa_document','nexa_subscription']) AS r

UNION ALL

SELECT r, 'nexa_subscription', has_database_privilege(r, 'nexa_subscription', 'CONNECT'), false,
       CASE WHEN has_database_privilege(r, 'nexa_subscription', 'CONNECT') THEN 'VIOLATION' ELSE 'ok' END
FROM unnest(ARRAY['nexa_auth','nexa_user','nexa_chat','nexa_document','nexa_rag']) AS r

ORDER BY target_database, service_role;

\echo ''
\echo '== 2. Each service role MUST be able to connect to its own database =='
\echo ''

SELECT t.owning_service,
       t.service_role,
       has_database_privilege(t.service_role, t.service_role, 'CONNECT') AS can_connect,
       true AS expected,
       CASE WHEN has_database_privilege(t.service_role, t.service_role, 'CONNECT')
            THEN 'ok' ELSE 'VIOLATION' END AS verdict
FROM (VALUES
  ('nexa_auth',         'auth-service'),
  ('nexa_user',         'user-service'),
  ('nexa_chat',         'chat-service'),
  ('nexa_document',     'document-service'),
  ('nexa_rag',          'rag-service'),
  ('nexa_subscription', 'subscription-service')
) AS t(service_role, owning_service)
ORDER BY t.owning_service;

\echo ''
\echo '== 3. PUBLIC must hold no CONNECT privilege on any service database =='
\echo ''

SELECT datname,
       has_database_privilege('public', datname, 'CONNECT') AS public_can_connect,
       false AS expected,
       CASE WHEN has_database_privilege('public', datname, 'CONNECT')
            THEN 'VIOLATION' ELSE 'ok' END AS verdict
FROM pg_database
WHERE datname LIKE 'nexa\_%'
ORDER BY datname;

\echo ''
\echo '== 4. Database owners must be the expected service role =='
\echo ''

SELECT d.datname AS database,
       pg_get_userbyid(d.datdba) AS owner,
       t.owning_service,
       CASE WHEN pg_get_userbyid(d.datdba) = t.service_role
            THEN 'ok' ELSE 'VIOLATION' END AS verdict
FROM pg_database d
JOIN (VALUES
  ('nexa_auth',         'auth-service'),
  ('nexa_user',         'user-service'),
  ('nexa_chat',         'chat-service'),
  ('nexa_document',     'document-service'),
  ('nexa_rag',          'rag-service'),
  ('nexa_subscription', 'subscription-service')
) AS t(service_role, owning_service) ON t.service_role = d.datname
ORDER BY t.owning_service;

\echo ''
\echo '== 5. The AI Service and the API Gateway must own no database =='
\echo ''
\echo 'Expected: an empty result set. Roles nexa_ai and nexa_gateway must not exist.'

SELECT rolname, 'VIOLATION: a stateless service must not have a database role' AS problem
FROM pg_roles
WHERE rolname IN ('nexa_ai', 'nexa_gateway');

\echo ''
\echo '== 6. pgvector must be installed ONLY in nexa_rag =='
\echo ''

\connect nexa_rag
SELECT 'nexa_rag' AS database, extname, extversion, 'ok' AS verdict
FROM pg_extension WHERE extname = 'vector';

\connect nexa_auth
SELECT 'nexa_auth' AS database, count(*) AS vector_extensions,
       CASE WHEN count(*) = 0 THEN 'ok' ELSE 'VIOLATION' END AS verdict
FROM pg_extension WHERE extname = 'vector';

\connect nexa_user
SELECT 'nexa_user' AS database, count(*) AS vector_extensions,
       CASE WHEN count(*) = 0 THEN 'ok' ELSE 'VIOLATION' END AS verdict
FROM pg_extension WHERE extname = 'vector';

\connect nexa_chat
SELECT 'nexa_chat' AS database, count(*) AS vector_extensions,
       CASE WHEN count(*) = 0 THEN 'ok' ELSE 'VIOLATION' END AS verdict
FROM pg_extension WHERE extname = 'vector';

\connect nexa_document
SELECT 'nexa_document' AS database, count(*) AS vector_extensions,
       CASE WHEN count(*) = 0 THEN 'ok' ELSE 'VIOLATION' END AS verdict
FROM pg_extension WHERE extname = 'vector';

\connect nexa_subscription
SELECT 'nexa_subscription' AS database, count(*) AS vector_extensions,
       CASE WHEN count(*) = 0 THEN 'ok' ELSE 'VIOLATION' END AS verdict
FROM pg_extension WHERE extname = 'vector';

\echo ''
\echo '== Verification complete. Every row above must read ok. =='
\echo ''