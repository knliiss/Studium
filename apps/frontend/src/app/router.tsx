import { Navigate, Outlet, Route, Routes, generatePath, useParams } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import { RequireAuth, RequireRole } from '@/features/auth/route-guards'
import { AdminAuditPage } from '@/pages/admin/AdminAuditPage'
import { AdminSystemPage } from '@/pages/admin/AdminSystemPage'
import { AdminUsersPage } from '@/pages/admin/AdminUsersPage'
import { AnalyticsPage } from '@/pages/analytics/AnalyticsPage'
import { DashboardPage } from '@/pages/app/DashboardPage'
import { AssignmentsPage } from '@/pages/assignments/AssignmentsPage'
import { ReviewPage } from '@/pages/assignments/ReviewPage'
import { AuthLayout } from '@/pages/auth/AuthLayout'
import { LoginPage } from '@/pages/auth/LoginPage'
import { MfaPage } from '@/pages/auth/MfaPage'
import { PasswordResetPage } from '@/pages/auth/PasswordResetPage'
import { RegisterPage } from '@/pages/auth/RegisterPage'
import { GroupDetailPage } from '@/pages/education/GroupDetailPage'
import { GroupsPage } from '@/pages/education/GroupsPage'
import { EducationCenterPage } from '@/pages/education/EducationCenterPage'
import { SubjectDetailPage } from '@/pages/education/SubjectDetailPage'
import { SubjectsPage } from '@/pages/education/SubjectsPage'
import { TeachersPage } from '@/pages/education/TeachersPage'
import { TopicDetailPage } from '@/pages/education/TopicDetailPage'
import { NotificationsPage } from '@/pages/notifications/NotificationsPage'
import { ProfilePage } from '@/pages/profile/ProfilePage'
import { SchedulePage } from '@/pages/schedule/SchedulePage'
import { SearchPage } from '@/pages/search/SearchPage'
import { AccessDeniedPage } from '@/pages/shared/AccessDeniedPage'
import { NotFoundPage } from '@/pages/shared/NotFoundPage'
import { GradesPage } from '@/pages/student/GradesPage'
import { TestsPage } from '@/pages/testing/TestsPage'
import { getDashboardPath } from '@/shared/lib/roles'
import { AppShell } from '@/widgets/shell/AppShell'

export function AppRouter() {
  return (
    <Routes>
      <Route element={<AuthFrame />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/password-reset" element={<PasswordResetPage />} />
        <Route path="/mfa" element={<MfaPage />} />
      </Route>

      <Route path="/access-denied" element={<AccessDeniedPage />} />

      <Route element={<RequireAuth><AppShell /></RequireAuth>}>
        <Route index element={<RoleHomeRedirect />} />
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="/notifications" element={<NotificationsPage />} />

        <Route element={<RequireRole allowedRoles={['STUDENT', 'TEACHER', 'ADMIN', 'OWNER']} />}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/schedule" element={<SchedulePage />} />
          <Route path="/education" element={<EducationCenterPage />} />
          <Route path="/subjects" element={<SubjectsPage />} />
          <Route path="/subjects/:subjectId" element={<SubjectDetailPage />} />
          <Route path="/subjects/:subjectId/topics/:topicId" element={<TopicDetailPage />} />
          <Route path="/groups" element={<GroupsPage />} />
          <Route path="/groups/:groupId" element={<GroupDetailPage />} />
          <Route path="/teachers" element={<TeachersPage />} />
          <Route path="/teachers/:teacherId" element={<TeachersPage />} />
          <Route path="/assignments" element={<AssignmentsPage />} />
          <Route path="/assignments/:assignmentId" element={<AssignmentsPage />} />
          <Route path="/tests" element={<TestsPage />} />
          <Route path="/tests/:testId" element={<TestsPage />} />
          <Route path="/analytics" element={<AnalyticsPage />} />
        </Route>

        <Route element={<RequireRole allowedRoles={['STUDENT']} />}>
          <Route path="/grades" element={<GradesPage />} />

          <Route path="/student/dashboard" element={<Navigate replace to="/dashboard" />} />
          <Route path="/student/schedule" element={<Navigate replace to="/schedule" />} />
          <Route path="/student/education" element={<Navigate replace to="/education" />} />
          <Route path="/student/subjects" element={<Navigate replace to="/subjects" />} />
          <Route path="/student/subjects/:subjectId" element={<ParamRedirect to="/subjects/:subjectId" />} />
          <Route path="/student/topics/:topicId" element={<Navigate replace to="/subjects" />} />
          <Route path="/student/assignments" element={<Navigate replace to="/assignments" />} />
          <Route path="/student/assignments/:assignmentId" element={<ParamRedirect to="/assignments/:assignmentId" />} />
          <Route path="/student/submissions" element={<Navigate replace to="/assignments" />} />
          <Route path="/student/tests" element={<Navigate replace to="/tests" />} />
          <Route path="/student/tests/:testId" element={<ParamRedirect to="/tests/:testId" />} />
          <Route path="/student/grades" element={<Navigate replace to="/grades" />} />
          <Route path="/student/notifications" element={<Navigate replace to="/notifications" />} />
          <Route path="/student/profile" element={<Navigate replace to="/profile" />} />
          <Route path="/student/analytics" element={<Navigate replace to="/analytics" />} />
        </Route>

        <Route element={<RequireRole allowedRoles={['TEACHER']} />}>
          <Route path="/review" element={<ReviewPage />} />
          <Route path="/review/:submissionId" element={<ReviewPage />} />
          <Route path="/submissions" element={<Navigate replace to="/review" />} />
          <Route path="/submissions/:submissionId" element={<ParamRedirect to="/review/:submissionId" />} />

          <Route path="/teacher/dashboard" element={<Navigate replace to="/dashboard" />} />
          <Route path="/teacher/schedule" element={<Navigate replace to="/schedule" />} />
          <Route path="/teacher/subjects" element={<Navigate replace to="/subjects" />} />
          <Route path="/teacher/education" element={<Navigate replace to="/education" />} />
          <Route path="/teacher/assignments" element={<Navigate replace to="/assignments" />} />
          <Route path="/teacher/assignments/:assignmentId" element={<ParamRedirect to="/assignments/:assignmentId" />} />
          <Route path="/teacher/submissions" element={<Navigate replace to="/submissions" />} />
          <Route path="/teacher/submissions/:submissionId/grade" element={<ParamRedirect to="/submissions/:submissionId" />} />
          <Route path="/teacher/tests" element={<Navigate replace to="/tests" />} />
          <Route path="/teacher/tests/:testId" element={<ParamRedirect to="/tests/:testId" />} />
          <Route path="/teacher/notifications" element={<Navigate replace to="/notifications" />} />
          <Route path="/teacher/profile" element={<Navigate replace to="/profile" />} />
          <Route path="/teacher/analytics" element={<Navigate replace to="/analytics" />} />
        </Route>

        <Route element={<RequireRole allowedRoles={['ADMIN', 'OWNER']} />}>
          <Route path="/admin/dashboard" element={<Navigate replace to="/dashboard" />} />
          <Route path="/admin/users" element={<AdminUsersPage />} />
          <Route path="/admin/audit" element={<AdminAuditPage />} />
          <Route path="/admin/system" element={<AdminSystemPage />} />
          <Route path="/search" element={<SearchPage />} />

          <Route path="/owner/dashboard" element={<Navigate replace to="/dashboard" />} />
          <Route path="/admin/education" element={<Navigate replace to="/education" />} />
          <Route path="/admin/groups" element={<Navigate replace to="/groups" />} />
          <Route path="/admin/subjects" element={<Navigate replace to="/subjects" />} />
          <Route path="/admin/topics" element={<Navigate replace to="/subjects" />} />
          <Route path="/admin/schedule" element={<Navigate replace to="/schedule?mode=manage" />} />
          <Route path="/admin/rooms" element={<Navigate replace to="/schedule?mode=manage&section=rooms" />} />
          <Route path="/admin/lesson-slots" element={<Navigate replace to="/schedule?mode=manage&section=slots" />} />
          <Route path="/admin/assignments" element={<Navigate replace to="/assignments" />} />
          <Route path="/admin/assignments/:assignmentId" element={<ParamRedirect to="/assignments/:assignmentId" />} />
          <Route path="/admin/tests" element={<Navigate replace to="/tests" />} />
          <Route path="/admin/tests/:testId" element={<ParamRedirect to="/tests/:testId" />} />
          <Route path="/admin/analytics" element={<Navigate replace to="/analytics" />} />
          <Route path="/admin/notifications" element={<Navigate replace to="/notifications" />} />
          <Route path="/admin/search" element={<Navigate replace to="/search" />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}

function AuthFrame() {
  return (
    <AuthLayout>
      <Outlet />
    </AuthLayout>
  )
}

function RoleHomeRedirect() {
  const { roles } = useAuth()

  return <Navigate replace to={getDashboardPath(roles)} />
}

function ParamRedirect({ to }: { to: string }) {
  const params = useParams()

  return <Navigate replace to={generatePath(to, params)} />
}
