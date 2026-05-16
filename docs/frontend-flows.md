# Frontend Flows

## Role-Aware Navigation
Frontend uses role-based route guards and grouped sidebar navigation.

Implemented behavior:
- collapsible sidebar
- grouped navigation sections per role
- expandable nested route source sections
- profile/settings entry in personal/header flows

## Student Flow
Main pages:
- dashboard
- schedule hub (`/schedule`, `/schedule/me`, scoped schedule contexts)
- subjects list/detail and topic content routes
- assignments (view + submit)
- tests (available tests + own result views)
- grades
- notifications center/dropdown
- my group page (`/my-group`)
- profile + Telegram connection section

Constraints:
- no academic structure management actions
- no global user/room/schedule-admin operations

## Teacher Flow
Main pages:
- dashboard
- schedule read views
- subjects and teaching content routes
- assignments and review queue
- tests and result review
- group rosters (read-oriented)
- notifications + profile

Constraints:
- no global user administration
- no admin-only platform control pages

## Admin/Owner Flow
Main pages:
- dashboard
- admin users/audit/system
- search
- full academic management routes (subjects/groups/specialties/streams/curriculum)
- rooms and schedule management routes
- analytics and notifications

## Subject and Topic Flow
- subject cards/list pages route into subject detail
- topic routes include lecture/material subpages
- permissions differ by role (view vs manage)

## Assignment Flow
- students: view assignment, submit attempts, view own submission state
- teachers/admin: create/update/publish/archive, review submissions, assign grades
- file attachment previews are integrated via file-service-backed endpoints

## Test Flow
- students: open tests, start/finish attempts, view own result state
- teachers/admin: manage test content, review results, perform review actions

## Schedule Flow
- schedule hub supports group/teacher/room/me contexts
- students have read access for schedule views
- admin/owner schedule constructor/management routes are separated

## Notification Flow
- dropdown shows recent notifications, unread badge, mark-all-read in loaded scope
- notifications center supports read/unread views and delete actions
- data comes from `/api/notifications/**` via gateway

## Profile + Telegram Flow
Implemented profile Telegram states:
- not connected
- waiting for Telegram confirmation (token/link flow)
- connected (status, preferences, test message, disconnect)
- unavailable (when config/runtime not available)

Behavior:
- generate connect token/link action
- optional status polling (short-lived interval window)
- copy link/code actions
- localized status and error messages
