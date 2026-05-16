# Roles and Permissions

This summary reflects current practical behavior across gateway policies, backend `@PreAuthorize` guards, and frontend route/sidebar exposure.

## Student
Student can:
- view dashboard
- view own and public/read schedule contexts
- view subjects assigned through group/education scope
- view lectures/materials available through subject routes
- submit assignments and manage own submission interactions
- take available tests and view own outcomes
- view grades page
- view notifications and mark/read/delete in own scope
- view own group roster page (`/my-group`)
- manage profile and Telegram connection/preferences

Student cannot:
- create/manage subjects/groups/academic structure
- manage rooms/schedule templates as admin workflow
- perform global user/platform admin operations

## Teacher
Teacher can:
- view dashboard
- view read schedule contexts
- access assigned subject workflows
- manage assignment/test instructional content where backend rules allow
- review submissions and test results
- view group rosters (read-oriented flows)
- view notifications/profile and Telegram settings

Teacher cannot:
- perform global user administration
- access admin-only platform/audit/system settings pages
- execute admin-only academic structure management actions

## Admin and Owner
Admin/Owner can:
- access admin dashboards and system pages
- manage users and roles (subject to auth policy rules)
- manage academic structure (subjects/groups/specialties/streams/plans)
- manage rooms and schedule templates/constructor routes
- access analytics, search, audit, and platform notifications

## Notes
- Some backend endpoints still allow broader read guards than frontend exposes by default; frontend UX remains role-directed and safer by design.
- Owner retains full platform authority; admin is broad but still policy-constrained in selected auth role transitions.
