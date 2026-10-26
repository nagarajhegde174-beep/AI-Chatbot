#!/bin/bash
# =====================================================================
# NexaAI Kafka Topic Provisioning — KRaft mode
#
# Creates core domain event topics and dead-letter topics (DLT) in Kafka.
# ZooKeeper is prohibited and absent.
# =====================================================================

set -e

BOOTSTRAP_SERVER="${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}"

echo "Connecting to Kafka cluster (KRaft) at ${BOOTSTRAP_SERVER}..."

topics=(
  "auth.user.registered.v1:3"
  "auth.user.registered.v1.DLT:3"
  "auth.user.email_verified.v1:3"
  "auth.user.email_verified.v1.DLT:3"
  "auth.user.login.v1:3"
  "user.profile.updated.v1:3"
  "user.status.changed.v1:3"
  "user.account.deleted.v1:3"
  "chat.conversation.created.v1:3"
  "chat.message.sent.v1:6"
  "chat.message.sent.v1.DLT:6"
  "chat.message.completed.v1:6"
  "ai.inference.requested.v1:6"
  "ai.inference.completed.v1:6"
  "document.uploaded.v1:3"
  "document.processed.v1:3"
  "payment.completed.v1:3"
  "payment.failed.v1:3"
  "subscription.changed.v1:3"
)

for item in "${topics[@]}"; do
  topic="${item%%:*}"
  partitions="${item##*:}"
  echo "Ensuring topic: ${topic} (partitions: ${partitions})"
  /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server "${BOOTSTRAP_SERVER}" \
    --create --if-not-exists \
    --topic "${topic}" \
    --partitions "${partitions}" \
    --replication-factor 1
done

echo "All NexaAI KRaft Kafka topics successfully provisioned."
