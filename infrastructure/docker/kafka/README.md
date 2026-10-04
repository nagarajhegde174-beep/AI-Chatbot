# Apache Kafka for NexaAI — KRaft mode only

Kafka runs in **KRaft mode**. The broker is its own controller. There is no ZooKeeper container,
no `KAFKA_ZOOKEEPER_CONNECT`, no `ZOOKEEPER_*` variable and no ZooKeeper client dependency
anywhere in this repository. ZooKeeper is prohibited by [`../../../docs/RULES.md`](../../../docs/RULES.md) §4.

## Topology

| | |
|---|---|
| Node | `kafka-1` |
| Broker id | `1` |
| Roles | `broker,controller` — a combined node, which is the recommended single-node KRaft setup |
| Controller quorum voters | `1@kafka:29093` |
| Cluster id | Fixed for local development so the volume can be reused; generated per environment in a real deployment |

## Listeners

| Listener | Address | Used by |
|---|---|---|
| `PLAINTEXT` (internal) | `kafka:29092` | Every NexaAI service, over the internal Compose network |
| `EXTERNAL` (host) | `localhost:9092` | Local tooling only: `kafka-topics`, a console, debugging |
| `CONTROLLER` | `kafka:29093` | KRaft controller quorum, never used for client traffic |

Services use the internal listener. Nothing reaches Kafka from the browser, and port `9092` is
published to the host only, never to the outside world.

## Single-node settings

These are **local development only** and must be raised in any real deployment:

| Setting | Local value | Production expectation |
|---|---|---|
| `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR` | `1` | `3` |
| `KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR` | `1` | `3` |
| `KAFKA_TRANSACTION_STATE_LOG_MIN_ISR` | `1` | `2` |
| `KAFKA_DEFAULT_REPLICATION_FACTOR` | `1` | `3` |
| Broker count | `1` | `3` or more, across nodes |

Tracked as a known gap in [`../../../docs/SECURITY.md`](../../../docs/SECURITY.md) §13 and
scheduled for Phase 11.

## Topic ownership

One producer owns each topic and creates it. A consumer never writes to a topic it does not own.
The authoritative list, with partitions, keys, consumers and payload schemas, is
[`../../../docs/SERVICE_CONTRACTS.md`](../../../docs/SERVICE_CONTRACTS.md) §12.

Topic creation is left to each service's `NewTopic` beans rather than to an external script,
because the producer is the party accountable for its schema
([`../../../docs/ARCHITECTURE.md`](../../../docs/ARCHITECTURE.md) ADR-020).

## When topics are actually created

**No topic exists yet.** No service is implemented, so no producer has declared anything
([`../../../docs/ARCHITECTURE.md`](../../../docs/ARCHITECTURE.md)). The topic list in the contracts document is
a specification, not a description of a running cluster.

## Useful commands

```bash
# List topics
docker compose -f infrastructure/docker-compose.yml --profile infra exec kafka \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:29092 --list

# Describe one topic, showing partition count and leader
docker compose -f infrastructure/docker-compose.yml --profile infra exec kafka \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:29092 \
  --describe --topic chat.message.completed.v1

# Tail a topic from the host
docker compose -f infrastructure/docker-compose.yml --profile infra exec kafka \
  /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:29092 \
  --topic document.indexed.v1 --from-beginning --max-messages 5

# Confirm the mode really is KRaft: this must print the KRaft metadata, and there
# must be no ZooKeeper process in the container.
docker compose -f infrastructure/docker-compose.yml --profile infra exec kafka \
  /opt/kafka/bin/kafka-metadata-quorum.sh --bootstrap-server localhost:29092 describe --status
```

## Note on the image

The official `apache/kafka` image is used because it runs KRaft natively with no wrapper process.
Topics, ACLs and configs live in the container's own storage; deleting the volume resets the
cluster, which is the expected behaviour for a local development broker.

## STATUS: NOT YET STARTED

This configuration has not been run. There was no Docker daemon available when Phase 0 was
written. The Compose file is validated by parsing only
([`../../../docs/ARCHITECTURE.md`](../../../docs/ARCHITECTURE.md) §6.1).