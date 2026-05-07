import { CalendarDays, ChevronLeft, ChevronRight } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { useAuth } from '@/features/auth/useAuth'
import { buildPlanningRange, buildTeacherDirectoryStats, loadSubjectScope } from '@/pages/education/helpers'
import {
  adminUserService,
  analyticsService,
  educationService,
  scheduleService,
  userDirectoryService,
} from '@/shared/api/services'
import { formatDate, formatDateTime } from '@/shared/lib/format'
import { hasAnyRole } from '@/shared/lib/roles'
import type { AdminUserResponse, UserSummaryResponse } from '@/shared/types/api'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { UserAvatar } from '@/shared/ui/UserAvatar'
import { Breadcrumbs } from '@/widgets/common/Breadcrumbs'
import { MetricCard } from '@/widgets/common/MetricCard'

type TeacherUser = AdminUserResponse | UserSummaryResponse
interface TeacherDirectoryStats {
  groupCount: number
  subjectCount: number
}

const pageSize = 12

export function TeachersPage() {
  const { teacherId } = useParams()

  if (teacherId) {
    return <TeacherDetailPage teacherId={teacherId} />
  }

  return <TeacherListPage />
}

function TeacherListPage() {
  const { t } = useTranslation()
  const { primaryRole, roles, session } = useAuth()
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const isAdmin = hasAnyRole(roles, ['ADMIN', 'OWNER'])
  const normalizedSearch = search.trim()

  const adminTeachersQuery = useQuery({
    queryKey: ['teachers', 'admin-list', normalizedSearch, page],
    queryFn: () => adminUserService.list({
      page,
      size: pageSize,
      role: 'TEACHER',
      search: normalizedSearch || undefined,
      sortBy: 'username',
      direction: 'asc',
    }),
    enabled: isAdmin,
  })
  const subjectScopeQuery = useQuery({
    queryKey: ['teachers', 'subject-scope', isAdmin, primaryRole, session?.user.id],
    queryFn: () => loadSubjectScope(primaryRole, session?.user.id ?? '', isAdmin),
    enabled: Boolean(isAdmin || session?.user.id),
  })
  const teacherIds = useMemo(
    () => Array.from(new Set((subjectScopeQuery.data ?? []).flatMap((subject) => subject.teacherIds))),
    [subjectScopeQuery.data],
  )
  const directoryQuery = useQuery({
    queryKey: ['teachers', 'directory', teacherIds.join(',')],
    queryFn: () => userDirectoryService.lookup(teacherIds),
    enabled: !isAdmin && teacherIds.length > 0,
  })

  const teacherStatsById = useMemo(
    () => buildTeacherDirectoryStats(subjectScopeQuery.data ?? []),
    [subjectScopeQuery.data],
  )

  if (
    subjectScopeQuery.isLoading
    || (isAdmin && adminTeachersQuery.isLoading)
    || (!isAdmin && directoryQuery.isLoading)
  ) {
    return <LoadingState />
  }

  if (
    subjectScopeQuery.isError
    || (isAdmin && adminTeachersQuery.isError)
    || (!isAdmin && directoryQuery.isError)
  ) {
    return <ErrorState description={t('common.states.error')} title={t('navigation.shared.teachers')} />
  }

  const adminTeachers = adminTeachersQuery.data?.content ?? []
  const visibleTeachers = isAdmin
    ? adminTeachers
    : (directoryQuery.data ?? [])
        .filter((teacher) => {
          const label = `${teacher.username} ${teacher.email ?? ''}`.toLowerCase()
          return !normalizedSearch || label.includes(normalizedSearch.toLowerCase())
        })
        .slice(page * pageSize, (page + 1) * pageSize)
  const totalPages = isAdmin
    ? (adminTeachersQuery.data?.totalPages ?? 1)
    : Math.max(Math.ceil(((directoryQuery.data ?? []).length || 1) / pageSize), 1)

  return (
    <div className="space-y-6">
      <Breadcrumbs items={[{ label: t('navigation.shared.education'), to: '/education' }, { label: t('navigation.shared.teachers') }]} />
      <PageHeader
        description={t('teachers.description')}
        title={t('navigation.shared.teachers')}
      />

      <Card>
        <FormField label={t('common.actions.search')}>
          <Input
            placeholder={t('teachers.searchPlaceholder')}
            value={search}
            onChange={(event) => {
              setSearch(event.target.value)
              setPage(0)
            }}
          />
        </FormField>
      </Card>

      {visibleTeachers.length === 0 ? (
        <EmptyState description={t('teachers.empty')} title={t('navigation.shared.teachers')} />
      ) : (
        <div className="grid gap-3">
          {visibleTeachers.map((teacher) => (
            <TeacherCard
              key={teacher.id}
              stats={teacherStatsById.get(teacher.id)}
              teacher={teacher}
            />
          ))}
        </div>
      )}

      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-text-secondary">
          {t('cardPicker.pageSummary', { page: page + 1, totalPages })}
        </p>
        <div className="flex gap-2">
          <Button disabled={page === 0} variant="secondary" onClick={() => setPage((current) => Math.max(current - 1, 0))}>
            <ChevronLeft className="mr-2 h-4 w-4" />
            {t('common.actions.previous')}
          </Button>
          <Button disabled={page + 1 >= totalPages} variant="secondary" onClick={() => setPage((current) => current + 1)}>
            {t('common.actions.next')}
            <ChevronRight className="ml-2 h-4 w-4" />
          </Button>
        </div>
      </div>
    </div>
  )
}

function TeacherCard({ stats, teacher }: { stats?: TeacherDirectoryStats; teacher: TeacherUser }) {
  const { t } = useTranslation()
  const displayName = getTeacherDisplayName(teacher)
  const showUsername = displayName !== teacher.username

  return (
    <Link to={`/teachers/${teacher.id}`} className="group block h-full">
      <Card className="flex h-full flex-col justify-between gap-4 transition hover:-translate-y-0.5 hover:border-accent/40 hover:shadow-[var(--shadow-soft)] xl:flex-row xl:items-center">
        <div className="min-w-0 flex-1 space-y-4">
          <div className="flex items-start gap-3">
            <UserAvatar
              displayName={displayName}
              email={teacher.email}
              size="md"
              username={teacher.username}
            />
            <div className="min-w-0">
              <p className="truncate text-lg font-semibold text-text-primary">{displayName}</p>
              {showUsername ? (
                <p className="truncate text-sm text-text-muted">@{teacher.username}</p>
              ) : null}
              {teacher.email ? (
                <p className="truncate text-sm text-text-secondary">{teacher.email}</p>
              ) : null}
            </div>
          </div>
          <div className="grid gap-3 md:grid-cols-2">
            <div className="rounded-[16px] border border-border bg-surface-muted px-4 py-3">
              <p className="text-xs font-semibold uppercase tracking-[0.16em] text-text-muted">
                {t('teachers.assignedSubjects')}
              </p>
              <p className="mt-2 text-lg font-semibold text-text-primary">{stats?.subjectCount ?? '—'}</p>
            </div>
            <div className="rounded-[16px] border border-border bg-surface-muted px-4 py-3">
              <p className="text-xs font-semibold uppercase tracking-[0.16em] text-text-muted">
                {t('teachers.connectedGroups')}
              </p>
              <p className="mt-2 text-lg font-semibold text-text-primary">{stats?.groupCount ?? '—'}</p>
            </div>
          </div>
        </div>
        <span className="inline-flex min-h-11 shrink-0 items-center justify-center rounded-[14px] border border-border bg-surface-muted px-4 text-sm font-medium text-text-primary transition group-hover:border-border-strong">
          {t('common.actions.open')}
        </span>
      </Card>
    </Link>
  )
}

function TeacherDetailPage({ teacherId }: { teacherId: string }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { primaryRole, roles, session } = useAuth()
  const isAdmin = hasAnyRole(roles, ['ADMIN', 'OWNER'])
  const range = useMemo(() => buildPlanningRange(21), [])

  const teacherQuery = useQuery({
    queryKey: ['teachers', 'detail', teacherId, isAdmin],
    queryFn: async () => {
      if (isAdmin) {
        return adminUserService.getById(teacherId)
      }
      const [teacher] = await userDirectoryService.lookup([teacherId])
      return teacher
    },
  })
  const allSubjectsQuery = useQuery({
    queryKey: ['teachers', 'detail-subjects', teacherId, primaryRole, session?.user.id],
    queryFn: () => loadSubjectScope(primaryRole, session?.user.id ?? '', isAdmin),
    enabled: Boolean(session?.user.id || isAdmin),
  })
  const scheduleQuery = useQuery({
    queryKey: ['teachers', 'schedule', teacherId, range],
    queryFn: () => scheduleService.getTeacherRange(teacherId, range.dateFrom, range.dateTo),
  })
  const analyticsQuery = useQuery({
    queryKey: ['teachers', 'analytics', teacherId],
    queryFn: () => analyticsService.getTeacher(teacherId),
    enabled: isAdmin || teacherId === session?.user.id,
  })

  const assignedSubjects = useMemo(
    () => (allSubjectsQuery.data ?? []).filter((subject) => subject.teacherIds.includes(teacherId)),
    [allSubjectsQuery.data, teacherId],
  )
  const groupIds = useMemo(
    () => Array.from(new Set(assignedSubjects.flatMap((subject) => subject.groupIds))),
    [assignedSubjects],
  )
  const groupsQuery = useQuery({
    queryKey: ['teachers', 'groups', groupIds.join(',')],
    queryFn: () => Promise.all(groupIds.map((groupId) => educationService.getGroup(groupId))),
    enabled: groupIds.length > 0,
  })

  if (teacherQuery.isLoading || allSubjectsQuery.isLoading) {
    return <LoadingState />
  }

  if (teacherQuery.isError || allSubjectsQuery.isError || !teacherQuery.data) {
    return <ErrorState description={t('common.states.error')} title={t('navigation.shared.teachers')} />
  }

  const teacher = teacherQuery.data
  const displayName = getTeacherDisplayName(teacher)
  const lessons = scheduleQuery.data ?? []
  const groups = groupsQuery.data ?? []
  const groupNameById = new Map(groups.map((group) => [group.id, group.name]))
  const nextLesson = lessons[0] ?? null
  const showUsername = displayName !== teacher.username

  return (
    <div className="space-y-6">
      <Breadcrumbs
        items={[
          { label: t('navigation.shared.education'), to: '/education' },
          { label: t('navigation.shared.teachers'), to: '/teachers' },
          { label: displayName },
        ]}
      />
      <Button variant="secondary" onClick={() => navigate('/teachers')}>
        {t('teachers.backToTeachers')}
      </Button>

      <Card className="space-y-5">
        <PageHeader
          actions={(
            <Link to={`/schedule/teachers/${teacherId}`}>
              <Button variant="secondary">
                <CalendarDays className="mr-2 h-4 w-4" />
                {t('navigation.shared.schedule')}
              </Button>
            </Link>
          )}
          description={t('teachers.description')}
          title={displayName}
        />
        <div className="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
          <div className="flex items-center gap-4">
            <UserAvatar
              displayName={displayName}
              email={teacher.email}
              size="lg"
              username={teacher.username}
            />
            <div className="space-y-1">
              {showUsername ? (
                <p className="text-sm font-medium text-text-muted">@{teacher.username}</p>
              ) : null}
              {teacher.email ? (
                <p className="text-sm text-text-secondary">{teacher.email}</p>
              ) : null}
            </div>
          </div>
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
            <MetricCard title={t('teachers.assignedSubjects')} value={assignedSubjects.length} />
            <MetricCard title={t('teachers.connectedGroups')} value={groupIds.length} />
            <MetricCard title={t('teachers.upcomingLessons')} value={lessons.length} />
            <MetricCard
              title={t('education.nextLesson')}
              value={nextLesson ? formatDate(nextLesson.date) : '—'}
            />
          </div>
        </div>
      </Card>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_340px]">
        <main className="space-y-6">
          <Card className="space-y-4">
            <PageHeader title={t('teachers.assignedSubjects')} />
            {assignedSubjects.length === 0 ? (
              <EmptyState description={t('teachers.noAssignedSubjects')} title={t('teachers.assignedSubjects')} />
            ) : (
              <div className="grid gap-3 md:grid-cols-2">
                {assignedSubjects.map((subject) => (
                  <Link
                    key={subject.id}
                    className="rounded-[16px] border border-border bg-surface-muted p-4 transition hover:border-accent/40"
                    to={`/subjects/${subject.id}`}
                  >
                    <p className="font-semibold text-text-primary">{subject.name}</p>
                    <p className="mt-1 text-sm text-text-secondary">
                      {t('education.subjectGroupsCount', { count: subject.groupIds.length })}
                    </p>
                  </Link>
                ))}
              </div>
            )}
          </Card>

          <Card className="space-y-4">
            <PageHeader title={t('navigation.shared.schedule')} />
            {scheduleQuery.isLoading ? (
              <p className="text-sm leading-6 text-text-secondary">{t('common.states.loading')}</p>
            ) : scheduleQuery.isError ? (
              <p className="text-sm leading-6 text-text-secondary">{t('teachers.scheduleUnavailable')}</p>
            ) : lessons.length === 0 ? (
              <EmptyState description={t('teachers.noSchedule')} title={t('navigation.shared.schedule')} />
            ) : (
              <div className="space-y-3">
                {lessons.slice(0, 8).map((lesson) => {
                  const subject = assignedSubjects.find((item) => item.id === lesson.subjectId)
                  return (
                    <div key={`${lesson.date}-${lesson.slotId}-${lesson.groupId}`} className="rounded-[16px] border border-border bg-surface-muted p-4">
                      <p className="font-semibold text-text-primary">{subject?.name ?? t('education.subject')}</p>
                      <p className="mt-1 text-sm text-text-secondary">{formatDate(lesson.date)}</p>
                      <p className="mt-1 text-sm text-text-secondary">
                        {groupNameById.get(lesson.groupId) ?? t('education.group')}
                      </p>
                    </div>
                  )
                })}
              </div>
            )}
          </Card>
        </main>

        <aside className="space-y-5">
          <Card className="space-y-4">
            <PageHeader title={t('navigation.shared.groups')} />
            {groupsQuery.isLoading ? (
              <p className="text-sm leading-6 text-text-secondary">{t('common.states.loading')}</p>
            ) : groupsQuery.isError ? (
              <p className="text-sm leading-6 text-text-secondary">{t('teachers.groupsUnavailable')}</p>
            ) : groups.length === 0 ? (
              <EmptyState description={t('teachers.noGroups')} title={t('navigation.shared.groups')} />
            ) : (
              <div className="space-y-2">
                {groups.map((group) => (
                  <Link key={group.id} className="block rounded-[14px] border border-border bg-surface-muted px-3 py-2 text-sm font-medium text-text-primary" to={`/groups/${group.id}`}>
                    {group.name}
                  </Link>
                ))}
              </div>
            )}
          </Card>

          <Card className="space-y-4">
            <PageHeader title={t('navigation.shared.analytics')} />
            {analyticsQuery.isLoading ? (
              <p className="text-sm leading-6 text-text-secondary">{t('common.states.loading')}</p>
            ) : analyticsQuery.isError ? (
              <EmptyState description={t('teachers.analyticsUnavailable')} title={t('navigation.shared.analytics')} />
            ) : analyticsQuery.data ? (
              <div className="grid gap-3">
                <MetricCard title={t('teachers.publishedAssignments')} value={analyticsQuery.data.publishedAssignmentsCount} />
                <MetricCard title={t('teachers.publishedTests')} value={analyticsQuery.data.publishedTestsCount} />
                <MetricCard title={t('teachers.assignedGrades')} value={analyticsQuery.data.assignedGradesCount} />
                <p className="text-xs text-text-muted">{formatDateTime(analyticsQuery.data.updatedAt)}</p>
              </div>
            ) : (
              <EmptyState description={t('analytics.notEnoughDataYet')} title={t('navigation.shared.analytics')} />
            )}
          </Card>
        </aside>
      </div>
    </div>
  )
}

function getTeacherDisplayName(teacher: TeacherUser) {
  if ('displayName' in teacher && teacher.displayName?.trim()) {
    return teacher.displayName
  }

  return teacher.username
}
