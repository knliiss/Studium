#!/usr/bin/env bash
set -euo pipefail

: "${POSTGRES_DB:?POSTGRES_DB is required}"
: "${POSTGRES_USER:?POSTGRES_USER is required}"
: "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}"

POSTGRES_HOST="${POSTGRES_HOST:-postgres}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
DB_INIT_MAX_RETRIES="${DB_INIT_MAX_RETRIES:-20}"
DB_INIT_RETRY_DELAY_SECONDS="${DB_INIT_RETRY_DELAY_SECONDS:-3}"

SQL_FILE="$(mktemp)"
cleanup() {
  rm -f "$SQL_FILE"
}
trap cleanup EXIT

for schema in \
  "${AUTH_DB_SCHEMA:-auth}" \
  "${PROFILE_DB_SCHEMA:-profile}" \
  "${EDUCATION_DB_SCHEMA:-education}" \
  "${SCHEDULE_DB_SCHEMA:-schedule}" \
  "${ASSIGNMENT_DB_SCHEMA:-assignment}" \
  "${CONTENT_DB_SCHEMA:-content}" \
  "${TESTING_DB_SCHEMA:-testing}" \
  "${FILE_DB_SCHEMA:-file}" \
  "${ANALYTICS_DB_SCHEMA:-analytics}" \
  "${AUDIT_DB_SCHEMA:-audit}" \
  "${NOTIFICATION_DB_SCHEMA:-notification}"; do
  printf 'CREATE SCHEMA IF NOT EXISTS "%s" AUTHORIZATION "%s";\n' "$schema" "$POSTGRES_USER" >> "$SQL_FILE"
done

attempt=1
while true; do
  if PGPASSWORD="$POSTGRES_PASSWORD" psql \
    --host "$POSTGRES_HOST" \
    --port "$POSTGRES_PORT" \
    --username "$POSTGRES_USER" \
    --dbname "$POSTGRES_DB" \
    --set ON_ERROR_STOP=1 \
    --file "$SQL_FILE"; then
    break
  fi

  if [[ "$attempt" -ge "$DB_INIT_MAX_RETRIES" ]]; then
    echo "PostgreSQL schema initialization failed after ${DB_INIT_MAX_RETRIES} attempts."
    exit 1
  fi

  echo "PostgreSQL is temporarily unavailable or out of client slots. Retrying in ${DB_INIT_RETRY_DELAY_SECONDS}s..."
  attempt=$((attempt + 1))
  sleep "$DB_INIT_RETRY_DELAY_SECONDS"
done

echo "Schemas are initialized."
