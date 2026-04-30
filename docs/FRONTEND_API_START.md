# Frontend API Start

Use the gateway as the only frontend base URL:

- Local base URL: `http://localhost:8080`
- API prefix: `/api`
- Authenticated requests use `Authorization: Bearer <access-token>`

Detailed screen-to-endpoint mapping is maintained in [FRONTEND_SCREEN_API_MAP.md](/Users/knalis/Documents/dev/java/Studium/docs/FRONTEND_SCREEN_API_MAP.md).

## Local Startup

- Linux/macOS: `./infra/scripts/local/start-local.sh`
- Linux/macOS without Gradle build: `./infra/scripts/local/start-local.sh --skip-build`
- Windows: `infra\scripts\local\start-local.bat`
- Windows without Gradle build: `infra\scripts\local\start-local.bat --skip-build`

The startup scripts create `.env` when missing, generate `infra/keys/private.pem` and `infra/keys/public.pem` when missing, build service boot JARs unless skipped, start the full Docker Compose stack, and run demo seed when enabled.
On Windows, automatic demo seed requires `bash` to be available in `PATH`.

## Demo Credentials

- `owner / ChangeMe123!`
- `admin.demo / DemoPass123!`
- `teacher.alpha / DemoPass123!`
- `teacher.beta / DemoPass123!`
- `student.one / DemoPass123!`

## Auth Flow

- Login: `POST /api/auth/login`
- Refresh: `POST /api/auth/refresh`
- Logout: `POST /api/auth/logout`
- MFA-capable flows may return challenge state through `AuthResponse`. Frontend should handle `MFA_REQUIRED` and continue with:
- `POST /api/auth/mfa/challenges/dispatch`
- `POST /api/auth/mfa/challenges/verify`
- `GET /api/auth/mfa/challenges/status`

## Main Screens and Endpoints

- App shell / current user:
  `GET /api/profile/me`
  `PATCH /api/profile/me`
- Main dashboard:
  `GET /api/v1/dashboard/student/me`
  `GET /api/v1/dashboard/teacher/me`
  `GET /api/v1/dashboard/admin/overview`
- Schedule:
  `GET /api/v1/schedule/me/week`
  `GET /api/v1/schedule/me/range`
  `GET /api/v1/schedule/me/export.ics`
  `GET /api/v1/schedule/search`
- Assignments:
  `GET /api/v1/assignments/topic/{topicId}`
  `GET /api/v1/assignments/{id}`
  `POST /api/v1/submissions`
  `GET /api/v1/submissions/{submissionId}/comments`
  `POST /api/v1/submissions/{submissionId}/comments`
- Teacher assignment workflows:
  `GET /api/v1/submissions/assignment/{assignmentId}`
  `POST /api/v1/grades`
  `POST /api/v1/assignments/{id}/publish`
  `POST /api/v1/assignments/{id}/archive`
- Testing:
  `GET /api/v1/testing/tests/topic/{topicId}`
  `POST /api/v1/testing/tests/{id}/start`
  `POST /api/v1/testing/results`
- Teacher testing workflows:
  `POST /api/v1/testing/tests`
  `POST /api/v1/testing/tests/{id}/publish`
  `POST /api/v1/testing/tests/{id}/close`
  `POST /api/v1/testing/tests/{id}/archive`
  `GET /api/v1/testing/results/test/{testId}`
  `PATCH /api/v1/testing/results/{id}/score`
- Notifications:
  `GET /api/notifications/me`
  `GET /api/notifications/me/unread-count`
  `PATCH /api/notifications/{notificationId}/read`
  `PATCH /api/notifications/me/read-all`
- Files:
  `POST /api/files`
  `GET /api/files/{fileId}`
  `GET /api/files/{fileId}/download`
  `GET /api/files/{fileId}/preview`
- Analytics:
  `GET /api/v1/analytics/students/{userId}`
  `GET /api/v1/analytics/students/{userId}/risk`
  `GET /api/v1/analytics/teachers/{teacherId}/groups-at-risk`
- Search:
  `GET /api/v1/search?q=<text>&page=0&size=20`
- Audit:
  `GET /api/v1/audit`
  `GET /api/v1/audit/{auditEventId}`

## Pagination Convention

- Standard query parameters: `page`, `size`, `sortBy`, `direction`
- Default page is `0`
- Default size is usually `20`
- `direction` is `asc` or `desc`

## Common Error Format

All services return the shared error envelope:

```json
{
  "timestamp": "2026-04-26T10:00:00Z",
  "status": 403,
  "error": "Forbidden",
  "errorCode": "ACCESS_DENIED",
  "message": "Access denied",
  "path": "/api/v1/analytics/students/...",
  "requestId": "01HXYZ..."
}
```

Common frontend-relevant codes:

- `VALIDATION_FAILED`
- `ACCESS_DENIED`
- `ENTITY_NOT_FOUND`
- `DEADLINE_EXPIRED`
- `MAX_SUBMISSIONS_EXCEEDED`
- `MAX_ATTEMPTS_EXCEEDED`
- `INVALID_DATE_RANGE`
- `TEST_NOT_AVAILABLE`
- `TEST_TIME_EXPIRED`
- `FILE_PREVIEW_NOT_AVAILABLE`

## Notes

- Use gateway routes only. Do not call downstream services directly from the frontend.
- Global search is currently restricted to `OWNER` and `ADMIN`.
- Student analytics endpoints are self-scoped for non-admin users.
- Teacher analytics and teacher dashboards are self-scoped for non-admin users.
