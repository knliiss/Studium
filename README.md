# Studium LMS

Studium is a Java 21 microservice-based Learning Management System with a React frontend. It supports role-based academic workflows (Student, Teacher, Admin, Owner), schedule access, assignments, testing, notifications, file previews, and Telegram bot integration.

## Current Project Scope
Implemented and active in this repository:
- JWT-based auth and profile management
- Role-aware frontend navigation and routes
- Education structure (groups, subjects, topics, specialties/streams/curriculum plans)
- Schedule read APIs for students/teachers/admins and schedule management for admin/owner
- Assignments, submissions, grading, attachment preview/download
- Tests, attempts/results, and teacher review flows
- In-app notifications (dropdown + center) with read/delete actions
- Telegram linking and bot service (long polling, callback menus, admin bot management)
- File upload + preview pipeline (PDF/image preview support)

## Repository Layout
- `apps/*`: deployable services and frontend
- `libs/*`: shared modules (security, web contracts, event contracts)
- `infra/*`: Docker/local orchestration, init scripts, keys
- `docs/*`: architecture and operations documentation

## Services (Runtime)
- `gateway` (public API entrypoint)
- `auth-service`
- `profile-service`
- `education-service`
- `schedule-service`
- `assignment-service`
- `testing-service`
- `notification-service`
- `telegram-bot-service`
- `file-service`
- `analytics-service`
- `audit-service`
- `content-service`
- `frontend`

## Tech Stack
- Java 21, Spring Boot 3.5, Spring Security
- Spring Cloud Gateway (gateway)
- PostgreSQL, Flyway
- Redis
- Kafka
- MinIO
- React + TypeScript + Vite
- Docker Compose for local environment

## Quick Start (Local)
1. Copy env defaults:
```bash
cp .env.example .env
```
2. Start full local stack:
```bash
./infra/scripts/local/start-local.sh
```
3. Seed deterministic demo data:
```bash
./infra/scripts/local/seed-demo.sh
```
4. Open app:
- Frontend: `http://localhost:3000`
- Gateway API: `http://localhost:8080`

For detailed setup and troubleshooting see [docs/local-development.md](docs/local-development.md).

## Demo Users (Seed Script)
The seed script provisions these users with password `DemoPass123!`:
- `admin.demo` (ADMIN)
- `teacher.alpha` (TEACHER)
- `teacher.beta` (TEACHER)
- `student.one` ... `student.five` (STUDENT)

Owner bootstrap user comes from env defaults:
- username: `${AUTH_OWNER_USERNAME}` (default `owner`)
- password: `${AUTH_OWNER_PASSWORD}` (default `ChangeMe123!`)

## Environment Variables
Primary env source is `.env.example`.

High-impact variable groups:
- Ports/service URLs
- Database and schema vars
- JWT keys and issuer/audience
- Internal shared secrets
- Telegram integration (`TELEGRAM_*`, `FRONTEND_BASE_URL`, `NOTIFICATION_SERVICE_URL`, `INTERNAL_SERVICE_TOKEN`)

See full mapping in [docs/environment.md](docs/environment.md).

## Validation and Checks
Frontend checks:
```bash
cd apps/frontend
npm run typecheck
npm run lint
npm test -- --run
```

Example backend build checks:
```bash
./gradlew :apps:gateway:bootJar
./gradlew :apps:notification-service:bootJar
./gradlew :apps:telegram-bot-service:bootJar
```

## Known Current Limitations
- Telegram bot daily schedule screen currently renders from a placeholder internal schedule context (real schedule contract for Telegram context is not wired yet).
- Telegram URL buttons must be public URLs; localhost/internal URLs are intentionally suppressed in bot menus.
- Search endpoint is admin/owner scoped.

## Documentation Map
- Architecture: [docs/architecture.md](docs/architecture.md)
- Backend services: [docs/backend-services.md](docs/backend-services.md)
- Frontend role flows: [docs/frontend-flows.md](docs/frontend-flows.md)
- Telegram bot: [docs/telegram-bot.md](docs/telegram-bot.md)
- Notifications: [docs/notifications.md](docs/notifications.md)
- Roles/permissions: [docs/roles-permissions.md](docs/roles-permissions.md)
- Environment variables: [docs/environment.md](docs/environment.md)
- Local setup/troubleshooting: [docs/local-development.md](docs/local-development.md)
