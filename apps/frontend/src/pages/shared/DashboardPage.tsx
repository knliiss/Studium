import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import { AccessDeniedPage } from '@/pages/shared/AccessDeniedPage'
import { assignmentService, dashboardService, educationService, userDirectoryService } from '@/shared/api/services'
import {
  getAuditActionLabel,
  getAuditEntityTypeLabel,
  getDashboardDeadlineTypeLabel,
} from '@/shared/lib/enum-labels'
import { formatDateTime } from '@/shared/lib/format'
import { hasAnyRole } from '@/shared/lib/roles'
import type { DashboardAssignmentItemResponse, DashboardTestItemResponse, ResolvedLessonResponse, SubjectResponse } from '@/shared/types/api'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { DataTable } from '@/shared/ui/DataTable'
import { PageHeader } from '@/shared/ui/PageHeader'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { DeadlineBadge } from '@/widgets/common/DeadlineBadge'
import { MetricCard } from '@/widgets/common/MetricCard'
import { RiskBadge } from '@/widgets/common/RiskBadge'
import { StatusBadge } from '@/widgets/common/StatusBadge'
import { ScheduleWeekView } from '@/widgets/schedule/ScheduleWeekView'

export function DashboardPage() {
  const { primaryRole, roles } = useAuth()

  if (primaryRole === 'STUDENT') {
    return <StudentDashboard />
  }
  if (primaryRole === 'TEACHER') {
    return <TeacherDashboard />
  }

  if (hasAnyRole(roles, ['ADMIN', 'OWNER'])) {
    return <AdminDashboard />
  }

  return <AccessDeniedPage />
}

function StudentDashboard() {
  const { t } = useTranslation()
  const dashboardQuery = useQuery({
    queryKey: ['dashboard', 'student'],
    queryFn: () => dashboardService.getStudentDashboard(),
  })
  const dashboard = dashboardQuery.data
  const disciplineSubjectIds = useMemo(
    () => uniqueIds([
      ...(dashboard?.todaySchedule.map((lesson) => lesson.subjectId) ?? []),
      ...(dashboard?.pendingAssignments.map((assignment) => assignment.subjectId) ?? []),
      ...(dashboard?.availableTests.map((test) => test.subjectId) ?? []),
      ...(dashboard?.upcomingDeadlines.map((deadline) => deadline.subjectId ?? '') ?? []),
    ]),
    [dashboard],
  )
  const disciplinesQuery = useQuery({
    queryKey: ['dashboard', 'student-disciplines', disciplineSubjectIds.join(',')],
    queryFn: () => loadSubjectsById(disciplineSubjectIds),
    enabled: disciplineSubjectIds.length > 0,
  })

  if (dashboardQuery.isLoading) {
    return <LoadingState />
  }

  if (dashboardQuery.isError || !dashboard) {
    return <ErrorState title={t('dashboard.title')} description={t('common.states.error')} />
  }

  return (
    <div className="space-y-6">
      <PageHeader
        description={t('dashboard.student.description')}
        title={t('dashboard.title')}
      />

      <DisciplinesBlock
        assignments={dashboard.pendingAssignments}
        lessons={dashboard.todaySchedule}
        subjects={disciplinesQuery.data ?? []}
        tests={dashboard.availableTests}
        title={t('dashboard.disciplines.studentTitle')}
      />

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <MetricCard title={t('dashboard.metrics.averageScore')} value={dashboard.progressSummary.averageScore ?? '-'} />
        <MetricCard title={t('dashboard.metrics.activityScore')} value={dashboard.progressSummary.activityScore} />
        <MetricCard title={t('dashboard.metrics.disciplineScore')} value={dashboard.progressSummary.disciplineScore} />
        <MetricCard title={t('dashboard.metrics.unreadNotifications')} value={dashboard.unreadNotificationsCount} />
      </div>

      <section className="space-y-4">
        <PageHeader title={t('dashboard.todaySchedule')} />
        <ScheduleWeekView lessons={dashboard.todaySchedule} />
      </section>

      <div className="grid gap-6 xl:grid-cols-2">
        <section className="space-y-4">
          <PageHeader title={t('dashboard.upcomingDeadlines')} />
          <DataTable
            columns={[
              { key: 'title', header: t('common.labels.title'), render: (item) => item.title },
              { key: 'type', header: t('dashboard.type'), render: (item) => getDashboardDeadlineTypeLabel(item.type) },
              { key: 'deadline', header: t('common.labels.deadline'), render: (item) => <DeadlineBadge deadline={item.deadline} /> },
            ]}
            rows={dashboard.upcomingDeadlines}
          />
        </section>

        <section className="space-y-4">
          <PageHeader title={t('dashboard.recentGrades')} />
          <DataTable
            columns={[
              { key: 'assignmentTitle', header: t('dashboard.assignment'), render: (item) => item.assignmentTitle },
              { key: 'score', header: t('common.labels.score'), render: (item) => item.score },
              { key: 'gradedAt', header: t('dashboard.gradedAt'), render: (item) => formatDateTime(item.gradedAt) },
            ]}
            rows={dashboard.recentGrades}
          />
        </section>
      </div>

      <div className="grid gap-6 xl:grid-cols-2">
        <section className="space-y-4">
          <PageHeader title={t('dashboard.pendingAssignments')} />
          <DataTable
            columns={[
              {
                key: 'title',
                header: t('common.labels.title'),
                render: (item) => (
                  <Link className="font-medium text-accent" to={`/assignments/${item.assignmentId}`}>
                    {item.title}
                  </Link>
                ),
              },
              { key: 'status', header: t('common.labels.status'), render: (item) => <StatusBadge value={item.status} /> },
              { key: 'deadline', header: t('common.labels.deadline'), render: (item) => <DeadlineBadge deadline={item.deadline} /> },
            ]}
            rows={dashboard.pendingAssignments}
          />
        </section>

        <section className="space-y-4">
          <PageHeader title={t('dashboard.availableTests')} />
          <DataTable
            columns={[
              {
                key: 'title',
                header: t('common.labels.title'),
                render: (item) => (
                  <Link className="font-medium text-accent" to={`/tests/${item.testId}`}>
                    {item.title}
                  </Link>
                ),
              },
              { key: 'status', header: t('common.labels.status'), render: (item) => <StatusBadge value={item.status} /> },
              { key: 'attempts', header: t('testing.attempts'), render: (item) => `${item.attemptsUsed}/${item.maxAttempts}` },
            ]}
            rows={dashboard.availableTests}
          />
        </section>
      </div>

      <div className="flex items-center gap-3">
        <span className="text-sm font-semibold text-text-secondary">{t('analytics.riskLevel.label')}</span>
        <RiskBadge value={dashboard.riskLevel} />
      </div>
    </div>
  )
}

function TeacherDashboard() {
  const { t } = useTranslation()
  const dashboardQuery = useQuery({
    queryKey: ['dashboard', 'teacher'],
    queryFn: () => dashboardService.getTeacherDashboard(),
  })
  const dashboard = dashboardQuery.data
  const disciplineSubjectIds = useMemo(
    () => uniqueIds([
      ...(dashboard?.todayLessons.map((lesson) => lesson.subjectId) ?? []),
      ...(dashboard?.activeAssignments.map((assignment) => assignment.subjectId) ?? []),
      ...(dashboard?.activeTests.map((test) => test.subjectId) ?? []),
    ]),
    [dashboard],
  )
  const pendingStudentIds = useMemo(
    () => uniqueIds(dashboard?.pendingSubmissionsToReview.map((submission) => submission.studentId) ?? []),
    [dashboard?.pendingSubmissionsToReview],
  )
  const pendingAssignmentIds = useMemo(
    () => uniqueIds(dashboard?.pendingSubmissionsToReview.map((submission) => submission.assignmentId) ?? []),
    [dashboard?.pendingSubmissionsToReview],
  )
  const riskGroupIds = useMemo(
    () => uniqueIds(dashboard?.groupsAtRisk.map((group) => group.groupId) ?? []),
    [dashboard?.groupsAtRisk],
  )
  const disciplinesQuery = useQuery({
    queryKey: ['dashboard', 'teacher-disciplines', disciplineSubjectIds.join(',')],
    queryFn: () => loadSubjectsById(disciplineSubjectIds),
    enabled: disciplineSubjectIds.length > 0,
  })
  const pendingStudentsQuery = useQuery({
    queryKey: ['dashboard', 'teacher-pending-students', pendingStudentIds.join(',')],
    queryFn: () => userDirectoryService.lookup(pendingStudentIds),
    enabled: pendingStudentIds.length > 0,
  })
  const pendingAssignmentsQuery = useQuery({
    queryKey: ['dashboard', 'teacher-pending-assignments', pendingAssignmentIds.join(',')],
    queryFn: async () => {
      const assignments = await Promise.all(pendingAssignmentIds.map(async (assignmentId) => {
        try {
          return await assignmentService.getAssignment(assignmentId)
        } catch {
          return null
        }
      }))

      return assignments.filter((assignment): assignment is NonNullable<typeof assignment> => Boolean(assignment))
    },
    enabled: pendingAssignmentIds.length > 0,
  })
  const riskGroupsQuery = useQuery({
    queryKey: ['dashboard', 'teacher-risk-groups', riskGroupIds.join(',')],
    queryFn: async () => {
      const groups = await Promise.all(riskGroupIds.map(async (groupId) => {
        try {
          return await educationService.getGroup(groupId)
        } catch {
          return null
        }
      }))

      return groups.filter((group): group is NonNullable<typeof group> => Boolean(group))
    },
    enabled: riskGroupIds.length > 0,
  })

  if (dashboardQuery.isLoading) {
    return <LoadingState />
  }

  if (dashboardQuery.isError || !dashboard) {
    return <ErrorState title={t('dashboard.title')} description={t('common.states.error')} />
  }
  const pendingStudentById = new Map((pendingStudentsQuery.data ?? []).map((student) => [student.id, student]))
  const pendingAssignmentById = new Map((pendingAssignmentsQuery.data ?? []).map((assignment) => [assignment.id, assignment]))
  const riskGroupById = new Map((riskGroupsQuery.data ?? []).map((group) => [group.id, group]))

  return (
    <div className="space-y-6">
      <PageHeader
        description={t('dashboard.teacher.description')}
        title={t('dashboard.title')}
      />

      <DisciplinesBlock
        assignments={dashboard.activeAssignments}
        lessons={dashboard.todayLessons}
        subjects={disciplinesQuery.data ?? []}
        tests={dashboard.activeTests}
        title={t('dashboard.disciplines.teacherTitle')}
      />

      <section className="space-y-4">
        <PageHeader title={t('dashboard.todayLessons')} />
        <ScheduleWeekView lessons={dashboard.todayLessons} />
      </section>

      <div className="grid gap-6 xl:grid-cols-2">
        <section className="space-y-4">
          <PageHeader title={t('dashboard.pendingSubmissions')} />
          <DataTable
            columns={[
              {
                key: 'student',
                header: t('education.student'),
                render: (item) => (
                  <Link className="font-medium text-accent" to={`/review/${item.submissionId}`}>
                    {pendingStudentById.get(item.studentId)?.username ?? t('education.unknownStudent')}
                  </Link>
                ),
              },
              {
                key: 'assignment',
                header: t('dashboard.assignment'),
                render: (item) => pendingAssignmentById.get(item.assignmentId)?.title ?? t('dashboard.assignment'),
              },
              { key: 'submittedAt', header: t('assignments.submittedAt'), render: (item) => formatDateTime(item.submittedAt) },
            ]}
            rows={dashboard.pendingSubmissionsToReview}
          />
        </section>

        <section className="space-y-4">
          <PageHeader title={t('dashboard.groupsAtRisk')} />
          <DataTable
            columns={[
              {
                key: 'groupId',
                header: t('navigation.shared.groups'),
                render: (item) => (
                  <Link className="font-medium text-accent" to={`/groups/${item.groupId}`}>
                    {riskGroupById.get(item.groupId)?.name ?? t('education.group')}
                  </Link>
                ),
              },
              { key: 'risk', header: t('analytics.riskLevel.label'), render: (item) => <RiskBadge value={item.riskLevel} /> },
              { key: 'affected', header: t('analytics.affectedStudents'), render: (item) => item.affectedStudentsCount },
            ]}
            rows={dashboard.groupsAtRisk}
          />
        </section>
      </div>

      <div className="grid gap-6 xl:grid-cols-2">
        <section className="space-y-4">
          <PageHeader title={t('dashboard.activeAssignments')} />
          <DataTable
            columns={[
              {
                key: 'title',
                header: t('common.labels.title'),
                render: (item) => (
                  <Link className="font-medium text-accent" to={`/assignments/${item.assignmentId}`}>
                    {item.title}
                  </Link>
                ),
              },
              { key: 'status', header: t('common.labels.status'), render: (item) => <StatusBadge value={item.status} /> },
              { key: 'deadline', header: t('common.labels.deadline'), render: (item) => <DeadlineBadge deadline={item.deadline} /> },
            ]}
            rows={dashboard.activeAssignments}
          />
        </section>
        <section className="space-y-4">
          <PageHeader title={t('dashboard.activeTests')} />
          <DataTable
            columns={[
              {
                key: 'title',
                header: t('common.labels.title'),
                render: (item) => (
                  <Link className="font-medium text-accent" to={`/tests/${item.testId}`}>
                    {item.title}
                  </Link>
                ),
              },
              { key: 'status', header: t('common.labels.status'), render: (item) => <StatusBadge value={item.status} /> },
              { key: 'availableUntil', header: t('testing.availableUntil'), render: (item) => formatDateTime(item.availableUntil) },
            ]}
            rows={dashboard.activeTests}
          />
        </section>
      </div>
    </div>
  )
}

function AdminDashboard() {
  const { t } = useTranslation()
  const dashboardQuery = useQuery({
    queryKey: ['dashboard', 'admin'],
    queryFn: () => dashboardService.getAdminDashboard(),
  })

  if (dashboardQuery.isLoading) {
    return <LoadingState />
  }

  if (dashboardQuery.isError || !dashboardQuery.data) {
    return <ErrorState title={t('dashboard.title')} description={t('common.states.error')} />
  }

  const dashboard = dashboardQuery.data

  return (
    <div className="space-y-6">
      <PageHeader
        description={t('dashboard.admin.description')}
        title={t('dashboard.title')}
      />

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <MetricCard title={t('dashboard.metrics.totalStudents')} value={dashboard.totalStudents} />
        <MetricCard title={t('dashboard.metrics.totalTeachers')} value={dashboard.totalTeachers} />
        <MetricCard title={t('dashboard.metrics.totalGroups')} value={dashboard.totalGroups} />
        <MetricCard title={t('dashboard.metrics.highRiskStudents')} value={dashboard.highRiskStudentsCount} />
      </div>

      {dashboard.analyticsSummary.totalStudentsTracked > 0 ? (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          <MetricCard title={t('dashboard.metrics.trackedStudents')} value={dashboard.analyticsSummary.totalStudentsTracked} />
          <MetricCard title={t('dashboard.metrics.activityScore')} value={dashboard.analyticsSummary.averageActivityScore.toFixed(1)} />
          <MetricCard title={t('dashboard.metrics.disciplineScore')} value={dashboard.analyticsSummary.averageDisciplineScore.toFixed(1)} />
          <MetricCard title={t('dashboard.metrics.activeDeadlines')} value={dashboard.activeDeadlinesCount} />
        </div>
      ) : (
        <Card>
          <EmptyState description={t('analytics.notEnoughDataYet')} title={t('navigation.shared.analytics')} />
        </Card>
      )}

      <section className="space-y-4">
        <PageHeader title={t('dashboard.recentAudit')} />
          <DataTable
            columns={[
            { key: 'action', header: t('audit.action'), render: (item) => getAuditActionLabel(item.action) },
            { key: 'entityType', header: t('audit.entityType'), render: (item) => getAuditEntityTypeLabel(item.entityType) },
            { key: 'sourceService', header: t('audit.sourceService'), render: (item) => item.sourceService },
            { key: 'occurredAt', header: t('audit.occurredAt'), render: (item) => formatDateTime(item.occurredAt) },
          ]}
          rows={dashboard.recentAuditEvents}
        />
      </section>
    </div>
  )
}

function DisciplinesBlock({
  assignments,
  lessons,
  subjects,
  tests,
  title,
}: {
  assignments: DashboardAssignmentItemResponse[]
  lessons: ResolvedLessonResponse[]
  subjects: SubjectResponse[]
  tests: DashboardTestItemResponse[]
  title: string
}) {
  const { t } = useTranslation()
  const [collapsed, setCollapsed] = useState(false)
  const cards = subjects.map((subject) => {
    const subjectAssignments = assignments.filter((assignment) => assignment.subjectId === subject.id)
    const subjectTests = tests.filter((test) => test.subjectId === subject.id)
    const subjectLessons = lessons.filter((lesson) => lesson.subjectId === subject.id)
    const nearestDeadline = subjectAssignments
      .slice()
      .sort((left, right) => left.deadline.localeCompare(right.deadline))[0]

    return {
      subject,
      activeItemsCount: subjectAssignments.length + subjectTests.length,
      nearestDeadline,
      nextLesson: subjectLessons[0],
    }
  })

  return (
    <Card className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <PageHeader title={title} />
        <Button variant="secondary" onClick={() => setCollapsed((value) => !value)}>
          {collapsed ? t('common.actions.expand') : t('common.actions.collapse')}
        </Button>
      </div>
      {!collapsed ? (
        cards.length === 0 ? (
          <EmptyState description={t('analytics.notEnoughDataYet')} title={title} />
        ) : (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {cards.map((card) => (
              <Link
                key={card.subject.id}
                className="rounded-[16px] border border-border bg-surface-muted p-4 transition hover:border-border-strong"
                to={`/subjects/${card.subject.id}`}
              >
                <p className="font-semibold text-text-primary">{card.subject.name}</p>
                <div className="mt-3 space-y-2 text-sm text-text-secondary">
                  <p>{t('dashboard.disciplines.activeItems', { count: card.activeItemsCount })}</p>
                  <p>
                    {t('dashboard.disciplines.nearestDeadline')}:{' '}
                    {card.nearestDeadline ? formatDateTime(card.nearestDeadline.deadline) : t('analytics.notEnoughDataYet')}
                  </p>
                  <p>
                    {t('dashboard.disciplines.nextLesson')}:{' '}
                    {card.nextLesson ? formatDateTime(card.nextLesson.date) : t('analytics.notEnoughDataYet')}
                  </p>
                </div>
              </Link>
            ))}
          </div>
        )
      ) : null}
    </Card>
  )
}

async function loadSubjectsById(subjectIds: string[]) {
  const subjects = await Promise.all(subjectIds.map(async (subjectId) => {
    try {
      return await educationService.getSubject(subjectId)
    } catch {
      return null
    }
  }))

  return subjects.filter((subject): subject is SubjectResponse => Boolean(subject))
}

function uniqueIds(values: string[]) {
  return Array.from(new Set(values.filter(Boolean)))
}
