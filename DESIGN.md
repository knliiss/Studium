# DESIGN.md

This document is a mandatory source of truth for future frontend agents working on Studium.
Read it before creating layouts, components, pages, or frontend design tokens.

## 1. Product Identity

Studium is a modern LMS platform for academic workflows.
It should feel like a serious academic SaaS workspace, not a playful consumer education app.

Mandatory product impression:

- modern LMS platform
- serious academic SaaS
- role-based workspace
- clean and structured
- fast and professional
- academically calm, but modern

Do not make the UI feel:

- childish
- generic Bootstrap
- visually overloaded
- marketing-heavy
- excessively decorative

## 2. Design Inspiration

Use a hybrid direction with clear boundaries:

### Linear influence

Use for:

- dashboards
- admin UX
- compact data tables
- precise layouts
- operational workflows
- subtle purple or indigo accents

### Notion influence

Use for:

- subjects
- topics
- lecture or content-like pages
- readable document flows
- calm workspace feeling
- soft content spacing

### Vercel influence

Use for:

- app shell
- settings
- technical and admin pages
- monochrome discipline
- sharp hierarchy
- clean typography

### Stripe influence

Use lightly for:

- login polish
- highlight cards on dashboards
- subtle premium accents

Rules:

- do not overuse gradients
- do not make the product look like fintech
- do not directly copy any external brand
- use these products only as directional references

## 3. Visual Style

Design direction:

- light mode first
- dark mode ready through the same semantic tokens
- neutral background
- white or near-white surfaces
- subtle borders
- soft shadows only when useful
- restrained purple or indigo accent
- muted text hierarchy
- compact enterprise layout
- readable, consistent spacing

Avoid:

- random bright colors
- childish education icons
- heavy gradients
- glassmorphism
- oversized components
- inconsistent spacing
- floating decorative shapes without product value

## 4. Design Tokens

Start with semantic tokens. Keep names stable so light and dark mode use the same contract.

### Colors

Suggested light tokens:

| Token | Value | Usage |
| --- | --- | --- |
| `--color-background` | `#f5f7fb` | app background |
| `--color-surface` | `#ffffff` | cards, panels, forms |
| `--color-surface-muted` | `#eef2f7` | secondary panels, table headers |
| `--color-border` | `#d9dfeb` | input, card, divider borders |
| `--color-text-primary` | `#101828` | headings, key text |
| `--color-text-secondary` | `#344054` | body text |
| `--color-text-muted` | `#667085` | helper text, metadata |
| `--color-accent` | `#5b61f6` | primary actions, focus states, active nav |
| `--color-success` | `#16a34a` | success states |
| `--color-warning` | `#d97706` | warning states |
| `--color-danger` | `#dc2626` | destructive and risk states |
| `--color-info` | `#2563eb` | informational states |

Dark mode guidance:

- keep the same semantic names
- darken background and surfaces, do not invert accent logic
- preserve border contrast
- avoid neon accents
- keep charts, badges, and alerts restrained
- support light, dark, and system preferences through a central theme provider
- components must use semantic tokens so cards, inputs, tables, drawers, and modals adapt without per-page color forks

Suggested dark anchors:

- background around `#0f1720`
- surface around `#111827`
- muted surface around `#172033`
- border around `#263041`
- primary text around `#f3f4f6`
- secondary text around `#d0d5dd`
- muted text around `#98a2b3`

### Typography

Use one clean sans-serif family for most UI.

Recommended default:

- primary font: `Manrope`, sans-serif
- optional technical mono: `IBM Plex Mono`, monospace for ids, request ids, and compact technical metadata only

Rules:

- no excessive font mixing
- keep headings clear and compact
- keep body text easy to scan
- keep table text compact and readable

Suggested type scale:

- page title: `32 / 40`
- section heading: `24 / 32`
- card heading: `18 / 26`
- body: `14 / 22` or `15 / 24`
- compact table text: `13 / 20`
- helper text: `12 / 18`

### Spacing

Use a 4px / 8px based scale.

Suggested spacing set:

- `4`
- `8`
- `12`
- `16`
- `20`
- `24`
- `32`
- `40`
- `48`

Page layout targets:

- desktop page padding: `24` to `32`
- tablet page padding: `20`
- mobile page padding: `16`

### Radius

Use restrained rounding:

- buttons and inputs: `10px`
- cards and dialogs: `12px`
- pills and badges: `999px` only where semantically useful

Do not use playful oversized rounding.

### Motion

Motion must be subtle and functional:

- standard transitions: `120ms` to `180ms`
- hover/focus transitions only
- panel open/close motion should feel calm
- no decorative page animations

## 5. Core Layout

Global shell rules:

- left sidebar on desktop
- collapsible sidebar or sheet navigation on tablet and mobile
- topbar with global search, notifications, and profile menu
- role-based navigation
- page header with title, short description, and primary action
- consistent content width and spacing

Layout sizing guidance:

- sidebar width: `248px` to `264px`
- topbar height: `56px` to `64px`
- dashboard content can use wide layout
- document-like pages should cap readable width around `960px` to `1100px`
- data-heavy admin pages may use fuller width

## 6. Role-Based Navigation

Sidebar structure must be role-aware.

### Student

- Dashboard
- Schedule
- Subjects
- Assignments
- Tests
- Grades
- Notifications
- Analytics
- Profile

### Teacher

- Dashboard
- Schedule
- Subjects
- Assignments
- Submissions
- Tests
- Analytics
- Notifications
- Profile

### Admin

- Dashboard
- Users
- Groups
- Subjects
- Topics
- Schedule
- Rooms
- Lesson Slots
- Assignments
- Tests
- Analytics
- Audit
- Notifications

Do not expose navigation items for APIs that the current role cannot use.

## 7. Required Components

These components must be reused instead of building page-specific one-offs.

| Component | Purpose |
| --- | --- |
| `AppShell` | global frame with sidebar, topbar, and content slot |
| `Sidebar` | role-based navigation, section grouping, active state |
| `Topbar` | global search trigger, notifications, profile menu |
| `PageHeader` | title, description, actions, secondary controls |
| `Breadcrumbs` | lightweight orientation for deep admin and detail pages |
| `DataTable` | compact tables with pagination, sorting, filters, row actions |
| `StatusBadge` | lifecycle and status rendering |
| `RoleBadge` | role display for users and permissions |
| `EmptyState` | no-data presentation with clear next action |
| `LoadingState` | consistent skeleton or loading placeholder |
| `ErrorState` | recoverable page-level error treatment |
| `ConfirmDialog` | destructive confirmation and irreversible actions |
| `FormField` | label, control, hint, validation message wrapper |
| `DateRangePicker` | shared date range input for search, analytics, and audit |
| `SearchCommand` | command-style global search entry and results list |
| `NotificationDropdown` | topbar notification summary |
| `FilePreview` | inline preview, download fallback, unsupported preview state |
| `ScheduleWeekView` | week grid for schedule pages |
| `ScheduleDayList` | stacked day list for compact or mobile schedule |
| `MetricCard` | analytics and dashboard KPI card |
| `RiskBadge` | low/medium/high risk rendering |
| `DeadlineBadge` | deadline state and urgency rendering |

## 8. Page UX Rules

Every page must support:

- loading state
- empty state
- error state
- retry action where useful
- pagination where API supports it
- filters where API supports them
- clear primary action
- consistent breadcrumbs where useful

Forms must support:

- validation messages near the field
- disabled submit while loading
- API error display
- success feedback
- cancel or back action

Tables must support:

- compact rows
- status badges
- action menu
- pagination
- readable columns

## 9. Domain-Specific UI Rules

### Schedule

- clearly separate `lessonType` and `lessonFormat`
- `lessonType`: Lecture, Practical, Laboratory
- `lessonFormat`: Online, Offline
- support both week grid and day list
- support filters by group, teacher, room, and lesson type
- show conflict preview before saving schedule changes

### Assignments

- show assignment status clearly
- show deadline prominently
- show late submission policy
- show max submissions
- show accepted file types
- show submission state clearly
- show grade and feedback clearly

### Testing

- show attempts and remaining attempts clearly
- show time limit and availability window
- do not expose correct answers before backend rules allow it
- keep test-taking UI focused and distraction-free

### Notifications

- show unread state clearly
- show notification type
- show payload details for schedule changes
- allow mark one as read and mark all as read

### Analytics

- use clean metric cards
- show risk level clearly
- keep charts simple
- avoid noisy visualization

### Audit

- admin-only table
- filterable
- expandable old/new payload details
- prioritize readability over visual flourish

## 10. Accessibility

Required:

- semantic buttons and links
- explicit input labels
- visible keyboard focus states
- sufficient contrast
- readable font sizes
- no clickable `div` elements when `button` or `a` is correct
- `aria-*` attributes where needed

## 11. Quality Bar

The frontend must:

- look consistent across roles
- not use fake data in production paths
- not hardcode backend URLs
- not expose unavailable screens
- handle backend errors cleanly
- stay visually restrained and professional
- feel like one coherent product, not separate admin and student mini-apps
