import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import { AccessDeniedPage } from '@/pages/shared/AccessDeniedPage'
import { assignmentService, dashboardService, educationService, testingService } from '@/shared/api/services'
import { isAccessDeniedApiError } from '@/shared/lib/api-errors'
import { formatDateTime } from '@/shared/lib/format'
import { hasAnyRole } from '@/shared/lib/roles'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { DataTable } from '@/shared/ui/DataTable'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { SectionTabs } from '@/shared/ui/SectionTabs'
import { Textarea } from '@/shared/ui/Textarea'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { Breadcrumbs } from '@/widgets/common/Breadcrumbs'
import { DeadlineBadge } from '@/widgets/common/DeadlineBadge'
import { MetricCard } from '@/widgets/common/MetricCard'
import { StatusBadge } from '@/widgets/common/StatusBadge'

type TopicTab = 'overview' | 'materials' | 'assignments' | 'tests' | 'progress'

export function TopicDetailPage() {
  const { t } = useTranslation()
  const { subjectId = '', topicId = '' } = useParams()
  const { roles, primaryRole } = useAuth()
  const queryClient = useQueryClient()
  const canManageAssessments = hasAnyRole(roles, ['TEACHER', 'ADMIN', 'OWNER'])
  const isStudent = primaryRole === 'STUDENT'

  const [activeTab, setActiveTab] = useState<TopicTab>('overview')
  const [assignmentForm, setAssignmentForm] = useState({
    title: '',
    description: '',
    deadline: '',
    allowLateSubmissions: true,
    maxSubmissions: 1,
    allowResubmit: true,
    maxFileSizeMb: 10,
  })
  const [testForm, setTestForm] = useState({
    title: '',
    maxAttempts: 1,
    timeLimitMinutes: 30,
    availableFrom: '',
    availableUntil: '',
    showCorrectAnswersAfterSubmit: false,
    shuffleQuestions: false,
    shuffleAnswers: false,
  })

  const subjectQuery = useQuery({
    queryKey: ['education', 'subject', subjectId],
    queryFn: () => educationService.getSubject(subjectId),
    enabled: Boolean(subjectId),
  })
  const topicsQuery = useQuery({
    queryKey: ['education', 'subject-topics', subjectId],
    queryFn: () => educationService.getTopicsBySubject(subjectId, { page: 0, size: 100 }),
    enabled: Boolean(subjectId),
  })
  const assignmentsQuery = useQuery({
    queryKey: ['education', 'topic-assignments', topicId],
    queryFn: () => assignmentService.getAssignmentsByTopic(topicId, { page: 0, size: 50 }),
    enabled: Boolean(topicId),
  })
  const testsQuery = useQuery({
    queryKey: ['education', 'topic-tests', topicId],
    queryFn: () => testingService.getTestsByTopic(topicId, { page: 0, size: 50 }),
    enabled: Boolean(topicId),
  })
  const studentDashboardQuery = useQuery({
    queryKey: ['dashboard', 'student'],
    queryFn: () => dashboardService.getStudentDashboard(),
    enabled: isStudent,
  })

  const createAssignmentMutation = useMutation({
    mutationFn: () =>
      assignmentService.createAssignment({
        ...assignmentForm,
        topicId,
        deadline: new Date(assignmentForm.deadline).toISOString(),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'topic-assignments', topicId] })
    },
  })
  const createTestMutation = useMutation({
    mutationFn: () =>
      testingService.createTest({
        ...testForm,
        topicId,
        availableFrom: testForm.availableFrom ? new Date(testForm.availableFrom).toISOString() : null,
        availableUntil: testForm.availableUntil ? new Date(testForm.availableUntil).toISOString() : null,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'topic-tests', topicId] })
    },
  })

  if (subjectQuery.isLoading || topicsQuery.isLoading || assignmentsQuery.isLoading || testsQuery.isLoading || studentDashboardQuery.isLoading) {
    return <LoadingState />
  }

  if (subjectQuery.isError || topicsQuery.isError || assignmentsQuery.isError || testsQuery.isError || studentDashboardQuery.isError) {
    if (
      isAccessDeniedApiError(subjectQuery.error)
      || isAccessDeniedApiError(topicsQuery.error)
      || isAccessDeniedApiError(assignmentsQuery.error)
      || isAccessDeniedApiError(testsQuery.error)
    ) {
      return <AccessDeniedPage />
    }

    return <ErrorState description={t('common.states.error')} title={t('education.topic')} />
  }

  const subject = subjectQuery.data
  const topic = topicsQuery.data?.items.find((item) => item.id === topicId)

  if (!subject || !topic) {
    return <ErrorState description={t('common.states.notFound')} title={t('education.topic')} />
  }

  const assignments = assignmentsQuery.data?.items ?? []
  const tests = testsQuery.data?.items ?? []
  const dashboard = studentDashboardQuery.data
  const topicAssignments = dashboard?.pendingAssignments.filter((item) => item.topicId === topicId) ?? []
  const topicTests = dashboard?.availableTests.filter((item) => item.topicId === topicId) ?? []
  const topicGrades = dashboard?.recentGrades.filter((item) => item.topicId === topicId) ?? []

  return (
    <div className="space-y-6">
      <Breadcrumbs
        items={[
          { label: t('navigation.shared.education'), to: '/education' },
          { label: t('navigation.shared.subjects'), to: '/subjects' },
          { label: subject.name, to: `/subjects/${subjectId}` },
          { label: topic.title },
        ]}
      />

      <PageHeader
        actions={(
          <Link to={`/subjects/${subjectId}`}>
            <Button variant="secondary">{t('education.backToSubject')}</Button>
          </Link>
        )}
        description={t('education.topicDetailDescription')}
        title={topic.title}
      />

      <SectionTabs
        activeId={activeTab}
        items={[
          { id: 'overview', label: t('education.topicTabs.overview') },
          { id: 'materials', label: t('education.topicTabs.materials') },
          { id: 'assignments', label: t('education.topicTabs.assignments') },
          { id: 'tests', label: t('education.topicTabs.tests') },
          { id: 'progress', label: t('education.topicTabs.progress') },
        ]}
        onChange={(tabId) => setActiveTab(tabId as TopicTab)}
      />

      {activeTab === 'overview' ? (
        <div className="space-y-6">
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <MetricCard title={t('education.orderIndex')} value={topic.orderIndex} />
            <MetricCard title={t('education.overview.assignments')} value={assignments.length} />
            <MetricCard title={t('education.overview.tests')} value={tests.length} />
            <MetricCard title={t('education.topicParentSubject')} value={subject.name} />
          </div>

          <Card className="space-y-3">
            <PageHeader title={t('education.topicOverviewTitle')} />
            <p className="text-sm leading-6 text-text-secondary">{t('education.topicOverviewDescription')}</p>
          </Card>
        </div>
      ) : null}

      {activeTab === 'materials' ? (
        <EmptyState
          description={t('education.materialsUnavailable')}
          title={t('education.topicTabs.materials')}
        />
      ) : null}

      {activeTab === 'assignments' ? (
        <div className="space-y-6">
          {canManageAssessments ? (
            <Card className="space-y-4">
              <PageHeader
                description={t('assignments.topicCreateDescription')}
                title={t('assignments.createAssignment')}
              />
              <div className="grid gap-4 xl:grid-cols-2">
                <FormField label={t('common.labels.title')}>
                  <Input
                    value={assignmentForm.title}
                    onChange={(event) => setAssignmentForm((current) => ({ ...current, title: event.target.value }))}
                  />
                </FormField>
                <FormField label={t('common.labels.deadline')}>
                  <Input
                    type="datetime-local"
                    value={assignmentForm.deadline}
                    onChange={(event) => setAssignmentForm((current) => ({ ...current, deadline: event.target.value }))}
                  />
                </FormField>
              </div>
              <FormField label={t('common.labels.description')}>
                <Textarea
                  value={assignmentForm.description}
                  onChange={(event) => setAssignmentForm((current) => ({ ...current, description: event.target.value }))}
                />
              </FormField>
              <Button disabled={createAssignmentMutation.isPending} onClick={() => createAssignmentMutation.mutate()}>
                {t('common.actions.create')}
              </Button>
            </Card>
          ) : null}

          {assignments.length === 0 ? (
            <EmptyState description={t('assignments.empty')} title={t('education.topicTabs.assignments')} />
          ) : (
            <DataTable
              columns={[
                {
                  key: 'title',
                  header: t('common.labels.title'),
                  render: (assignment) => (
                    <Link className="font-medium text-accent" to={`/assignments/${assignment.id}`}>
                      {assignment.title}
                    </Link>
                  ),
                },
                {
                  key: 'deadline',
                  header: t('common.labels.deadline'),
                  render: (assignment) => <DeadlineBadge deadline={assignment.deadline} />,
                },
                {
                  key: 'status',
                  header: t('common.labels.status'),
                  render: (assignment) => <StatusBadge value={assignment.status} />,
                },
              ]}
              rows={assignments}
            />
          )}
        </div>
      ) : null}

      {activeTab === 'tests' ? (
        <div className="space-y-6">
          {canManageAssessments ? (
            <Card className="space-y-4">
              <PageHeader
                description={t('testing.topicCreateDescription')}
                title={t('testing.createTest')}
              />
              <div className="grid gap-4 xl:grid-cols-2">
                <FormField label={t('common.labels.title')}>
                  <Input
                    value={testForm.title}
                    onChange={(event) => setTestForm((current) => ({ ...current, title: event.target.value }))}
                  />
                </FormField>
                <FormField label={t('testing.availableUntil')}>
                  <Input
                    type="datetime-local"
                    value={testForm.availableUntil}
                    onChange={(event) => setTestForm((current) => ({ ...current, availableUntil: event.target.value }))}
                  />
                </FormField>
              </div>
              <Button disabled={createTestMutation.isPending} onClick={() => createTestMutation.mutate()}>
                {t('common.actions.create')}
              </Button>
            </Card>
          ) : null}

          {tests.length === 0 ? (
            <EmptyState description={t('testing.empty')} title={t('education.topicTabs.tests')} />
          ) : (
            <DataTable
              columns={[
                {
                  key: 'title',
                  header: t('common.labels.title'),
                  render: (test) => (
                    <Link className="font-medium text-accent" to={`/tests/${test.id}`}>
                      {test.title}
                    </Link>
                  ),
                },
                {
                  key: 'availableUntil',
                  header: t('testing.availableUntil'),
                  render: (test) => formatDateTime(test.availableUntil),
                },
                {
                  key: 'status',
                  header: t('common.labels.status'),
                  render: (test) => <StatusBadge value={test.status} />,
                },
              ]}
              rows={tests}
            />
          )}
        </div>
      ) : null}

      {activeTab === 'progress' ? (
        isStudent ? (
          <div className="space-y-6">
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
              <MetricCard title={t('education.progressAssignments')} value={topicAssignments.length} />
              <MetricCard title={t('education.progressTests')} value={topicTests.length} />
              <MetricCard title={t('education.progressGrades')} value={topicGrades.length} />
              <MetricCard
                title={t('dashboard.metrics.averageScore')}
                value={topicGrades.length ? (topicGrades.reduce((sum, item) => sum + item.score, 0) / topicGrades.length).toFixed(1) : '-'}
              />
            </div>
          </div>
        ) : (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <MetricCard title={t('education.progressAssignments')} value={assignments.length} />
            <MetricCard title={t('education.progressTests')} value={tests.length} />
            <MetricCard title={t('education.progressTopicOrder')} value={topic.orderIndex} />
            <MetricCard title={t('education.topicParentSubject')} value={subject.name} />
          </div>
        )
      ) : null}
    </div>
  )
}
