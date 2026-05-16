# Screenshot Report

## Purpose
This screenshot pass produced a structured desktop-sized image set for project documentation. The goal was to show realistic, data-populated user flows without changing product behavior or redesigning the UI.

## Tested Roles
- `student.one`
- `teacher.alpha`
- `admin.demo`

`admin.demo` was used for the administrative flow instead of `owner` because it exposed the same core management routes in the seed environment while also having a more complete notification-driven demo flow.

## Tested Viewport
- Browser viewport: `1600x1000`
- Theme: dark
- Locale target: Ukrainian (`uk`)
- Format: PNG viewport screenshots

## Screenshot Sets

### Student
- `01_student_dashboard.png`
- `02_student_subjects.png`
- `03_student_subject_detail.png`
- `04_student_assignments.png`
- `05_student_assignment_detail.png`
- `06_student_tests.png`
- `07_student_test_detail.png`
- `08_student_grades.png`
- `09_student_schedule_hub.png`
- `10_student_my_schedule.png`
- `11_student_group_schedule.png`
- `12_student_my_group.png`
- `13_student_notifications.png`
- `14_student_profile_telegram.png`

### Teacher
- `15_teacher_dashboard.png`
- `16_teacher_subjects.png`
- `17_teacher_subject_detail.png`
- `18_teacher_assignments.png`
- `19_teacher_assignment_review.png`
- `20_teacher_tests.png`
- `21_teacher_test_results.png`
- `22_teacher_schedule_hub.png`
- `23_teacher_teaching_schedule.png`
- `24_teacher_group_rosters.png`
- `25_teacher_notifications.png`
- `26_teacher_profile_telegram.png`

### Admin
- `27_admin_dashboard.png`
- `28_admin_subjects.png`
- `29_admin_groups.png`
- `30_admin_teachers.png`
- `31_admin_specialties.png`
- `32_admin_rooms.png`
- `33_admin_schedule.png`
- `34_admin_users.png`
- `35_admin_audit.png`
- `36_admin_notifications.png`
- `37_admin_profile_telegram.png`

## Flows Covered
- Student dashboard, subjects, assignments, tests, grades, schedules, group, notifications, and profile/Telegram entry point
- Teacher dashboard, course context, assignment review, test result review entry point, schedules, group rosters, notifications, and profile/Telegram entry point
- Admin dashboard, academic management pages, schedule management view, user management, audit, notifications, and profile/Telegram entry point

## Known Gaps / Pages Not Captured
- Student material/file preview was represented through the populated subject overview rather than a separate dedicated file-view route because the current subject detail already surfaces the latest material card clearly in the documentation set.
- Student teacher-schedule and room-schedule browsing were not split into separate screenshots; the group schedule browsing view was chosen as the strongest documentation example for cross-context schedule access.
- Teacher test result detail review page was probed successfully during the pass, but the summary results page was chosen for the final exported set because it communicates the overall management flow more clearly in one screenshot.
- Admin analytics was not included in the final screenshot set because the current seed data renders a low-information empty/placeholder state that adds little value to documentation.

## Issues Noticed During Screenshot Pass
- Role: Student, Teacher, Admin
  Route: `/schedule`
  Issue: The schedule hub contains untranslated placeholder copy such as `Context Hub Title`, `Context Hub Subtitle`, `Week Preview Title`, `Week Preview Description`, and `Go To My Schedule`.
- Role: Student, Teacher, Admin
  Route: `/profile`
  Issue: The profile and Telegram connection section contains untranslated UI labels and actions, including `Display Name`, `Avatar`, `Update Avatar`, `Delete Avatar`, `Title`, `Not Connected`, `Connect Explanation`, `Generate Link`, and `Check Status`.
- Role: Student, Teacher, Admin
  Route: `/notifications`
  Issue: Notification filters and several notification titles remain partially untranslated in the Ukrainian locale, including labels such as `Unread`, `Assignments`, `Tests`, `Grades`, `Schedule`, `Materials`, `System`, and some body/title text like `Welcome`.
- Role: Student
  Route: `/notifications`
  Issue: The page header shows `Кількість непрочитаних: 1`, while the visible notification list in the captured viewport is already marked as read, suggesting either a pagination/state mismatch or unread counter inconsistency.
- Role: Admin
  Route: `/teachers`
  Issue: The teacher directory page contains a generic untranslated heading/description label (`Description`) in the Ukrainian locale.

## Verification
The following artifacts were created and verified locally:
- Screenshot directory: `docs/screenshots/`
- Screenshot index: `docs/screenshots/README.md`
- Screenshot report: `docs/screenshot-report.md`
