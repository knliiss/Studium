# ARCHITECTURE.md

This document is a mandatory source of truth for future frontend agents working on Studium.
Read it before implementing API clients, auth flow, routing, feature modules, or screen wiring.

## 1. System Overview

Studium currently provides a Java 21 microservice LMS backend.

Core facts:

- Java 21 microservice LMS backend
- single public gateway entrypoint
- frontend must call the gateway only
- each backend service owns its own data
- Kafka is used for async domain events
- PostgreSQL schemas are separated by service

The frontend must align with the current backend documentation and must not invent missing API surface.

## 2. Public API Rule

Hard rule:

The frontend MUST call only the gateway.

Default local base URL:

`http://localhost:8080`

The frontend must NOT call downstream service ports directly such as `8081`, `8085`, `8088`, or `8090`.

## 3. Backend Services

Current services and frontend relevance:

| Service | Responsibility | Frontend-facing endpoint area | Frontend call rule |
| --- | --- | --- | --- |
| `gateway` | public entrypoint, routing, JWT validation, BFF aggregation | `/api/auth/**`, `/api/profile/**`, `/api/v1/**`, `/api/files/**`, `/api/notifications/**` | frontend always calls these paths through gateway |
| `auth-service` | auth, registration, refresh, MFA, password reset, admin user management | `/api/auth/**`, `/api/admin/**` through gateway | never call service port directly |
| `profile-service` | current profile, avatar binding | `/api/profile/**` through gateway | gateway path only |
| `education-service` | groups, subjects, topics, memberships | `/api/v1/education/**` through gateway | gateway path only |
| `schedule-service` | semesters, slots, rooms, templates, overrides, resolved schedules | `/api/v1/schedule/**` through gateway | gateway path only |
| `assignment-service` | assignments, submissions, grades, assignment dashboards | `/api/v1/assignments/**`, `/api/v1/submissions/**`, `/api/v1/grades/**` through gateway | gateway path only |
| `testing-service` | tests, questions, answers, test results, testing dashboards | `/api/v1/testing/**` through gateway | gateway path only |
| `file-service` | upload, metadata, preview, download, lifecycle | `/api/files/**` through gateway | gateway path only |
| `analytics-service` | student, teacher, group, subject, admin analytics | `/api/v1/analytics/**` through gateway | gateway path only |
| `notification-service` | notification list, unread count, mark read, realtime support | `/api/notifications/**` and `/ws/notifications/**` through gateway | gateway path only |
| `audit-service` | audit read API for admin use | `/api/v1/audit/**` through gateway | gateway path only |

## 4. Gateway Responsibilities

The gateway handles:

- routing
- JWT validation
- access control
- selected BFF aggregation
- dashboard aggregation
- schedule `/api/v1/schedule/me/**` aggregation
- global search aggregation under `/api/v1/search`

The gateway must NOT contain:

- schedule business logic
- grading logic
- test scoring logic
- analytics calculations
- file ownership business logic

Frontend consequence:

- if a gateway BFF endpoint exists, use it instead of manually combining multiple downstream endpoints in the UI

## 5. Auth Architecture

Current login flow:

1. frontend submits `POST /api/auth/login`
2. request body uses `LoginRequest`
3. login is username-based in the current backend:
   - `username`
   - `password`
4. successful login returns `AuthResponse`
5. current `AuthResponse` fields are:
   - `status`
   - `accessToken`
   - `refreshToken`
   - `user`
   - `mfaChallenge`

Authorization header format:

`Authorization: Bearer <accessToken>`

Access token storage expectations:

- keep session state in one centralized auth module
- inject the bearer token from a shared API client layer
- do not scatter token handling across pages or components
- do not hardcode tokens in requests
- if persistent session storage is introduced later, keep it inside the auth/session layer only

401 behavior:

- token missing, invalid, or expired
- frontend should clear session state and redirect to login or show a re-auth flow

403 behavior:

- token is valid, but the role or ownership does not allow the action
- frontend should show access denied UI or a clear inline message

Current role model:

- `OWNER`
- `ADMIN`
- `TEACHER`
- `STUDENT`

Frontend role behavior:

- hide unavailable navigation items
- hide unavailable actions
- still handle backend `403`
- never rely only on UI checks for security

## 6. API Client Architecture

The frontend must use a centralized API client layer.

It must support:

- base URL from environment
- auth token injection
- shared error parsing
- typed request and response models
- pagination helpers
- file upload helpers
- file download helpers
- no duplicated fetch logic

Shared error envelope fields currently returned by backend:

- `timestamp`
- `status`
- `error`
- `errorCode`
- `message`
- `path`
- `requestId`
- `details`

The API client should parse these fields once and expose a consistent frontend error shape.

## 7. Frontend Suggested Structure

There is no frontend source tree in the current repository state.
If a frontend is introduced, use this structure unless an existing frontend structure already exists:

- `src/app`
- `src/pages`
- `src/features`
- `src/entities`
- `src/widgets`
- `src/shared/api`
- `src/shared/ui`
- `src/shared/lib`
- `src/shared/config`
- `src/shared/types`

If a frontend already exists when future work starts, follow the existing structure instead of forcing a new one.

## 8. Main Frontend Domains

### Auth

- login
- register
- password reset
- MFA if supported

### Profile

- current profile
- avatar
- profile settings

### Education

- groups
- subjects
- topics

### Schedule

- my schedule
- group schedule
- teacher schedule
- room schedule
- semester, slot, room, and template management
- import
- conflict check
- calendar export

### Assignments

- assignment list
- assignment detail
- submission
- comments
- grading
- lifecycle

### Testing

- tests
- questions
- answers
- attempts and results
- lifecycle

### Files

- upload
- metadata
- preview
- download

### Notifications

- list
- unread count
- mark read
- realtime updates if websocket is wired

### Analytics

- student analytics
- teacher analytics
- admin analytics

### Dashboard

- student dashboard
- teacher dashboard
- admin dashboard

### Search

- global search

### Audit

- admin audit log

## 9. Data Flow Rules

Mandatory frontend data flow rules:

- use dashboard endpoints for dashboard pages
- use the API contract as the source of truth
- do not manually assemble dashboards if a gateway BFF endpoint exists
- handle empty arrays and empty pages
- handle paginated responses consistently
- handle access denied gracefully

Specific current examples:

- use `GET /api/v1/dashboard/student/me` for student dashboard
- use `GET /api/v1/dashboard/teacher/me` for teacher dashboard
- use `GET /api/v1/dashboard/admin/overview` for admin dashboard
- use `GET /api/v1/schedule/me/**` for current-user schedule displays
- use `GET /api/v1/search` only for admin or owner search UI

## 10. File Handling

Rules:

- upload through `POST /api/files`
- preview through `GET /api/files/{fileId}/preview`
- download through `GET /api/files/{fileId}/download`
- read metadata through `GET /api/files/{fileId}` or `/metadata`
- do not access MinIO directly from the frontend
- unsupported previews must show fallback UI

Current public file lifecycle endpoints also exist:

- `PUT /api/files/{fileId}/lifecycle/active`
- `PUT /api/files/{fileId}/lifecycle/orphaned`

Use them only where the product actually needs lifecycle control.

## 11. Schedule Handling

Important schedule rules:

- `lessonType` and `lessonFormat` are separate fields
- `lessonType`: `LECTURE`, `PRACTICAL`, `LABORATORY`
- `lessonFormat`: `ONLINE`, `OFFLINE`
- `weekType`: `ALL`, `ODD`, `EVEN`
- overrides may cancel, replace, or add lessons
- frontend should use resolved schedule endpoints for display
- frontend should call conflict check before saving schedule changes

Current resolved display endpoints:

- `GET /api/v1/schedule/me/week`
- `GET /api/v1/schedule/me/range`
- `GET /api/v1/schedule/groups/{groupId}/week`
- `GET /api/v1/schedule/groups/{groupId}/range`
- `GET /api/v1/schedule/teachers/{teacherId}/week`
- `GET /api/v1/schedule/teachers/{teacherId}/range`
- `GET /api/v1/schedule/rooms/{roomId}/week`
- `GET /api/v1/schedule/rooms/{roomId}/range`
- `GET /api/v1/schedule/search`

## 12. Notification Handling

Notification rules:

- use unread count endpoint
- use paginated list endpoint
- support mark one as read
- support mark all as read
- schedule notification payloads include lesson-related metadata
- deadline reminder notifications exist in the backend
- websocket support can be used for realtime updates if the frontend wires it later

Current primary endpoints:

- `GET /api/notifications/me`
- `GET /api/notifications/me/unread-count`
- `PATCH /api/notifications/{notificationId}/read`
- `PATCH /api/notifications/me/read-all`

## 13. Pagination / Filtering / Sorting

Project conventions:

- `page`
- `size`
- `sortBy`
- `direction`

Special note:

- `GET /api/v1/audit` still accepts `sort` as a compatibility alias, but `sortBy` is the standard convention

Frontend must not assume unbounded lists.

## 14. Error Handling

Frontend UI behavior must follow backend error classes:

- `400` validation -> show field errors or request-level validation message
- `401` -> redirect to login or trigger session recovery
- `403` -> access denied page or inline access message
- `404` -> not found or empty resource state
- `409` conflict -> show conflict panel or user-resolvable message
- `500` -> generic error state with visible `requestId` when present

Common domain codes already relevant for UI:

- `VALIDATION_FAILED`
- `ACCESS_DENIED`
- `ENTITY_NOT_FOUND`
- `SCHEDULE_CONFLICT`
- `FILE_PREVIEW_NOT_AVAILABLE`
- `INVALID_DATE_RANGE`
- `DEADLINE_EXPIRED`
- `MAX_ATTEMPTS_EXCEEDED`
- `MAX_SUBMISSIONS_EXCEEDED`
- `FILE_TYPE_NOT_ALLOWED`
- `FILE_TOO_LARGE`
- `INVALID_STATE_TRANSITION`
- `TEST_NOT_AVAILABLE`
- `TEST_TIME_EXPIRED`
- `AUDIT_EVENT_NOT_FOUND`

## 15. Local Development

Backend local startup:

- `./infra/scripts/local/start-local.sh`
- `./infra/scripts/local/start-local.sh --skip-build`
- `infra\scripts\local\start-local.bat`
- `infra\scripts\local\start-local.bat --skip-build`

Frontend-relevant local documentation:

- API contract: `docs/API_CONTRACT.md`
- frontend API onboarding: `docs/FRONTEND_API_START.md`
- frontend screen map: `docs/FRONTEND_SCREEN_API_MAP.md`
- backend verification baseline: `docs/BACKEND_VERIFICATION.md`
- Postman docs: `docs/postman/README.md`

Demo credentials for local development are documented in the API contract and Postman docs.

## 16. Frontend Implementation Rules

Future frontend agents must:

- read `DESIGN.md`
- read `ARCHITECTURE.md`
- read `docs/API_CONTRACT.md`
- read `docs/FRONTEND_API_START.md`
- read `docs/FRONTEND_SCREEN_API_MAP.md`
- not invent endpoints
- not hardcode fake data
- not bypass the gateway
- not call downstream services directly
- not expose screens for unauthorized roles
- keep UI consistent with the design system
