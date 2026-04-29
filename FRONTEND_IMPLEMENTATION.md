# Frontend Implementation

## Stack

- React 19
- TypeScript
- Vite
- Tailwind CSS v4
- React Router
- TanStack Query
- Axios
- i18next + react-i18next
- React Hook Form + Zod

Frontend source lives in `apps/frontend`.

## Local Setup

1. Start the backend and local infrastructure:

```bash
./infra/scripts/local/start-local.sh
```

Skip the backend rebuild when artifacts are already up to date:

```bash
./infra/scripts/local/start-local.sh --skip-build
```

2. Full Docker startup now also includes the frontend container:

```bash
./infra/scripts/local/start-local.sh
```

Frontend-only rebuild and restart without restacking backend services:

```bash
./infra/scripts/local/start-local.sh --frontend-only
./infra/scripts/local/start-local.sh -f
```

3. Prepare frontend environment for host-side development:

```bash
cd apps/frontend
cp .env.example .env
```

4. Install dependencies and start the dev server:

```bash
npm install
npm run dev
```

5. Build production assets:

```bash
npm run build
```

## Environment Variables

- `VITE_API_BASE_URL`
  - default local value: `http://localhost:8080`
  - frontend must call only the gateway

## Auth Flow

- Login uses `POST /api/auth/login` with `username` and `password`.
- Successful authentication stores `accessToken`, optional `refreshToken`, and `user` in local storage through `src/shared/lib/storage.ts`.
- Requests attach `Authorization: Bearer <token>` through `src/shared/api/client.ts`.
- `401` responses clear the stored session and redirect to `/login`.
- `403` responses keep the user signed in and route them to the access denied screen.
- MFA uses `/api/auth/mfa/dispatch` and `/api/auth/mfa/verify` through `src/pages/auth/MfaPage.tsx`.

## Routing Overview

Router source: `src/app/router.tsx`

- Public routes:
  - `/login`
  - `/register`
  - `/password-reset`
  - `/mfa`
- Student workspace:
  - `/student/dashboard`
  - `/student/schedule`
  - `/student/subjects`
  - `/student/assignments`
  - `/student/tests`
  - `/student/grades`
  - `/student/notifications`
  - `/student/profile`
  - `/student/analytics`
- Teacher workspace:
  - `/teacher/dashboard`
  - `/teacher/schedule`
  - `/teacher/subjects`
  - `/teacher/assignments`
  - `/teacher/submissions`
  - `/teacher/tests`
  - `/teacher/notifications`
  - `/teacher/profile`
  - `/teacher/analytics`
- Admin and owner workspace:
  - `/admin/dashboard`
  - `/admin/users`
  - `/admin/groups`
  - `/admin/subjects`
  - `/admin/topics`
  - `/admin/schedule`
  - `/admin/rooms`
  - `/admin/lesson-slots`
  - `/admin/assignments`
  - `/admin/tests`
  - `/admin/analytics`
  - `/admin/audit`
  - `/admin/notifications`
  - `/admin/search`

Navigation config lives in `src/shared/config/navigation.ts`.

## Role-Based Navigation

- Student navigation exposes only student workspace screens.
- Teacher navigation exposes teaching, submissions, and analytics flows.
- Admin and owner navigation exposes management, analytics, audit, and search flows.
- UI hides unavailable navigation items, but backend authorization remains the source of truth.

## API Client Structure

- `src/shared/api/client.ts`
  - Axios instance
  - base URL from env
  - auth token injection
  - shared error parsing
  - unauthorized redirect hook
- `src/shared/api/auth.ts`
  - auth and MFA requests
- `src/shared/api/services.ts`
  - domain-specific gateway service wrappers

All production requests go through the gateway only.

## i18n Architecture

Core files:

- `src/shared/i18n/index.ts`
- `src/shared/i18n/config.ts`
- `src/shared/i18n/locales/en/*.json`
- `src/shared/i18n/locales/uk/*.json`
- `src/shared/i18n/locales/pl/*.json`

Current locale namespaces:

- `common`
- `errors`
- `validation`

Rules implemented in code:

- browser locale `uk` selects Ukrainian
- browser locale `pl` selects Polish
- all other or unsupported locales fall back to English
- selected language is persisted in local storage
- language switching is immediate and does not require reload
- backend enum values remain canonical in requests and responses
- enum display labels are localized through `src/shared/lib/enum-labels.ts`

## How To Add a New Language

1. Add a new locale code to `LocaleValue` in `src/shared/types/api.ts`.
2. Add locale JSON files under `src/shared/i18n/locales/<code>/`.
3. Register the locale in `src/shared/i18n/config.ts`.
4. Extend `src/shared/ui/LanguageSwitcher.tsx`.
5. Add localized enum, error, navigation, and page labels.

Components must keep using translation keys. Do not hardcode UI strings.

## Translation File Structure

Current structure:

```text
src/shared/i18n/
├── config.ts
├── index.ts
└── locales/
    ├── en/
    │   ├── common.json
    │   ├── errors.json
    │   └── validation.json
    ├── uk/
    │   ├── common.json
    │   ├── errors.json
    │   └── validation.json
    └── pl/
        ├── common.json
        ├── errors.json
        └── validation.json
```

## Design System Notes

- Global tokens live in `apps/frontend/src/index.css`.
- App shell is composed from:
  - `src/widgets/shell/AppShell.tsx`
  - `src/widgets/shell/Sidebar.tsx`
  - `src/widgets/shell/Topbar.tsx`
- Shared reusable UI lives in `src/shared/ui`.
- Domain widgets live in `src/widgets/*`.
- Design direction follows `DESIGN.md`: restrained SaaS, compact data-heavy layouts, neutral surfaces, restrained indigo accent.

## Known Limitations

- Topic detail is resolved from subject-scoped topic lists because the backend does not expose a dedicated public topic-by-id endpoint.
- Student test detail uses dashboard-visible tests plus lifecycle actions because the current public contract does not expose a separate question payload for direct test rendering.
- Student submissions are represented through assignment detail and submit flows because the current public contract does not expose a dedicated personal submissions listing endpoint.
- Some admin create forms accept raw IDs where the backend does not provide richer lookup endpoints yet.
- Live notification WebSocket UI is not wired yet; the current frontend uses polling/query refresh patterns against the HTTP notification contract.
