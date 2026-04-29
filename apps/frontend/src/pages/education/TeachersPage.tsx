import { CalendarDays, ChevronLeft, ChevronRight } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { useAuth } from '@/features/auth/useAuth'
import { buildPlanningRange, loadAccessibleSubjects } from '@/pages/education/helpers'
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
  const accessibleSubjectsQuery = useQuery({
    queryKey: ['teachers', 'subjects', primaryRole, session?.user.id],
    queryFn: () => loadAccessibleSubjects(primaryRole, session?.user.id ?? ''),
    enabled: Boolean(!isAdmin && session?.user.id),
  })
  const teacherIds = useMemo(
    () => Array.from(new Set((accessibleSubjectsQuery.data ?? []).flatMap((subject) => subject.teacherIds))),
    [accessibleSubjectsQuery.data],
  )
  const directoryQuery = useQuery({
    queryKey: ['teachers', 'directory', teacherIds.join(',')],
    queryFn: () => userDirectoryService.lookup(teacherIds),
    enabled: !isAdmin && teacherIds.length > 0,
  })

  const subjectCountByTeacher = useMemo(() => {
    const counter = new Map<string, number>()
    for (const subject of accessibleSubjectsQuery.data ?? []) {
      for (const id of subject.teacherIds) {
        counter.set(id, (counter.get(id) ?? 0) + 1)
      }
    }
    return counter
  }, [accessibleSubjectsQuery.data])

  if ((isAdmin && adminTeachersQuery.isLoading) || (!isAdmin && (accessibleSubjectsQuery.isLoading || directoryQuery.isLoading))) {
    return <LoadingState />
  }

  if ((isAdmin && adminTeachersQuery.isError) || (!isAdmin && (accessibleSubjectsQuery.isError || directoryQuery.isError))) {
    return <ErrorState description={t('common.states.error')} title={t('navigation.shared.teachers')} />
  }

  const adminTeachers = adminTeachersQuery.data?.content ?? []
  const visibleTeachers = isAdmin
    ? adminTeachers
    : (directoryQuery.data ?? [])
        .filter((teacher) => {
          const label = `${teacher.username} ${teacher.email}`.toLowerCase()
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
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {visibleTeachers.map((teacher) => (
            <TeacherCard
              key={teacher.id}
              assignedSubjectsCount={isAdmin ? undefined : subjectCountByTeacher.get(teacher.id)}
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

function TeacherCard({ assignedSubjectsCount, teacher }: { assignedSubjectsCount?: number; teacher: TeacherUser }) {
  const { t } = useTranslation()
  const displayName = getTeacherDisplayName(teacher)

  return (
    <Link to={`/teachers/${teacher.id}`}>
      <Card className="h-full space-y-4 transition hover:-translate-y-0.5 hover:border-accent/40 hover:shadow-[var(--shadow-soft)]">
        <div className="flex items-start gap-3">
          <UserAvatar
            displayName={displayName}
            email={teacher.email}
            size="md"
            username={teacher.username}
          />
          <div className="min-w-0">
            <p className="truncate text-lg font-semibold text-text-primary">{displayName}</p>
            <p className="truncate text-sm text-text-secondary">{teacher.email}</p>
          </div>
        </div>
        <div className="grid gap-3 text-sm text-text-secondary">
          <p>{t('teachers.assignedSubjects')}: {assignedSubjectsCount ?? t('analytics.notEnoughDataYet')}</p>
          <p>{t('teachers.scheduleLoad')}: {t('analytics.notEnoughDataYet')}</p>
        </div>
        <p className="text-sm font-semibold text-accent">{t('common.actions.open')}</p>
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
    queryFn: async () => {
      if (isAdmin) {
        const page = await educationService.listSubjects({ page: 0, size: 100, sortBy: 'name', direction: 'asc' })
        return page.items
      }
      return loadAccessibleSubjects(primaryRole, session?.user.id ?? '')
    },
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

  if (teacherQuery.isLoading || allSubjectsQuery.isLoading || scheduleQuery.isLoading || groupsQuery.isLoading) {
    return <LoadingState />
  }

  if (teacherQuery.isError || allSubjectsQuery.isError || scheduleQuery.isError || groupsQuery.isError || !teacherQuery.data) {
    return <ErrorState description={t('common.states.error')} title={t('navigation.shared.teachers')} />
  }

  const teacher = teacherQuery.data
  const displayName = getTeacherDisplayName(teacher)
  const lessons = scheduleQuery.data ?? []

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

      <PageHeader
        actions={(
          <Link to={`/schedule?teacherId=${teacherId}`}>
            <Button variant="secondary">
              <CalendarDays className="mr-2 h-4 w-4" />
              {t('navigation.shared.schedule')}
            </Button>
          </Link>
        )}
        description={teacher.email}
        title={displayName}
      />

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
            {lessons.length === 0 ? (
              <EmptyState description={t('teachers.noSchedule')} title={t('navigation.shared.schedule')} />
            ) : (
              <div className="space-y-3">
                {lessons.slice(0, 8).map((lesson) => {
                  const subject = assignedSubjects.find((item) => item.id === lesson.subjectId)
                  return (
                    <div key={`${lesson.date}-${lesson.slotId}-${lesson.groupId}`} className="rounded-[16px] border border-border bg-surface-muted p-4">
                      <p className="font-semibold text-text-primary">{subject?.name ?? t('education.subject')}</p>
                      <p className="mt-1 text-sm text-text-secondary">{formatDate(lesson.date)}</p>
                    </div>
                  )
                })}
              </div>
            )}
          </Card>
        </main>

        <aside className="space-y-5">
          <Card className="space-y-4">
            <div className="flex items-center gap-3">
              <UserAvatar
                displayName={displayName}
                email={teacher.email}
                size="lg"
                username={teacher.username}
              />
              <div>
                <p className="font-semibold text-text-primary">{displayName}</p>
                <p className="text-sm text-text-secondary">{teacher.username}</p>
              </div>
            </div>
          </Card>

          <Card className="space-y-4">
            <PageHeader title={t('navigation.shared.groups')} />
            {(groupsQuery.data ?? []).length === 0 ? (
              <EmptyState description={t('teachers.noGroups')} title={t('navigation.shared.groups')} />
            ) : (
              <div className="space-y-2">
                {(groupsQuery.data ?? []).map((group) => (
                  <Link key={group.id} className="block rounded-[14px] border border-border bg-surface-muted px-3 py-2 text-sm font-medium text-text-primary" to={`/groups/${group.id}`}>
                    {group.name}
                  </Link>
                ))}
              </div>
            )}
          </Card>

          <Card className="space-y-4">
            <PageHeader title={t('navigation.shared.analytics')} />
            {analyticsQuery.data ? (
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
