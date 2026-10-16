# CI/CD

Phase 0 CI. Three workflows, each with one job.

| Workflow | Trigger | What it does |
|---|---|---|
| [`rules.yml`](rules.yml) | every push | Architecture rules: monolith, fake microservices, shared database access, ZooKeeper, Tailwind, secrets, roles, live payments, phase awareness |
| [`frontend.yml`](frontend.yml) | changes under `frontend/` | Lint, type-check, production build, bundle secret scan, bundle budget |
| [`backend.yml`](backend.yml) | changes under `backend/` | Builds and tests **only the services that exist** |

---

## Phase awareness

**The rule that shapes all three workflows: CI validates only artifacts that actually exist.**

- `backend.yml` **discovers** which services have a `pom.xml` and builds exactly those. In
  Phase 0 the set is empty, and the workflow says so in its job summary rather than reporting a
  pass that implies application code was verified.
- No workflow requires a future service to compile.
- No workflow builds a Docker image, because no service has a Dockerfile yet. An image-build
  workflow is added in the phase where the first service exists.
- Rule checks that describe structure validate the **plan**. Rule checks that describe content
  validate only what is **present**.

This is [ADR-016](../../docs/DECISIONS.md). The alternative — a hardcoded matrix of eight
services — is guaranteed red until Phase 12, and the only way to make it green is eight
`--exit-zero` steps, which is worse than a red pipeline.

---

## Honesty

From [`../../docs/RULES.md`](../../docs/RULES.md) §11:

- No `--exit-zero`. No skipped check. No relaxed assertion.
- A job that finds nothing to do **says so explicitly** in its output and in `$GITHUB_STEP_SUMMARY`.
- A disabled test is a build failure, not a comment.
- Infrastructure checks assert. None of them warn.

The `backend.yml` `not-implemented` job is the clearest example. It passes, and its output says
in as many words that no application code was built or tested. A green pipeline that hid that
fact would be worse than a red one.

---

## Rule checks

`rules.yml` enforces, each naming the rule in `docs/RULES.md`:

| Check | Rule |
|---|---|
| R1 | The eight service directories exist and are documented |
| R1b | No `backend/pom.xml`, no `<modules>`, no intra-repository parent |
| R2 | No service POM depends on another service, by artifactId or relativePath |
| R3 | No placeholder Java or Kotlin code in any service (Phase 0) |
| R3b | No Dockerfile for a service that does not exist |
| R3c | A service that **is** implemented has its pom, port, config, tests and Dockerfile |
| R4 | Each service names only its own database |
| R5 | `ai-service` and `api-gateway` have no datasource, JPA or Flyway |
| R6 | No ZooKeeper in code or configuration; no ZooKeeper image or service |
| R7 | Kafka is in KRaft mode |
| R8 | No Tailwind; Bootstrap and Sass present; no Next.js, Vue or Angular |
| R9 | No credential-shaped string, no tracked `.env`, no tracked key material |
| R10 | No role outside `USER` and `ADMIN` |
| R11 | No `rzp_live_` key and no live-mode variable anywhere |
| R12 | The Compose file parses |
| R13 | All ten documents exist and `docs/` is tracked |
| R14 | No credentials in the frontend, and no `VITE_*` variable that could hold one |

R3 and R3c are deliberately inverses of each other: the first forbids placeholder code, the
second requires completeness once a service is real. Together they prevent both failure modes —
an empty service that pretends to exist, and a half-built one that slips through.

---

## What is **not** verified in CI

Stated here so it is not mistaken for a pass
([`../../docs/MEMORY.md`](../../docs/MEMORY.md) §6.1):

| | |
|---|---|
| Compose stack starts | **No.** No Docker daemon in the development environment. `config` parses; nothing is started. |
| Database isolation script | **No.** Never executed. The strongest architectural guarantee is currently argued, not demonstrated. |
| `redis.conf` loads | **No.** |
| `nexaai.conf` parses | **No.** |
| Frontend Dockerfile builds | **No.** |
| Spring Boot / Spring AI versions | **No.** No POM exists, so no version has been resolved against Maven Central. |

Starting the stack and running both SQL scripts is the first task of Phase 1.

---

## Adding a service

When a service is implemented, in its own phase:

1. Add its entry to `infrastructure/docker-compose.yml`, with its own port and **only its own
   database credentials**.
2. `backend.yml` discovers it automatically. **No workflow change is needed.**
3. R3c starts enforcing that it has a pom, port, config, tests and Dockerfile.
4. Add a Docker image build job once the first service exists.