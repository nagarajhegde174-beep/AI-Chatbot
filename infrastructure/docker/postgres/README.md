# PostgreSQL for NexaAI local development

One PostgreSQL container, but **not** one shared database. Each service gets its own database
and its own role, and a role is granted on exactly one database.

This is a local development convenience. Splitting into separate instances is a deployment
decision, not an architecture change — nothing in the application code knows they share a host
([`../../../docs/DECISIONS.md`](../../../docs/DECISIONS.md) ADR-002).

## Files

| File | Purpose |
|---|---|
| `init/01-create-databases.sql` | Creates the six roles and six databases, revokes `PUBLIC`, grants per service. Runs once, in order, on an empty data directory. |
| `verify/02-verify-ownership.sql` | Proves the isolation rule actually holds in PostgreSQL rather than being a convention. |

## What is created

| Database | Owner role | Service | pgvector |
|---|---|---|---|
| `nexa_auth` | `nexa_auth` | auth-service | no |
| `nexa_user` | `nexa_user` | user-service | no |
| `nexa_chat` | `nexa_chat` | chat-service | no |
| `nexa_document` | `nexa_document` | document-service | no |
| `nexa_rag` | `nexa_rag` | rag-service | **yes** |
| `nexa_subscription` | `nexa_subscription` | subscription-service | no |

The **AI Service** and the **API Gateway** own no database, so they have no role here. There is
deliberately no shared or cross-service role: a role that could see two databases would defeat
the entire rule.

`nexa_readonly` is a development convenience for ad-hoc inspection. It can `CONNECT` to the
document and vector stores only, and still cannot cross a service boundary.

## Why this enforces the rule

[`../../../docs/RULES.md`](../../../docs/RULES.md) §3 forbids a service from reading another
service's database. Two things enforce it:

1. **PostgreSQL grants.** A cross-service connection is refused by the server, so an accidental
   query fails loudly instead of quietly succeeding.
2. **Ownership of the schema.** Each service's Flyway migrations own that database's DDL, so no
   one else can alter it. A central migration runner is rejected for exactly this reason: it
   would need every service's credentials ([`../../../docs/DECISIONS.md`](../../../docs/DECISIONS.md) ADR-019).

## Verify the isolation

With the stack running:

```bash
docker compose -f infrastructure/docker-compose.yml exec -T postgres \
  psql -U nexa_admin -d nexa_admin -f /verify/02-verify-ownership.sql
```

The script prints the actual value next to the expected value for every check, and **every row
must read `ok`**:

1. No service role can `CONNECT` to another service's database.
2. Each service role **can** connect to its own database.
3. `PUBLIC` holds no `CONNECT` privilege on any service database.
4. Each database's owner is its own service role.
5. `nexa_ai` and `nexa_gateway` do not exist, because those services own no database.
6. The `vector` extension exists in `nexa_rag` and in no other database.

These assertions use `has_database_privilege()` rather than attempting a connection and hoping
it fails. An earlier draft used the "expect this to error" style against `pg_catalog.pg_database`,
which was **wrong**: that catalog is cluster-wide and readable by every role, so the query would
have succeeded and reported a false pass. A security check that can silently pass is worse than
no check.

## STATUS: NOT YET EXECUTED

Neither script has been run. There was no Docker daemon available when Phase 0 was written, so
both files have been reviewed but never executed. See
[`../../../docs/MEMORY.md`](../../../docs/MEMORY.md) §6.1.

Running these two scripts is the first task of Phase 1.

## Notes

- `init/` only runs when the data volume is empty. To re-run it after editing, reset the volume:
  `docker compose -f infrastructure/docker-compose.yml --profile infra down -v`.
- Migrations are **not** in this directory. Each service owns its Flyway migrations under
  `backend/<service>/src/main/resources/db/migration`, applied by that service at startup.
  Nobody edits another service's schema, not even by hand.
- The passwords here are local placeholders. Real environments inject credentials from the
  deployment pipeline; see [`../../../docs/RULES.md`](../../../docs/RULES.md) §6.
- The `pgvector/pgvector:pg17` image is used because the RAG Service needs the extension. Any
  host image with pgvector installed works; this one is the simplest.