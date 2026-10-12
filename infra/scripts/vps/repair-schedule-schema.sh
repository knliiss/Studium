#!/usr/bin/env bash
set -euo pipefail

# One-time, non-destructive repair for the first Studium demo deployment.
# PgBouncer transaction pooling can cause unqualified Flyway CREATE TABLE
# statements to execute in a schema different from the recorded Flyway history.
# Refuse ambiguous/partial layouts; never reset volumes or history.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
LOCAL_COMPOSE="$ROOT_DIR/infra/docker/docker-compose.local.yml"
VPS_COMPOSE="$ROOT_DIR/infra/docker/docker-compose.vps.yml"

[[ -f "$ENV_FILE" ]] || { echo "Missing .env" >&2; exit 1; }
set -a
source "$ENV_FILE"
set +a

compose() {
  docker compose --env-file "$ENV_FILE" -f "$LOCAL_COMPOSE" -f "$VPS_COMPOSE" "$@"
}

psql_query() {
  compose exec -T postgres psql -X -qAt -v ON_ERROR_STOP=1 \
    -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-postgres}" -c "$1"
}

tables="'academic_semesters','lesson_slots','rooms','room_capabilities','schedule_templates','schedule_overrides','teacher_debts','schedule_outbox_events'"
source_count="$(psql_query "SELECT count(*) FROM pg_tables WHERE schemaname='content' AND tablename IN ($tables);")"
schedule_count="$(psql_query "SELECT count(*) FROM pg_tables WHERE schemaname='schedule' AND tablename IN ($tables);")"
other_count="$(psql_query "SELECT count(*) FROM pg_tables WHERE schemaname NOT IN ('schedule','content') AND tablename IN ($tables);")"
history_count="$(psql_query "SELECT count(*) FROM schedule.flyway_schema_history WHERE version IN ('1','2') AND success=true;")"

echo "Schedule tables: content=$source_count/8, schedule=$schedule_count/8, other=$other_count/8; completed Flyway migrations=$history_count/2"

if [[ "$schedule_count" == "8" && "$source_count" == "0" && "$other_count" == "0" ]]; then
  echo "Schedule tables already exist in the correct schema; no changes necessary."
  exit 0
fi

if [[ "$source_count" != "8" || "$schedule_count" != "0" || "$other_count" != "0" || "$history_count" != "2" ]]; then
  echo "Unexpected database layout. No schema changes made." >&2
  echo "Inspect pg_tables and Flyway history before changing schema ownership." >&2
  exit 1
fi

# Ensure all eight relations are ordinary application tables. The ALTER TABLE
# operations and index/schema moves are committed or rolled back together.
echo "Moving the eight schedule tables from content to schedule in one transaction..."
compose exec -T postgres psql -X -v ON_ERROR_STOP=1 \
  -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-postgres}" <<'SQL'
BEGIN;
ALTER TABLE content.academic_semesters SET SCHEMA schedule;
ALTER TABLE content.lesson_slots SET SCHEMA schedule;
ALTER TABLE content.rooms SET SCHEMA schedule;
ALTER TABLE content.room_capabilities SET SCHEMA schedule;
ALTER TABLE content.schedule_templates SET SCHEMA schedule;
ALTER TABLE content.schedule_overrides SET SCHEMA schedule;
ALTER TABLE content.teacher_debts SET SCHEMA schedule;
ALTER TABLE content.schedule_outbox_events SET SCHEMA schedule;
COMMIT;
SQL

verified="$(psql_query "SELECT count(*) FROM pg_tables WHERE schemaname='schedule' AND tablename IN ($tables);")"
[[ "$verified" == "8" ]] || { echo "Post-migration validation failed" >&2; exit 1; }
echo "Recovered schedule schema without dropping tables or resetting Flyway history."
