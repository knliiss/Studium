#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
TOKEN_FILE="$ROOT_DIR/.secrets/cloudflare-tunnel.token"
STATE_FILE="$ROOT_DIR/.vps-demo-seeded"
export STUDIUM_COMPOSE_OVERRIDE_FILE="$ROOT_DIR/infra/docker/docker-compose.vps.yml"
export STUDIUM_SKIP_AUTO_SEED=true

for binary in docker python3 openssl curl java; do
  command -v "$binary" >/dev/null 2>&1 || { echo "Missing $binary" >&2; exit 1; }
done
docker compose version >/dev/null 2>&1 || { echo "Docker Compose v2.24.4+ required" >&2; exit 1; }
[[ -s "$TOKEN_FILE" ]] || { echo "Save the new Cloudflare Tunnel token to $TOKEN_FILE first" >&2; exit 1; }

if [[ ! -f "$ENV_FILE" ]]; then
  cp "$ROOT_DIR/.env.example" "$ENV_FILE"
  python3 - "$ENV_FILE" <<'PY'
import base64
import os
from pathlib import Path
import secrets
import sys

path = Path(sys.argv[1])
values = {
    "POSTGRES_PASSWORD": secrets.token_urlsafe(32),
    "MINIO_ROOT_PASSWORD": secrets.token_urlsafe(32),
    "FILE_INTERNAL_SHARED_SECRET": secrets.token_urlsafe(40),
    "NOTIFICATION_INTERNAL_SHARED_SECRET": secrets.token_urlsafe(40),
    "EDUCATION_INTERNAL_SHARED_SECRET": secrets.token_urlsafe(40),
    "AUDIT_INTERNAL_SHARED_SECRET": secrets.token_urlsafe(40),
    "INTERNAL_SERVICE_TOKEN": secrets.token_urlsafe(40),
    "AUTH_OWNER_PASSWORD": secrets.token_urlsafe(30),
    "AUTH_MFA_ENCRYPTION_KEY": base64.b64encode(os.urandom(32)).decode(),
    "GATEWAY_PORT": "18080",
    "FRONTEND_PORT": "13000",
    "GATEWAY_CORS_ALLOWED_ORIGINS": "https://lms.knalis.rest",
    "FRONTEND_BASE_URL": "https://lms.knalis.rest",
    "FRONTEND_VITE_API_BASE_URL": "https://lms.knalis.rest",
    "APP_WEB_BASE_URL": "https://lms.knalis.rest",
    "APP_BASE_DOMAIN": "lms.knalis.rest",
    "TELEGRAM_ENABLED": "false",
    "DEMO_SEED_ENABLED": "true",
}
seen = set()
lines = []
for line in path.read_text().splitlines():
    key = line.partition("=")[0]
    if key in values and not line.lstrip().startswith("#"):
        lines.append(f"{key}={values[key]}")
        seen.add(key)
    else:
        lines.append(line)
for key, value in values.items():
    if key not in seen:
        lines.append(f"{key}={value}")
path.write_text("\n".join(lines) + "\n")
PY
  echo "Created .env with random passwords and LMS origin"
fi
chmod 600 "$ENV_FILE"
if grep -Eq '^(POSTGRES_PASSWORD=postgres|MINIO_ROOT_PASSWORD=minio123|AUTH_OWNER_PASSWORD=ChangeMe123!|FILE_INTERNAL_SHARED_SECRET=change-me-file-internal)' "$ENV_FILE"; then
  echo "Insecure .env defaults detected; replace them before running" >&2
  exit 1
fi
if ! java -version 2>&1 | grep -Eq 'version "21[."]|openjdk 21'; then
  echo "Java 21 JDK required" >&2
  exit 1
fi
set -a
source "$ENV_FILE"
set +a

rebuild_needed=false
for service in auth-service profile-service education-service schedule-service assignment-service content-service testing-service file-service analytics-service audit-service notification-service telegram-bot-service gateway; do
  if ! compgen -G "$ROOT_DIR/apps/$service/build/libs/*.jar" >/dev/null; then
    rebuild_needed=true
    break
  fi
done
if [[ "$rebuild_needed" == "true" ]]; then
  echo "Missing JARs: forcing complete first build"
  bash "$ROOT_DIR/infra/scripts/local/start-local.sh" --rebuild "$@"
else
  bash "$ROOT_DIR/infra/scripts/local/start-local.sh" "$@"
fi

if [[ "${DEMO_SEED_ENABLED:-true}" == "true" && ( ! -f "$STATE_FILE" || "${STUDIUM_RESEED:-false}" == "true" ) ]]; then
  bash "$ROOT_DIR/infra/scripts/local/seed-demo.sh"
  touch "$STATE_FILE"
fi

docker compose --env-file "$ENV_FILE" \
  -f "$ROOT_DIR/infra/docker/docker-compose.local.yml" \
  -f "$STUDIUM_COMPOSE_OVERRIDE_FILE" up -d cloudflared

curl -fsS "http://127.0.0.1:${GATEWAY_PORT:-18080}/actuator/health" >/dev/null
curl -fsS "http://127.0.0.1:${FRONTEND_PORT:-13000}/" >/dev/null
echo "Studium checks OK. Public address: https://lms.knalis.rest"
