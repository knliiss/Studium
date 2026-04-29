import { useQuery } from '@tanstack/react-query'
import { BookOpen, GraduationCap, Users } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import { adminUserService, analyticsService, educationService } from '@/shared/api/services'
import { formatDateTime } from '@/shared/lib/format'
import { Card } from '@/shared/ui/Card'
import { CardPicker } from '@/shared/ui/CardPicker'
import type { CardPickerItem } from '@/shared/ui/CardPicker'
import { PageHeader } from '@/shared/ui/PageHeader'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { UserAvatar } from '@/shared/ui/UserAvatar'
import { MetricCard } from '@/widgets/common/MetricCard'
import { RiskBadge } from '@/widgets/common/RiskBadge'

type AnalyticsCategory = 'groups' | 'subjects' | 'students'

export function AnalyticsPage() {
  const { primaryRole, session } = useAuth()
  const userId = session?.user.id ?? ''

  if (primaryRole === 'STUDENT') {
    return <StudentAnalytics userId={userId} />
  }
  if (primaryRole === 'TEACHER') {
    return <TeacherAnalytics teacherId={userId} />
  }

  return <AdminAnalytics />
}

function StudentAnalytics({ userId }: { userId: string }) {
  const { t } = useTranslation()
  const analyticsQuery = useQuery({
    queryKey: ['analytics', 'student', userId],
    queryFn: () => analyticsService.getStudent(userId),
    enabled: Boolean(userId),
  })
  const subjectsQuery = useQuery({
    queryKey: ['analytics', 'student-subjects', userId],
    queryFn: () => analyticsService.getStudentSubjects(userId),
    enabled: Boolean(userId),
  })
  const riskQuery = useQuery({
    queryKey: ['analytics', 'student-risk', userId],
    queryFn: () => analyticsService.getStudentRisk(userId),
    enabled: Boolean(userId),
  })
  const subjectIds = useMemo(
    () => Array.from(new Set((subjectsQuery.data ?? []).map((row) => row.subjectId))),
    [subjectsQuery.data],
  )
  const subjectNamesQuery = useQuery({
    queryKey: ['analytics', 'student-subject-names', subjectIds.join(',')],
    queryFn: () => loadSubjectsById(subjectIds),
    enabled: subjectIds.length > 0,
  })
  const subjectNameById = useMemo(
    () => new Map((subjectNamesQuery.data ?? []).map((subject) => [subject.id, subject.name])),
    [subjectNamesQuery.data],
  )

  if (analyticsQuery.isLoading || subjectsQuery.isLoading || riskQuery.isLoading) {
    return <LoadingState />
  }

  if (analyticsQuery.isError || subjectsQuery.isError || riskQuery.isError || !analyticsQuery.data || !riskQuery.data) {
    return <ErrorState title={t('analytics.title')} description={t('common.states.error')} />
  }

  const hasEnoughData = hasStudentAnalyticsData(analyticsQuery.data)

  return (
    <div className="space-y-6">
      <PageHeader description={t('analytics.studentDescription')} title={t('analytics.title')} />
      {!hasEnoughData ? (
        <EmptyState description={t('analytics.notEnoughDataYet')} title={t('analytics.title')} />
      ) : (
        <>
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <MetricCard title={t('dashboard.metrics.averageScore')} value={analyticsQuery.data.averageScore ?? '-'} />
            <MetricCard title={t('dashboard.metrics.activityScore')} value={analyticsQuery.data.activityScore} />
            <MetricCard title={t('dashboard.metrics.disciplineScore')} value={analyticsQuery.data.disciplineScore} />
            <MetricCard title={t('analytics.lastActivity')} value={formatDateTime(analyticsQuery.data.lastActivityAt)} />
          </div>
          <Card className="space-y-3">
            <div className="flex items-center gap-3">
              <span className="text-sm font-semibold text-text-secondary">{t('analytics.riskLevel.label')}</span>
              <RiskBadge value={riskQuery.data.riskLevel} />
            </div>
          </Card>
        </>
      )}

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {(subjectsQuery.data ?? []).map((row) => (
          <Card key={`${row.subjectId}-${row.groupId}`} className="space-y-3">
            <h2 className="text-lg font-semibold text-text-primary">
              {subjectNameById.get(row.subjectId) ?? t('education.subject')}
            </h2>
            <div className="grid gap-3 text-sm text-text-secondary">
              <p>{t('dashboard.metrics.averageScore')}: {row.averageScore ?? t('analytics.notEnoughDataYet')}</p>
              <p>{t('analytics.completionRate')}: {formatPercent(row.completionRate)}</p>
              <p>{t('education.overview.missedDeadlines')}: {formatPercent(row.missedDeadlineRate)}</p>
            </div>
          </Card>
        ))}
      </div>
    </div>
  )
}

function TeacherAnalytics({ teacherId }: { teacherId: string }) {
  const { t } = useTranslation()
  const analyticsQuery = useQuery({
    queryKey: ['analytics', 'teacher', teacherId],
    queryFn: () => analyticsService.getTeacher(teacherId),
    enabled: Boolean(teacherId),
  })
  const groupsQuery = useQuery({
    queryKey: ['analytics', 'teacher-groups-at-risk', teacherId],
    queryFn: () => analyticsService.getTeacherGroupsAtRisk(teacherId),
    enabled: Boolean(teacherId),
  })
  const groupIds = useMemo(
    () => Array.from(new Set((groupsQuery.data ?? []).map((group) => group.groupId))),
    [groupsQuery.data],
  )
  const groupNamesQuery = useQuery({
    queryKey: ['analytics', 'teacher-group-names', groupIds.join(',')],
    queryFn: () => loadGroupsById(groupIds),
    enabled: groupIds.length > 0,
  })
  const groupNameById = useMemo(
    () => new Map((groupNamesQuery.data ?? []).map((group) => [group.id, group.name])),
    [groupNamesQuery.data],
  )

  if (analyticsQuery.isLoading || groupsQuery.isLoading) {
    return <LoadingState />
  }

  if (analyticsQuery.isError || groupsQuery.isError || !analyticsQuery.data) {
    return <ErrorState title={t('analytics.title')} description={t('common.states.error')} />
  }

  const hasEnoughData = analyticsQuery.data.publishedAssignmentsCount > 0
    || analyticsQuery.data.publishedTestsCount > 0
    || analyticsQuery.data.assignedGradesCount > 0

  return (
    <div className="space-y-6">
      <PageHeader description={t('analytics.teacherDescription')} title={t('analytics.title')} />
      {!hasEnoughData ? (
        <EmptyState description={t('analytics.notEnoughDataYet')} title={t('analytics.title')} />
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          <MetricCard title={t('assignments.publishedAssignments')} value={analyticsQuery.data.publishedAssignmentsCount} />
          <MetricCard title={t('testing.publishedTests')} value={analyticsQuery.data.publishedTestsCount} />
          <MetricCard title={t('assignments.gradedSubmissions')} value={analyticsQuery.data.assignedGradesCount} />
          <MetricCard title={t('dashboard.metrics.averageScore')} value={analyticsQuery.data.averageStudentScore ?? '-'} />
        </div>
      )}
      {(groupsQuery.data ?? []).length === 0 ? (
        <EmptyState description={t('analytics.notEnoughDataYet')} title={t('dashboard.groupsAtRisk')} />
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {(groupsQuery.data ?? []).map((group) => (
            <Link key={group.groupId} to={`/groups/${group.groupId}`}>
              <Card className="space-y-3 transition hover:border-border-strong">
                <h2 className="text-lg font-semibold text-text-primary">
                  {groupNameById.get(group.groupId) ?? t('education.group')}
                </h2>
                <RiskBadge value={group.highRiskStudentsCount > 0 ? 'HIGH' : group.mediumRiskStudentsCount > 0 ? 'MEDIUM' : 'LOW'} />
                <p className="text-sm text-text-secondary">
                  {t('analytics.affectedStudents')}: {group.highRiskStudentsCount}
                </p>
              </Card>
            </Link>
          ))}
        </div>
      )}
    </div>
  )
}

function AdminAnalytics() {
  const { t } = useTranslation()
  const [category, setCategory] = useState<AnalyticsCategory>('groups')
  const [groupSearch, setGroupSearch] = useState('')
  const [subjectSearch, setSubjectSearch] = useState('')
  const [studentSearch, setStudentSearch] = useState('')
  const [selectedGroupId, setSelectedGroupId] = useState('')
  const [selectedSubjectId, setSelectedSubjectId] = useState('')
  const [selectedStudentId, setSelectedStudentId] = useState('')
  const overviewQuery = useQuery({
    queryKey: ['analytics', 'admin-overview'],
    queryFn: () => analyticsService.getDashboardOverview(),
  })
  const groupsQuery = useQuery({
    queryKey: ['analytics', 'groups', groupSearch],
    queryFn: () => educationService.listGroups({
      page: 0,
      size: 12,
      q: groupSearch.trim() || undefined,
      sortBy: 'name',
      direction: 'asc',
    }),
  })
  const subjectsQuery = useQuery({
    queryKey: ['analytics', 'subjects', subjectSearch],
    queryFn: () => educationService.listSubjects({
      page: 0,
      size: 12,
      q: subjectSearch.trim() || undefined,
      sortBy: 'name',
      direction: 'asc',
    }),
  })
  const studentsQuery = useQuery({
    queryKey: ['analytics', 'students', studentSearch],
    queryFn: () => adminUserService.list({
      page: 0,
      size: 12,
      role: 'STUDENT',
      search: studentSearch.trim() || undefined,
      sortBy: 'username',
      direction: 'asc',
    }),
  })
  const groupAnalyticsQuery = useQuery({
    queryKey: ['analytics', 'group-overview', selectedGroupId],
    queryFn: () => analyticsService.getGroupOverview(selectedGroupId),
    enabled: Boolean(selectedGroupId),
  })
  const subjectAnalyticsQuery = useQuery({
    queryKey: ['analytics', 'subject-overview', selectedSubjectId],
    queryFn: () => analyticsService.getSubjectAnalytics(selectedSubjectId),
    enabled: Boolean(selectedSubjectId),
  })
  const studentAnalyticsQuery = useQuery({
    queryKey: ['analytics', 'student-overview', selectedStudentId],
    queryFn: () => analyticsService.getStudent(selectedStudentId),
    enabled: Boolean(selectedStudentId),
  })

  const groupItems = useMemo<CardPickerItem[]>(
    () => (groupsQuery.data?.items ?? []).map((group) => ({
      id: group.id,
      title: group.name,
      description: t('education.groupCardDescription'),
      meta: t('education.groupScheduleStatusUnknown'),
      leading: <Users className="h-5 w-5 text-accent" />,
    })),
    [groupsQuery.data?.items, t],
  )
  const subjectItems = useMemo<CardPickerItem[]>(
    () => (subjectsQuery.data?.items ?? []).map((subject) => ({
      id: subject.id,
      title: subject.name,
      description: subject.description ?? t('education.subjectDescriptionFallback'),
      meta: t('education.subjectGroupsCount', { count: subject.groupIds.length }),
      leading: <BookOpen className="h-5 w-5 text-accent" />,
    })),
    [subjectsQuery.data?.items, t],
  )
  const studentItems = useMemo<CardPickerItem[]>(
    () => (studentsQuery.data?.content ?? []).map((student) => ({
      id: student.id,
      title: student.displayName?.trim() || student.username,
      description: student.email,
      meta: t('education.student'),
      leading: <UserAvatar email={student.email} username={student.username} size="sm" />,
    })),
    [studentsQuery.data?.content, t],
  )

  if (overviewQuery.isLoading) {
    return <LoadingState />
  }

  if (overviewQuery.isError || !overviewQuery.data) {
    return <ErrorState title={t('analytics.title')} description={t('common.states.error')} />
  }

  return (
    <div className="space-y-6">
      <PageHeader description={t('analytics.adminDescription')} title={t('analytics.title')} />
      {overviewQuery.data.totalStudentsTracked === 0 ? (
        <EmptyState description={t('analytics.notEnoughDataYet')} title={t('analytics.title')} />
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          <MetricCard title={t('dashboard.metrics.trackedStudents')} value={overviewQuery.data.totalStudentsTracked} />
          <MetricCard title={t('analytics.riskLevel.HIGH')} value={overviewQuery.data.highRiskStudentsCount} />
          <MetricCard title={t('dashboard.metrics.activityScore')} value={overviewQuery.data.averageActivityScore.toFixed(1)} />
          <MetricCard title={t('analytics.totalLateSubmissions')} value={overviewQuery.data.totalLateSubmissions} />
        </div>
      )}

      <div className="grid gap-4 md:grid-cols-3">
        {(['groups', 'subjects', 'students'] as AnalyticsCategory[]).map((item) => (
          <button
            key={item}
            className={`rounded-[18px] border p-4 text-left transition ${
              category === item ? 'border-accent bg-accent-muted/40' : 'border-border bg-surface'
            }`}
            type="button"
            onClick={() => setCategory(item)}
          >
            <GraduationCap className="mb-3 h-5 w-5 text-accent" />
            <p className="font-semibold text-text-primary">{t(`analytics.categories.${item}`)}</p>
          </button>
        ))}
      </div>

      <div className="grid gap-6 xl:grid-cols-[0.9fr_1.1fr]">
        <Card className="space-y-4">
          {category === 'groups' ? (
            <CardPicker
              emptyDescription={t('education.emptyGroupsSearch')}
              emptyTitle={t('navigation.shared.groups')}
              items={groupItems}
              loading={groupsQuery.isLoading}
              searchLabel={t('common.actions.search')}
              searchPlaceholder={t('education.groupSearchPlaceholder')}
              searchValue={groupSearch}
              selectedIds={selectedGroupId ? [selectedGroupId] : []}
              onSearchChange={setGroupSearch}
              onToggle={setSelectedGroupId}
            />
          ) : null}
          {category === 'subjects' ? (
            <CardPicker
              emptyDescription={t('education.emptySubjectsSearch')}
              emptyTitle={t('navigation.shared.subjects')}
              items={subjectItems}
              loading={subjectsQuery.isLoading}
              searchLabel={t('common.actions.search')}
              searchPlaceholder={t('education.subjectSearchPlaceholder')}
              searchValue={subjectSearch}
              selectedIds={selectedSubjectId ? [selectedSubjectId] : []}
              onSearchChange={setSubjectSearch}
              onToggle={setSelectedSubjectId}
            />
          ) : null}
          {category === 'students' ? (
            <CardPicker
              emptyDescription={t('education.emptyStudentCandidates')}
              emptyTitle={t('education.student')}
              items={studentItems}
              loading={studentsQuery.isLoading}
              searchLabel={t('common.actions.search')}
              searchPlaceholder={t('analytics.studentSearchPlaceholder')}
              searchValue={studentSearch}
              selectedIds={selectedStudentId ? [selectedStudentId] : []}
              onSearchChange={setStudentSearch}
              onToggle={setSelectedStudentId}
            />
          ) : null}
        </Card>

        <Card className="space-y-4">
          {category === 'groups' ? (
            groupAnalyticsQuery.data ? (
              <AnalyticsMetricGrid
                items={[
                  [t('dashboard.metrics.averageScore'), groupAnalyticsQuery.data.averageScore ?? t('analytics.notEnoughDataYet')],
                  [t('education.overview.atRiskStudents'), groupAnalyticsQuery.data.highRiskStudentsCount],
                  [t('education.overview.missedDeadlinesRaw'), groupAnalyticsQuery.data.totalMissedDeadlines],
                  [t('analytics.updatedAt'), formatDateTime(groupAnalyticsQuery.data.updatedAt)],
                ]}
              />
            ) : (
              <EmptyState description={t('analytics.selectGroup')} title={t('analytics.groupOverview')} />
            )
          ) : null}
          {category === 'subjects' ? (
            subjectAnalyticsQuery.data?.items.length ? (
              <AnalyticsMetricGrid
                items={[
                  [t('analytics.completionRate'), formatPercent(subjectAnalyticsQuery.data.items[0].completionRate)],
                  [t('education.overview.atRiskStudents'), subjectAnalyticsQuery.data.items[0].atRiskStudentsCount],
                  [t('education.overview.lateSubmissionRate'), formatPercent(subjectAnalyticsQuery.data.items[0].lateSubmissionRate)],
                  [t('analytics.updatedAt'), formatDateTime(subjectAnalyticsQuery.data.items[0].updatedAt)],
                ]}
              />
            ) : (
              <EmptyState description={selectedSubjectId ? t('analytics.notEnoughDataYet') : t('analytics.selectSubject')} title={t('analytics.subjectOverview')} />
            )
          ) : null}
          {category === 'students' ? (
            studentAnalyticsQuery.data && hasStudentAnalyticsData(studentAnalyticsQuery.data) ? (
              <AnalyticsMetricGrid
                items={[
                  [t('dashboard.metrics.averageScore'), studentAnalyticsQuery.data.averageScore ?? t('analytics.notEnoughDataYet')],
                  [t('dashboard.metrics.activityScore'), studentAnalyticsQuery.data.activityScore],
                  [t('dashboard.metrics.disciplineScore'), studentAnalyticsQuery.data.disciplineScore],
                  [t('analytics.lastActivity'), formatDateTime(studentAnalyticsQuery.data.lastActivityAt)],
                ]}
              />
            ) : (
              <EmptyState description={selectedStudentId ? t('analytics.notEnoughDataYet') : t('analytics.selectStudent')} title={t('education.student')} />
            )
          ) : null}
        </Card>
      </div>
    </div>
  )
}

function AnalyticsMetricGrid({ items }: { items: Array<[string, string | number]> }) {
  return (
    <div className="grid gap-4 md:grid-cols-2">
      {items.map(([title, value]) => (
        <MetricCard key={title} title={title} value={value} />
      ))}
    </div>
  )
}

function hasStudentAnalyticsData(data: {
  averageScore: number | null
  assignmentsSubmittedCount: number
  testsCompletedCount: number
  missedDeadlinesCount: number
  lastActivityAt: string | null
}) {
  return data.averageScore !== null
    || data.assignmentsSubmittedCount > 0
    || data.testsCompletedCount > 0
    || data.missedDeadlinesCount > 0
    || Boolean(data.lastActivityAt)
}

function formatPercent(value: number) {
  return `${Math.round(value * 100)}%`
}

async function loadSubjectsById(subjectIds: string[]) {
  const subjects = await Promise.all(subjectIds.map(async (subjectId) => {
    try {
      return await educationService.getSubject(subjectId)
    } catch {
      return null
    }
  }))
  return subjects.filter((subject): subject is NonNullable<typeof subject> => Boolean(subject))
}

async function loadGroupsById(groupIds: string[]) {
  const groups = await Promise.all(groupIds.map(async (groupId) => {
    try {
      return await educationService.getGroup(groupId)
    } catch {
      return null
    }
  }))
  return groups.filter((group): group is NonNullable<typeof group> => Boolean(group))
}