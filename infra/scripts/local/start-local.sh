#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/infra/docker/docker-compose.local.yml"
ENV_FILE="$ROOT_DIR/.env"
ENV_EXAMPLE_FILE="$ROOT_DIR/.env.example"
KEY_DIR="$ROOT_DIR/infra/keys"
PRIVATE_KEY_FILE="$KEY_DIR/private.pem"
PUBLIC_KEY_FILE="$KEY_DIR/public.pem"
BUILD_CACHE_FILE="$ROOT_DIR/.build-cache"

APP_SERVICES=(
  auth-service
  profile-service
  education-service
  schedule-service
  assignment-service
  content-service
  testing-service
  file-service
  analytics-service
  audit-service
  notification-service
  telegram-bot-service
  gateway
  frontend
)

BOOTJAR_TASKS=(
  :apps:auth-service:bootJar
  :apps:profile-service:bootJar
  :apps:education-service:bootJar
  :apps:schedule-service:bootJar
  :apps:assignment-service:bootJar
  :apps:content-service:bootJar
  :apps:testing-service:bootJar
  :apps:file-service:bootJar
  :apps:analytics-service:bootJar
  :apps:audit-service:bootJar
  :apps:notification-service:bootJar
  :apps:gateway:bootJar
)

SKIP_BUILD=false
FRONTEND_ONLY=false
FORCE_REBUILD=false
FRESH_START=false
CHANGED_SERVICES=()
CACHE_KEYS=()
CACHE_VALUES=()

usage() {
  cat <<'USAGE'
Usage: ./infra/scripts/local/start-local.sh [OPTIONS]

Options:
  --skip-build, -s            Skip Gradle build (use existing JARs)
  --frontend-only, -f         Rebuild and restart only frontend without restarting the rest
  --rebuild, -r               Force complete rebuild and restart of all services
  --fresh, --from-scratch     Stop stack, remove volumes, rebuild and start from zero
  --help, -h                  Show this help message

Smart restart behavior:
  - Detects changes in source code and only rebuilds/restarts affected services
  - Caches build hashes in .build-cache for fast change detection
  - Use -r flag to force full rebuild regardless of changes
  - Use -s flag to skip all builds (use existing JARs/containers)
USAGE
}

compose() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

create_minimal_env() {
  cat >"$ENV_FILE" <<'EOF'
# Generated minimal local defaults
GATEWAY_PORT=8080
AUTH_PORT=8081
PROFILE_PORT=8082
FILE_PORT=8083
NOTIFICATION_PORT=8084
EDUCATION_PORT=8085
ASSIGNMENT_PORT=8086
TESTING_PORT=8087
SCHEDULE_PORT=8088
ANALYTICS_PORT=8089
AUDIT_PORT=8090
CONTENT_PORT=8091
POSTGRES_DB=postgres
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
AUTH_DB_SCHEMA=auth
PROFILE_DB_SCHEMA=profile
FILE_DB_SCHEMA=file
NOTIFICATION_DB_SCHEMA=notification
EDUCATION_DB_SCHEMA=education
ASSIGNMENT_DB_SCHEMA=assignment
TESTING_DB_SCHEMA=testing
SCHEDULE_DB_SCHEMA=schedule
ANALYTICS_DB_SCHEMA=analytics
AUDIT_DB_SCHEMA=audit
CONTENT_DB_SCHEMA=content
KAFKA_PORT=29092
KAFKA_BOOTSTRAP_SERVERS=localhost:29092
KAFKA_CLUSTER_ID=MkU3OEVBNTcwNTJENDM2Qk
MINIO_PORT=9000
MINIO_CONSOLE_PORT=9001
MINIO_ROOT_USER=minio
MINIO_ROOT_PASSWORD=minio123
MINIO_BUCKET_PUBLIC=public
MINIO_BUCKET_PRIVATE=private
FRONTEND_PORT=3000
FRONTEND_VITE_API_BASE_URL=http://localhost:8080
JWT_ISSUER=dev.knalis.auth-service
JWT_AUDIENCE=dev.knalis.api
FILE_INTERNAL_SHARED_SECRET=change-me-file-internal
NOTIFICATION_INTERNAL_SHARED_SECRET=change-me-notification-internal
EDUCATION_INTERNAL_SHARED_SECRET=change-me-education-internal
AUDIT_INTERNAL_SHARED_SECRET=change-me-audit-internal
AUTH_OWNER_SEED_ENABLED=true
AUTH_OWNER_USERNAME=owner
AUTH_OWNER_EMAIL=owner@example.com
AUTH_OWNER_PASSWORD=ChangeMe123!
AUTH_MFA_ENABLED=true
AUTH_MFA_ENCRYPTION_KEY=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=
DEMO_SEED_ENABLED=true
EOF
  echo ".env.example missing, created minimal .env"
}

ensure_env() {
  if [[ -f "$ENV_FILE" ]]; then
    echo ".env already exists"
    return
  fi

  if [[ -f "$ENV_EXAMPLE_FILE" ]]; then
    cp "$ENV_EXAMPLE_FILE" "$ENV_FILE"
    echo ".env created from .env.example"
    return
  fi

  create_minimal_env
}

sync_missing_env_keys() {
  if [[ ! -f "$ENV_EXAMPLE_FILE" ]]; then
    return
  fi

  local appended_keys=()
  while IFS= read -r line || [[ -n "$line" ]]; do
    if [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]]; then
      continue
    fi

    local key="${line%%=*}"
    if [[ -z "$key" ]]; then
      continue
    fi

    if ! rg -q "^${key}=" "$ENV_FILE"; then
      printf '\n%s\n' "$line" >>"$ENV_FILE"
      appended_keys+=("$key")
    fi
  done <"$ENV_EXAMPLE_FILE"

  if (( ${#appended_keys[@]} > 0 )); then
    echo "Appended missing .env keys from .env.example: ${appended_keys[*]}"
  fi
}

load_env() {
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
}

require_openssl() {
  if ! command -v openssl >/dev/null 2>&1; then
    echo "openssl is required to generate RSA keys. Install openssl and rerun the script." >&2
    exit 1
  fi
}

ensure_keys() {
  mkdir -p "$KEY_DIR"

  if [[ -f "$PRIVATE_KEY_FILE" && -f "$PUBLIC_KEY_FILE" ]]; then
    echo "JWT RSA keys already exist"
    return
  fi

  if [[ -f "$PRIVATE_KEY_FILE" && ! -f "$PUBLIC_KEY_FILE" ]]; then
    require_openssl
    openssl rsa -in "$PRIVATE_KEY_FILE" -pubout -out "$PUBLIC_KEY_FILE" >/dev/null 2>&1
    chmod 644 "$PUBLIC_KEY_FILE"
    echo "Generated public.pem from existing private.pem"
    return
  fi

  if [[ ! -f "$PRIVATE_KEY_FILE" && -f "$PUBLIC_KEY_FILE" ]]; then
    echo "infra/keys/public.pem exists but infra/keys/private.pem is missing. Restore the private key or remove the orphaned public key and rerun." >&2
    exit 1
  fi

  require_openssl
  openssl genrsa -out "$PRIVATE_KEY_FILE" 2048 >/dev/null 2>&1
  openssl rsa -in "$PRIVATE_KEY_FILE" -pubout -out "$PUBLIC_KEY_FILE" >/dev/null 2>&1
  chmod 600 "$PRIVATE_KEY_FILE"
  chmod 644 "$PUBLIC_KEY_FILE"
  echo "Generated JWT RSA key pair in infra/keys"
}

# Compute hash of source files for a service
compute_service_hash() {
  local service_name=$1
  local service_path="$ROOT_DIR/apps/$service_name"

  if [[ ! -d "$service_path" ]]; then
    echo ""
    return
  fi

  # Hash: all files under src/ (Java/Kotlin/resources/tests) + build files + Dockerfile
  local hash=""
  if [[ -d "$service_path/src" ]]; then
    hash=$(find "$service_path/src" -type f 2>/dev/null | sort | xargs md5sum 2>/dev/null | md5sum)
  fi

  if [[ -f "$service_path/build.gradle" ]]; then
    local build_hash=$(md5sum "$service_path/build.gradle" 2>/dev/null | cut -d' ' -f1)
    hash="${hash:-}:${build_hash}"
  fi

  if [[ -f "$service_path/build.gradle.kts" ]]; then
    local build_kts_hash=$(md5sum "$service_path/build.gradle.kts" 2>/dev/null | cut -d' ' -f1)
    hash="${hash:-}:${build_kts_hash}"
  fi

  if [[ -f "$service_path/Dockerfile" ]]; then
    local dockerfile_hash=$(md5sum "$service_path/Dockerfile" 2>/dev/null | cut -d' ' -f1)
    hash="${hash:-}:${dockerfile_hash}"
  fi

  echo "$hash"
}

# Compute hash for frontend
compute_frontend_hash() {
  local frontend_path="$ROOT_DIR/apps/frontend"

  if [[ ! -d "$frontend_path" ]]; then
    echo ""
    return
  fi

  # Hash: src/, vite.config.ts, package.json, tsconfig.json
  local hash=""
  if [[ -d "$frontend_path/src" ]]; then
    hash=$(find "$frontend_path/src" -type f \( -name "*.ts" -o -name "*.tsx" -o -name "*.css" \) 2>/dev/null | sort | xargs md5sum 2>/dev/null | md5sum)
  fi

  for file in "vite.config.ts" "package.json" "tsconfig.json"; do
    if [[ -f "$frontend_path/$file" ]]; then
      local file_hash=$(md5sum "$frontend_path/$file" 2>/dev/null | cut -d' ' -f1)
      hash="${hash:-}:${file_hash}"
    fi
  done

  echo "$hash"
}

# Load cached hashes
load_cache() {
  if [[ ! -f "$BUILD_CACHE_FILE" ]]; then
    return
  fi

  # Cache format: service_name:hash
  while IFS=: read -r service_name cached_hash; do
    if [[ -z "$service_name" ]] || [[ -z "$cached_hash" ]]; then
      continue
    fi
    CACHE_KEYS+=("$service_name")
    CACHE_VALUES+=("$cached_hash")
  done <"$BUILD_CACHE_FILE"
}

cache_get() {
  local key="$1"
  local i
  for i in "${!CACHE_KEYS[@]}"; do
    if [[ "${CACHE_KEYS[$i]}" == "$key" ]]; then
      printf '%s' "${CACHE_VALUES[$i]}"
      return 0
    fi
  done
  return 1
}

# Save current hashes to cache
save_cache() {
  {
    for service_name in "${APP_SERVICES[@]}"; do
      if [[ "$service_name" == "frontend" ]]; then
        local current_hash=$(compute_frontend_hash)
      else
        local current_hash=$(compute_service_hash "$service_name")
      fi

      if [[ -n "$current_hash" ]]; then
        echo "${service_name}:${current_hash}"
      fi
    done
  } >"$BUILD_CACHE_FILE"
}

# Detect which services have changes
detect_changed_services() {
  CHANGED_SERVICES=()

  for service_name in "${APP_SERVICES[@]}"; do
    if [[ "$service_name" == "frontend" ]]; then
      local current_hash=$(compute_frontend_hash)
    else
      local current_hash=$(compute_service_hash "$service_name")
    fi

    local cached_hash=""
    cached_hash=$(cache_get "$service_name" || true)

    # If no cached hash or hash differs, mark as changed
    if [[ -z "$cached_hash" ]] || [[ "$current_hash" != "$cached_hash" ]]; then
      CHANGED_SERVICES+=("$service_name")
    fi
  done
}

build_jars() {
  if [[ "$SKIP_BUILD" == "true" ]]; then
    echo "Skipping Gradle build"
    return
  fi

  if [[ "$FORCE_REBUILD" == "true" ]]; then
    echo "Force rebuild: building all boot JARs..."
    (
      cd "$ROOT_DIR"
      ./gradlew "${BOOTJAR_TASKS[@]}"
    )
    return
  fi

  # Load cached hashes and detect changes
  load_cache
  detect_changed_services

  if (( ${#CHANGED_SERVICES[@]} == 0 )); then
    echo "No source changes detected. Skipping build."
    return
  fi

  echo "Detected changes in: ${CHANGED_SERVICES[*]}"

  # Build only changed backend services (exclude frontend from bootJar)
  local tasks_to_build=()
  for service_name in "${CHANGED_SERVICES[@]}"; do
    if [[ "$service_name" != "frontend" ]]; then
      case "$service_name" in
        auth-service) tasks_to_build+=( ":apps:auth-service:bootJar" ) ;;
        profile-service) tasks_to_build+=( ":apps:profile-service:bootJar" ) ;;
        education-service) tasks_to_build+=( ":apps:education-service:bootJar" ) ;;
        schedule-service) tasks_to_build+=( ":apps:schedule-service:bootJar" ) ;;
        assignment-service) tasks_to_build+=( ":apps:assignment-service:bootJar" ) ;;
        testing-service) tasks_to_build+=( ":apps:testing-service:bootJar" ) ;;
        file-service) tasks_to_build+=( ":apps:file-service:bootJar" ) ;;
        analytics-service) tasks_to_build+=( ":apps:analytics-service:bootJar" ) ;;
        audit-service) tasks_to_build+=( ":apps:audit-service:bootJar" ) ;;
        notification-service) tasks_to_build+=( ":apps:notification-service:bootJar" ) ;;
        gateway) tasks_to_build+=( ":apps:gateway:bootJar" ) ;;
      esac
    fi
  done

  if (( ${#tasks_to_build[@]} > 0 )); then
    echo "Building changed backend services..."
    (
      cd "$ROOT_DIR"
      ./gradlew "${tasks_to_build[@]}"
    )
  fi
}

seed_demo_data() {
  if [[ "${DEMO_SEED_ENABLED:-true}" != "true" ]]; then
    echo "Demo seed disabled via DEMO_SEED_ENABLED"
    return
  fi

  echo "Running demo seed..."
  "$ROOT_DIR/infra/scripts/local/seed-demo.sh"
}

start_frontend_only() {
  echo "Rebuilding and starting only the frontend container..."
  compose up -d --build --no-deps frontend
  echo "Frontend container started successfully"
}

fresh_start_cleanup() {
  echo "Fresh start requested: stopping existing stack and removing volumes..."
  compose down -v --remove-orphans || true
  rm -f "$BUILD_CACHE_FILE"
}

start_stack() {
  local auth_replicas="${AUTH_REPLICAS:-1}"
  local profile_replicas="${PROFILE_REPLICAS:-1}"
  local education_replicas="${EDUCATION_REPLICAS:-1}"
  local schedule_replicas="${SCHEDULE_REPLICAS:-1}"
  local assignment_replicas="${ASSIGNMENT_REPLICAS:-1}"
  local testing_replicas="${TESTING_REPLICAS:-1}"
  local file_replicas="${FILE_REPLICAS:-1}"
  local analytics_replicas="${ANALYTICS_REPLICAS:-1}"
  local audit_replicas="${AUDIT_REPLICAS:-1}"
  local notification_replicas="${NOTIFICATION_REPLICAS:-1}"
  local gateway_replicas="${GATEWAY_REPLICAS:-1}"

  echo "Starting infrastructure containers..."
  compose up -d postgres pgbouncer redis kafka minio service-lb

  echo "Initializing PostgreSQL schemas..."
  compose run --rm db-init

  echo "Initializing Kafka topics..."
  compose run --rm kafka-init

  echo "Initializing MinIO buckets..."
  compose run --rm minio-init

  # If force rebuild or no cache, start all services with build
  if [[ "$FORCE_REBUILD" == "true" ]] || [[ ! -f "$BUILD_CACHE_FILE" ]]; then
    echo "Starting all application containers with full rebuild..."
    compose up -d --build \
      --scale auth-service="$auth_replicas" \
      --scale profile-service="$profile_replicas" \
      --scale education-service="$education_replicas" \
      --scale schedule-service="$schedule_replicas" \
      --scale assignment-service="$assignment_replicas" \
      --scale testing-service="$testing_replicas" \
      --scale file-service="$file_replicas" \
      --scale analytics-service="$analytics_replicas" \
      --scale audit-service="$audit_replicas" \
      --scale notification-service="$notification_replicas" \
      --scale gateway="$gateway_replicas" \
      "${APP_SERVICES[@]}"
  else
    # Incremental: restart only changed services
    if (( ${#CHANGED_SERVICES[@]} > 0 )); then
      echo "Restarting changed services: ${CHANGED_SERVICES[*]}"
      compose up -d --build --no-deps "${CHANGED_SERVICES[@]}"
    else
      echo "No service changes detected. Ensuring stack is running..."
      compose up -d --no-build "${APP_SERVICES[@]}"
    fi
  fi

  seed_demo_data
  echo "Local stack started successfully"
}

for arg in "$@"; do
  case "$arg" in
    --skip-build|-s)
      SKIP_BUILD=true
      ;;
    --frontend-only|-f)
      FRONTEND_ONLY=true
      ;;
    --rebuild|-r)
      FORCE_REBUILD=true
      ;;
    --fresh|--from-scratch)
      FRESH_START=true
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $arg" >&2
      usage
      exit 1
      ;;
  esac
done

ensure_env
sync_missing_env_keys
load_env
ensure_keys

if [[ "$FRESH_START" == "true" && "$FRONTEND_ONLY" == "true" ]]; then
  echo "--fresh/--from-scratch cannot be used with --frontend-only" >&2
  exit 1
fi

if [[ "$FRESH_START" == "true" && "$SKIP_BUILD" == "true" ]]; then
  echo "--fresh/--from-scratch requires build; do not use with --skip-build" >&2
  exit 1
fi

if [[ "$FRESH_START" == "true" ]]; then
  FORCE_REBUILD=true
  fresh_start_cleanup
fi

if [[ "$FRONTEND_ONLY" == "true" ]]; then
  start_frontend_only
  save_cache
  exit 0
fi

build_jars
start_stack
save_cache
