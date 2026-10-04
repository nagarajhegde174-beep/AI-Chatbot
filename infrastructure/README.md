# NexaAI infrastructure

Local development infrastructure for NexaAI: PostgreSQL with pgvector, Redis, Kafka in KRaft
mode, and an NGINX edge.

**Phase 0: only these four exist.** The eight backend services are added here as they are
implemented, each in its own phase. See [`../../docs/ARCHITECTURE.md`](../../docs/ARCHITECTURE.md).

---

## Layout

```
infrastructure/
├── .env.example              empty placeholders only
├── docker-compose.yml         Postgres, Redis, Kafka, NGINX
└── docker/
    ├── postgres/
    │   ├── README.md          what is created and how to verify it
    │   ├── init/01-create-databases.sql
    │   └── verify/02-verify-ownership.sql
    ├── redis/redis.conf
    ├── kafka/README.md        KRaft topology
    └── nginx/
        ├── README.md
        └── nexaai.conf
```

---

## Start

```bash
cd infrastructure
cp .env.example .env          # local placeholders; real secrets stay out of git
docker compose --profile infra up -d
```

Brings up PostgreSQL (with pgvector), Redis and Kafka in KRaft mode, with one database and one
role per service.

The `edge` service is behind its own profile because its `upstream api-gateway` cannot resolve
until Phase 3:

```bash
docker compose --profile edge up -d      # expected to fail before Phase 3
```

### Reset

`init/` only runs on an empty data volume. To re-run it after editing the SQL:

```bash
docker compose --profile infra down -v
```

---

## Verify database isolation

This is the check that matters most, because one database and one role per service is the
strongest architectural guarantee in the design
([`../../docs/RULES.md`](../../docs/RULES.md) §3):

```bash
docker compose --profile infra exec -T postgres \
  psql -U nexa_admin -d nexa_admin -f /verify/02-verify-ownership.sql
```

Every row must read `ok`.

---

## Ports

| Service | Host port | Internal address |
|---|:--:|---|
| PostgreSQL | 5432 | `postgres:5432` |
| Redis | 6379 | `redis:6379` |
| Kafka (external, host tooling) | 9092 | — |
| Kafka (internal, services) | — | `kafka:29092` |
| NGINX edge | 8089 | `edge:80` |

Planned: frontend `8088`, gateway `8080`, services `8081`–`8087`.

---

## Rules this directory enforces

1. **No ZooKeeper.** Kafka runs in KRaft mode. A ZooKeeper service added here must fail the
   build.
2. **One database and one role per service.** No shared role that can see two databases.
3. **Redis is a cache.** Persistence is deliberately off, so "not the system of record" is
   enforced by configuration rather than by discipline.
4. **No secrets.** `.env.example` holds empty placeholders. `.env` is gitignored.
5. **No service that does not exist.** A Compose entry for an unimplemented service would be
   configuration that lies.

---

## STATUS: NOT YET EXECUTED

**The Docker daemon was not available when Phase 0 was written.** Therefore:

- `docker compose config` **parses** (verified), but nothing has been **started**.
- Neither SQL script has been **run**.
- `redis.conf` has not been **loaded**.
- `nexaai.conf` has not been **parsed** by NGINX.

Everything in this directory is reviewed but unverified at runtime. Starting the stack and
running both SQL scripts is the first task of Phase 1
([`../../docs/ARCHITECTURE.md`](../../docs/ARCHITECTURE.md) §6.1).