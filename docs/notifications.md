# Notifications

## In-App Notifications
`notification-service` persists user notifications and exposes API groups under `/api/notifications/**`.

Implemented user operations:
- list notifications (`/api/notifications`, `/api/notifications/me`)
- unread count (`/api/notifications/unread-count`, `/api/notifications/me/unread-count`)
- mark single notification read (`PATCH /api/notifications/{notificationId}/read`)
- mark all as read (`PATCH /api/notifications/read-all` or `/me/read-all`)
- delete one (`DELETE /api/notifications/{notificationId}`)
- delete all (`DELETE /api/notifications` or `/me`)

## Frontend UX
### Dropdown
- unread badge and latest items
- “mark all read” for loaded set behavior
- open full notifications center
- delete single and delete-all actions

### Notifications Center
- paginated list behavior
- read/unread status handling
- clear/delete flows based on available API actions

## Realtime Delivery
`notification-service` provides websocket delivery (`/ws/notifications`) and redis fanout integration for scaled local/runtime topologies.

## Telegram Delivery
Notification pipeline supports Telegram delivery for linked users:
- link state and preferences are stored in notification-service
- delivery attempts update success/failure metrics in Telegram link records
- delivery failure can deactivate link to avoid repeated hard failures

## Telegram Preferences
Current preference categories include:
- telegram enabled
- assignments
- tests
- grades
- schedule
- materials
- system

These are exposed through profile Telegram API and consumed by frontend Telegram settings toggles.
