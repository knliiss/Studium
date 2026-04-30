import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import { dashboardService, educationService, testingService, userDirectoryService } from '@/shared/api/services'
import { formatDateTime } from '@/shared/lib/format'
import { toGroupOption, toSubjectOption, toTopicOption } from '@/shared/lib/picker-options'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { DataTable } from '@/shared/ui/DataTable'
import { EntityPicker } from '@/shared/ui/EntityPicker'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { SegmentedControl } from '@/shared/ui/SegmentedControl'
import { Textarea } from '@/shared/ui/Textarea'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { StatusBadge } from '@/widgets/common/StatusBadge'
import { loadAccessibleGroups, loadAccessibleSubjects, loadManagedSubjects } from '@/pages/education/helpers'
import type { QuestionType, TestGroupAvailabilityResponse } from '@/shared/types/api'
import { Breadcrumbs } from '@/widgets/common/Breadcrumbs'

interface TestManagementRow {
  id: string
  title: string
  status: string
  availableFrom: string | null
  availableUntil: string | null
  timeLimitMinutes: number | null
  maxAttempts: number
  maxPoints: number
  attemptsUsed?: number
}

const questionTypes: QuestionType[] = [
  'SINGLE_CHOICE',
  'MULTIPLE_CHOICE',
  'TRUE_FALSE',
  'SHORT_ANSWER',
  'LONG_TEXT',
  'NUMERIC',
  'MATCHING',
  'ORDERING',
  'FILL_IN_THE_BLANK',
  'FILE_ANSWER',
  'MANUAL_GRADING',
]

export function TestsPage() {
  const { primaryRole } = useAuth()
  const { testId } = useParams()

  if (testId) {
    return <TestDetailPage testId={testId} />
  }

  if (primaryRole === 'STUDENT') {
    return <StudentTestsPage />
  }

  return <ManagementTestsPage />
}

function StudentTestsPage() {
  const { t } = useTranslation()
  const dashboardQuery = useQuery({
    queryKey: ['dashboard', 'student'],
    queryFn: () => dashboardService.getStudentDashboard(),
  })

  if (dashboardQuery.isLoading) {
    return <LoadingState />
  }

  if (dashboardQuery.isError || !dashboardQuery.data) {
    return <ErrorState title={t('navigation.shared.tests')} description={t('common.states.error')} />
  }

  return (
    <div className="space-y-6">
      <PageHeader description={t('testing.studentDescription')} title={t('navigation.shared.tests')} />
      <DataTable
        columns={[
          { key: 'title', header: t('common.labels.title'), render: (item) => <Link className="font-medium text-accent" to={`/tests/${item.testId}`}>{item.title}</Link> },
          { key: 'status', header: t('common.labels.status'), render: (item) => <StatusBadge value={item.status} /> },
          { key: 'attempts', header: t('testing.attempts'), render: (item) => `${item.attemptsUsed}/${item.maxAttempts}` },
          { key: 'availableUntil', header: t('testing.availableUntil'), render: (item) => formatDateTime(item.availableUntil) },
        ]}
        rows={dashboardQuery.data.availableTests}
      />
    </div>
  )
}

function ManagementTestsPage() {
  const { t } = useTranslation()
  const { primaryRole, session } = useAuth()
  const queryClient = useQueryClient()
  const [query, setQuery] = useState('test')
  const [groupSearch, setGroupSearch] = useState('')
  const [selectedGroupId, setSelectedGroupId] = useState('')
  const [selectedSubjectId, setSelectedSubjectId] = useState('')
  const [form, setForm] = useState({
    topicId: '',
    title: '',
    maxAttempts: 1,
    maxPoints: 100,
    timeLimitMinutes: 30,
    availableFrom: '',
    availableUntil: '',
    showCorrectAnswersAfterSubmit: false,
    shuffleQuestions: false,
    shuffleAnswers: false,
  })
  const isTeacher = primaryRole === 'TEACHER'
  const normalizedGroupSearch = groupSearch.trim()

  const teacherDashboardQuery = useQuery({
    queryKey: ['dashboard', 'teacher'],
    queryFn: () => dashboardService.getTeacherDashboard(),
    enabled: isTeacher,
  })
  const adminSearchQuery = useQuery({
    queryKey: ['tests', 'search', query],
    queryFn: () => testingService.searchTests({ q: query, page: 0, size: 50 }),
    enabled: !isTeacher,
  })
  const accessibleGroupsQuery = useQuery({
    queryKey: ['education', 'groups', 'test-accessible', primaryRole, session?.user.id],
    queryFn: () => loadAccessibleGroups(primaryRole, session?.user.id ?? ''),
    enabled: Boolean(isTeacher && session?.user.id),
  })
  const accessibleSubjectsQuery = useQuery({
    queryKey: ['education', 'subjects', 'test-accessible', primaryRole, session?.user.id],
    queryFn: () => loadAccessibleSubjects(primaryRole, session?.user.id ?? ''),
    enabled: Boolean(isTeacher && session?.user.id),
  })
  const adminGroupSearchQuery = useQuery({
    queryKey: ['education', 'test-group-search', normalizedGroupSearch],
    queryFn: () => educationService.listGroups({
      page: 0,
      size: 20,
      q: normalizedGroupSearch || undefined,
      sortBy: 'name',
      direction: 'asc',
    }),
    enabled: !isTeacher,
  })
  const adminSubjectsQuery = useQuery({
    queryKey: ['education', 'test-subjects', selectedGroupId],
    queryFn: () => educationService.getSubjectsByGroup(selectedGroupId, { page: 0, size: 100 }),
    enabled: Boolean(!isTeacher && selectedGroupId),
  })
  const topicsQuery = useQuery({
    queryKey: ['education', 'test-topics', selectedSubjectId],
    queryFn: () => educationService.getTopicsBySubject(selectedSubjectId, { page: 0, size: 100 }),
    enabled: Boolean(selectedSubjectId),
  })

  const createMutation = useMutation({
    mutationFn: () =>
      testingService.createTest({
        ...form,
        availableFrom: form.availableFrom ? new Date(form.availableFrom).toISOString() : null,
        availableUntil: form.availableUntil ? new Date(form.availableUntil).toISOString() : null,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      await queryClient.invalidateQueries({ queryKey: ['tests'] })
    },
  })

  const groupNameById = useMemo(
    () => new Map((accessibleGroupsQuery.data ?? []).map((group) => [group.id, group.name])),
    [accessibleGroupsQuery.data],
  )
  const groupOptions = isTeacher
    ? (accessibleGroupsQuery.data ?? []).map((group) => toGroupOption(group))
    : (adminGroupSearchQuery.data?.items ?? []).map((group) => toGroupOption(group))
  const subjectOptions = isTeacher
    ? (accessibleSubjectsQuery.data ?? []).map((subject) => toSubjectOption(
        subject,
        subject.groupId ? groupNameById.get(subject.groupId) : undefined,
      ))
    : (adminSubjectsQuery.data?.items ?? []).map((subject) => toSubjectOption(subject))
  const topicOptions = (topicsQuery.data?.items ?? []).map((topic) => {
    const selectedSubject = subjectOptions.find((option) => option.value === selectedSubjectId)
    return toTopicOption(topic, selectedSubject?.label)
  })

  const rows: TestManagementRow[] = isTeacher
    ? (teacherDashboardQuery.data?.activeTests ?? []).map((item) => ({
        id: item.testId,
        title: item.title,
        status: item.status,
        availableFrom: item.availableFrom,
        availableUntil: item.availableUntil,
        timeLimitMinutes: item.timeLimitMinutes,
        maxAttempts: item.maxAttempts,
        maxPoints: 100,
        attemptsUsed: item.attemptsUsed,
      }))
    : (adminSearchQuery.data?.items ?? []).map((item) => ({
        id: item.id,
        title: item.title,
        status: String(item.targetMetadata.status ?? ''),
        availableFrom: null,
        availableUntil: typeof item.targetMetadata.availableUntil === 'string' && item.targetMetadata.availableUntil
          ? item.targetMetadata.availableUntil
          : null,
        timeLimitMinutes: null,
        maxAttempts: 0,
        maxPoints: 0,
      }))

  if (
    (isTeacher && (teacherDashboardQuery.isLoading || accessibleGroupsQuery.isLoading || accessibleSubjectsQuery.isLoading))
    || (!isTeacher && (adminSearchQuery.isLoading || adminSubjectsQuery.isLoading))
  ) {
    return <LoadingState />
  }

  if (
    (isTeacher && (teacherDashboardQuery.isError || accessibleGroupsQuery.isError || accessibleSubjectsQuery.isError))
    || (!isTeacher && (adminSearchQuery.isError || adminSubjectsQuery.isError || adminGroupSearchQuery.isError))
  ) {
    return <ErrorState title={t('navigation.shared.tests')} description={t('common.states.error')} />
  }

  return (
    <div className="space-y-6">
      <PageHeader
        description={t('testing.managementDescription')}
        title={t('navigation.shared.tests')}
      />

      <Card className="space-y-4">
        <PageHeader title={t('testing.createTest')} />
        <div className="grid gap-4 xl:grid-cols-3">
          {!isTeacher ? (
            <EntityPicker
              label={t('navigation.shared.groups')}
              value={selectedGroupId}
              options={groupOptions}
              placeholder={t('testing.selectGroup')}
              emptyLabel={t('education.noGroups')}
              searchLabel={t('common.actions.search')}
              searchPlaceholder={t('testing.groupSearchPlaceholder')}
              searchValue={groupSearch}
              onChange={(value) => {
                setSelectedGroupId(value)
                setSelectedSubjectId('')
                setForm((current) => ({ ...current, topicId: '' }))
              }}
              onSearchChange={setGroupSearch}
            />
          ) : null}
          <EntityPicker
            disabled={!isTeacher && !selectedGroupId}
            label={t('education.subject')}
            value={selectedSubjectId}
            options={subjectOptions}
            placeholder={t('testing.selectSubject')}
            emptyLabel={!isTeacher && !selectedGroupId ? t('testing.selectGroupFirst') : t('education.noSubjects')}
            onChange={(value) => {
              setSelectedSubjectId(value)
              setForm((current) => ({ ...current, topicId: '' }))
            }}
          />
          <EntityPicker
            disabled={!selectedSubjectId}
            label={t('education.topic')}
            value={form.topicId}
            options={topicOptions}
            placeholder={t('testing.selectTopic')}
            emptyLabel={!selectedSubjectId ? t('testing.selectSubjectFirst') : t('education.noTopics')}
            onChange={(value) => setForm((current) => ({ ...current, topicId: value }))}
          />
        </div>
        <div className="grid gap-4 xl:grid-cols-2">
          <FormField label={t('common.labels.title')}>
            <Input value={form.title} onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))} />
          </FormField>
          <FormField label={t('testing.availableUntil')}>
            <Input type="datetime-local" value={form.availableUntil} onChange={(event) => setForm((current) => ({ ...current, availableUntil: event.target.value }))} />
          </FormField>
        </div>
        <div className="grid gap-4 xl:grid-cols-4">
          <FormField label={t('testing.maxAttempts')}>
            <Input type="number" value={form.maxAttempts} onChange={(event) => setForm((current) => ({ ...current, maxAttempts: Number(event.target.value) }))} />
          </FormField>
          <FormField label={t('testing.maxPoints')}>
            <Input type="number" value={form.maxPoints} onChange={(event) => setForm((current) => ({ ...current, maxPoints: Number(event.target.value) }))} />
          </FormField>
          <FormField label={t('testing.timeLimitMinutes')}>
            <Input type="number" value={form.timeLimitMinutes} onChange={(event) => setForm((current) => ({ ...current, timeLimitMinutes: Number(event.target.value) }))} />
          </FormField>
          <FormField label={t('testing.availableFrom')}>
            <Input type="datetime-local" value={form.availableFrom} onChange={(event) => setForm((current) => ({ ...current, availableFrom: event.target.value }))} />
          </FormField>
        </div>
        <Button disabled={!form.topicId || createMutation.isPending} onClick={() => createMutation.mutate()}>
          {t('common.actions.create')}
        </Button>
      </Card>

      {!isTeacher ? (
        <Card className="space-y-4">
          <FormField label={t('common.labels.query')}>
            <Input value={query} onChange={(event) => setQuery(event.target.value)} />
          </FormField>
        </Card>
      ) : null}

      {rows.length === 0 ? (
        <EmptyState description={t('testing.empty')} title={t('navigation.shared.tests')} />
      ) : (
        <DataTable
          columns={[
            { key: 'title', header: t('common.labels.title'), render: (item) => <Link className="font-medium text-accent" to={`/tests/${item.id}`}>{item.title}</Link> },
            { key: 'status', header: t('common.labels.status'), render: (item) => <StatusBadge value={item.status} /> },
            { key: 'availableUntil', header: t('testing.availableUntil'), render: (item) => formatDateTime(item.availableUntil) },
          ]}
          rows={rows}
        />
      )}
    </div>
  )
}

function TestDetailPage({ testId }: { testId: string }) {
  const { t } = useTranslation()
  const { primaryRole, session } = useAuth()
  const queryClient = useQueryClient()
  const isStudent = primaryRole === 'STUDENT'
  const isTeacher = primaryRole === 'TEACHER'
  const [resultScore, setResultScore] = useState(0)
  const [availabilityGroupSearch, setAvailabilityGroupSearch] = useState('')
  const [availabilityForm, setAvailabilityForm] = useState({
    groupId: '',
    visible: false,
    availableFrom: '',
    availableUntil: '',
    deadline: '',
    maxAttempts: 1,
  })
  const [questionForm, setQuestionForm] = useState({
    text: '',
    description: '',
    type: 'SINGLE_CHOICE' as QuestionType,
    points: 1,
    orderIndex: 0,
    required: true,
    feedback: '',
  })
  const [answerForm, setAnswerForm] = useState({ questionId: '', text: '', isCorrect: false })
  const [resultOverrideForm, setResultOverrideForm] = useState({ resultId: '', score: 0, reason: '' })
  const testQuery = useQuery({
    queryKey: ['tests', testId],
    queryFn: () => testingService.getTest(testId),
  })
  const availabilityQuery = useQuery({
    queryKey: ['tests', testId, 'availability'],
    queryFn: () => testingService.getTestAvailability(testId),
    enabled: !isStudent,
  })
  const questionsQuery = useQuery({
    queryKey: ['tests', testId, 'questions'],
    queryFn: () => testingService.getQuestionsByTest(testId),
    enabled: !isStudent,
  })
  const normalizedAvailabilityGroupSearch = availabilityGroupSearch.trim()
  const accessibleGroupsQuery = useQuery({
    queryKey: ['education', 'test-detail-groups', primaryRole, session?.user.id],
    queryFn: () => loadAccessibleGroups(primaryRole, session?.user.id ?? ''),
    enabled: Boolean(!isStudent && isTeacher && session?.user.id),
  })
  const adminGroupSearchQuery = useQuery({
    queryKey: ['education', 'test-detail-group-search', normalizedAvailabilityGroupSearch],
    queryFn: () => educationService.listGroups({
      page: 0,
      size: 20,
      q: normalizedAvailabilityGroupSearch || undefined,
      sortBy: 'name',
      direction: 'asc',
    }),
    enabled: !isStudent && !isTeacher,
  })
  const availabilityGroupIds = useMemo(
    () => Array.from(new Set((availabilityQuery.data ?? []).map((item) => item.groupId))),
    [availabilityQuery.data],
  )
  const availabilityGroupsQuery = useQuery({
    queryKey: ['education', 'test-availability-groups', availabilityGroupIds],
    queryFn: async () => Promise.all(availabilityGroupIds.map((groupId) => educationService.getGroup(groupId))),
    enabled: !isStudent && availabilityGroupIds.length > 0,
  })
  const subjectScopeQuery = useQuery({
    queryKey: ['education', 'test-detail-subject-scope', primaryRole, session?.user.id],
    queryFn: () => isTeacher
      ? loadAccessibleSubjects(primaryRole, session?.user.id ?? '')
      : loadManagedSubjects(),
    enabled: Boolean(!isStudent && testQuery.data),
  })
  const testSubjectQuery = useQuery({
    queryKey: ['education', 'test-detail-subject', testId, testQuery.data?.topicId, subjectScopeQuery.data?.length],
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
    enabled: Boolean(!isStudent && testQuery.data && subjectScopeQuery.data),
  })
  const connectedGroupsQuery = useQuery({
    queryKey: ['education', 'test-connected-groups', testSubjectQuery.data?.groupIds.join(',')],
    queryFn: async () => Promise.all((testSubjectQuery.data?.groupIds ?? []).map((groupId) => educationService.getGroup(groupId))),
    enabled: Boolean(!isStudent && testSubjectQuery.data?.groupIds.length),
  })
  const testResultsQuery = useQuery({
    queryKey: ['tests', testId, 'results'],
    queryFn: () => testingService.getTestResultsByTest(testId, { page: 0, size: 50 }),
    enabled: !isStudent,
  })
  const resultStudentIds = useMemo(
    () => Array.from(new Set((testResultsQuery.data?.items ?? []).map((result) => result.userId))),
    [testResultsQuery.data?.items],
  )
  const resultStudentsQuery = useQuery({
    queryKey: ['tests', testId, 'result-students', resultStudentIds.join(',')],
    queryFn: () => userDirectoryService.lookup(resultStudentIds),
    enabled: resultStudentIds.length > 0,
  })

  const startMutation = useMutation({
    mutationFn: () => testingService.startTest(testId),
  })
  const submitResultMutation = useMutation({
    mutationFn: () => testingService.submitTestResult({ testId, score: resultScore }),
  })
  const publishMutation = useMutation({
    mutationFn: () => testingService.publishTest(testId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['tests'] })
    },
  })
  const closeMutation = useMutation({
    mutationFn: () => testingService.closeTest(testId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['tests'] })
    },
  })
  const archiveMutation = useMutation({
    mutationFn: () => testingService.archiveTest(testId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['tests'] })
    },
  })
  const availabilityMutation = useMutation({
    mutationFn: () =>
      testingService.upsertTestAvailability(testId, {
        groupId: availabilityForm.groupId,
        visible: availabilityForm.visible,
        availableFrom: availabilityForm.availableFrom ? new Date(availabilityForm.availableFrom).toISOString() : null,
        availableUntil: availabilityForm.availableUntil ? new Date(availabilityForm.availableUntil).toISOString() : null,
        deadline: availabilityForm.deadline ? new Date(availabilityForm.deadline).toISOString() : null,
        maxAttempts: availabilityForm.maxAttempts,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['tests', testId, 'availability'] })
      await queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })
  const createQuestionMutation = useMutation({
    mutationFn: () => testingService.createQuestion({
      ...questionForm,
      testId,
      orderIndex: questionForm.orderIndex || (questionsQuery.data?.length ?? 0),
    }),
    onSuccess: async (question) => {
      await queryClient.invalidateQueries({ queryKey: ['tests', testId, 'questions'] })
      setAnswerForm((current) => ({ ...current, questionId: question.id }))
      setQuestionForm((current) => ({
        ...current,
        text: '',
        description: '',
        feedback: '',
        orderIndex: (questionsQuery.data?.length ?? 0) + 1,
      }))
    },
  })
  const createAnswerMutation = useMutation({
    mutationFn: () => testingService.createAnswer(answerForm),
    onSuccess: () => {
      setAnswerForm((current) => ({ ...current, text: '', isCorrect: false }))
    },
  })
  const overrideResultMutation = useMutation({
    mutationFn: () => testingService.overrideTestResultScore(resultOverrideForm.resultId, {
      score: resultOverrideForm.score,
      reason: resultOverrideForm.reason.trim() || undefined,
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['tests', testId, 'results'] })
      setResultOverrideForm({ resultId: '', score: 0, reason: '' })
    },
  })

  if (
    testQuery.isLoading
    || questionsQuery.isLoading
    || (!isStudent && (
      availabilityQuery.isLoading
      || subjectScopeQuery.isLoading
      || testSubjectQuery.isLoading
      || connectedGroupsQuery.isLoading
      || testResultsQuery.isLoading
      || resultStudentsQuery.isLoading
    ))
  ) {
    return <LoadingState />
  }

  if (
    testQuery.isError
    || questionsQuery.isError
    || (!isStudent && (
      availabilityQuery.isError
      || subjectScopeQuery.isError
      || testSubjectQuery.isError
      || connectedGroupsQuery.isError
      || testResultsQuery.isError
      || resultStudentsQuery.isError
    ))
    || !testQuery.data
  ) {
    return <ErrorState title={t('navigation.shared.tests')} description={t('common.states.error')} />
  }

  const test = testQuery.data
  const questions = questionsQuery.data ?? []
  const availabilityRows = availabilityQuery.data ?? []
  const availabilityByGroupId = new Map(availabilityRows.map((availability) => [availability.groupId, availability]))
  const resultStudentById = new Map((resultStudentsQuery.data ?? []).map((student) => [student.id, student]))
  const availabilityGroupCards = (connectedGroupsQuery.data?.length ? connectedGroupsQuery.data : availabilityGroupsQuery.data ?? [])
    .map((group) => ({
      group,
      availability: availabilityByGroupId.get(group.id) ?? null,
    }))
  const groupOptions = isTeacher
    ? (connectedGroupsQuery.data?.length ? connectedGroupsQuery.data : accessibleGroupsQuery.data ?? []).map((group) => toGroupOption(group))
    : (connectedGroupsQuery.data?.length ? connectedGroupsQuery.data : adminGroupSearchQuery.data?.items ?? []).map((group) => toGroupOption(group))
  const usedPoints = questions.reduce((total, question) => total + question.points, 0)
  const remainingPoints = test.maxPoints - usedPoints
  const nextQuestionWouldExceed = usedPoints + questionForm.points > test.maxPoints
  const questionOptions = questions.map((question) => ({
    value: question.id,
    label: question.text,
    description: `${t(`testing.questionType.${question.type}`)} · ${question.points}`,
  }))
  const availabilitySaveDisabledReason = !availabilityForm.groupId
    ? t('availability.selectGroupReason')
    : !availabilityForm.availableUntil
      ? t('availability.selectAvailableUntilReason')
      : ''

  return (
    <div className="space-y-6">
      <Breadcrumbs items={[{ label: t('navigation.shared.tests'), to: '/tests' }, { label: test.title }]} />
      <Link to="/tests">
        <Button variant="secondary">{t('testing.backToTests')}</Button>
      </Link>
      <PageHeader description={t('testing.detailDescription')} title={test.title} />
      <Card className="space-y-4">
        <div className="flex flex-wrap items-center gap-3">
          <StatusBadge value={test.status} />
        </div>
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4 text-sm text-text-secondary">
          <p>{t('testing.attempts')}: {test.maxAttempts}</p>
          <p>{t('testing.maxPoints')}: {test.maxPoints}</p>
          <p>{t('testing.availableFrom')}: {formatDateTime(test.availableFrom)}</p>
          <p>{t('testing.availableUntil')}: {formatDateTime(test.availableUntil)}</p>
          <p>{t('testing.timeLimitMinutes')}: {test.timeLimitMinutes ?? '-'}</p>
        </div>

        {isStudent ? (
          <Card className="space-y-4 bg-surface-muted">
            <p className="text-sm leading-6 text-text-secondary">{t('testing.studentContractNote')}</p>
            <div className="flex flex-wrap gap-3">
              <Button variant="secondary" onClick={() => startMutation.mutate()}>
                {t('common.actions.start')}
              </Button>
              <FormField label={t('common.labels.score')}>
                <Input type="number" value={resultScore} onChange={(event) => setResultScore(Number(event.target.value))} />
              </FormField>
              <Button onClick={() => submitResultMutation.mutate()}>{t('common.actions.submit')}</Button>
            </div>
          </Card>
        ) : (
          <div className="space-y-4">
            <div className="flex flex-wrap gap-3">
              <Button disabled={usedPoints > test.maxPoints} variant="secondary" onClick={() => publishMutation.mutate()}>
                {t('common.actions.publish')}
              </Button>
              <Button variant="secondary" onClick={() => closeMutation.mutate()}>
                {t('testing.closeTest')}
              </Button>
              <Button onClick={() => archiveMutation.mutate()}>{t('assignments.archive')}</Button>
            </div>
            {usedPoints > test.maxPoints ? (
              <Card className="border-danger/30 bg-danger/5 px-4 py-3">
                <p className="text-sm font-semibold text-danger">{t('testing.pointsExceeded')}</p>
              </Card>
            ) : null}

            <div className="grid gap-4 xl:grid-cols-[280px_minmax(0,1fr)]">
              <Card className="space-y-4 bg-surface-muted">
                <PageHeader title={t('testing.questions')} />
                <div className="grid gap-3">
                  <MetricRow label={t('testing.maxPoints')} value={test.maxPoints} />
                  <MetricRow label={t('testing.usedPoints')} value={usedPoints} />
                  <MetricRow label={t('testing.remainingPoints')} value={Math.max(remainingPoints, 0)} />
                </div>
                {questions.length === 0 ? (
                  <p className="text-sm leading-6 text-text-secondary">{t('testing.noQuestions')}</p>
                ) : (
                  <div className="space-y-2">
                    {questions.map((question) => (
                      <button
                        key={question.id}
                        className="w-full rounded-[14px] border border-border bg-surface px-3 py-2 text-left transition hover:border-border-strong"
                        type="button"
                        onClick={() => setAnswerForm((current) => ({ ...current, questionId: question.id }))}
                      >
                        <p className="line-clamp-2 text-sm font-semibold text-text-primary">{question.text}</p>
                        <p className="mt-1 text-xs text-text-secondary">
                          {t(`testing.questionType.${question.type}`)} · {question.points}
                        </p>
                      </button>
                    ))}
                  </div>
                )}
              </Card>

              <Card className="space-y-4 bg-surface-muted">
                <PageHeader title={t('testing.questionEditor')} />
                <div className="grid gap-4 xl:grid-cols-2">
                  <FormField label={t('testing.questionTypeLabel')}>
                    <select
                      className="field-control min-h-11 px-3"
                      value={questionForm.type}
                      onChange={(event) => setQuestionForm((current) => ({ ...current, type: event.target.value as QuestionType }))}
                    >
                      {questionTypes.map((type) => (
                        <option key={type} value={type}>{t(`testing.questionType.${type}`)}</option>
                      ))}
                    </select>
                  </FormField>
                  <FormField label={t('testing.points')}>
                    <Input type="number" value={questionForm.points} onChange={(event) => setQuestionForm((current) => ({ ...current, points: Number(event.target.value) }))} />
                  </FormField>
                </div>
                <FormField label={t('testing.questionText')}>
                  <Textarea value={questionForm.text} onChange={(event) => setQuestionForm((current) => ({ ...current, text: event.target.value }))} />
                </FormField>
                <FormField label={t('common.labels.description')}>
                  <Textarea value={questionForm.description} onChange={(event) => setQuestionForm((current) => ({ ...current, description: event.target.value }))} />
                </FormField>
                <FormField label={t('testing.feedback')}>
                  <Textarea value={questionForm.feedback} onChange={(event) => setQuestionForm((current) => ({ ...current, feedback: event.target.value }))} />
                </FormField>
                {nextQuestionWouldExceed ? (
                  <p className="text-sm font-semibold text-danger">{t('testing.pointsExceeded')}</p>
                ) : null}
                <Button disabled={!questionForm.text.trim() || nextQuestionWouldExceed} onClick={() => createQuestionMutation.mutate()}>
                  {t('testing.addQuestion')}
                </Button>

                <div className="border-t border-border pt-4">
                  <PageHeader title={t('testing.addAnswer')} />
                  <div className="grid gap-4 xl:grid-cols-3">
                    <EntityPicker
                      label={t('testing.question')}
                      value={answerForm.questionId}
                      options={questionOptions}
                      placeholder={t('testing.selectQuestion')}
                      emptyLabel={t('testing.questionPickerHint')}
                      onChange={(value) => setAnswerForm((current) => ({ ...current, questionId: value }))}
                    />
                    <FormField label={t('testing.answerText')}>
                      <Input value={answerForm.text} onChange={(event) => setAnswerForm((current) => ({ ...current, text: event.target.value }))} />
                    </FormField>
                    <FormField label={t('testing.isCorrect')}>
                      <Input type="checkbox" checked={answerForm.isCorrect} onChange={(event) => setAnswerForm((current) => ({ ...current, isCorrect: event.target.checked }))} />
                    </FormField>
                  </div>
                  <Button disabled={!answerForm.questionId || !answerForm.text.trim()} onClick={() => createAnswerMutation.mutate()}>
                    {t('testing.addAnswer')}
                  </Button>
                </div>
              </Card>
            </div>
          </div>
        )}
      </Card>
      {!isStudent ? (
        <Card className="space-y-4">
          <PageHeader description={t('availability.testDescription')} title={t('availability.title')} />
          {availabilityGroupCards.length === 0 ? (
            <EmptyState description={t('availability.testEmpty')} title={t('availability.title')} />
          ) : (
            <div className="grid gap-3 md:grid-cols-2">
              {availabilityGroupCards.map(({ availability, group }) => (
                <button
                  key={group.id}
                  className="rounded-[14px] border border-border bg-surface-muted p-4 text-left transition hover:border-border-strong focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-accent/15"
                  type="button"
                  onClick={() => setAvailabilityForm({
                    groupId: group.id,
                    visible: availability?.visible ?? false,
                    availableFrom: toDateTimeLocal(availability?.availableFrom ?? test.availableFrom),
                    availableUntil: toDateTimeLocal(availability?.availableUntil ?? test.availableUntil),
                    deadline: toDateTimeLocal(availability?.deadline),
                    maxAttempts: availability?.maxAttempts ?? test.maxAttempts,
                  })}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="font-semibold text-text-primary">{group.name}</p>
                      <p className="mt-1 text-sm text-text-secondary">{t('testing.availableFrom')}: {availability?.availableFrom ? formatDateTime(availability.availableFrom) : t('availability.immediately')}</p>
                      <p className="text-sm text-text-secondary">{t('testing.availableUntil')}: {availability?.availableUntil ? formatDateTime(availability.availableUntil) : '-'}</p>
                      <p className="text-sm text-text-secondary">{t('testing.maxAttempts')}: {availability?.maxAttempts ?? test.maxAttempts}</p>
                    </div>
                    <span className={availability?.visible ? 'rounded-full bg-success/10 px-2.5 py-1 text-xs font-semibold text-success' : 'rounded-full bg-warning/10 px-2.5 py-1 text-xs font-semibold text-warning'}>
                      {availability ? getTestAvailabilityStatus(availability, t) : t('availability.hidden')}
                    </span>
                  </div>
                </button>
              ))}
            </div>
          )}

          <div className="grid gap-4 xl:grid-cols-3">
            <EntityPicker
              label={t('navigation.shared.groups')}
              value={availabilityForm.groupId}
              options={groupOptions}
              placeholder={t('availability.selectGroup')}
              emptyLabel={t('education.noGroups')}
              searchLabel={t('common.actions.search')}
              searchPlaceholder={t('availability.groupSearchPlaceholder')}
              searchValue={availabilityGroupSearch}
              onChange={(value) => setAvailabilityForm((current) => ({ ...current, groupId: value }))}
              onSearchChange={isTeacher ? undefined : setAvailabilityGroupSearch}
            />
            <FormField label={t('common.labels.status')}>
              <SegmentedControl
                ariaLabel={t('availability.title')}
                value={availabilityForm.visible ? 'visible' : 'hidden'}
                options={[
                  { value: 'hidden', label: t('availability.hidden') },
                  { value: 'visible', label: t('availability.visible') },
                ]}
                onChange={(value) => setAvailabilityForm((current) => ({ ...current, visible: value === 'visible' }))}
              />
            </FormField>
            <FormField label={t('testing.availableFrom')}>
              <Input type="datetime-local" value={availabilityForm.availableFrom} onChange={(event) => setAvailabilityForm((current) => ({ ...current, availableFrom: event.target.value }))} />
            </FormField>
            <FormField label={t('testing.availableUntil')}>
              <Input type="datetime-local" value={availabilityForm.availableUntil} onChange={(event) => setAvailabilityForm((current) => ({ ...current, availableUntil: event.target.value }))} />
            </FormField>
            <FormField label={t('common.labels.deadline')}>
              <Input type="datetime-local" value={availabilityForm.deadline} onChange={(event) => setAvailabilityForm((current) => ({ ...current, deadline: event.target.value }))} />
            </FormField>
            <FormField label={t('testing.maxAttempts')}>
              <Input type="number" value={availabilityForm.maxAttempts} onChange={(event) => setAvailabilityForm((current) => ({ ...current, maxAttempts: Number(event.target.value) }))} />
            </FormField>
          </div>
          {availabilitySaveDisabledReason ? (
            <p className="text-sm font-semibold text-text-secondary">{t('availability.saveUnavailable', { reason: availabilitySaveDisabledReason })}</p>
          ) : null}
          <Button disabled={Boolean(availabilitySaveDisabledReason) || availabilityMutation.isPending} onClick={() => availabilityMutation.mutate()}>
            {t('common.actions.save')}
          </Button>
        </Card>
      ) : null}
      {!isStudent ? (
        <Card className="space-y-4">
          <PageHeader description={t('testing.resultReviewDescription')} title={t('testing.resultReview')} />
          {(testResultsQuery.data?.items ?? []).length === 0 ? (
            <EmptyState description={t('testing.noResultsToReview')} title={t('testing.resultReview')} />
          ) : (
            <DataTable
              columns={[
                { key: 'userId', header: t('testing.student'), render: (item) => resultStudentById.get(item.userId)?.username ?? t('education.unknownStudent') },
                { key: 'score', header: t('common.labels.score'), render: (item) => `${item.score}/${test.maxPoints}` },
                { key: 'autoScore', header: t('testing.autoScore'), render: (item) => item.autoScore },
                { key: 'reviewedAt', header: t('testing.reviewedAt'), render: (item) => item.reviewedAt ? formatDateTime(item.reviewedAt) : t('testing.notReviewed') },
                {
                  key: 'actions',
                  header: t('common.labels.actions'),
                  render: (item) => (
                    <Button
                      variant="secondary"
                      onClick={() => setResultOverrideForm({
                        resultId: item.id,
                        score: item.score,
                        reason: item.manualOverrideReason ?? '',
                      })}
                    >
                      {t('testing.reviewResult')}
                    </Button>
                  ),
                },
              ]}
              rows={testResultsQuery.data?.items ?? []}
            />
          )}
          {resultOverrideForm.resultId ? (
            <div className="grid gap-4 xl:grid-cols-3">
              <FormField label={t('common.labels.score')}>
                <Input
                  max={test.maxPoints}
                  min={0}
                  type="number"
                  value={resultOverrideForm.score}
                  onChange={(event) => setResultOverrideForm((current) => ({ ...current, score: Number(event.target.value) }))}
                />
              </FormField>
              <FormField label={t('testing.overrideReason')}>
                <Input
                  value={resultOverrideForm.reason}
                  onChange={(event) => setResultOverrideForm((current) => ({ ...current, reason: event.target.value }))}
                />
              </FormField>
              <div className="flex items-end gap-3">
                <Button
                  disabled={resultOverrideForm.score < 0 || resultOverrideForm.score > test.maxPoints || overrideResultMutation.isPending}
                  onClick={() => overrideResultMutation.mutate()}
                >
                  {t('testing.saveReview')}
                </Button>
                <Button variant="secondary" onClick={() => setResultOverrideForm({ resultId: '', score: 0, reason: '' })}>
                  {t('common.actions.cancel')}
                </Button>
              </div>
            </div>
          ) : null}
          <p className="text-sm text-text-secondary">{t('testing.perQuestionReviewUnavailable')}</p>
        </Card>
      ) : null}
    </div>
  )
}

function MetricRow({ label, value }: { label: string; value: number }) {
  return (
    <div className="flex items-center justify-between gap-3 rounded-[14px] border border-border bg-surface px-3 py-2">
      <span className="text-sm text-text-secondary">{label}</span>
      <span className="text-sm font-semibold text-text-primary">{value}</span>
    </div>
  )
}

function toDateTimeLocal(value: string | null | undefined) {
  return value ? value.slice(0, 16) : ''
}

function getTestAvailabilityStatus(
  availability: TestGroupAvailabilityResponse,
  t: (key: string) => string,
) {
  if (!availability.visible) {
    return t('availability.hidden')
  }

  const now = Date.now()
  const opensAt = availability.availableFrom ? new Date(availability.availableFrom).getTime() : null
  const closesAt = availability.availableUntil ? new Date(availability.availableUntil).getTime() : null

  if (opensAt && opensAt > now) {
    return t('availability.opensLater')
  }
  if (closesAt && closesAt < now) {
    return t('availability.closed')
  }
  return t('availability.open')
}
