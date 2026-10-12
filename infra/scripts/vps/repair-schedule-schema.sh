#!/usr/bin/env bash
set -euo pipefail

# One-time, non-destructive recovery for the first Studium demo deployment:
# transaction-pooled PgBouncer recorded Flyway versions in "schedule" but
# created all eight unqualified schedule tables under "public".
# This script refuses partial or unexpected layouts instead of guessing.
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
public_count="$(psql_query "SELECT count(*) FROM pg_tables WHERE schemaname='public' AND tablename IN ($tables);")"
schedule_count="$(psql_query "SELECT count(*) FROM pg_tables WHERE schemaname='schedule' AND tablename IN ($tables);")"
history_count="$(psql_query "SELECT count(*) FROM schedule.flyway_schema_history WHERE version IN ('1','2') AND success=true;")"

echo "Schedule tables: public=$public_count/8, schedule=$schedule_count/8; completed Flyway migrations=$history_count/2"

if [[ "$schedule_count" == "8" ]]; then
  echo "Schedule tables already exist in the correct schema; no changes necessary."
  exit 0
fi

if [[ "$public_count" != "8" || "$schedule_count" != "0" || "$history_count" != "2" ]]; then
  echo "Unexpected database layout. No schema changes made." >&2
  echo "Inspect pg_tables and Flyway history, then repair only the missing parts." >&2
  exit 1
fi

echo "Moving existing schedule tables from public to schedule in one transaction..."
compose exec -T postgres psql -X -v ON_ERROR_STOP=1 \
  -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-postgres}" <<'SQL'
BEGIN;
ALTER TABLE public.academic_semesters SET SCHEMA schedule;
ALTER TABLE public.lesson_slots SET SCHEMA schedule;
ALTER TABLE public.rooms SET SCHEMA schedule;
ALTER TABLE public.room_capabilities SET SCHEMA schedule;
ALTER TABLE public.schedule_templates SET SCHEMA schedule;
ALTER TABLE public.schedule_overrides SET SCHEMA schedule;
ALTER TABLE public.teacher_debts SET SCHEMA schedule;
ALTER TABLE public.schedule_outbox_events SET SCHEMA schedule;
COMMIT;
SQL

verified="$(psql_query "SELECT count(*) FROM pg_tables WHERE schemaname='schedule' AND tablename IN ($tables);")"
[[ "$verified" == "8" ]] || { echo "Post-migration validation failed" >&2; exit 1; }
echo "Recovered schedule schema. You may rerun deploy-vps.sh without deleting any volumes."
