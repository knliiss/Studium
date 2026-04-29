#!/usr/bin/env bash
set -euo pipefail

: "${POSTGRES_DB:?POSTGRES_DB is required}"
: "${POSTGRES_USER:?POSTGRES_USER is required}"
: "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}"

POSTGRES_HOST="${POSTGRES_HOST:-postgres}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"

for schema in \
  "${AUTH_DB_SCHEMA:-auth}" \
  "${PROFILE_DB_SCHEMA:-profile}" \
  "${EDUCATION_DB_SCHEMA:-education}" \
  "${SCHEDULE_DB_SCHEMA:-schedule}" \
  "${ASSIGNMENT_DB_SCHEMA:-assignment}" \
  "${TESTING_DB_SCHEMA:-testing}" \
  "${FILE_DB_SCHEMA:-file}" \
  "${ANALYTICS_DB_SCHEMA:-analytics}" \
  "${AUDIT_DB_SCHEMA:-audit}" \
  "${NOTIFICATION_DB_SCHEMA:-notification}"; do
  PGPASSWORD="$POSTGRES_PASSWORD" psql \
    --host "$POSTGRES_HOST" \
    --port "$POSTGRES_PORT" \
    --username "$POSTGRES_USER" \
    --dbname "$POSTGRES_DB" \
    --set ON_ERROR_STOP=1 \
    --command "CREATE SCHEMA IF NOT EXISTS \"${schema}\" AUTHORIZATION \"${POSTGRES_USER}\";"
done

echo "Schemas are initialized."
