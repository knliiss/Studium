import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import { loadAccessibleSubjects, loadManagedSubjects } from '@/pages/education/helpers'
import { educationService, testingService, userDirectoryService } from '@/shared/api/services'
import { getLocalizedRequestErrorMessage } from '@/shared/lib/api-errors'
import { formatDateTime } from '@/shared/lib/format'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { DataTable } from '@/shared/ui/DataTable'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { SegmentedControl } from '@/shared/ui/SegmentedControl'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { Breadcrumbs } from '@/widgets/common/Breadcrumbs'
import { MetricCard } from '@/widgets/common/MetricCard'
import { PageHeader } from '@/shared/ui/PageHeader'

interface AttemptRow {
  id: string
  studentId: string
  studentName: string
  groupName: string
  attemptNumber: number
  status: 'REVIEWED' | 'PENDING_REVIEW'
  score: number
  maxPoints: number
  percent: number
  timeSpentSeconds: number | null
  submittedAt: string
  reviewedAt: string | null
  reviewedByName: string
}

type ResultsTab = 'attempts' | 'questionStats'

export function TestResultsPage({
  testId,
  embedded = false,
}: {
  testId: string
  embedded?: boolean
}) {
  const { t } = useTranslation()
  const { primaryRole, session } = useAuth()
  const managementEnabled = primaryRole !== 'STUDENT'
  const [activeTab, setActiveTab] = useState<ResultsTab>('attempts')
  const [studentSearch, setStudentSearch] = useState('')
  const [groupFilter, setGroupFilter] = useState('ALL')
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'REVIEWED' | 'PENDING_REVIEW'>('ALL')

  const testQuery = useQuery({
    queryKey: ['tests', testId],
    queryFn: () => testingService.getTest(testId),
    enabled: managementEnabled,
  })
  const resultsQuery = useQuery({
    queryKey: ['tests', testId, 'results'],
    queryFn: () => testingService.getTestResultsByTest(testId, { page: 0, size: 100 }),
    enabled: managementEnabled,
  })
  const questionStatsQuery = useQuery({
    queryKey: ['tests', testId, 'question-stats'],
    queryFn: () => testingService.getTestQuestionStatistics(testId),
    enabled: managementEnabled,
  })
  const resultReviewsQuery = useQuery({
    queryKey: ['tests', testId, 'result-review-metadata', (resultsQuery.data?.items ?? []).map((item) => item.id).join(',')],
    queryFn: async () => {
      const resultIds = (resultsQuery.data?.items ?? []).map((item) => item.id)
      if (resultIds.length === 0) {
        return []
      }
      return Promise.all(resultIds.map((id) => testingService.getTestResultReview(id)))
    },
    enabled: Boolean(managementEnabled && resultsQuery.data),
  })

  const subjectScopeQuery = useQuery({
    queryKey: ['education', 'test-results-subject-scope', primaryRole, session?.user.id],
    queryFn: () => {
      if (primaryRole === 'TEACHER') {
        return loadAccessibleSubjects(primaryRole, session?.user.id ?? '')
      }
      return loadManagedSubjects()
    },
    enabled: Boolean(managementEnabled && session?.user.id),
  })
  const testSubjectQuery = useQuery({
    queryKey: ['education', 'test-results-subject', testId, testQuery.data?.topicId, subjectScopeQuery.data?.length],
    queryFn: async () => {
      const test = testQuery.data
      if (!test) {
        return null
      }

      for (const subject of subjectScopeQuery.data ?? []) {
        const topicsPage = await educationService.getTopicsBySubject(subject.id, {
          page: 0,
          size: 100,
          sortBy: 'orderIndex',
          direction: 'asc',
        })
        if (topicsPage.items.some((topic) => topic.id === test.topicId)) {
          return subject
        }
      }

      return null
    },
    enabled: Boolean(managementEnabled && testQuery.data && subjectScopeQuery.data),
  })

  const studentIds = useMemo(
    () => Array.from(new Set((resultsQuery.data?.items ?? []).map((item) => item.userId))),
    [resultsQuery.data?.items],
  )
  const reviewedByIds = useMemo(
    () => Array.from(new Set((resultsQuery.data?.items ?? []).map((item) => item.reviewedByUserId).filter(Boolean) as string[])),
    [resultsQuery.data?.items],
  )
  const usersQuery = useQuery({
    queryKey: ['tests', testId, 'result-users', [...studentIds, ...reviewedByIds].sort().join(',')],
    queryFn: () => userDirectoryService.lookup([...studentIds, ...reviewedByIds]),
    enabled: managementEnabled && (studentIds.length > 0 || reviewedByIds.length > 0),
  })

  if (!managementEnabled) {
    return <ErrorState title={t('testing.testResultsTitle')} description={t('errors:ACCESS_DENIED')} />
  }

  if (
    testQuery.isLoading
    || resultsQuery.isLoading
    || questionStatsQuery.isLoading
    || subjectScopeQuery.isLoading
    || testSubjectQuery.isLoading
    || resultReviewsQuery.isLoading
    || usersQuery.isLoading
  ) {
    return <LoadingState />
  }

  if (
    testQuery.isError
    || resultsQuery.isError
    || questionStatsQuery.isError
    || subjectScopeQuery.isError
    || testSubjectQuery.isError
    || resultReviewsQuery.isError
    || usersQuery.isError
    || !testQuery.data
  ) {
    return (
      <ErrorState
        title={t('testing.testResultsTitle')}
        description={getLocalizedRequestErrorMessage(
          testQuery.error
            ?? resultsQuery.error
            ?? questionStatsQuery.error
            ?? subjectScopeQuery.error
            ?? testSubjectQuery.error
            ?? resultReviewsQuery.error
            ?? usersQuery.error,
          t,
        )}
        onRetry={() => {
          void testQuery.refetch()
          void resultsQuery.refetch()
          void questionStatsQuery.refetch()
        }}
      />
    )
  }

  const test = testQuery.data
  const userById = new Map((usersQuery.data ?? []).map((item) => [item.id, item]))
  const reviewByResultId = new Map((resultReviewsQuery.data ?? []).map((item) => [item.resultId, item]))

  const attemptRows = buildAttemptRows(
    resultsQuery.data?.items ?? [],
    userById,
    reviewByResultId,
    test.maxPoints,
    t,
  )

  const filteredRows = attemptRows.filter((row) => {
    const matchesSearch = !studentSearch.trim() || row.studentName.toLowerCase().includes(studentSearch.toLowerCase())
    const matchesGroup = groupFilter === 'ALL' || row.groupName === groupFilter
    const matchesStatus = statusFilter === 'ALL' || row.status === statusFilter
    return matchesSearch && matchesGroup && matchesStatus
  })

  const uniqueGroupNames = Array.from(new Set(attemptRows.map((row) => row.groupName).filter(Boolean))).sort()
  const reviewedCount = attemptRows.filter((row) => row.status === 'REVIEWED').length
  const pendingCount = attemptRows.length - reviewedCount
  const averageScore = attemptRows.length > 0
    ? attemptRows.reduce((sum, row) => sum + row.score, 0) / attemptRows.length
    : 0
  const maxScore = attemptRows.length > 0
    ? Math.max(...attemptRows.map((row) => row.score))
    : 0
  const averageTimeSeconds = attemptRows.filter((row) => row.timeSpentSeconds != null)
  const avgTime = averageTimeSeconds.length > 0
    ? averageTimeSeconds.reduce((sum, row) => sum + (row.timeSpentSeconds ?? 0), 0) / averageTimeSeconds.length
    : null

  return (
    <div className={embedded ? 'space-y-4' : 'space-y-6'}>
      {!embedded ? (
        <Breadcrumbs
          items={testSubjectQuery.data
            ? [
                { label: t('navigation.shared.subjects'), to: '/subjects' },
                { label: testSubjectQuery.data.name, to: `/subjects/${testSubjectQuery.data.id}` },
                { label: t('navigation.shared.tests'), to: `/subjects/${testSubjectQuery.data.id}?tab=tests` },
                { label: test.title, to: `/tests/${testId}` },
                { label: t('testing.testResultsTitle') },
              ]
            : [
                { label: t('navigation.shared.tests'), to: '/tests' },
                { label: test.title, to: `/tests/${testId}` },
                { label: t('testing.testResultsTitle') },
              ]}
        />
      ) : null}

      <PageHeader
        description={embedded ? undefined : t('testing.testResultsDescription')}
        title={t('testing.testResultsTitle')}
      />

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-6">
        <MetricCard title={t('testing.studentsCompleted')} value={String(new Set(attemptRows.map((row) => row.studentId)).size)} />
        <MetricCard title={t('testing.averageScore')} value={attemptRows.length > 0 ? averageScore.toFixed(1) : '-'} />
        <MetricCard title={t('testing.maxScore')} value={String(maxScore)} />
        <MetricCard title={t('testing.averageTime')} value={formatDuration(avgTime, t)} />
        <MetricCard title={t('testing.attempts')} value={String(attemptRows.length)} />
        <MetricCard title={t('testing.questions')} value={String(questionStatsQuery.data?.length ?? 0)} />
      </div>

      <Card className="space-y-2 rounded-2xl border border-border bg-surface p-4">
        <p className="text-sm text-text-secondary">{t('testing.reviewStatusSummary', { reviewed: reviewedCount, pending: pendingCount })}</p>
      </Card>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_220px_220px]">
        <FormField label={t('testing.searchStudent')}>
          <Input value={studentSearch} onChange={(event) => setStudentSearch(event.target.value)} />
        </FormField>
        <FormField label={t('testing.groupFilter')}>
          <select
            className="field-control min-h-11 px-3"
            value={groupFilter}
            onChange={(event) => setGroupFilter(event.target.value)}
          >
            <option value="ALL">{t('common.labels.all')}</option>
            {uniqueGroupNames.map((groupName) => (
              <option key={groupName} value={groupName}>{groupName}</option>
            ))}
          </select>
        </FormField>
        <FormField label={t('testing.reviewStatus')}>
          <SegmentedControl
            ariaLabel={t('testing.reviewStatus')}
            options={[
              { value: 'ALL', label: t('common.labels.all') },
              { value: 'PENDING_REVIEW', label: t('testing.pendingReview') },
              { value: 'REVIEWED', label: t('testing.reviewed') },
            ]}
            value={statusFilter}
            onChange={(value) => setStatusFilter(value as 'ALL' | 'REVIEWED' | 'PENDING_REVIEW')}
          />
        </FormField>
      </div>

      <div className="overflow-x-auto pb-1">
        <div className="inline-flex min-w-full gap-2 rounded-2xl border border-border bg-surface p-1">
          {([
            { id: 'attempts', label: t('testing.resultsTabs.attempts') },
            { id: 'questionStats', label: t('testing.resultsTabs.questionStats') },
          ] as Array<{ id: ResultsTab; label: string }>).map((tab) => (
            <button
              key={tab.id}
              className={`whitespace-nowrap rounded-xl px-3 py-2 text-sm font-medium transition ${activeTab === tab.id ? 'bg-accent text-accent-foreground' : 'text-text-secondary hover:bg-surface-muted'}`}
              type="button"
              onClick={() => setActiveTab(tab.id)}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {activeTab === 'attempts' ? (
        filteredRows.length === 0 ? (
          <EmptyState description={t('testing.noResultsToReview')} title={t('testing.resultsTabs.attempts')} />
        ) : (
          <DataTable
            columns={[
              { key: 'student', header: t('testing.student'), render: (row) => row.studentName },
              { key: 'group', header: t('navigation.shared.groups'), render: (row) => row.groupName },
              { key: 'attempt', header: t('testing.attemptNumber'), render: (row) => row.attemptNumber },
              { key: 'status', header: t('testing.reviewStatus'), render: (row) => row.status === 'REVIEWED' ? t('testing.reviewed') : t('testing.pendingReview') },
              { key: 'score', header: t('common.labels.score'), render: (row) => `${row.score}/${row.maxPoints}` },
              { key: 'percent', header: t('testing.percent'), render: (row) => `${row.percent}%` },
              { key: 'timeSpent', header: t('testing.timeSpent'), render: (row) => formatDuration(row.timeSpentSeconds, t) },
              { key: 'submittedAt', header: t('testing.submittedAt'), render: (row) => formatDateTime(row.submittedAt) },
              { key: 'reviewedAt', header: t('testing.reviewedAt'), render: (row) => row.reviewedAt ? formatDateTime(row.reviewedAt) : '-' },
              { key: 'reviewedBy', header: t('testing.reviewedBy'), render: (row) => row.reviewedByName || '-' },
              {
                key: 'actions',
                header: t('common.labels.actions'),
                render: (row) => (
                  <Link to={`/tests/${testId}/results/${row.id}/review`}>
                    <Button variant="secondary">{t('common.actions.view')}</Button>
                  </Link>
                ),
              },
            ]}
            rows={filteredRows}
          />
        )
      ) : null}

      {activeTab === 'questionStats' ? (
        questionStatsQuery.data && questionStatsQuery.data.length > 0 ? (
          <DataTable
            columns={[
              { key: 'order', header: '#', render: (item) => item.orderIndex + 1 },
              { key: 'question', header: t('testing.question'), render: (item) => item.questionText },
              { key: 'type', header: t('testing.questionTypeLabel'), render: (item) => t(`testing.questionType.${item.questionType}`) },
              { key: 'attempts', header: t('testing.attempts'), render: (item) => item.attemptsCount },
              { key: 'avgScore', header: t('testing.averageScore'), render: (item) => `${item.averageScore.toFixed(1)}/${item.maxPoints}` },
              { key: 'full', header: t('testing.fullScoreCount'), render: (item) => item.fullScoreCount },
              { key: 'zero', header: t('testing.zeroScoreCount'), render: (item) => item.zeroScoreCount },
            ]}
            rows={questionStatsQuery.data}
          />
        ) : (
          <EmptyState description={t('testing.noQuestionStats')} title={t('testing.resultsTabs.questionStats')} />
        )
      ) : null}
    </div>
  )
}

function buildAttemptRows(
  results: Array<{ id: string; userId: string; score: number; reviewedAt: string | null; reviewedByUserId: string | null; createdAt: string }>,
  usersById: Map<string, { username: string }>,
  reviewByResultId: Map<string, { resultId: string; totalTimeSpentSeconds: number | null }>,
  maxPoints: number,
  t: (key: string) => string,
): AttemptRow[] {
  const sorted = [...results].sort((a, b) => a.createdAt.localeCompare(b.createdAt))
  const perUserAttemptIndex = new Map<string, number>()

  return sorted.map((result) => {
    const currentAttempt = (perUserAttemptIndex.get(result.userId) ?? 0) + 1
    perUserAttemptIndex.set(result.userId, currentAttempt)

    const reviewMeta = reviewByResultId.get(result.id)
    const percent = maxPoints > 0 ? Math.round((result.score / maxPoints) * 100) : 0

    return {
      id: result.id,
      studentId: result.userId,
      studentName: usersById.get(result.userId)?.username ?? t('education.unknownStudent'),
      groupName: '-',
      attemptNumber: currentAttempt,
      status: result.reviewedAt ? 'REVIEWED' : 'PENDING_REVIEW',
      score: result.score,
      maxPoints,
      percent,
      timeSpentSeconds: reviewMeta?.totalTimeSpentSeconds ?? null,
      submittedAt: result.createdAt,
      reviewedAt: result.reviewedAt,
      reviewedByName: result.reviewedByUserId ? (usersById.get(result.reviewedByUserId)?.username ?? '-') : '-',
    }
  })
}

function formatDuration(seconds: number | null, t: (key: string, values?: Record<string, unknown>) => string) {
  if (seconds == null || seconds < 0) {
    return '-'
  }
  const minutes = Math.floor(seconds / 60)
  const remainder = seconds % 60
  return t('testing.timeSpentValue', { minutes, seconds: remainder.toString().padStart(2, '0') })
}
