# Studium

Studium is a Java 21 multi-module Gradle monorepo built around a microservice platform for LMS-style workflows. It combines authentication, profile management, education structure, scheduling, assignments, testing, file storage, analytics, and real-time notifications behind a single gateway. The repository includes application code, shared libraries, local infrastructure, bootstrap scripts, and environment templates required to run the system end-to-end on a developer machine.

## What the Project Contains

Studium is organized into three main areas:

- `apps/*`: deployable Spring Boot services
- `apps/frontend`: React + TypeScript gateway-only frontend
- `libs/*`: shared libraries used across services
- `infra/*`: Docker Compose setup, initialization scripts, keys, and local bootstrap helpers

## Technology Stack

- Java 21
- Gradle
- Spring Boot 3.5
- Spring Cloud Gateway
- Spring Security OAuth2 Resource Server
- Spring Data JPA
- Spring Data Redis
- Spring Kafka
- PostgreSQL
- Redis
- Kafka
- MinIO
- Docker Compose
- Testcontainers

## Database Migrations

Studium uses Flyway for schema evolution in all database-owning microservices.

- Migration scripts live in each service under `src/main/resources/db/migration`.
- Local and runtime startup run Flyway automatically.
- Hibernate DDL is set to `validate` to prevent implicit schema mutation.
- Schema guards (where present) are validation-only and fail fast when Flyway migrations were not applied.

When adding schema changes:

1. Add a new migration file with the next version (`V2__...`, `V3__...`) in the owning service.
2. Keep migrations additive and non-destructive for compatibility with existing local volumes.
3. Run the service tests for the touched module and verify startup logs include successful Flyway migration.

## Services

### `gateway`

The `gateway` service is the single external entry point. It is built with Spring WebFlux and Spring Cloud Gateway.

Responsibilities:

- routes public HTTP traffic to downstream services
- exposes the WebSocket notification entrypoint
- exposes BFF schedule endpoints under `/api/v1/schedule/me/**`
- exposes personal schedule calendar export under `/api/v1/schedule/me/export.ics`
- enforces JWT-based access control
- applies public endpoint rules
- performs request correlation and request logging
- keeps ban state and rate limiting data in Redis
- consumes auth ban/unban events from Kafka

Default port:

- `8080`

Main routes:

- `/api/auth/**` -> `auth-service`
- `/api/admin/**` -> `auth-service`
- `/api/profile/**` -> `profile-service`
- `/api/v1/education/**` -> `education-service`
- `/api/v1/schedule/**` -> `schedule-service`
- `/api/v1/schedule/me/**` -> handled inside `gateway` and aggregated from `education-service` + `schedule-service`
- `/api/v1/assignments/**` -> `assignment-service`
- `/api/v1/submissions/**` -> `assignment-service`
- `/api/v1/grades/**` -> `assignment-service`
- `/api/v1/testing/**` -> `testing-service`
- `/api/files/**` -> `file-service`
- `/api/notifications/**` -> `notification-service`
- `/api/v1/dashboard/**` -> handled inside `gateway` and aggregated from downstream services
- `/api/v1/search` -> handled inside `gateway` and aggregated from `education-service`, `assignment-service`, and `testing-service`
- `/api/v1/analytics/**` -> `analytics-service`
- `/api/v1/audit/**` -> `audit-service`
- `/ws/notifications/**` -> `notification-service`

Gateway notes:

- direct schedule routes are proxied unchanged, so schedule query parameters such as `lessonType` on `/api/v1/schedule/search` pass through as-is
- the gateway BFF schedule payload mirrors `schedule-service` resolved lesson reads and includes `lessonType`, `lessonTypeDisplayName`, `lessonFormat`, `sourceType`, and `overrideType`

### `auth-service`

The `auth-service` is the identity and access service.

Responsibilities:

- user registration
- login and logout
- refresh tokens
- password reset token lifecycle
- role-aware access control
- owner bootstrap on startup
- MFA support
- user ban management
- auth outbox publishing to Kafka

Default port:

- `8081`

Implementation notes:

- servlet stack
- PostgreSQL for persistent auth data
- Redis for caching and rate limiting
- Kafka for domain event publication
- RSA key pair for JWT signing and verification

### `profile-service`

The `profile-service` manages user profile data.

Responsibilities:

- create and update profile information
- search and read profile records
- bind avatar references to files stored in `file-service`
- consume registration/profile-related Kafka events
- cache selected reads in Redis

Default port:

- `8082`

Implementation notes:

- servlet stack
- PostgreSQL for profile persistence
- Redis for caching
- HTTP integration with `file-service`

### `education-service`

The `education-service` owns the academic structure and memberships used by downstream services.

Responsibilities:

- manage groups
- manage subjects
- manage topics
- manage group-to-student membership reads used by gateway and notification flows

Default port:

- `8085`

Implementation notes:

- servlet stack
- PostgreSQL for education persistence
- Redis for caching
- internal HTTP endpoints consumed by `gateway`, `notification-service`, and other academic services

### `schedule-service`

The `schedule-service` owns academic semesters, lesson slots, rooms, recurring templates, overrides, and resolved schedule reads.

Responsibilities:

- manage lesson slots and rooms
- manage academic semesters
- manage recurring schedule templates
- expose template lifecycle with `DRAFT`, `ACTIVE`, and `ARCHIVED` while preserving the existing `active` flag for compatibility
- validate schedule conflicts before save via preview endpoint
- import recurring templates in bulk with per-item error reporting
- manage one-time schedule overrides
- resolve group, teacher, and room schedule reads
- export resolved group and teacher schedules as iCalendar
- publish schedule domain events for downstream notification flows

Default port:

- `8088`

Implementation notes:

- servlet stack
- PostgreSQL for schedule persistence
- Redis for caching
- Kafka outbox publication for schedule events

### `assignment-service`

The `assignment-service` manages assignments, submissions, and grades.

Responsibilities:

- create and update assignments
- keep assignment lifecycle in `DRAFT`, `PUBLISHED`, and `ARCHIVED`
- enforce assignment submission policies for lateness, max submissions, resubmission, file type, and file size
- accept submissions
- manage submission comments
- assign grades and feedback
- publish assignment and grade events
- schedule assignment deadline reminders through `notification-service`

Default port:

- `8086`

Implementation notes:

- servlet stack
- PostgreSQL for assignment persistence
- Redis for caching
- Kafka outbox publication for assignment events

### `testing-service`

The `testing-service` manages tests, questions, answers, and test results.

Responsibilities:

- create and publish tests
- keep test lifecycle in `DRAFT`, `PUBLISHED`, `CLOSED`, and `ARCHIVED`
- manage questions and answers
- record test attempts and results with availability and time-limit enforcement
- publish testing events
- schedule test deadline reminders through `notification-service`

Default port:

- `8087`

Implementation notes:

- servlet stack
- PostgreSQL for testing persistence
- Redis for caching
- Kafka outbox publication for testing events

### `file-service`

The `file-service` handles file metadata and object storage integration.

Responsibilities:

- upload and download files
- expose file metadata and inline preview contracts for frontend consumers
- track file ownership and lifecycle state
- enforce upload limits and quotas
- manage public/private buckets in MinIO
- expose internal endpoints used by other services
- support cleanup flows for orphaned files

Default port:

- `8083`

Implementation notes:

- servlet stack
- PostgreSQL for metadata
- MinIO for object storage
- internal shared-secret protected endpoints

### `analytics-service`

The `analytics-service` consumes academic activity events and exposes read-oriented analytics endpoints.

Responsibilities:

- aggregate dashboard metrics
- expose student, teacher, subject, and group analytics reads
- consume academic events from Kafka

Default port:

- `8089`

Implementation notes:

- servlet stack
- PostgreSQL for analytics snapshots
- Redis for caching
- Kafka consumers for academic event ingestion

### `notification-service`

The `notification-service` manages persistent notifications and real-time delivery.

Responsibilities:

- store user notifications in PostgreSQL
- expose notification APIs
- expose read-state and delete operations required by notification center UX
- provide WebSocket delivery for live updates
- publish realtime fanout through Redis
- consume auth, schedule, assignment, and testing domain events from Kafka

Default port:

- `8084`

Implementation notes:

- servlet stack with Spring WebSocket
- PostgreSQL for notification persistence
- Redis for live fanout
- Kafka consumers for event-driven notification creation

## Schedule Read and Notification Contract

Schedule reads and schedule-related notifications distinguish the academic lesson type from the delivery format.

- `lessonType`: academic nature of the class, for example `LECTURE`, `PRACTICAL`, `LABORATORY`
- `lessonTypeDisplayName`: frontend-friendly label, for example `Lecture`
- `lessonFormat`: delivery mode, for example `ONLINE` or `OFFLINE`

Resolved schedule responses returned by `schedule-service` and gateway BFF endpoints under `/api/v1/schedule/me/**` include both `lessonType` and `lessonTypeDisplayName`.

Schedule search also supports an optional `lessonType` filter:

```text
GET /api/v1/schedule/search?groupId=<uuid>&dateFrom=2026-09-01&dateTo=2026-09-30&lessonType=LECTURE
```

Additional schedule endpoints added for frontend readiness:

```text
POST /api/v1/schedule/conflicts/check
POST /api/v1/schedule/templates/import
GET  /api/v1/schedule/groups/{groupId}/export.ics
GET  /api/v1/schedule/teachers/{teacherId}/export.ics
GET  /api/v1/schedule/me/export.ics
```

Schedule-related notification payloads include schedule metadata required by the frontend, including:

- `lessonType`
- `lessonTypeDisplayName`
- `lessonFormat`
- `date`
- `groupId`
- `subjectId`
- `teacherId`
- `slotId`
- `roomId`
- `onlineMeetingUrl`

Example resolved lesson shape:

```json
{
  "date": "2026-09-08",
  "groupId": "6a0f7d6f-3ef2-4ef0-a2a8-a8d64013c7b2",
  "subjectId": "1173a4b1-6e14-4f2c-82a3-492f60a0db74",
  "teacherId": "db8e1d0f-4f8f-4ad0-98e4-b9d6a78f7c92",
  "slotId": "75634f36-7c07-41f7-bce6-4b8b623aaf77",
  "weekType": "ODD",
  "lessonType": "LABORATORY",
  "lessonTypeDisplayName": "Laboratory",
  "lessonFormat": "OFFLINE",
  "roomId": "29dfd30b-3baa-4bc0-9af0-955b0ef01d2d",
  "sourceType": "OVERRIDE",
  "overrideType": "EXTRA"
}
```

## Frontend-Ready Endpoint Additions

### Notification API

The notification center can use both the legacy `/me` routes and the frontend-friendly aliases below:

```text
GET    /api/notifications
GET    /api/notifications/unread-count
PATCH  /api/notifications/{id}/read
PATCH  /api/notifications/read-all
DELETE /api/notifications/{id}
```

Notification responses include:

- `id`
- `type`
- `title`
- `body`
- `payload`
- `read`
- `createdAt`

### File API

Frontend-facing file endpoints:

```text
GET /api/files/{fileId}
GET /api/files/{fileId}/metadata
GET /api/files/{fileId}/download
GET /api/files/{fileId}/preview
```

### Search API

Current secure-baseline global search endpoint:

```text
GET /api/v1/search?q=<text>&page=0&size=20
```

Notes:

- currently restricted to `OWNER` and `ADMIN`
- aggregates `education-service`, `assignment-service`, and `testing-service`
- teacher/student search stays closed until ownership scoping is implemented safely

Metadata responses expose `fileId`, `originalFileName`, `contentType`, `sizeBytes`, `ownerId`, `visibility`, `previewAvailable`, and timestamps. Inline preview is currently available for PDF and image content types.

### Schedule Import and Export

- `POST /api/v1/schedule/conflicts/check` reuses the existing schedule conflict engine and returns `hasConflicts` plus frontend-friendly conflict items.
- `POST /api/v1/schedule/templates/import` accepts JSON `{ "items": [...] }` where each item matches `CreateScheduleTemplateRequest`.
- `GET /api/v1/schedule/groups/{groupId}/export.ics` and `GET /api/v1/schedule/teachers/{teacherId}/export.ics` accept optional `dateFrom` and `dateTo`; if omitted, the active semester range is used.
- `GET /api/v1/schedule/me/export.ics` is implemented in `gateway` and aggregates the current user's groups before returning a single iCalendar feed.

## Shared Libraries

### `libs:event-contracts`

Contains shared Kafka event payload contracts used between services. This keeps producer and consumer services aligned on event structure.

### `libs:shared-security`

Provides shared JWT, key-loading, and security auto-configuration helpers used across gateway and backend services.

### `libs:shared-web`

Provides shared web concerns such as error envelopes, exception handling, and request correlation helpers.

## High-Level Architecture

Client traffic enters through `gateway`. From there, requests are proxied to the appropriate backend service. Cross-service asynchronous communication is handled through Kafka event contracts defined in `libs:event-contracts`.

Typical flows:

- registration: `auth-service` publishes `UserRegisteredEvent` -> `profile-service` creates a profile -> `notification-service` creates a welcome notification
- moderation: `auth-service` publishes ban/unban events -> `gateway` updates ban state enforcement
- schedule change: `schedule-service` publishes schedule events -> `notification-service` creates schedule notifications that include `lessonType`, `lessonTypeDisplayName`, and `lessonFormat`
- notification delivery: `notification-service` stores notification data and pushes live updates over WebSocket
- avatar workflow: `profile-service` references files owned and validated by `file-service`

## Security Model

Studium uses a shared JWT resource-server model.

Key points:

- `auth-service` issues JWT access tokens
- all services validate JWTs using the shared public RSA key
- the gateway is reactive, while downstream services use the servlet stack
- roles are read from JWT claims and mapped to Spring Security authorities
- internal service endpoints are protected with `X-Internal-Secret`
- selected public endpoints are explicitly allowlisted in gateway configuration

## Repository Layout

```text
Studium/
├── apps/
│   ├── auth-service/
│   ├── frontend/
│   ├── assignment-service/
│   ├── analytics-service/
│   ├── education-service/
│   ├── file-service/
│   ├── gateway/
│   ├── notification-service/
│   ├── profile-service/
│   ├── schedule-service/
│   └── testing-service/
├── infra/
│   ├── docker/
│   │   ├── docker-compose.local.yml
│   │   └── init/
│   ├── env/
│   ├── keys/
│   └── scripts/
│       └── local/
├── libs/
│   ├── event-contracts/
│   ├── shared-security/
│   └── shared-web/
├── .env.example
├── build.gradle
├── settings.gradle
└── gradlew
```

## Prerequisites

To run the project locally, install:

- JDK 21
- Node.js 20+
- Docker
- Docker Compose
- a POSIX-compatible shell environment for the bootstrap scripts

Optional but useful:

- IntelliJ IDEA
- `psql`
- `kcat` or Kafka UI tools
- MinIO client

## Local Development

### 1. Bootstrap the full local stack

The easiest way to start everything is:

```bash
./infra/scripts/local/start-local.sh
```

This command:

- creates `.env` from `.env.example` if needed
- backfills missing `.env` keys from `.env.example` without overwriting existing values
- generates a local RSA key pair in `infra/keys`
- builds boot JARs for all services
- starts infrastructure containers
- initializes PostgreSQL schemas
- initializes Kafka topics
- initializes MinIO buckets
- starts all backend services plus the frontend container
- runs demo seed data when `DEMO_SEED_ENABLED=true`

On Windows, automatic demo seed uses the same `seed-demo.sh` flow and therefore requires `bash` in `PATH` (for example Git Bash or WSL). Without `bash`, the stack still starts and the script prints that the seed step was skipped.

Skip the Gradle build phase when you already have up-to-date artifacts:

```bash
./infra/scripts/local/start-local.sh --skip-build
./infra/scripts/local/start-local.sh -s
```

Windows uses the matching batch entrypoint:

```bat
infra\scripts\local\start-local.bat
infra\scripts\local\start-local.bat --skip-build
infra\scripts\local\start-local.bat -s
```

Rebuild and restart only the frontend container without restarting the rest of the local stack:

```bash
./infra/scripts/local/start-local.sh --frontend-only
./infra/scripts/local/start-local.sh -f
```

```bat
infra\scripts\local\start-local.bat --frontend-only
infra\scripts\local\start-local.bat -f
```

### 2. Run the frontend locally

```bash
cd apps/frontend
cp .env.example .env
npm install
npm run dev
```

Frontend verification commands:

```bash
npm run lint
npm run typecheck
npm run build
```

The Dockerized frontend is available on:

- `http://localhost:3000` by default

Local gateway CORS defaults allow the frontend dev origin on `http://localhost:3000` and `http://127.0.0.1:3000` for all gateway API paths and preflight requests.
Override `GATEWAY_CORS_ALLOWED_ORIGINS` in `.env` if your local frontend runs on a different origin.

### 3. Common local operations

Stop the stack:

```bash
docker compose --env-file .env -f infra/docker/docker-compose.local.yml down --remove-orphans
```

Inspect status:

```bash
docker compose --env-file .env -f infra/docker/docker-compose.local.yml ps
```

Follow logs:

```bash
docker compose --env-file .env -f infra/docker/docker-compose.local.yml logs -f --tail=200
docker compose --env-file .env -f infra/docker/docker-compose.local.yml logs -f --tail=200 gateway
```

### 4. Run tests

```bash
./gradlew test
```

Run a specific module:

```bash
./gradlew :apps:auth-service:test
./gradlew :apps:gateway:test
```

### 5. Build artifacts

Build everything:

```bash
./gradlew build
```

Build executable JARs only:

```bash
./gradlew \
  :apps:auth-service:bootJar \
  :apps:profile-service:bootJar \
  :apps:education-service:bootJar \
  :apps:schedule-service:bootJar \
  :apps:assignment-service:bootJar \
  :apps:testing-service:bootJar \
  :apps:file-service:bootJar \
  :apps:analytics-service:bootJar \
  :apps:audit-service:bootJar \
  :apps:notification-service:bootJar \
  :apps:gateway:bootJar
```

## Local Infrastructure

The local Docker Compose stack is defined in `infra/docker/docker-compose.local.yml`.

It starts:

- PostgreSQL
- Redis
- Kafka
- MinIO
- `auth-service`
- `profile-service`
- `education-service`
- `schedule-service`
- `assignment-service`
- `testing-service`
- `file-service`
- `analytics-service`
- `audit-service`
- `notification-service`
- `gateway`

Initialization scripts:

- `infra/docker/init/init-db.sh`: creates service schemas
- `infra/docker/init/init-kafka.sh`: creates Kafka topics
- `infra/docker/init/init-minio.sh`: prepares object storage buckets

## Environment Configuration

Environment defaults live in `.env.example`. The local bootstrap script copies this file to `.env` on first run.

Main categories of environment variables:

- service ports
- replica counts for local compose scaling
- PostgreSQL configuration
- Redis configuration
- Kafka configuration
- MinIO configuration
- JWT key locations and issuer/audience
- internal shared secrets
- auth configuration
- education and schedule client configuration
- assignment, testing, and analytics configuration
- gateway rate limiting
- profile and file constraints
- notification realtime settings

## Ports

Default application ports:

- `8080` gateway
- `8081` auth-service
- `8082` profile-service
- `8083` file-service
- `8084` notification-service
- `8085` education-service
- `8086` assignment-service
- `8087` testing-service
- `8088` schedule-service
- `8089` analytics-service
- `8090` audit-service

Default infrastructure ports:

- `5432` PostgreSQL
- `6379` Redis
- `29092` Kafka
- `9000` MinIO API
- `9001` MinIO Console

## Persistence Model

Studium uses separate PostgreSQL schemas for service boundaries:

- `auth`
- `profile`
- `education`
- `schedule`
- `assignment`
- `testing`
- `file`
- `analytics`
- `notification`

This keeps ownership explicit while still allowing a single local PostgreSQL instance during development.

## Messaging Model

Kafka is used for asynchronous domain communication.

Topics initialized by the local bootstrap include:

- `auth.user-registered.v1`
- `auth.user-email-changed.v1`
- `auth.user-username-changed.v1`
- `auth.user-banned.v1`
- `auth.user-unbanned.v1`
- `schedule.override-created.v1`
- `schedule.lesson-cancelled.v1`
- `schedule.lesson-replaced.v1`
- `schedule.extra-lesson-created.v1`
- `assignment.assignment-created.v1`
- `assignment.assignment-updated.v1`
- `assignment.assignment-opened.v1`
- `assignment.assignment-submitted.v1`
- `assignment.grade-assigned.v1`
- `testing.test-published.v1`
- `testing.test-started.v1`
- `testing.test-completed.v1`
- `education.topic-opened.v1`

This allows services to react to state changes without tight runtime coupling.

## Observability

All services include Spring Boot Actuator and Micrometer Prometheus registry support.

Expect standard endpoints such as:

- `/actuator/health`
- `/actuator/info`
- `/actuator/metrics`
- `/actuator/prometheus`

The gateway and services also apply request correlation and structured error responses to improve traceability across the system.

## Developer Notes

### JWT keys

The project expects:

- `infra/keys/private.pem`
- `infra/keys/public.pem`

The local bootstrap script generates them automatically if they are missing.

### Internal service calls

Some service-to-service endpoints are intentionally not public. Those are protected by shared secret headers and should only be called by trusted internal services.

### Redis usage

Redis is used for:

- gateway rate limiting
- gateway ban-state cache
- profile caching
- notification realtime fanout
- auth-related cache/rate-limit data
- backend service caches where enabled

### MinIO usage

`file-service` is responsible for object storage interaction and expects MinIO buckets to exist before normal operation. Local init scripts prepare those buckets automatically.

### Frontend onboarding

Frontend-facing onboarding and source-of-truth references:

- `DESIGN.md`
- `ARCHITECTURE.md`
- `FRONTEND_IMPLEMENTATION.md`
- `docs/FRONTEND_API_START.md`
- `docs/FRONTEND_SCREEN_API_MAP.md`
- `docs/BACKEND_VERIFICATION.md`

## Suggested First Steps for a New Contributor

1. Read `settings.gradle` to understand the module layout.
2. Start the local stack with `./infra/scripts/local/start-local.sh`.
3. Verify container and service status with `docker compose --env-file .env -f infra/docker/docker-compose.local.yml ps`.
4. Run `./gradlew test`.
5. Start from the gateway route configuration and the auth-service flows if you want to understand the core request lifecycle first.

## Module Summary

If you want a quick mental model:

- `gateway` is the front door
- `auth-service` is identity and authorization
- `profile-service` is user profile data
- `education-service` is groups, subjects, topics, and memberships
- `schedule-service` is semesters, rooms, templates, overrides, and resolved lessons
- `assignment-service` is assignments, submissions, and grades
- `testing-service` is tests, questions, answers, and results
- `file-service` is object metadata and storage integration
- `analytics-service` is academic read analytics
- `notification-service` is persistent and realtime user notifications
- `libs/*` contains the shared contracts and foundations used by all services

## Current Scope

This repository currently includes a complete local development environment and service implementation for the modules listed above. Deployment automation beyond the local environment is not part of the active repository state, so this README documents the local workflow and the code that actually exists in the tree today.
