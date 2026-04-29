#!/usr/bin/env bash
set -euo pipefail

: "${KAFKA_BOOTSTRAP_SERVERS:?KAFKA_BOOTSTRAP_SERVERS is required}"

TOPICS=(
  "auth.user-registered.v1"
  "auth.user-email-changed.v1"
  "auth.user-username-changed.v1"
  "auth.user-banned.v1"
  "auth.user-unbanned.v1"
  "schedule.override-created.v1"
  "schedule.lesson-cancelled.v1"
  "schedule.lesson-replaced.v1"
  "schedule.extra-lesson-created.v1"
  "assignment.assignment-created.v1"
  "assignment.assignment-updated.v1"
  "assignment.assignment-opened.v1"
  "assignment.assignment-submitted.v1"
  "assignment.grade-assigned.v1"
  "academic.deadline-missed.v1"
  "testing.test-published.v1"
  "testing.test-started.v1"
  "testing.test-completed.v1"
  "education.topic-opened.v1"
  "content.lecture-opened.v1"
)

for topic in "${TOPICS[@]}"; do
  kafka-topics \
    --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" \
    --create \
    --if-not-exists \
    --topic "$topic" \
    --partitions 3 \
    --replication-factor 1
done

echo "Kafka topics are initialized."
