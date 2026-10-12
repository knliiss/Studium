#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
set -a
source "$ROOT_DIR/.env"
set +a
compose() {
  docker compose --env-file "$ROOT_DIR/.env" \
    -f "$ROOT_DIR/infra/docker/docker-compose.local.yml" \
    -f "$ROOT_DIR/infra/docker/docker-compose.vps.yml" "$@"
}
required="auth.users profile.user_profiles education.groups schedule.academic_semesters assignment.assignments content.topic_materials testing.tests file.stored_files analytics.raw_academic_events audit.audit_events notification.notifications"
for attempt in $(seq 1 90); do
  missing=""
  for table in $required; do
    exists="$(compose exec -T postgres psql -X -qAt -v ON_ERROR_STOP=1 \
      -U "${POSTGRES_USER:-postgres}" -d "${POSTGRES_DB:-postgres}" \
      -c "SELECT to_regclass('$table') IS NOT NULL;" 2>/dev/null || true)"
    if [[ "$exists" != "t" ]]; then
      missing="$missing $table"
    fi
  done
  [[ -z "$missing" ]] && break
  if (( attempt % 10 == 0 )); then
    echo "Waiting for Flyway schema tables:$missing" >&2
  fi
  sleep 2
done
if [[ -n "$missing" ]]; then
  echo "ERROR: Database schemas not ready:$missing" >&2
  exit 1
fi
echo "All required database tables are available."

if ! compose exec -T service-lb sh -c 'command -v wget >/dev/null 2>&1'; then
  echo "ERROR: HAProxy container lacks wget health probe utility" >&2
  exit 1
fi

for backend in auth:18081 profile:18082 education:18085 schedule:18088 assignment:18086 content:18091 testing:18087 file:18083 analytics:18089 audit:18090 notification:18084; do
  name="${backend%%:*}"
  port="${backend##*:}"
  ready=false
  for attempt in $(seq 1 90); do
    if compose exec -T service-lb wget -q -O /dev/null \
      "http://127.0.0.1:${port}/actuator/health" >/dev/null 2>&1; then
      ready=true
      break
    fi
    sleep 2
  done
  if [[ "$ready" != true ]]; then
    echo "ERROR: $name backend never passed HAProxy health check (port $port)." >&2
    echo "Check the logs for ${name}-service, without deleting Docker volumes." >&2
    exit 1
  fi
  echo "Ready: $name"
done
