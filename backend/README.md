# backend/ — Eight independent Spring Boot services

**Phase 0: no code exists here.** Each directory holds a README describing its boundary, port,
database and planned responsibility
([`../../docs/DECISIONS.md`](../../docs/DECISIONS.md) ADR-015, [`../../docs/RULES.md`](../../docs/RULES.md) §10).

There is deliberately **no `backend/pom.xml`** and no aggregator of any kind
([`../../docs/DECISIONS.md`](../../docs/DECISIONS.md) ADR-012).

## The services

| Directory | Port | Database | Phase |
|---|:--:|---|:--:|
| [`api-gateway`](api-gateway) | 8080 | *none* | 3 |
| [`auth-service`](auth-service) | 8081 | `nexa_auth` | 1 |
| [`user-service`](user-service) | 8082 | `nexa_user` | 2 |
| [`chat-service`](chat-service) | 8083 | `nexa_chat` | 5 |
| [`ai-service`](ai-service) | 8084 | *none* | 4 |
| [`document-service`](document-service) | 8085 | `nexa_document` | 6 |
| [`rag-service`](rag-service) | 8086 | `nexa_rag` | 7 |
| [`subscription-service`](subscription-service) | 8087 | `nexa_subscription` | 9 |

## When a service is implemented

It gets its own, and nothing outside its own directory:

```
backend/<service>/
├── pom.xml                            parent = spring-boot-starter-parent, no <relativePath>
├── Dockerfile
├── README.md
├── .dockerignore
└── src/
    ├── main/java/com/nexaai/<pkg>/     one @SpringBootApplication
    ├── main/resources/
    │   ├── application.yml             its own port
    │   └── db/migration/               its own Flyway migrations
    └── test/java/com/nexaai/<pkg>/     its own tests
```

Then it builds on its own, with nothing else present:

```bash
cd backend/<service>
mvn -B clean verify
```

## The rules that apply to every service

Full text in [`../../docs/RULES.md`](../../docs/RULES.md). The short version:

1. **No aggregator POM.** Each service's parent comes from Maven Central.
2. **No dependency on another service.** Not a jar, not a sibling module, not a relative path.
3. **No shared business logic.** Contract types are duplicated deliberately; the shared artefact
   is [`../../docs/SERVICE_CONTRACTS.md`](../../docs/SERVICE_CONTRACTS.md), not a jar.
4. **One database, one role.** Never read another service's database.
5. **No ZooKeeper.** Kafka is KRaft.
6. **No secrets in source.** Environment variables only.
7. **No extra roles.** Exactly `USER` and `ADMIN`.
8. **No placeholder code.** If a service is not implemented, its directory stays empty.

## Port allocation is fixed

Ports are allocated in [`../../docs/ARCHITECTURE.md`](../../docs/ARCHITECTURE.md) §1.2 and
referenced by the route table in
[`../../docs/SERVICE_CONTRACTS.md`](../../docs/SERVICE_CONTRACTS.md) §4.2, by the Compose file
and by CI. A service that invents its own port will be inconsistent with all four.