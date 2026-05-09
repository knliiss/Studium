# API Contract

This document reflects the current public HTTP API exposed through `gateway` and the downstream public controllers present in the repository. It is intentionally tied to the existing codebase and DTO classes under `dev.knalis.*`.

Current verification baseline:

- Collection: `docs/postman/Studium.postman_collection.json`
- Environment: `docs/postman/Studium.local.postman_environment.json`
- Latest successful local run: `149` tests, `149` passed, `0` failed, `0` errors

## Access Matrix

| Role | Current baseline access |
| --- | --- |
| `ROLE_OWNER` | Full access to all current public endpoints |
| `ROLE_ADMIN` | Full administrative access across auth, education, schedule, assignments, testing, analytics, audit, dashboard, and search |
| `ROLE_TEACHER` | Read schedule, manage own assignment/test drafts and publications, grade and review submissions on owned assignments, teacher analytics, teacher dashboard, notifications |
| `ROLE_STUDENT` | Own profile, own schedule BFF, schedule reads, assignment submission, test start/result submit, own analytics, student dashboard, notifications |

Notes:

- `gateway` is the only public entrypoint.
- Shared error envelope comes from `libs/shared-web`.
- Validation is performed on request DTOs with `jakarta.validation`.
- Pagination and sortable lists use `page`, `size`, `sortBy`, and `direction`.
- `sort` is accepted as a backward-compatible alias on `GET /api/v1/audit`, but `sortBy` is the standard contract.

## Common Error Contract

All new and existing servlet services use the shared error response:

```json
{
  "timestamp": "2026-04-26T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "errorCode": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/v1/schedule/templates/import",
  "requestId": "01HXYZ...",
  "details": {
    "fieldErrors": {
      "items[0].lessonType": ["must not be null"]
    }
  }
}
```

Common domain codes already in use include:

- `UNAUTHORIZED`
- `VALIDATION_FAILED`
- `ACCESS_DENIED`
- `ENTITY_NOT_FOUND`
- `SCHEDULE_CONFLICT`
- `DEADLINE_EXPIRED`
- `MAX_SUBMISSIONS_EXCEEDED`
- `MAX_ATTEMPTS_EXCEEDED`
- `FILE_TYPE_NOT_ALLOWED`
- `FILE_TOO_LARGE`
- `INVALID_ASSIGNMENT_STATE`
- `INVALID_TEST_STATE`
- `INVALID_STATE_TRANSITION`
- `TEST_NOT_AVAILABLE`
- `TEST_TIME_EXPIRED`
- `FILE_PREVIEW_NOT_AVAILABLE`
- `LECTURE_NOT_FOUND`
- `LECTURE_NOT_ACCESSIBLE`
- `LECTURE_ATTACHMENT_NOT_FOUND`
- `ASSIGNMENT_ATTACHMENT_NOT_FOUND`
- `SUBMISSION_ATTACHMENT_NOT_FOUND`
- `ASSIGNMENT_FILE_ACCESS_DENIED`
- `SUBMISSION_FILE_ACCESS_DENIED`
- `ASSIGNMENT_NOT_ACCESSIBLE`
- `SUBMISSION_NOT_ACCESSIBLE`
- `FILE_ACCESS_DENIED`
- `FILE_SERVICE_UNAVAILABLE`
- `FILE_ATTACHMENT_NOT_ALLOWED`
- `INVALID_DATE_RANGE`
- `AUDIT_EVENT_NOT_FOUND`
- `SPECIALTY_NOT_FOUND`
- `SPECIALTY_CODE_ALREADY_EXISTS`
- `SPECIALTY_HAS_DEPENDENCIES`
- `STREAM_NOT_FOUND`
- `STREAM_HAS_GROUPS`
- `STREAM_SPECIALTY_YEAR_MISMATCH`
- `CURRICULUM_PLAN_NOT_FOUND`
- `CURRICULUM_PLAN_ALREADY_EXISTS`
- `CURRICULUM_PLAN_INVALID_COUNTS`
- `GROUP_CURRICULUM_OVERRIDE_NOT_FOUND`
- `GROUP_CURRICULUM_OVERRIDE_ALREADY_EXISTS`
- `ROOM_CAPABILITY_NOT_FOUND`
- `ROOM_CAPABILITY_ALREADY_EXISTS`
- `ROOM_CAPABILITY_INVALID_PRIORITY`

## Public Endpoints

### Gateway BFF

- `GET /api/v1/schedule/me/week`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Request DTO: none, query `startDate`
  Response DTO: `dev.knalis.gateway.dto.ResolvedLessonResponse[]`
- `GET /api/v1/schedule/me/range`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Request DTO: none, query `dateFrom`, `dateTo`
  Response DTO: `dev.knalis.gateway.dto.ResolvedLessonResponse[]`
- `GET /api/v1/schedule/me/export.ics`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Request DTO: none, optional query `dateFrom`, `dateTo`
  Response DTO: `text/calendar`
- `GET /api/v1/dashboard/student/me`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Response DTO: `dev.knalis.gateway.dto.StudentDashboardResponse`
  Notes: aggregates current-user schedule, deadlines, grades, notifications, analytics, pending assignments, and available tests
- `GET /api/v1/dashboard/teacher/me`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Response DTO: `dev.knalis.gateway.dto.TeacherDashboardResponse`
  Notes: aggregates teacher-scoped lessons, assignment review queues, active assignments/tests, at-risk groups, and schedule-change notifications
- `GET /api/v1/dashboard/admin/overview`
  Role access: `OWNER`, `ADMIN`
  Response DTO: `dev.knalis.gateway.dto.AdminDashboardOverviewResponse`
  Notes: aggregates auth, education, analytics, active deadlines, and recent audit events
- `GET /api/v1/search`
  Role access: `OWNER`, `ADMIN`
  Query: `q`, `page`, `size`, `sortBy`, `direction`
  Response DTO: `dev.knalis.gateway.dto.SearchPageResponse`
  Notes: current implementation aggregates `education-service`, `assignment-service`, and `testing-service` under a secure admin-only baseline until broader ownership scoping is available

### Auth Service

- `POST /api/auth/register`
  Role access: public
  Request DTO: `RegisterRequest`
  Response DTO: `AuthResponse`
- `POST /api/auth/login`
  Role access: public
  Request DTO: `LoginRequest`
  Response DTO: `AuthResponse`
- `POST /api/auth/refresh`
  Role access: public
  Request DTO: `RefreshTokenRequest`
  Response DTO: `AuthResponse`
- `POST /api/auth/logout`
  Role access: authenticated
  Request DTO: `LogoutRequest`
  Response DTO: empty body
- `POST /api/auth/password-reset/request`
  Role access: public
  Request DTO: `RequestPasswordResetRequest`
  Response DTO: `AcceptedActionResponse`
- `POST /api/auth/password-reset/confirm`
  Role access: public
  Request DTO: `ConfirmPasswordResetRequest`
  Response DTO: empty body
- `PATCH /api/auth/me/username`
  Role access: authenticated
  Request DTO: `UpdateUsernameRequest`
  Response DTO: `UserAuthResponse`
- `PATCH /api/auth/me/email`
  Role access: authenticated
  Request DTO: `UpdateEmailRequest`
  Response DTO: `UserAuthResponse`
- `PATCH /api/auth/me/password`
  Role access: authenticated
  Request DTO: `ChangePasswordRequest`
  Response DTO: empty body
- `POST /api/auth/users/lookup`
  Role access: authenticated
  Request DTO: `UserDirectoryLookupRequest`
  Response DTO: `UserSummaryResponse[]`
- `GET /api/auth/mfa/methods`
  Role access: authenticated
  Response DTO: `MfaMethodsResponse`
- `POST /api/auth/mfa/totp/setup`
  Role access: authenticated
  Request DTO: `SetupTotpRequest`
  Response DTO: `TotpSetupResponse`
- `POST /api/auth/mfa/totp/confirm`
  Role access: authenticated
  Request DTO: `ConfirmTotpSetupRequest`
  Response DTO: `MfaMethodsResponse`
- `POST /api/auth/mfa/methods/disable`
  Role access: authenticated
  Request DTO: `DisableMfaMethodRequest`
  Response DTO: `MfaMethodsResponse`
- `POST /api/auth/mfa/challenges/dispatch`
  Role access: public challenge flow
  Request DTO: `MfaDispatchChallengeRequest`
  Response DTO: `MfaChallengeResponse`
- `POST /api/auth/mfa/challenges/verify`
  Role access: public challenge flow
  Request DTO: `MfaVerifyChallengeRequest`
  Response DTO: `AuthResponse`
- `GET /api/auth/mfa/challenges/status`
  Role access: public challenge flow
  Response DTO: `MfaChallengeResponse`
- `GET /api/admin/users/statistics`
  Role access: `OWNER`, `ADMIN`
  Response DTO: `AdminUserStatsResponse`
- `GET /api/admin/users`
  Role access: `OWNER`, `ADMIN`
  Query: `page`, `size`, `sortBy`, `direction`, `search`, `role`, `banned`
  Response DTO: `AdminUserPageResponse`
- `GET /api/admin/users/{userId}`
  Role access: `OWNER`, `ADMIN`
  Response DTO: `AdminUserResponse`
- `PATCH /api/admin/users/{userId}/roles`
  Role access: `OWNER`, `ADMIN`
  Request DTO: `AdminUpdateUserRolesRequest`
  Response DTO: `AdminUserResponse`
  Notes: normal role management cannot assign `OWNER`; `ADMIN` cannot assign `ADMIN` or `OWNER`; `OWNER` can assign `ADMIN`, `TEACHER`, `STUDENT`, and `USER`.
- `POST /api/admin/users/{userId}/ban`
  Role access: `OWNER`, `ADMIN`
  Request DTO: `AdminBanUserRequest`
  Response DTO: empty body
- `POST /api/admin/users/{userId}/unban`
  Role access: `OWNER`, `ADMIN`
  Response DTO: empty body
- `POST /api/admin/users/{userId}/revoke-sessions`
  Role access: `OWNER`, `ADMIN`
  Response DTO: empty body

### Profile Service

- `GET /api/profile/me`
  Role access: authenticated
  Response DTO: `UserProfileResponse`
- `PATCH /api/profile/me`
  Role access: authenticated
  Request DTO: `UpdateMyProfileRequest`
  Response DTO: `UserProfileResponse`
- `PUT /api/profile/me/avatar`
  Role access: authenticated
  Request DTO: `UpdateAvatarRequest`
  Response DTO: `UserProfileResponse`
- `DELETE /api/profile/me/avatar`
  Role access: authenticated
  Response DTO: `UserProfileResponse`

### Education Service

- `POST /api/v1/education/groups`
  Role access: `OWNER`, `ADMIN`
  Request DTO: `CreateGroupRequest`
  Response DTO: `GroupResponse`
- `GET /api/v1/education/groups`
  Role access: authenticated
  Query: `page`, `size`, `sortBy`, `direction`, optional `q`
  Response DTO: `GroupPageResponse`
- `GET /api/v1/education/groups/{id}`
  Role access: authenticated
  Response DTO: `GroupResponse`
- `PUT /api/v1/education/groups/{id}`
  Role access: `OWNER`, `ADMIN`
  Request DTO: `UpdateGroupRequest`
  Response DTO: `GroupResponse`
  Notes: supports `specialtyId`, `studyYear`, `streamId`, `subgroupMode`; stream specialty/year must match group specialty/year when both are set
- `GET /api/v1/education/groups/{groupId}/resolved-subjects`
  Role access: authenticated (scoped by group membership/teacher subject assignment/admin ownership)
  Query: optional `semesterNumber`
  Response DTO: `ResolvedGroupSubjectResponse[]`
  Notes: response merges active curriculum plans, group overrides, and direct subject-group bindings
- `GET /api/v1/education/groups/{groupId}/curriculum-overrides`
  Role access: authenticated (read scope applies)
  Response DTO: `GroupCurriculumOverrideResponse[]`
- `POST /api/v1/education/groups/{groupId}/curriculum-overrides`
  Role access: `OWNER`, `ADMIN`
  Request DTO: `CreateGroupCurriculumOverrideRequest`
  Response DTO: `GroupCurriculumOverrideResponse`
- `PUT /api/v1/education/groups/{groupId}/curriculum-overrides/{overrideId}`
  Role access: `OWNER`, `ADMIN`
  Request DTO: `UpdateGroupCurriculumOverrideRequest`
  Response DTO: `GroupCurriculumOverrideResponse`
- `DELETE /api/v1/education/groups/{groupId}/curriculum-overrides/{overrideId}`
  Role access: `OWNER`, `ADMIN`
- `GET /api/v1/education/specialties`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Query: optional `active`
  Response DTO: `SpecialtyResponse[]`
- `GET /api/v1/education/specialties/{id}`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Response DTO: `SpecialtyResponse`
- `POST /api/v1/education/specialties`
  Role access: `OWNER`, `ADMIN`
  Request DTO: `CreateSpecialtyRequest`
  Response DTO: `SpecialtyResponse`
- `PUT /api/v1/education/specialties/{id}`
  Role access: `OWNER`, `ADMIN`
  Request DTO: `UpdateSpecialtyRequest`
  Response DTO: `SpecialtyResponse`
- `POST /api/v1/education/specialties/{id}/archive`
  Role access: `OWNER`, `ADMIN`
  Response DTO: `SpecialtyResponse`
- `POST /api/v1/education/specialties/{id}/restore`
  Role access: `OWNER`, `ADMIN`
  Response DTO: `SpecialtyResponse`
- `GET /api/v1/education/streams`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Query: optional `specialtyId`, `studyYear`, `active`
  Response DTO: `StreamResponse[]`
- `GET /api/v1/education/streams/{id}`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Response DTO: `StreamResponse`
- `POST /api/v1/education/streams`
  Role access: `OWNER`, `ADMIN`
  Request DTO: `CreateStreamRequest`
  Response DTO: `StreamResponse`
- `PUT /api/v1/education/streams/{id}`
  Role access: `OWNER`, `ADMIN`
  Request DTO: `UpdateStreamRequest`
  Response DTO: `StreamResponse`
- `POST /api/v1/education/streams/{id}/archive`
  Role access: `OWNER`, `ADMIN`
  Response DTO: `StreamResponse`
- `POST /api/v1/education/streams/{id}/restore`
  Role access: `OWNER`, `ADMIN`
  Response DTO: `StreamResponse`
- `GET /api/v1/education/streams/{id}/groups`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Response DTO: `GroupResponse[]`
- `GET /api/v1/education/curriculum-plans`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Query: optional `specialtyId`, `studyYear`, `semesterNumber`, `subjectId`, `active`
  Response DTO: `CurriculumPlanResponse[]`
- `GET /api/v1/education/curriculum-plans/{id}`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Response DTO: `CurriculumPlanResponse`
- `POST /api/v1/education/curriculum-plans`
  Role access: `OWNER`, `ADMIN`
  Request DTO: `CreateCurriculumPlanRequest`
  Response DTO: `CurriculumPlanResponse`
- `PUT /api/v1/education/curriculum-plans/{id}`
  Role access: `OWNER`, `ADMIN`
  Request DTO: `UpdateCurriculumPlanRequest`
  Response DTO: `CurriculumPlanResponse`
- `POST /api/v1/education/curriculum-plans/{id}/archive`
  Role access: `OWNER`, `ADMIN`
  Response DTO: `CurriculumPlanResponse`
- `POST /api/v1/education/curriculum-plans/{id}/restore`
  Role access: `OWNER`, `ADMIN`
  Response DTO: `CurriculumPlanResponse`
- `GET /api/v1/education/groups/by-user/{userId}`
  Role access: `OWNER`, `ADMIN`, or self
  Response DTO: `GroupMembershipResponse[]`
- `GET /api/v1/education/groups/{groupId}/students`
  Role access: authenticated
  Response DTO: `GroupStudentMembershipResponse[]`
- `POST /api/v1/education/groups/{groupId}/students`
  Role access: `OWNER`, `ADMIN`
  Request DTO: `CreateGroupStudentRequest`
  Response DTO: `GroupStudentMembershipResponse`
- `PATCH /api/v1/education/groups/{groupId}/students/{userId}`
  Role access: `OWNER`, `ADMIN`
  Request DTO: `UpdateGroupStudentRequest`
  Response DTO: `GroupStudentMembershipResponse`
- `DELETE /api/v1/education/groups/{groupId}/students/{userId}`
  Role access: `OWNER`, `ADMIN`
- `POST /api/v1/education/subjects`
  Role access: `OWNER`, `ADMIN`
  Request DTO: `CreateSubjectRequest`
  Response DTO: `SubjectResponse`
  Notes: initial creation requires only a subject name. Description, status, `groupIds`, and `teacherIds` are optional. Subjects can exist before they are linked to a group, so `SubjectResponse.groupId` may be `null` until a primary group is assigned.
- `GET /api/v1/education/subjects`
  Role access: authenticated
  Query: `page`, `size`, `sortBy`, `direction`, optional `q`
  Response DTO: `SubjectPageResponse`
- `GET /api/v1/education/subjects/{id}`
  Role access: authenticated
  Response DTO: `SubjectResponse`
- `PUT /api/v1/education/subjects/{id}`
  Role access: `OWNER`, `ADMIN`
  Request DTO: `UpdateSubjectRequest`
  Response DTO: `SubjectResponse`
- `GET /api/v1/education/subjects/group/{groupId}`
  Role access: authenticated
  Query: `page`, `size`, `sortBy`, `direction`
  Response DTO: `SubjectPageResponse`
- `POST /api/v1/education/topics`
  Role access: `OWNER`, `ADMIN`, assigned `TEACHER`
  Request DTO: `CreateTopicRequest`
  Response DTO: `TopicResponse`
- `PATCH /api/v1/education/topics/{topicId}`
  Role access: `OWNER`, `ADMIN`, assigned `TEACHER`
  Request DTO: `UpdateTopicRequest`
  Response DTO: `TopicResponse`
- `PATCH /api/v1/education/topics/subject/{subjectId}/reorder`
  Role access: `OWNER`, `ADMIN`, assigned `TEACHER`
  Request DTO: `ReorderTopicsRequest`
  Response DTO: `TopicResponse[]`
- `GET /api/v1/education/topics/subject/{subjectId}`
  Role access: authenticated
  Query: `page`, `size`, `sortBy`, `direction`
  Response DTO: `TopicPageResponse`
- `POST /api/v1/education/subjects/{subjectId}/topics/{topicId}/lectures`
  Role access: `OWNER`, `ADMIN`, assigned `TEACHER`
  Request DTO: `CreateLectureRequest`
  Response DTO: `LectureResponse`
  Notes: lectures are created in `DRAFT` status
- `GET /api/v1/education/lectures/{lectureId}`
  Role access: authenticated
  Response DTO: `LectureResponse`
  Notes: students can read only `PUBLISHED`/`CLOSED` lectures in subjects available to their groups
- `GET /api/v1/education/topics/{topicId}/lectures`
  Role access: authenticated
  Query: `page`, `size`, `sortBy`, `direction`
  Response DTO: `LecturePageResponse`
  Notes: student responses exclude `DRAFT` and `ARCHIVED`
- `PUT /api/v1/education/lectures/{lectureId}`
  Role access: `OWNER`, `ADMIN`, assigned `TEACHER`
  Request DTO: `UpdateLectureRequest`
  Response DTO: `LectureResponse`
  Notes: archived lectures must be restored before editing
- `PATCH /api/v1/education/lectures/{lectureId}/position`
  Role access: `OWNER`, `ADMIN`, assigned `TEACHER`
  Request DTO: `MoveLectureRequest`
  Response DTO: `LectureResponse`
- `POST /api/v1/education/lectures/{lectureId}/publish`
  Role access: `OWNER`, `ADMIN`, assigned `TEACHER`
  Response DTO: `LectureResponse`
- `POST /api/v1/education/lectures/{lectureId}/close`
  Role access: `OWNER`, `ADMIN`, assigned `TEACHER`
  Response DTO: `LectureResponse`
- `POST /api/v1/education/lectures/{lectureId}/reopen`
  Role access: `OWNER`, `ADMIN`, assigned `TEACHER`
  Response DTO: `LectureResponse`
- `POST /api/v1/education/lectures/{lectureId}/archive`
  Role access: `OWNER`, `ADMIN`, assigned `TEACHER`
  Response DTO: `LectureResponse`
- `POST /api/v1/education/lectures/{lectureId}/restore`
  Role access: `OWNER`, `ADMIN`, assigned `TEACHER`
  Response DTO: `LectureResponse`
  Notes: restore transition is `ARCHIVED -> DRAFT`
- `DELETE /api/v1/education/lectures/{lectureId}`
  Role access: `OWNER`, `ADMIN`, assigned `TEACHER`
  Response DTO: empty body
  Notes: hard delete is allowed only for archived lectures without attachments
- `GET /api/v1/education/lectures/{lectureId}/attachments`
  Role access: authenticated
  Response DTO: `LectureAttachmentResponse[]`
  Notes: same lecture read access rules apply as for lecture details (`PUBLISHED`/`CLOSED` for students, assigned teacher or admin/owner for management roles); response metadata (`originalFileName`, `contentType`, `sizeBytes`, `createdAt`, `uploadedByUserId`) is persisted in education-service at attach time, so list reads do not require live metadata lookups from file-service
- `POST /api/v1/education/lectures/{lectureId}/attachments`
  Role access: `OWNER`, `ADMIN`, assigned `TEACHER`
  Request DTO: `CreateLectureAttachmentRequest`
  Response DTO: `LectureAttachmentResponse`
- `DELETE /api/v1/education/lectures/{lectureId}/attachments/{attachmentId}`
  Role access: `OWNER`, `ADMIN`, assigned `TEACHER`
  Response DTO: empty body
- `GET /api/v1/education/lectures/{lectureId}/attachments/{attachmentId}/download`
  Role access: authenticated
  Response DTO: binary stream
  Notes: server verifies lecture access before file download; `attachmentId` must belong to `lectureId`; student access is limited to accessible subjects and lecture statuses `PUBLISHED`/`CLOSED`; endpoint still depends on file-service availability
- `GET /api/v1/education/lectures/{lectureId}/attachments/{attachmentId}/preview`
  Role access: authenticated
  Response DTO: binary stream
  Notes: same authorization and ownership checks as download
- `GET /api/v1/education/dashboard/admin/overview`
  Role access: `OWNER`, `ADMIN`
  Response DTO: `EducationAdminOverviewResponse`

### Schedule Service

Foundation behavior:
- `schedule-service` bootstraps global canonical lesson slots on startup when `SCHEDULE_BOOTSTRAP_ENABLED=true` (default). Slots are global, not semester-specific.
- Canonical slots are fixed pairs `1..8`: `08:30-09:50`, `10:05-11:25`, `11:40-13:00`, `13:15-14:35`, `14:50-16:10`, `16:25-17:45`, `18:00-19:20`, `19:35-20:55`.
- Public slot reads return only active canonical slots. Invalid local records such as pair `55` or `76` are not exposed as usable schedule pairs.
- `schedule-service` bootstraps an active current semester and a next future semester when no active semester exists. Current semesters are active and published; future semesters are created unpublished by default.
- `AcademicSemesterResponse` includes `published`. Student-only schedule reads hide unpublished future semesters; owner/admin/teacher reads can see planning data.
- Past semesters are treated as read-only history by product flow. Historical schedule reads remain available.
- Schedule templates, overrides, and resolved lessons include `subgroup` with canonical values `ALL`, `FIRST`, `SECOND`. Missing subgroup is treated as `ALL` for backwards-compatible writes.

- `POST /api/v1/schedule/semesters`
  Role access: `OWNER`, `ADMIN`
  Request DTO: `CreateAcademicSemesterRequest`
  Response DTO: `AcademicSemesterResponse`
  Notes: request/response include `published`; active semesters are always published
- `GET /api/v1/schedule/semesters/{id}`
  Role access: `OWNER`, `ADMIN`
  Response DTO: `AcademicSemesterResponse`
- `GET /api/v1/schedule/semesters/active`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Response DTO: `AcademicSemesterResponse`
  Notes: local/demo setup should return `200` because schedule foundation bootstrap creates an active semester
- `GET /api/v1/schedule/semesters`
  Role access: `OWNER`, `ADMIN`
  Response DTO: `AcademicSemesterResponse[]`
  Notes: ordered by `startDate desc` for schedule planning screens
- `PUT /api/v1/schedule/semesters/{id}`
  Role access: `OWNER`, `ADMIN`
  Request DTO: `UpdateAcademicSemesterRequest`
  Response DTO: `AcademicSemesterResponse`
- `POST /api/v1/schedule/slots`
  Role access: `OWNER`, `ADMIN`
  Request DTO: `CreateLessonSlotRequest`
  Response DTO: `LessonSlotResponse`
  Notes: only canonical active slots `1..8` with fixed times are valid
- `GET /api/v1/schedule/slots`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Response DTO: `LessonSlotResponse[]`
  Notes: returns active canonical slots only, sorted by pair number
- `PUT /api/v1/schedule/slots/{id}`
  Role access: `OWNER`, `ADMIN`
  Request DTO: `UpdateLessonSlotRequest`
  Response DTO: `LessonSlotResponse`
  Notes: updates must keep the slot on the canonical timetable and active
- `POST /api/v1/schedule/rooms`
  Role access: `OWNER`, `ADMIN`
  Request DTO: `CreateRoomRequest`
  Response DTO: `RoomResponse`
- `GET /api/v1/schedule/rooms`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Response DTO: `RoomResponse[]`
- `GET /api/v1/schedule/rooms/{id}`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Response DTO: `RoomResponse`
- `PUT /api/v1/schedule/rooms/{id}`
  Role access: `OWNER`, `ADMIN`
  Request DTO: `UpdateRoomRequest`
  Response DTO: `RoomResponse`
- `GET /api/v1/schedule/rooms/{id}/capabilities`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Query: optional `includeInactive` (default `false`)
  Response DTO: `RoomCapabilityResponse[]`
- `PUT /api/v1/schedule/rooms/{id}/capabilities`
  Role access: `OWNER`, `ADMIN`
  Request DTO: `UpdateRoomCapabilitiesRequest`
  Response DTO: `RoomCapabilityResponse[]`
- `POST /api/v1/schedule/templates`
  Role access: `OWNER`, `ADMIN`
  Request DTO: `CreateScheduleTemplateRequest`
  Response DTO: `ScheduleTemplateResponse`
  Notes: request/response include `subgroup`; `ScheduleTemplateResponse` exposes lifecycle `status` with `DRAFT`, `ACTIVE`, `ARCHIVED`
- `POST /api/v1/schedule/templates/bulk`
  Role access: `OWNER`, `ADMIN`
  Request DTO: `BulkCreateScheduleTemplatesRequest`
  Response DTO: `ScheduleTemplateResponse[]`
- `POST /api/v1/schedule/templates/import`
  Role access: `OWNER`, `ADMIN`
  Request DTO: `ImportScheduleTemplatesRequest`
  Response DTO: `ScheduleTemplateImportResponse`
- `PUT /api/v1/schedule/templates/{id}`
  Role access: `OWNER`, `ADMIN`
  Request DTO: `UpdateScheduleTemplateRequest`
  Response DTO: `ScheduleTemplateResponse`
- `DELETE /api/v1/schedule/templates/{id}`
  Role access: `OWNER`, `ADMIN`
  Response DTO: empty body
- `GET /api/v1/schedule/templates/semester/{semesterId}`
  Role access: `OWNER`, `ADMIN`
  Response DTO: `ScheduleTemplateResponse[]`
- `GET /api/v1/schedule/templates/group/{groupId}`
  Role access: `OWNER`, `ADMIN`
  Response DTO: `ScheduleTemplateResponse[]`
- `POST /api/v1/schedule/overrides`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Request DTO: `CreateScheduleOverrideRequest`
  Response DTO: `ScheduleOverrideResponse`
  Notes: request/response include `subgroup`; teacher `CANCEL` creates an occurrence override and an internal `TeacherDebt` record with status `OPEN`
- `PUT /api/v1/schedule/overrides/{id}`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Request DTO: `UpdateScheduleOverrideRequest`
  Response DTO: `ScheduleOverrideResponse`
  Notes: request/response include `subgroup`
- `DELETE /api/v1/schedule/overrides/{id}`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Response DTO: empty body
- `GET /api/v1/schedule/overrides/date/{date}`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Response DTO: `ScheduleOverrideResponse[]`
- `POST /api/v1/schedule/conflicts/check`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Request DTO: `ScheduleConflictCheckRequest`
  Response DTO: `ScheduleConflictCheckResponse`
  Notes: template checks require `semesterId`, `dayOfWeek`, `slotId`, `weekType`, `groupId`, `subjectId`, `teacherId`, `lessonType`, and `lessonFormat`; optional supported fields are `subgroup`, `roomId`, and `onlineMeetingUrl`
  Conflict types: `DUPLICATE_LESSON_CONFLICT`, `TEACHER_CONFLICT`, `ROOM_CONFLICT`, `GROUP_SUBGROUP_CONFLICT`
  Subgroup conflict rules: `FIRST` conflicts with `FIRST`, `SECOND` conflicts with `SECOND`, `ALL` conflicts with both, and `FIRST` does not conflict with `SECOND`
- `GET /api/v1/schedule/groups/{groupId}/week`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Query: `startDate`
  Response DTO: `ResolvedLessonResponse[]`
- `GET /api/v1/schedule/groups/{groupId}/range`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Query: `dateFrom`, `dateTo`
  Response DTO: `ResolvedLessonResponse[]`
- `GET /api/v1/schedule/groups/{groupId}/export.ics`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Query: optional `dateFrom`, `dateTo`
  Response DTO: `text/calendar`
- `GET /api/v1/schedule/teachers/{teacherId}/week`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Query: `startDate`
  Response DTO: `ResolvedLessonResponse[]`
- `GET /api/v1/schedule/teachers/{teacherId}/range`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Query: `dateFrom`, `dateTo`
  Response DTO: `ResolvedLessonResponse[]`
- `GET /api/v1/schedule/teachers/{teacherId}/export.ics`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Query: optional `dateFrom`, `dateTo`
  Response DTO: `text/calendar`
- `GET /api/v1/schedule/rooms/{roomId}/week`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Query: `startDate`
  Response DTO: `ResolvedLessonResponse[]`
- `GET /api/v1/schedule/rooms/{roomId}/range`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Query: `dateFrom`, `dateTo`
  Response DTO: `ResolvedLessonResponse[]`
- `GET /api/v1/schedule/search`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Query: `groupId?`, `teacherId?`, `roomId?`, `lessonType?`, `dateFrom`, `dateTo`
  Response DTO: `ResolvedLessonResponse[]`

### Assignment Service

- `POST /api/v1/assignments`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Request DTO: `CreateAssignmentRequest`
  Response DTO: `AssignmentResponse`
  Notes: create defaults to `DRAFT`; payload includes `status`, `allowLateSubmissions`, `maxSubmissions`, `allowResubmit`, `acceptedFileTypes`, `maxFileSizeMb`, and optional `orderIndex`
- `PUT /api/v1/assignments/{id}`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Request DTO: `UpdateAssignmentRequest`
  Response DTO: `AssignmentResponse`
- `POST /api/v1/assignments/{id}/publish`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Response DTO: `AssignmentResponse`
- `POST /api/v1/assignments/{id}/close`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Response DTO: `AssignmentResponse`
- `POST /api/v1/assignments/{id}/reopen`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Response DTO: `AssignmentResponse`
- `POST /api/v1/assignments/{id}/archive`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Response DTO: `AssignmentResponse`
- `POST /api/v1/assignments/{id}/restore`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Response DTO: `AssignmentResponse`
- `PATCH /api/v1/assignments/{id}/position`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Request DTO: `MoveAssignmentRequest`
  Response DTO: `AssignmentResponse`
  Notes: moves an assignment to another topic and persists its `orderIndex`; teachers are restricted to assignments they created
- `GET /api/v1/assignments/topic/{topicId}`
  Role access: authenticated
  Query: `page`, `size`, `sortBy`, `direction`
  Response DTO: `AssignmentPageResponse`
  Notes: students see only published assignments with visible group availability for one of their groups; teachers see published assignments plus their own hidden drafts/archives; `OWNER` and `ADMIN` see all
- `GET /api/v1/assignments/{id}`
  Role access: authenticated
  Response DTO: `AssignmentResponse`
- `GET /api/v1/assignments/{id}/attachments`
  Role access: authenticated
  Response DTO: `AssignmentAttachmentResponse[]`
  Notes: students can read only when assignment is accessible and in `PUBLISHED` or `CLOSED`; `DRAFT` and `ARCHIVED` are hidden from students; teachers can read managed assignments; `OWNER` and `ADMIN` can read all; response metadata is persisted in assignment-service at attach time, so list reads do not require live metadata lookups from file-service
- `POST /api/v1/assignments/{id}/attachments`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Request DTO: `CreateAssignmentAttachmentRequest`
  Response DTO: `AssignmentAttachmentResponse`
  Notes: teacher is restricted to managed assignment scope; archived assignments cannot be edited
- `DELETE /api/v1/assignments/{id}/attachments/{attachmentId}`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Response DTO: empty body
  Notes: `attachmentId` must belong to `assignmentId`
- `GET /api/v1/assignments/{id}/attachments/{attachmentId}/download`
  Role access: authenticated
  Response DTO: streamed file
  Notes: assignment-service validates assignment visibility and ownership binding before delegating to file-service internal API; endpoint still depends on file-service availability
- `GET /api/v1/assignments/{id}/attachments/{attachmentId}/preview`
  Role access: authenticated
  Response DTO: streamed file
  Notes: same authorization and ownership checks as download
- `GET /api/v1/assignments/{id}/availability`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Response DTO: `AssignmentGroupAvailabilityResponse[]`
  Notes: teachers are restricted to assignments they created; each row controls one group with `visible`, `availableFrom`, `deadline`, `allowLateSubmissions`, `maxSubmissions`, and `allowResubmit`
- `PUT /api/v1/assignments/{id}/availability`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Request DTO: `UpsertAssignmentGroupAvailabilityRequest`
  Response DTO: `AssignmentGroupAvailabilityResponse`
  Notes: new assignments remain hidden for all groups until at least one visible availability row is saved
- `GET /api/v1/assignments/search`
  Role access: `OWNER`, `ADMIN`
  Query: `q`, `page`, `size`, `sortBy`, `direction`
  Response DTO: `SearchPageResponse`
  Notes: secure admin-only search baseline
- `POST /api/v1/submissions`
  Role access: `OWNER`, `ADMIN`, `STUDENT`
  Request DTO: `CreateSubmissionRequest`
  Response DTO: `SubmissionResponse`
- `GET /api/v1/submissions/assignment/{assignmentId}`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Query: `page`, `size`, `sortBy`, `direction`
  Response DTO: `SubmissionPageResponse`
  Notes: teachers are restricted to assignments they created
- `POST /api/v1/grades`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Request DTO: `CreateGradeRequest`
  Response DTO: `GradeResponse`
  Notes: teachers are restricted to submissions belonging to assignments they created
- `GET /api/v1/submissions/{submissionId}/attachments`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Response DTO: `SubmissionAttachmentResponse[]`
  Notes: visible only to submission owner, assigned teacher for assignment scope, and `OWNER`/`ADMIN`; response metadata is persisted in assignment-service at attach time, so list reads do not require live metadata lookups from file-service
- `POST /api/v1/submissions/{submissionId}/attachments`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Request DTO: `CreateSubmissionAttachmentRequest`
  Response DTO: `SubmissionAttachmentResponse`
  Notes: only submission owner can attach; assignment policy and lifecycle checks apply
- `DELETE /api/v1/submissions/{submissionId}/attachments/{attachmentId}`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Response DTO: empty body
  Notes: `attachmentId` must belong to `submissionId`; removal follows submission-owner policy checks
- `GET /api/v1/submissions/{submissionId}/attachments/{attachmentId}/download`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Response DTO: streamed file
  Notes: assignment-service validates submission scope before delegating to file-service internal API; endpoint still depends on file-service availability
- `GET /api/v1/submissions/{submissionId}/attachments/{attachmentId}/preview`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Response DTO: streamed file
  Notes: same authorization and ownership checks as download
- `POST /api/v1/submissions/{submissionId}/comments`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Request DTO: `UpsertSubmissionCommentRequest`
  Response DTO: `SubmissionCommentResponse`
- `GET /api/v1/submissions/{submissionId}/comments`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Response DTO: `SubmissionCommentResponse[]`
  Notes: teacher access is restricted to submissions on assignments they created; student access is self-only
- `PUT /api/v1/submissions/{submissionId}/comments/{commentId}`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Request DTO: `UpsertSubmissionCommentRequest`
  Response DTO: `SubmissionCommentResponse`
- `DELETE /api/v1/submissions/{submissionId}/comments/{commentId}`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Response DTO: empty body
- `GET /api/v1/assignments/dashboard/student/me`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Response DTO: `StudentAssignmentDashboardResponse`
- `GET /api/v1/assignments/dashboard/teacher/me`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Response DTO: `TeacherAssignmentDashboardResponse`
- `GET /api/v1/assignments/dashboard/admin/overview`
  Role access: `OWNER`, `ADMIN`
  Response DTO: `AssignmentAdminOverviewResponse`

### Testing Service

- `POST /api/v1/testing/tests`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Request DTO: `CreateTestRequest`
  Response DTO: `TestResponse`
  Notes: create defaults to `DRAFT`; payload includes `maxAttempts`, `maxPoints`, `timeLimitMinutes`, `availableFrom`, `availableUntil`, `showCorrectAnswersAfterSubmit`, `shuffleQuestions`, `shuffleAnswers`, and optional `orderIndex`
- `GET /api/v1/testing/tests/{id}`
  Role access: authenticated
  Response DTO: `TestResponse`
  Notes: management roles can read managed tests; students can read only published tests with visible group availability for one of their groups
- `POST /api/v1/testing/questions`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Request DTO: `CreateQuestionRequest`
  Response DTO: `QuestionResponse`
  Notes: teachers can create questions only for tests they own; `OWNER` and `ADMIN` bypass ownership checks; question payload supports type, description, points, order, required flag, and feedback
- `GET /api/v1/testing/questions/test/{testId}`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Response DTO: `QuestionResponse[]`
  Notes: returns builder questions ordered by `orderIndex`
- `POST /api/v1/testing/answers`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Request DTO: `CreateAnswerRequest`
  Response DTO: `AnswerResponse`
  Notes: teachers can create answers only for questions under tests they own; `OWNER` and `ADMIN` bypass ownership checks
- `GET /api/v1/testing/tests/topic/{topicId}`
  Role access: authenticated
  Query: `page`, `size`, `sortBy`, `direction`
  Response DTO: `TestPageResponse`
  Notes: students see only published tests with visible and currently open group availability; teachers see published tests plus their own hidden drafts/closed items; `OWNER` and `ADMIN` see all
- `GET /api/v1/testing/tests/{id}/availability`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Response DTO: `TestGroupAvailabilityResponse[]`
  Notes: teachers are restricted to tests they created; each row controls one group with `visible`, `availableFrom`, `availableUntil`, optional `deadline`, and `maxAttempts`
- `PUT /api/v1/testing/tests/{id}/availability`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Request DTO: `UpsertTestGroupAvailabilityRequest`
  Response DTO: `TestGroupAvailabilityResponse`
  Notes: new tests remain hidden for all groups until at least one visible availability row is saved
- `GET /api/v1/testing/tests/search`
  Role access: `OWNER`, `ADMIN`
  Query: `q`, `page`, `size`, `sortBy`, `direction`
  Response DTO: `SearchPageResponse`
  Notes: secure admin-only search baseline
- `POST /api/v1/testing/tests/{id}/start`
  Role access: `OWNER`, `ADMIN`, `STUDENT`
  Response DTO: empty body
  Notes: public attempt flow is student-scoped; teachers manage lifecycle and content but do not start attempts through the gateway
- `POST /api/v1/testing/tests/{id}/publish`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Response DTO: `TestResponse`
- `POST /api/v1/testing/tests/{id}/close`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Response DTO: `TestResponse`
- `POST /api/v1/testing/tests/{id}/reopen`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Response DTO: `TestResponse`
- `POST /api/v1/testing/tests/{id}/archive`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Response DTO: `TestResponse`
- `POST /api/v1/testing/tests/{id}/restore`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Response DTO: `TestResponse`
- `PATCH /api/v1/testing/tests/{id}/position`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Request DTO: `MoveTestRequest`
  Response DTO: `TestResponse`
  Notes: moves a test to another topic and persists its `orderIndex`; teachers are restricted to tests they created
- `POST /api/v1/testing/results`
  Role access: `OWNER`, `ADMIN`, `STUDENT`
  Request DTO: `CreateTestResultRequest`
  Response DTO: `TestResultResponse`
- `GET /api/v1/testing/results/{id}`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Response DTO: `TestResultResponse`
  Notes: teachers are restricted to results for tests they created or subjects they are assigned to; response includes final score, original auto score, override score/reason, reviewer, and review timestamp
- `GET /api/v1/testing/results/test/{testId}`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Query: `page`, `size`
  Response DTO: `TestResultPageResponse`
  Notes: lists submitted results for a test so teachers can review auto-graded results
- `PATCH /api/v1/testing/results/{id}/score`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Request DTO: `OverrideTestResultScoreRequest`
  Response DTO: `TestResultResponse`
  Notes: preserves original `autoScore`, stores manual override metadata, and updates the final `score`
- `GET /api/v1/testing/dashboard/student/me`
  Role access: `OWNER`, `ADMIN`, `TEACHER`, `STUDENT`
  Response DTO: `StudentTestDashboardResponse`
- `GET /api/v1/testing/dashboard/teacher/me`
  Role access: `OWNER`, `ADMIN`, `TEACHER`
  Response DTO: `TeacherTestDashboardResponse`
- `GET /api/v1/testing/dashboard/admin/overview`
  Role access: `OWNER`, `ADMIN`
  Response DTO: `TestingAdminOverviewResponse`

### File Service

- `POST /api/files`
  Role access: authenticated
  Request DTO: multipart file + `fileKind`
  Response DTO: `StoredFileResponse`
- `GET /api/files/{fileId}`
  Role access: authenticated and authorized by file ownership/access rules
  Response DTO: `StoredFileResponse`
- `GET /api/files/{fileId}/metadata`
  Role access: same as metadata read
  Response DTO: `StoredFileResponse`
- `GET /api/files/{fileId}/download`
  Role access: same as metadata read
  Response DTO: streamed file
  Notes: raw `fileId` endpoints do not replace domain authorization flows such as lecture or assignment/submission attachment download/preview endpoints
- `GET /api/files/{fileId}/preview`
  Role access: same as metadata read
  Response DTO: streamed file (`application/pdf` and `image/*`)
- `DELETE /api/files/{fileId}`
  Role access: authenticated owner
  Response DTO: empty body
- `GET /api/files/mine`
  Role access: authenticated
  Query: optional `fileKind`
  Response DTO: `StoredFileResponse[]`
- `PUT /api/files/{fileId}/lifecycle/active`
  Role access: authenticated owner
  Response DTO: `StoredFileResponse`
- `PUT /api/files/{fileId}/lifecycle/orphaned`
  Role access: authenticated owner
  Response DTO: `StoredFileResponse`

### Notification Service

- `GET /api/notifications`
  Role access: authenticated
  Query: optional `status`, `page`, `size`, `sortBy`, `direction`
  Response DTO: `NotificationPageResponse`
- `GET /api/notifications/me`
  Role access: authenticated
  Query: optional `status`, `page`, `size`, `sortBy`, `direction`
  Response DTO: `NotificationPageResponse`
- `GET /api/notifications/unread-count`
  Role access: authenticated
  Response DTO: `UnreadCountResponse`
- `GET /api/notifications/me/unread-count`
  Role access: authenticated
  Response DTO: `UnreadCountResponse`
- `PATCH /api/notifications/{notificationId}/read`
  Role access: authenticated
  Response DTO: `NotificationResponse`
- `PATCH /api/notifications/read-all`
  Role access: authenticated
  Response DTO: `UnreadCountResponse`
- `PATCH /api/notifications/me/read-all`
  Role access: authenticated
  Response DTO: `UnreadCountResponse`
- `DELETE /api/notifications/{notificationId}`
  Role access: authenticated
  Response DTO: empty body
  Notes: deadline reminder notifications use `ASSIGNMENT_DEADLINE_REMINDER` and `TEST_DEADLINE_REMINDER` payloads with `entityType`, `entityId`, `deadline`, `reminderOffsetMinutes`, and `userId`

### Analytics Service

- `GET /api/v1/analytics/dashboard/overview`
  Role access: `OWNER`, `ADMIN`
  Response DTO: `DashboardOverviewResponse`
- `GET /api/v1/analytics/students/{userId}`
  Role access: `OWNER`, `ADMIN`, `STUDENT` with self-check for non-admin
  Response DTO: `StudentAnalyticsResponse`
- `GET /api/v1/analytics/students/{userId}/subjects`
  Role access: `OWNER`, `ADMIN`, `STUDENT` with self-check for non-admin
  Response DTO: `SubjectAnalyticsResponse[]`
- `GET /api/v1/analytics/students/{userId}/risk`
  Role access: `OWNER`, `ADMIN`, `STUDENT` with self-check for non-admin
  Response DTO: `StudentRiskResponse`
- `GET /api/v1/analytics/teachers/{teacherId}`
  Role access: `OWNER`, `ADMIN`, `TEACHER` with self-check for non-admin
  Response DTO: `TeacherAnalyticsResponse`
- `GET /api/v1/analytics/teachers/{teacherId}/groups-at-risk`
  Role access: `OWNER`, `ADMIN`, `TEACHER` with self-check for non-admin
  Response DTO: `GroupOverviewResponse[]`
- `GET /api/v1/analytics/groups/{groupId}/overview`
  Role access: `OWNER`, `ADMIN`
  Response DTO: `GroupOverviewResponse`
- `GET /api/v1/analytics/groups/{groupId}/students`
  Role access: `OWNER`, `ADMIN`
  Query: `page`, `size`, `sortBy`, `direction`
  Response DTO: `StudentGroupProgressPageResponse`
- `GET /api/v1/analytics/subjects/{subjectId}`
  Role access: `OWNER`, `ADMIN`
  Query: `page`, `size`, `sortBy`, `direction`
  Response DTO: `SubjectAnalyticsPageResponse`
- `GET /api/v1/analytics/subjects/{subjectId}/groups/{groupId}`
  Role access: `OWNER`, `ADMIN`
  Response DTO: `SubjectAnalyticsResponse`

### Audit Service

- `GET /api/v1/audit`
  Role access: `OWNER`, `ADMIN`
  Query: optional `actorId`, `entityType`, `entityId`, `dateFrom`, `dateTo`, `page`, `size`, `sortBy`, `direction`
  Response DTO: `AuditEventPageResponse`
- `GET /api/v1/audit/{auditEventId}`
  Role access: `OWNER`, `ADMIN`
  Response DTO: `AuditEventResponse`

## Validation Rules

- Request DTO validation is driven by `jakarta.validation` annotations on the corresponding request records.
- Pagination defaults currently used in the codebase are `page=0`, `size=20`, `sortBy` endpoint-specific, and `direction=desc` unless noted otherwise.
- `schedule-service` enforces `lessonType` separately from `lessonFormat`.
- `schedule-service` enforces canonical lesson slots `1..8`; arbitrary pair numbers are rejected for writes and filtered from public slot reads.
- `schedule-service` accepts `subgroup` values `ALL`, `FIRST`, `SECOND` on templates, overrides, and conflict checks.
- `schedule-service` conflict preview and template import return structured conflict details without exposing JPA entities.
- `file-service` preview returns `FILE_PREVIEW_NOT_AVAILABLE` for unsupported content types.
- `assignment-service` enforces `DEADLINE_EXPIRED`, `MAX_SUBMISSIONS_EXCEEDED`, `FILE_TYPE_NOT_ALLOWED`, and `FILE_TOO_LARGE` on submission writes.
- `testing-service` enforces `TEST_NOT_AVAILABLE`, `MAX_ATTEMPTS_EXCEEDED`, and `TEST_TIME_EXPIRED` on test start/submit flows.

## Demo Seed

- Local full-start entrypoints are `./infra/scripts/local/start-local.sh` and `infra\scripts\local\start-local.bat`.
- Both startup scripts create `.env` when missing, ensure `infra/keys/private.pem` and `infra/keys/public.pem`, start the full Docker Compose stack, and run `infra/scripts/local/seed-demo.sh` when `DEMO_SEED_ENABLED=true`.
- On Windows, automatic demo seed requires `bash` to be available in `PATH` (for example via Git Bash or WSL). The stack still starts without it.
- Seed credentials are deterministic:
  `owner / ChangeMe123!`
  `admin.demo / DemoPass123!`
  `teacher.alpha / DemoPass123!`
  `teacher.beta / DemoPass123!`
  `student.one / DemoPass123!`
  `student.two / DemoPass123!`
  `student.three / DemoPass123!`
  `student.four / DemoPass123!`
  `student.five / DemoPass123!`
- Seed data includes:
  owner/admin/teacher/student accounts
  groups, subjects, topics, group memberships
  current, future, and past semesters; canonical lesson slots `1..8`; rooms; odd/even templates; online/offline lessons; and a schedule override for the current day
  draft/published/archived assignments, graded and late submissions, draft/published/closed tests, notifications, analytics-driving interactions, and audit-producing actions
- The schedule foundation bootstrap means `GET /api/v1/schedule/semesters/active` should return `200` in local/demo setup.
- Demo data remains disabled unless `DEMO_SEED_ENABLED=true`; schedule foundation bootstrap can be disabled separately with `SCHEDULE_BOOTSTRAP_ENABLED=false`.
