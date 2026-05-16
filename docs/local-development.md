# Local Development

## Prerequisites
- Docker + Docker Compose
- Java 21
- Node.js (for frontend-only local workflows)
- Bash shell (scripts under `infra/scripts/local`)

## One-Time Setup
```bash
cp .env.example .env
./infra/scripts/local/start-local.sh
```

The start script will:
- bootstrap `.env` if missing
- ensure JWT keypair under `infra/keys`
- build/rebuild changed services
- start compose stack

## Main Local Commands
Start/update stack:
```bash
./infra/scripts/local/start-local.sh
```

Frontend-only restart path:
```bash
./infra/scripts/local/start-local.sh --frontend-only
```

Force full rebuild:
```bash
./infra/scripts/local/start-local.sh --rebuild
```

Fresh from scratch (destructive local reset):
```bash
./infra/scripts/local/start-local.sh --fresh
```

Seed deterministic demo data:
```bash
./infra/scripts/local/seed-demo.sh
```

## Service Endpoints (Default)
- Frontend: `http://localhost:3000`
- Gateway: `http://localhost:8080`
- Auth: `http://localhost:8081`
- Profile: `http://localhost:8082`
- File: `http://localhost:8083`
- Notification: `http://localhost:8084`
- Education: `http://localhost:8085`
- Assignment: `http://localhost:8086`
- Testing: `http://localhost:8087`
- Schedule: `http://localhost:8088`
- Analytics: `http://localhost:8089`
- Audit: `http://localhost:8090`
- Content: `http://localhost:8091`
- Telegram bot service: `http://localhost:8092`

Infra defaults:
- PostgreSQL `5432`
- PgBouncer `6432`
- Redis `6379`
- Kafka `29092`
- MinIO `9000` (+ console `9001`)

## Frontend Local Commands
```bash
cd apps/frontend
npm install
npm run dev
npm run typecheck
npm run lint
npm test -- --run
```

## Backend Build Commands (Examples)
```bash
./gradlew :apps:gateway:bootJar
./gradlew :apps:notification-service:bootJar
./gradlew :apps:telegram-bot-service:bootJar
```

## Telegram Local Setup Caveats
- `TELEGRAM_ENABLED=true` requires valid bot token to enable polling.
- Invalid/missing token does not crash service; polling is disabled.
- Bot URL buttons require public URLs; `FRONTEND_BASE_URL=http://localhost:3000` is not used as Telegram URL button target.
- Profile deep link (`https://t.me/<botUsername>?start=<token>`) remains valid in browser if bot username is configured.

## Troubleshooting
- If login/owner bootstrap fails, verify JWT keys in `infra/keys` and `.env` key paths.
- If seed script fails, ensure stack is fully healthy before running `seed-demo.sh`.
- If Telegram profile actions are unavailable, verify `TELEGRAM_ENABLED`, `TELEGRAM_BOT_USERNAME`, and notification internal token values.
- If file previews fail, verify `minio` and `file-service` health.
