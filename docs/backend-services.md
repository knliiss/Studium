# Backend Services

This document summarizes current practical responsibilities and endpoint groups. It is not an exhaustive endpoint reference.

## gateway
Purpose:
- single public API entrypoint and policy layer

Responsibilities:
- route requests to downstream services
- enforce role-based access rules
- expose BFF endpoints (dashboard, schedule-me aggregation)

Important endpoint groups:
- `/api/auth/**`
- `/api/profile/**`
- `/api/v1/education/**`
- `/api/v1/schedule/**`
- `/api/v1/assignments/**`, `/api/v1/submissions/**`, `/api/v1/grades/**`
- `/api/v1/testing/**`
- `/api/notifications/**`
- `/api/files/**`
- `/api/v1/analytics/**`
- `/api/v1/audit/**`

Security assumptions:
- all frontend traffic goes through gateway
- role restrictions are applied at route/policy layer

## auth-service
Purpose:
- identity and access control

Responsibilities:
- register/login/refresh/logout
- MFA challenge + verification
- user role admin/ban operations

Key env:
- JWT issuer/audience/keys
- owner bootstrap settings
- MFA config

## profile-service
Purpose:
- profile data and avatar references

Responsibilities:
- read/update own profile
- avatar update/delete using file-service IDs

Key env:
- file service URL and timeouts

## education-service
Purpose:
- academic structure ownership

Responsibilities:
- groups, subjects, topics
- specialties, streams, curriculum plans
- group membership rosters and related lookups

Security assumptions:
- admin/owner manage structure
- students/teachers read scoped educational data

## schedule-service
Purpose:
- schedule domain ownership

Responsibilities:
- schedule templates (admin/owner management)
- schedule overrides/conflict checks
- read/search/export for group/teacher/room schedules

Current access model:
- read endpoints: student/teacher/admin/owner
- management endpoints: mostly admin/owner (some teacher override operations)

## assignment-service
Purpose:
- assignments, submissions, grades

Responsibilities:
- assignment lifecycle operations
- submission creation and review flows
- grade write/read flows
- attachment preview/download bridges via file-service
- static submission file type policy enforcement (`SubmissionFileTypePolicy`)

Known policy note:
- accepted submission content types are centrally fixed in backend policy (not user-configured at runtime)

## testing-service
Purpose:
- tests and result workflows

Responsibilities:
- test/question/answer management
- student start/finish test attempts
- teacher/admin result review and updates
- preview/student view contracts used by frontend

## file-service
Purpose:
- file metadata + object storage access layer

Responsibilities:
- upload/download/preview
- ownership and access checks
- preview available for PDF/images
- internal endpoints for trusted service access

## notification-service
Purpose:
- notification persistence + fanout + Telegram link source-of-truth

Responsibilities:
- notification center APIs (`/api/notifications/**`)
- unread counters, read-all, delete flows
- websocket + redis fanout for realtime updates
- Telegram link tokens, status, preferences, test message API
- protected internal Telegram endpoints consumed by telegram-bot-service

## telegram-bot-service
Purpose:
- Telegram bot UX/runtime as separate microservice

Responsibilities:
- long polling update consumption
- annotation-based command and callback routing
- single-message edit-first inline menu UX
- generated banner/schedule image rendering + in-memory cache
- internal API consumption from notification-service

Critical runtime behavior:
- app can start when token is missing/invalid; polling is disabled instead of crashing
- no webhook

## analytics-service
Purpose:
- analytics and dashboard-oriented API surfaces

Responsibilities:
- student/teacher/admin analytics views
- data consumed by frontend pages and gateway BFF

## audit-service
Purpose:
- audit records and audit query APIs

Responsibilities:
- receive and expose audit trail data

## content-service
Purpose:
- content module surface for LMS domain

Responsibilities:
- content-oriented APIs and persistence (current coverage depends on active frontend paths)

## Known Cross-Service Limits
- Telegram schedule context currently does not consume resolved schedule data from schedule-service internal API; bot schedule day is currently placeholder/empty-state.
