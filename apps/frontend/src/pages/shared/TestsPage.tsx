import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Eye, LockKeyhole, Plus, Trash2 } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import { loadAccessibleGroups, loadAccessibleSubjects, loadManagedSubjects } from '@/pages/education/helpers'
import { dashboardService, educationService, testingService, userDirectoryService } from '@/shared/api/services'
import { getLocalizedRequestErrorMessage } from '@/shared/lib/api-errors'
import { cn } from '@/shared/lib/cn'
import { formatDateTime } from '@/shared/lib/format'
import { toGroupOption, toSubjectOption, toTopicOption } from '@/shared/lib/picker-options'
import { useDebouncedValue } from '@/shared/lib/useDebouncedValue'
import type {
  AnswerResponse,
  QuestionType,
  QuestionResponse,
  TestGroupAvailabilityResponse,
  TestQuestionViewResponse,
  TestStudentViewResponse,
} from '@/shared/types/api'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { DataTable } from '@/shared/ui/DataTable'
import { EntityPicker } from '@/shared/ui/EntityPicker'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { SegmentedControl } from '@/shared/ui/SegmentedControl'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { Textarea } from '@/shared/ui/Textarea'
import { Breadcrumbs } from '@/widgets/common/Breadcrumbs'
import { StatusBadge } from '@/widgets/common/StatusBadge'

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

interface QuestionOptionDraft {
  id: string
  text: string
  correct: boolean
}

interface MatchingPairDraft {
  id: string
  left: string
  right: string
}

type StudentAnswerValue = Record<string, string> | string | string[]

interface StudentAttemptSession {
  startedAt: number
  finishedAt: number | null
  score: number | null
  answers: Record<string, StudentAnswerValue>
}

interface BlankDraft {
  id: string
  acceptedAnswers: string
}

interface QuestionDraft {
  text: string
  description: string
  type: QuestionType
  points: number
  required: boolean
  feedback: string
  options: QuestionOptionDraft[]
  trueFalseAnswer: 'true' | 'false'
  acceptedAnswers: string
  caseSensitive: boolean
  rubric: string
  numericValue: string
  numericTolerance: string
  pairs: MatchingPairDraft[]
  orderingItems: QuestionOptionDraft[]
  blankText: string
  blanks: BlankDraft[]
  allowedFileTypes: string
  maxFileSizeMb: number
}

interface QuestionEditorItem {
  localId: string
  persistedId: string | null
  draft: QuestionDraft
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
    return (
      <ErrorState
        title={t('navigation.shared.tests')}
        description={getLocalizedRequestErrorMessage(dashboardQuery.error, t)}
        onRetry={() => void dashboardQuery.refetch()}
      />
    )
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
  const [query, setQuery] = useState('')
  const [groupSearch, setGroupSearch] = useState('')
  const debouncedQuery = useDebouncedValue(query.trim(), 350)
  const debouncedGroupSearch = useDebouncedValue(groupSearch.trim(), 350)
  const [selectedGroupId, setSelectedGroupId] = useState('')
  const [selectedSubjectId, setSelectedSubjectId] = useState('')
  const [form, setForm] = useState({
    topicId: '',
    title: '',
    maxAttempts: 1,
    maxPoints: 100,
    timeLimitMinutes: 30,
    availableUntil: '',
    showCorrectAnswersAfterSubmit: false,
    shuffleQuestions: false,
    shuffleAnswers: false,
  })
  const isTeacher = primaryRole === 'TEACHER'

  const teacherDashboardQuery = useQuery({
    queryKey: ['dashboard', 'teacher'],
    queryFn: () => dashboardService.getTeacherDashboard(),
    enabled: isTeacher,
  })
  const adminSearchQuery = useQuery({
    queryKey: ['tests', 'search', debouncedQuery],
    queryFn: () => testingService.searchTests({ q: debouncedQuery || ' ', page: 0, size: 30 }),
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
    queryKey: ['education', 'test-group-search', debouncedGroupSearch],
    queryFn: () => educationService.listGroups({
      page: 0,
      size: 20,
      q: debouncedGroupSearch || undefined,
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
        availableFrom: null,
        availableUntil: form.availableUntil ? new Date(form.availableUntil).toISOString() : null,
      }),
    onSuccess: async () => {
      setForm((current) => ({ ...current, title: '' }))
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
    return (
      <ErrorState
        title={t('navigation.shared.tests')}
        description={getLocalizedRequestErrorMessage(
          teacherDashboardQuery.error
            ?? accessibleGroupsQuery.error
            ?? accessibleSubjectsQuery.error
            ?? adminSearchQuery.error
            ?? adminSubjectsQuery.error
            ?? adminGroupSearchQuery.error,
          t,
        )}
        onRetry={() => {
          void teacherDashboardQuery.refetch()
          void accessibleGroupsQuery.refetch()
          void accessibleSubjectsQuery.refetch()
          void adminSearchQuery.refetch()
          void adminSubjectsQuery.refetch()
          void adminGroupSearchQuery.refetch()
        }}
      />
    )
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
            <Input min={1} type="number" value={form.maxAttempts} onChange={(event) => setForm((current) => ({ ...current, maxAttempts: Number(event.target.value) }))} />
          </FormField>
          <FormField label={t('testing.maxPoints')}>
            <Input min={1} type="number" value={form.maxPoints} onChange={(event) => setForm((current) => ({ ...current, maxPoints: Number(event.target.value) }))} />
          </FormField>
          <FormField label={t('testing.timeLimitMinutes')}>
            <Input min={0} type="number" value={form.timeLimitMinutes} onChange={(event) => setForm((current) => ({ ...current, timeLimitMinutes: Number(event.target.value) }))} />
          </FormField>
        </div>
        <Button disabled={!form.topicId || !form.title.trim() || createMutation.isPending} onClick={() => createMutation.mutate()}>
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
  const [availabilityGroupSearch, setAvailabilityGroupSearch] = useState('')
  const debouncedAvailabilityGroupSearch = useDebouncedValue(availabilityGroupSearch.trim(), 350)
  const [previewOpen, setPreviewOpen] = useState(false)
  const [attemptSession, setAttemptSession] = useState<StudentAttemptSession | null>(() => loadStudentAttemptSession(testId))
  const [availabilityForm, setAvailabilityForm] = useState({
    groupId: '',
    visible: false,
    availableUntil: '',
    maxAttempts: 1,
  })
  const [questionDrafts, setQuestionDrafts] = useState<QuestionEditorItem[]>([])
  const [selectedQuestionDraftId, setSelectedQuestionDraftId] = useState('')
  const [resultOverrideForm, setResultOverrideForm] = useState({ resultId: '', score: 0, reason: '' })
  const [questionEditorError, setQuestionEditorError] = useState('')

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
  const previewQuery = useQuery({
    queryKey: ['tests', testId, 'preview'],
    queryFn: () => testingService.getTestPreview(testId),
    enabled: !isStudent && previewOpen,
  })
  const studentViewQuery = useQuery({
    queryKey: ['tests', testId, 'student-view'],
    queryFn: () => testingService.getStudentTestView(testId),
    enabled: isStudent,
  })
  const accessibleGroupsQuery = useQuery({
    queryKey: ['education', 'test-detail-groups', primaryRole, session?.user.id],
    queryFn: () => loadAccessibleGroups(primaryRole, session?.user.id ?? ''),
    enabled: Boolean(!isStudent && isTeacher && session?.user.id),
  })
  const adminGroupSearchQuery = useQuery({
    queryKey: ['education', 'test-detail-group-search', debouncedAvailabilityGroupSearch],
    queryFn: () => educationService.listGroups({
      page: 0,
      size: 20,
      q: debouncedAvailabilityGroupSearch || undefined,
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
    queryKey: ['education', 'test-availability-groups', availabilityGroupIds.join(',')],
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
    onSuccess: async () => {
      const nextSession: StudentAttemptSession = {
        startedAt: Date.now(),
        finishedAt: null,
        score: null,
        answers: {},
      }
      setAttemptSession(nextSession)
      persistStudentAttemptSession(testId, nextSession)
      await queryClient.invalidateQueries({ queryKey: ['tests', testId, 'student-view'] })
      await queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })
  const finishMutation = useMutation({
    mutationFn: () => testingService.finishTest(testId, {
      answers: Object.entries(activeAttempt?.answers ?? {}).map(([questionId, value]) => ({
        questionId,
        value,
      })),
    }),
    onSuccess: async (result) => {
      setAttemptSession((current) => {
        if (!current) {
          return current
        }

        const nextSession: StudentAttemptSession = {
          ...current,
          finishedAt: Date.now(),
          score: result.score,
        }
        persistStudentAttemptSession(testId, nextSession)
        return nextSession
      })
      await queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      await queryClient.invalidateQueries({ queryKey: ['tests', testId, 'student-view'] })
    },
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
        availableFrom: null,
        availableUntil: availabilityForm.availableUntil ? new Date(availabilityForm.availableUntil).toISOString() : null,
        deadline: null,
        maxAttempts: availabilityForm.maxAttempts,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['tests', testId, 'availability'] })
      await queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })
  const saveQuestionsMutation = useMutation({
    mutationFn: async () => {
      const existingQuestions = questionsQuery.data ?? []
      const nextPersistedIds = new Set(questionDrafts.flatMap((item) => (item.persistedId ? [item.persistedId] : [])))

      for (const existingQuestion of existingQuestions) {
        if (!nextPersistedIds.has(existingQuestion.id)) {
          await testingService.deleteQuestion(existingQuestion.id)
        }
      }

      for (const [index, item] of questionDrafts.entries()) {
        const basePayload = buildQuestionPayload(item.draft, index)
        if (item.persistedId) {
          await testingService.updateQuestion(item.persistedId, basePayload)
          continue
        }

        const createdQuestion = await testingService.createQuestion({
          configurationJson: basePayload.configurationJson,
          description: basePayload.description,
          feedback: basePayload.feedback,
          orderIndex: index,
          points: basePayload.points,
          required: basePayload.required,
          testId,
          text: basePayload.text,
          type: basePayload.type,
        })
        await testingService.updateQuestion(createdQuestion.id, basePayload)
      }
    },
    onSuccess: async () => {
      setQuestionEditorError('')
      await queryClient.invalidateQueries({ queryKey: ['tests', testId, 'questions'] })
      await queryClient.invalidateQueries({ queryKey: ['tests', testId, 'preview'] })
    },
    onError: (error) => {
      setQuestionEditorError(getLocalizedRequestErrorMessage(error, t))
    },
  })
  const deleteTestMutation = useMutation({
    mutationFn: () => testingService.deleteTest(testId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['tests'] })
      window.location.assign('/tests')
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

  useEffect(() => {
    if (isStudent || !questionsQuery.data) {
      return
    }

    const nextDrafts = questionsQuery.data.map((question) => toQuestionEditorItem(question))
    setQuestionDrafts(nextDrafts)
    setSelectedQuestionDraftId((current) => {
      if (current && nextDrafts.some((item) => item.localId === current)) {
        return current
      }
      return nextDrafts[0]?.localId ?? ''
    })
  }, [isStudent, questionsQuery.data])

  useEffect(() => {
    if (!isStudent) {
      return
    }

    const persistedSession = loadStudentAttemptSession(testId)
    if (!persistedSession) {
      return
    }

    setAttemptSession(persistedSession)
  }, [isStudent, testId])

  if (
    testQuery.isLoading
    || studentViewQuery.isLoading
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
    || (!isStudent && questionsQuery.isError)
    || (isStudent && studentViewQuery.isError)
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
    return (
      <ErrorState
        title={t('navigation.shared.tests')}
        description={getLocalizedRequestErrorMessage(
          testQuery.error
            ?? questionsQuery.error
            ?? studentViewQuery.error
            ?? availabilityQuery.error
            ?? subjectScopeQuery.error
            ?? testSubjectQuery.error
            ?? connectedGroupsQuery.error
            ?? testResultsQuery.error
            ?? resultStudentsQuery.error,
          t,
        )}
        onRetry={() => {
          void testQuery.refetch()
          void questionsQuery.refetch()
          void studentViewQuery.refetch()
          void availabilityQuery.refetch()
        }}
      />
    )
  }

  const test = testQuery.data
  const selectedQuestionDraft = questionDrafts.find((item) => item.localId === selectedQuestionDraftId) ?? null
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
  const usedPoints = questionDrafts.reduce((total, item) => total + item.draft.points, 0)
  const remainingPoints = test.maxPoints - usedPoints
  const structureLocked = test.status !== 'DRAFT'
  const questionValidationReason = questionDrafts
    .map((item) => validateQuestionDraft(item.draft, t))
    .find(Boolean) ?? ''
  const publishDisabledReason = structureLocked
    ? ''
    : usedPoints > test.maxPoints
      ? t('testing.pointsExceeded')
      : questionDrafts.length === 0
        ? t('testing.validation.atLeastOneQuestion')
        : questionValidationReason
  const availabilitySaveDisabledReason = !availabilityForm.groupId
    ? t('availability.selectGroupReason')
    : !availabilityForm.availableUntil
      ? t('availability.selectAvailableUntilReason')
      : ''
  const activeAttempt = isStudent && attemptSession && !attemptSession.finishedAt ? attemptSession : null
  const attemptTimeLeftMs = activeAttempt && test.timeLimitMinutes
    ? Math.max(0, (activeAttempt.startedAt + test.timeLimitMinutes * 60_000) - Date.now())
    : null

  const handleStudentAnswerChange = (questionId: string, value: StudentAnswerValue) => {
    setAttemptSession((current) => {
      if (!current || current.finishedAt) {
        return current
      }

      const nextSession: StudentAttemptSession = {
        ...current,
        answers: {
          ...current.answers,
          [questionId]: value,
        },
      }
      persistStudentAttemptSession(testId, nextSession)
      return nextSession
    })
  }

  const handleFinishAttempt = () => {
    if (!activeAttempt || finishMutation.isPending || !studentViewQuery.data) {
      return
    }
    finishMutation.mutate()
  }

  useEffect(() => {
    if (!activeAttempt || !test.timeLimitMinutes || !studentViewQuery.data || finishMutation.isPending) {
      return
    }

    const expiresAt = activeAttempt.startedAt + test.timeLimitMinutes * 60_000
    if (Date.now() >= expiresAt) {
      handleFinishAttempt()
    }
  }, [activeAttempt, finishMutation.isPending, studentViewQuery.data, test.timeLimitMinutes])

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
          {structureLocked ? (
            <span className="inline-flex items-center rounded-full border border-warning/30 bg-warning/10 px-3 py-1 text-xs font-semibold text-warning">
              <LockKeyhole className="mr-1.5 h-3.5 w-3.5" />
              {t('testing.structureLocked')}
            </span>
          ) : null}
        </div>
        <div className="grid gap-3 text-sm text-text-secondary md:grid-cols-2 xl:grid-cols-4">
          <p>{t('testing.attempts')}: {test.maxAttempts}</p>
          <p>{t('testing.maxPoints')}: {test.maxPoints}</p>
          <p>{t('testing.availableUntil')}: {formatDateTime(test.availableUntil)}</p>
          <p>{t('testing.timeLimitMinutes')}: {test.timeLimitMinutes ?? '-'}</p>
        </div>

        {isStudent ? (
          <div className="space-y-4">
            {studentViewQuery.data && activeAttempt ? (
              <StudentTestViewPanel
                answers={activeAttempt.answers}
                attemptStartedAt={activeAttempt.startedAt}
                finishDisabled={finishMutation.isPending}
                onAnswerChange={handleStudentAnswerChange}
                onFinish={handleFinishAttempt}
                view={studentViewQuery.data}
              />
            ) : studentViewQuery.data ? (
              <StudentAttemptOverview
                availableUntil={test.availableUntil}
                completionScore={attemptSession?.score ?? null}
                finishPending={finishMutation.isPending}
                isCompleted={Boolean(attemptSession?.finishedAt)}
                questionCount={studentViewQuery.data.questions.length}
                timeLimitMinutes={studentViewQuery.data.timeLimitMinutes}
                title={test.title}
                onStart={() => startMutation.mutate()}
                startDisabled={startMutation.isPending}
              />
            ) : null}
            {activeAttempt && attemptTimeLeftMs === 0 && finishMutation.isPending ? (
              <p className="text-sm font-semibold text-warning">{t('testing.timeExpiredSubmitting')}</p>
            ) : null}
          </div>
        ) : (
          <div className="space-y-4">
            <div className="flex flex-wrap gap-3">
              <Button disabled={Boolean(publishDisabledReason) || structureLocked || publishMutation.isPending} variant="secondary" onClick={() => publishMutation.mutate()}>
                {t('common.actions.publish')}
              </Button>
              <Button variant="secondary" onClick={() => setPreviewOpen((current) => !current)}>
                <Eye className="mr-2 h-4 w-4" />
                {t('testing.previewAsStudent')}
              </Button>
              <Button disabled={test.status !== 'PUBLISHED'} variant="secondary" onClick={() => closeMutation.mutate()}>
                {t('testing.closeTest')}
              </Button>
              <Button onClick={() => archiveMutation.mutate()}>{t('assignments.archive')}</Button>
              <Button disabled={deleteTestMutation.isPending || test.status !== 'DRAFT'} variant="ghost" onClick={() => deleteTestMutation.mutate()}>
                {t('common.actions.delete')}
              </Button>
            </div>
            {publishDisabledReason ? (
              <div className="rounded-[14px] border border-danger/30 bg-danger/5 px-4 py-3">
                <p className="text-sm font-semibold text-danger">{publishDisabledReason}</p>
              </div>
            ) : null}
            {structureLocked ? (
              <div className="rounded-[14px] border border-warning/30 bg-warning/5 px-4 py-3">
                <p className="text-sm font-semibold text-warning">{t('testing.structureLockedMessage')}</p>
              </div>
            ) : null}
            {previewOpen ? (
              <PreviewPanel
                isError={previewQuery.isError}
                isLoading={previewQuery.isLoading}
                onRetry={() => void previewQuery.refetch()}
                view={previewQuery.data}
              />
            ) : null}

            <div className="grid gap-4 xl:grid-cols-[280px_minmax(0,1fr)]">
              <div className="space-y-4 rounded-[16px] border border-border bg-surface-muted p-4">
                <div className="flex items-center justify-between gap-3">
                  <PageHeader title={t('testing.questions')} />
                  <Button
                    disabled={structureLocked}
                    variant="secondary"
                    onClick={() => {
                      const nextItem = createQuestionEditorItem()
                      setQuestionDrafts((current) => [...current, nextItem])
                      setSelectedQuestionDraftId(nextItem.localId)
                    }}
                  >
                    <Plus className="mr-2 h-4 w-4" />
                    {t('testing.addQuestion')}
                  </Button>
                </div>
                <div className="grid gap-3">
                  <MetricRow label={t('testing.maxPoints')} value={test.maxPoints} />
                  <MetricRow label={t('testing.usedPoints')} value={usedPoints} />
                  <MetricRow label={t('testing.remainingPoints')} value={Math.max(remainingPoints, 0)} />
                </div>
                {questionDrafts.length === 0 ? (
                  <p className="text-sm leading-6 text-text-secondary">{t('testing.noQuestions')}</p>
                ) : (
                  <div className="space-y-2">
                    {questionDrafts.map((item, index) => (
                      <div
                        key={item.localId}
                        className={cn(
                          'rounded-[14px] border bg-surface px-3 py-3',
                          item.localId === selectedQuestionDraftId ? 'border-accent shadow-soft' : 'border-border',
                        )}
                      >
                        <button
                          className="block w-full text-left"
                          type="button"
                          onClick={() => setSelectedQuestionDraftId(item.localId)}
                        >
                          <p className="line-clamp-2 text-sm font-semibold text-text-primary">
                            {getQuestionPrompt(item.draft) || t('testing.untitledQuestion', { number: index + 1 })}
                          </p>
                          <p className="mt-1 text-xs text-text-secondary">
                            {t('testing.questionProgress', { current: index + 1, total: questionDrafts.length })}
                            {' · '}
                            {t(`testing.questionType.${item.draft.type}`)}
                            {' · '}
                            {t('testing.pointsValue', { count: item.draft.points })}
                          </p>
                        </button>
                        <div className="mt-3 flex flex-wrap gap-2">
                          <Button
                            disabled={structureLocked || index === 0}
                            variant="ghost"
                            onClick={() => {
                              setQuestionDrafts((current) => moveQuestionEditorItem(current, index, index - 1))
                            }}
                          >
                            {t('common.actions.moveUp')}
                          </Button>
                          <Button
                            disabled={structureLocked || index === questionDrafts.length - 1}
                            variant="ghost"
                            onClick={() => {
                              setQuestionDrafts((current) => moveQuestionEditorItem(current, index, index + 1))
                            }}
                          >
                            {t('common.actions.moveDown')}
                          </Button>
                          <Button
                            disabled={structureLocked}
                            variant="ghost"
                            onClick={() => {
                              const nextDrafts = questionDrafts.filter((question) => question.localId !== item.localId)
                              setQuestionDrafts(nextDrafts)
                              if (selectedQuestionDraftId === item.localId) {
                                setSelectedQuestionDraftId(nextDrafts[Math.max(0, index - 1)]?.localId ?? nextDrafts[0]?.localId ?? '')
                              }
                            }}
                          >
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              <div className="space-y-4 rounded-[16px] border border-border bg-surface-muted p-4">
                <PageHeader title={t('testing.questionEditor')} />
                {selectedQuestionDraft ? (
                  <QuestionTypeEditor
                    disabled={structureLocked}
                    draft={selectedQuestionDraft.draft}
                    onChange={(nextDraft) => {
                      setQuestionDrafts((current) => current.map((item) => item.localId === selectedQuestionDraft.localId
                        ? { ...item, draft: nextDraft }
                        : item))
                    }}
                    totalQuestions={questionDrafts.length}
                  />
                ) : (
                  <EmptyState description={t('testing.addQuestionToStart')} title={t('testing.questionEditor')} />
                )}
                {remainingPoints < 0 ? (
                  <p className="text-sm font-semibold text-danger">{t('testing.pointsExceeded')}</p>
                ) : null}
                {questionValidationReason && selectedQuestionDraft ? (
                  <p className="text-sm font-semibold text-warning">{questionValidationReason}</p>
                ) : null}
                {questionEditorError ? <p className="text-sm font-semibold text-danger">{questionEditorError}</p> : null}
                <Button
                  disabled={structureLocked || Boolean(publishDisabledReason) || saveQuestionsMutation.isPending}
                  onClick={() => saveQuestionsMutation.mutate()}
                >
                  {t('common.actions.save')}
                </Button>
              </div>
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
                    availableUntil: toDateTimeLocal(availability?.availableUntil ?? test.availableUntil),
                    maxAttempts: availability?.maxAttempts ?? test.maxAttempts,
                  })}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="font-semibold text-text-primary">{group.name}</p>
                      <p className="mt-1 text-sm text-text-secondary">{t('testing.availableUntil')}: {availability?.availableUntil ? formatDateTime(availability.availableUntil) : '-'}</p>
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
            <FormField label={t('testing.availableUntil')}>
              <Input type="datetime-local" value={availabilityForm.availableUntil} onChange={(event) => setAvailabilityForm((current) => ({ ...current, availableUntil: event.target.value }))} />
            </FormField>
            <FormField label={t('testing.maxAttempts')}>
              <Input min={1} type="number" value={availabilityForm.maxAttempts} onChange={(event) => setAvailabilityForm((current) => ({ ...current, maxAttempts: Number(event.target.value) }))} />
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

function QuestionTypeEditor({
  disabled,
  draft,
  onChange,
}: {
  disabled: boolean
  draft: QuestionDraft
  onChange: (draft: QuestionDraft) => void
  totalQuestions: number
}) {
  const { t } = useTranslation()
  const update = (patch: Partial<QuestionDraft>) => onChange({ ...draft, ...patch })

  return (
    <div className={cn('space-y-4', disabled && 'opacity-70')}>
      <div className="grid gap-4 xl:grid-cols-2">
        <FormField label={t('testing.questionTypeLabel')}>
          <select
            className="field-control min-h-11 px-3"
            disabled={disabled}
            value={draft.type}
            onChange={(event) => {
              const nextType = event.target.value as QuestionType
              onChange({ ...createQuestionDraft(nextType), text: draft.text, points: draft.points })
            }}
          >
            {questionTypes.map((type) => (
              <option key={type} value={type}>{t(`testing.questionType.${type}`)}</option>
            ))}
          </select>
        </FormField>
        <FormField label={t('testing.points')}>
          <Input disabled={disabled} min={0} type="number" value={draft.points} onChange={(event) => update({ points: Number(event.target.value) })} />
        </FormField>
      </div>
      <FormField label={draft.type === 'TRUE_FALSE' ? t('testing.statement') : draft.type === 'FILL_IN_THE_BLANK' ? t('testing.blankText') : t('testing.questionText')}>
        <Textarea
          disabled={disabled}
          value={draft.type === 'FILL_IN_THE_BLANK' ? draft.blankText : draft.text}
          onChange={(event) => {
            if (draft.type === 'FILL_IN_THE_BLANK') {
              update({ blankText: event.target.value })
              return
            }
            update({ text: event.target.value })
          }}
        />
      </FormField>
      <div className="grid gap-4 xl:grid-cols-2">
        <FormField label={t('common.labels.description')}>
          <Textarea disabled={disabled} value={draft.description} onChange={(event) => update({ description: event.target.value })} />
        </FormField>
        <FormField label={t('testing.feedback')}>
          <Textarea disabled={disabled} value={draft.feedback} onChange={(event) => update({ feedback: event.target.value })} />
        </FormField>
      </div>
      {draft.type === 'SINGLE_CHOICE' || draft.type === 'MULTIPLE_CHOICE' ? (
        <ChoiceOptionsEditor disabled={disabled} draft={draft} onChange={onChange} />
      ) : null}
      {draft.type === 'TRUE_FALSE' ? (
        <FormField label={t('testing.correctBoolean')}>
          <SegmentedControl
            ariaLabel={t('testing.correctBoolean')}
            options={[
              { value: 'true', label: t('testing.booleanTrue') },
              { value: 'false', label: t('testing.booleanFalse') },
            ]}
            value={draft.trueFalseAnswer}
            onChange={(value) => update({ trueFalseAnswer: value as 'true' | 'false' })}
          />
        </FormField>
      ) : null}
      {draft.type === 'SHORT_ANSWER' ? (
        <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_220px]">
          <FormField hint={t('testing.acceptedAnswersHint')} label={t('testing.acceptedAnswers')}>
            <Textarea disabled={disabled} value={draft.acceptedAnswers} onChange={(event) => update({ acceptedAnswers: event.target.value })} />
          </FormField>
          <label className="flex items-center gap-3 rounded-[14px] border border-border bg-surface px-3 py-3 text-sm text-text-secondary">
            <Input disabled={disabled} checked={draft.caseSensitive} type="checkbox" onChange={(event) => update({ caseSensitive: event.target.checked })} />
            <span>{t('testing.caseSensitive')}</span>
          </label>
        </div>
      ) : null}
      {draft.type === 'LONG_TEXT' || draft.type === 'MANUAL_GRADING' ? (
        <FormField label={t('testing.rubric')}>
          <Textarea disabled={disabled} value={draft.rubric} onChange={(event) => update({ rubric: event.target.value })} />
        </FormField>
      ) : null}
      {draft.type === 'NUMERIC' ? (
        <div className="grid gap-4 xl:grid-cols-2">
          <FormField label={t('testing.correctValue')}>
            <Input disabled={disabled} type="number" value={draft.numericValue} onChange={(event) => update({ numericValue: event.target.value })} />
          </FormField>
          <FormField label={t('testing.tolerance')}>
            <Input disabled={disabled} min={0} type="number" value={draft.numericTolerance} onChange={(event) => update({ numericTolerance: event.target.value })} />
          </FormField>
        </div>
      ) : null}
      {draft.type === 'MATCHING' ? <MatchingEditor disabled={disabled} draft={draft} onChange={onChange} /> : null}
      {draft.type === 'ORDERING' ? <OrderingEditor disabled={disabled} draft={draft} onChange={onChange} /> : null}
      {draft.type === 'FILL_IN_THE_BLANK' ? <FillBlankEditor disabled={disabled} draft={draft} onChange={onChange} /> : null}
      {draft.type === 'FILE_ANSWER' ? (
        <div className="grid gap-4 xl:grid-cols-2">
          <FormField label={t('testing.allowedFileTypes')}>
            <Input disabled={disabled} value={draft.allowedFileTypes} onChange={(event) => update({ allowedFileTypes: event.target.value })} />
          </FormField>
          <FormField label={t('testing.maxFileSizeMb')}>
            <Input disabled={disabled} min={1} type="number" value={draft.maxFileSizeMb} onChange={(event) => update({ maxFileSizeMb: Number(event.target.value) })} />
          </FormField>
          <FormField className="xl:col-span-2" label={t('testing.rubric')}>
            <Textarea disabled={disabled} value={draft.rubric} onChange={(event) => update({ rubric: event.target.value })} />
          </FormField>
        </div>
      ) : null}
    </div>
  )
}

function ChoiceOptionsEditor({
  disabled,
  draft,
  onChange,
}: {
  disabled: boolean
  draft: QuestionDraft
  onChange: (draft: QuestionDraft) => void
}) {
  const { t } = useTranslation()
  const multiple = draft.type === 'MULTIPLE_CHOICE'
  const updateOption = (id: string, patch: Partial<QuestionOptionDraft>) => {
    onChange({
      ...draft,
      options: draft.options.map((option) => {
        if (option.id !== id) {
          return multiple || !patch.correct ? option : { ...option, correct: false }
        }
        return { ...option, ...patch }
      }),
    })
  }

  return (
    <div className="space-y-3">
      <p className="text-sm font-semibold text-text-primary">{t('testing.answerOptions')}</p>
      {draft.options.map((option, index) => (
        <div key={option.id} className="grid gap-3 rounded-[14px] border border-border bg-surface p-3 md:grid-cols-[42px_minmax(0,1fr)_auto]">
          <input
            aria-label={t('testing.isCorrect')}
            checked={option.correct}
            disabled={disabled}
            type={multiple ? 'checkbox' : 'radio'}
            onChange={(event) => updateOption(option.id, { correct: event.target.checked })}
          />
          <Input
            disabled={disabled}
            placeholder={t('testing.optionPlaceholder', { number: index + 1 })}
            value={option.text}
            onChange={(event) => updateOption(option.id, { text: event.target.value })}
          />
          <Button
            disabled={disabled || draft.options.length <= 2}
            variant="ghost"
            onClick={() => onChange({ ...draft, options: draft.options.filter((item) => item.id !== option.id) })}
          >
            <Trash2 className="h-4 w-4" />
          </Button>
        </div>
      ))}
      <Button
        disabled={disabled}
        variant="secondary"
        onClick={() => onChange({ ...draft, options: [...draft.options, createOptionDraft()] })}
      >
        <Plus className="mr-2 h-4 w-4" />
        {t('testing.addOption')}
      </Button>
    </div>
  )
}

function MatchingEditor({ disabled, draft, onChange }: { disabled: boolean; draft: QuestionDraft; onChange: (draft: QuestionDraft) => void }) {
  const { t } = useTranslation()
  return (
    <div className="space-y-3">
      <p className="text-sm font-semibold text-text-primary">{t('testing.matchingPairs')}</p>
      {draft.pairs.map((pair, index) => (
        <div key={pair.id} className="grid gap-3 rounded-[14px] border border-border bg-surface p-3 md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto]">
          <Input
            disabled={disabled}
            placeholder={t('testing.leftItem', { number: index + 1 })}
            value={pair.left}
            onChange={(event) => onChange({ ...draft, pairs: draft.pairs.map((item) => item.id === pair.id ? { ...item, left: event.target.value } : item) })}
          />
          <Input
            disabled={disabled}
            placeholder={t('testing.rightItem', { number: index + 1 })}
            value={pair.right}
            onChange={(event) => onChange({ ...draft, pairs: draft.pairs.map((item) => item.id === pair.id ? { ...item, right: event.target.value } : item) })}
          />
          <Button disabled={disabled || draft.pairs.length <= 1} variant="ghost" onClick={() => onChange({ ...draft, pairs: draft.pairs.filter((item) => item.id !== pair.id) })}>
            <Trash2 className="h-4 w-4" />
          </Button>
        </div>
      ))}
      <Button disabled={disabled} variant="secondary" onClick={() => onChange({ ...draft, pairs: [...draft.pairs, createPairDraft()] })}>
        <Plus className="mr-2 h-4 w-4" />
        {t('testing.addPair')}
      </Button>
    </div>
  )
}

function OrderingEditor({ disabled, draft, onChange }: { disabled: boolean; draft: QuestionDraft; onChange: (draft: QuestionDraft) => void }) {
  const { t } = useTranslation()
  return (
    <div className="space-y-3">
      <p className="text-sm font-semibold text-text-primary">{t('testing.orderedItems')}</p>
      {draft.orderingItems.map((item, index) => (
        <div key={item.id} className="grid gap-3 rounded-[14px] border border-border bg-surface p-3 md:grid-cols-[64px_minmax(0,1fr)_auto]">
          <span className="text-sm font-semibold text-text-muted">{index + 1}</span>
          <Input
            disabled={disabled}
            placeholder={t('testing.orderingItemPlaceholder', { number: index + 1 })}
            value={item.text}
            onChange={(event) => onChange({ ...draft, orderingItems: draft.orderingItems.map((option) => option.id === item.id ? { ...option, text: event.target.value } : option) })}
          />
          <Button disabled={disabled || draft.orderingItems.length <= 2} variant="ghost" onClick={() => onChange({ ...draft, orderingItems: draft.orderingItems.filter((option) => option.id !== item.id) })}>
            <Trash2 className="h-4 w-4" />
          </Button>
        </div>
      ))}
      <Button disabled={disabled} variant="secondary" onClick={() => onChange({ ...draft, orderingItems: [...draft.orderingItems, createOptionDraft()] })}>
        <Plus className="mr-2 h-4 w-4" />
        {t('testing.addItem')}
      </Button>
    </div>
  )
}

function FillBlankEditor({ disabled, draft, onChange }: { disabled: boolean; draft: QuestionDraft; onChange: (draft: QuestionDraft) => void }) {
  const { t } = useTranslation()
  return (
    <div className="space-y-3">
      <p className="text-sm font-semibold text-text-primary">{t('testing.blankAnswers')}</p>
      {draft.blanks.map((blank, index) => (
        <div key={blank.id} className="grid gap-3 rounded-[14px] border border-border bg-surface p-3 md:grid-cols-[64px_minmax(0,1fr)_auto]">
          <span className="text-sm font-semibold text-text-muted">{index + 1}</span>
          <Input
            disabled={disabled}
            placeholder={t('testing.blankAcceptedAnswersPlaceholder')}
            value={blank.acceptedAnswers}
            onChange={(event) => onChange({ ...draft, blanks: draft.blanks.map((item) => item.id === blank.id ? { ...item, acceptedAnswers: event.target.value } : item) })}
          />
          <Button disabled={disabled || draft.blanks.length <= 1} variant="ghost" onClick={() => onChange({ ...draft, blanks: draft.blanks.filter((item) => item.id !== blank.id) })}>
            <Trash2 className="h-4 w-4" />
          </Button>
        </div>
      ))}
      <Button disabled={disabled} variant="secondary" onClick={() => onChange({ ...draft, blanks: [...draft.blanks, createBlankDraft()] })}>
        <Plus className="mr-2 h-4 w-4" />
        {t('testing.addBlank')}
      </Button>
    </div>
  )
}

function PreviewPanel({
  isError,
  isLoading,
  onRetry,
  view,
}: {
  isError: boolean
  isLoading: boolean
  onRetry: () => void
  view?: TestStudentViewResponse
}) {
  const { t } = useTranslation()
  if (isLoading) {
    return <LoadingState label={t('testing.loadingPreview')} />
  }
  if (isError || !view) {
    return <ErrorState title={t('testing.previewAsStudent')} description={t('common.states.error')} onRetry={onRetry} />
  }

  return <StudentTestViewPanel view={view} />
}

function StudentAttemptOverview({
  availableUntil,
  completionScore,
  finishPending,
  isCompleted,
  questionCount,
  startDisabled,
  timeLimitMinutes,
  title,
  onStart,
}: {
  availableUntil: string | null
  completionScore: number | null
  finishPending: boolean
  isCompleted: boolean
  questionCount: number
  startDisabled: boolean
  timeLimitMinutes: number | null
  title: string
  onStart: () => void
}) {
  const { t } = useTranslation()

  return (
    <div className="space-y-4 rounded-[16px] border border-border bg-surface-muted p-4">
      <div className="space-y-2">
        <h2 className="text-lg font-semibold text-text-primary">{title}</h2>
        <p className="text-sm leading-6 text-text-secondary">
          {isCompleted ? t('testing.attemptCompletedDescription') : t('testing.attemptStartDescription')}
        </p>
      </div>
      <div className="grid gap-3 text-sm text-text-secondary md:grid-cols-3">
        <p>{t('testing.questions')}: {questionCount}</p>
        <p>{t('testing.timeLimitMinutes')}: {timeLimitMinutes ?? t('testing.noTimeLimit')}</p>
        <p>{t('testing.availableUntil')}: {formatDateTime(availableUntil)}</p>
      </div>
      {isCompleted ? (
        <div className="rounded-[14px] border border-success/30 bg-success/5 px-4 py-3">
          <p className="text-sm font-semibold text-success">
            {completionScore !== null ? t('testing.attemptCompletedScore', { score: completionScore }) : t('testing.attemptCompleted')}
          </p>
          {finishPending ? <p className="mt-1 text-sm text-text-secondary">{t('common.states.loading')}</p> : null}
        </div>
      ) : null}
      <div className="flex flex-wrap gap-3">
        <Button disabled={startDisabled} variant="secondary" onClick={onStart}>
          {t('common.actions.start')}
        </Button>
      </div>
    </div>
  )
}

function StudentTestViewPanel({
  answers,
  attemptStartedAt,
  finishDisabled,
  onAnswerChange,
  onFinish,
  view,
}: {
  answers?: Record<string, StudentAnswerValue>
  attemptStartedAt?: number | null
  finishDisabled?: boolean
  onAnswerChange?: (questionId: string, value: StudentAnswerValue) => void
  onFinish?: () => void
  view: TestStudentViewResponse
}) {
  const { t } = useTranslation()
  const now = useNow(view.timeLimitMinutes && attemptStartedAt ? 1000 : null)
  const timeLeftLabel = view.timeLimitMinutes && attemptStartedAt
    ? formatTimeLeft(Math.max(0, (attemptStartedAt + view.timeLimitMinutes * 60_000) - now), t)
    : view.timeLimitMinutes
      ? t('testing.timeLimitValue', { count: view.timeLimitMinutes })
      : t('testing.noTimeLimit')
  const totalQuestionPoints = view.questions.reduce((sum, question) => sum + question.points, 0)

  return (
    <div className="space-y-4 rounded-[16px] border border-border bg-surface-muted p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          {view.preview ? (
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-accent">{t('testing.previewModeLabel')}</p>
          ) : null}
          <h2 className="text-lg font-semibold text-text-primary">{view.test.title}</h2>
          <p className="text-sm text-text-secondary">
            {t('testing.totalPointsValue', { count: view.totalPoints })}
            {' · '}
            {t('testing.questionPointsValue', { count: totalQuestionPoints })}
            {' · '}
            {timeLeftLabel}
          </p>
        </div>
        <StatusBadge value={view.test.status} />
      </div>
      {view.questions.length === 0 ? (
        <EmptyState description={t('testing.noQuestions')} title={t('testing.questions')} />
      ) : (
        <div className="space-y-3">
          {view.questions.map((question, index) => (
            <StudentQuestionCard
              answerValue={answers?.[question.id]}
              key={question.id}
              index={index}
              onAnswerChange={onAnswerChange}
              question={question}
              total={view.questions.length}
            />
          ))}
        </div>
      )}
      {!view.preview && onFinish ? (
        <div className="flex flex-wrap gap-3 pt-2">
          <Button disabled={finishDisabled} onClick={onFinish}>
            {t('testing.finishAttempt')}
          </Button>
        </div>
      ) : null}
    </div>
  )
}

function StudentQuestionCard({
  answerValue,
  index,
  onAnswerChange,
  question,
  total,
}: {
  answerValue?: StudentAnswerValue
  index: number
  onAnswerChange?: (questionId: string, value: StudentAnswerValue) => void
  question: TestQuestionViewResponse
  total: number
}) {
  const { t } = useTranslation()
  const config = parseQuestionConfiguration(question.configurationJson)

  return (
    <div className="rounded-[16px] border border-border bg-surface p-4">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3 border-b border-border pb-3">
        <p className="text-sm font-semibold text-text-primary">
          {t('testing.questionProgress', { current: index + 1, total })}
          {' · '}
          {t('testing.pointsValue', { count: question.points })}
        </p>
        <span className="rounded-full border border-border px-2.5 py-1 text-xs font-semibold text-text-secondary">
          {t(`testing.questionType.${question.type}`)}
        </span>
      </div>
      <div className="space-y-3">
        <p className="text-base font-semibold text-text-primary">{question.text}</p>
        {question.description ? <p className="text-sm leading-6 text-text-secondary">{question.description}</p> : null}
        {renderStudentAnswerSurface(question, answerValue, config, onAnswerChange, t)}
      </div>
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

function createQuestionDraft(type: QuestionType = 'SINGLE_CHOICE'): QuestionDraft {
  return {
    text: '',
    description: '',
    type,
    points: 1,
    required: true,
    feedback: '',
    options: [createOptionDraft(), createOptionDraft()],
    trueFalseAnswer: 'true',
    acceptedAnswers: '',
    caseSensitive: false,
    rubric: '',
    numericValue: '',
    numericTolerance: '0',
    pairs: [createPairDraft(), createPairDraft()],
    orderingItems: [createOptionDraft(), createOptionDraft()],
    blankText: '',
    blanks: [createBlankDraft()],
    allowedFileTypes: 'application/pdf,image/png,image/jpeg',
    maxFileSizeMb: 10,
  }
}

function createQuestionEditorItem(draft?: QuestionDraft, persistedId: string | null = null): QuestionEditorItem {
  return {
    localId: createLocalId(),
    persistedId,
    draft: draft ?? createQuestionDraft(),
  }
}

function createOptionDraft(): QuestionOptionDraft {
  return { id: createLocalId(), text: '', correct: false }
}

function createPairDraft(): MatchingPairDraft {
  return { id: createLocalId(), left: '', right: '' }
}

function createBlankDraft(): BlankDraft {
  return { id: createLocalId(), acceptedAnswers: '' }
}

function createLocalId() {
  return Math.random().toString(36).slice(2, 10)
}

function moveQuestionEditorItem(items: QuestionEditorItem[], fromIndex: number, toIndex: number) {
  const nextItems = [...items]
  const [movedItem] = nextItems.splice(fromIndex, 1)
  nextItems.splice(toIndex, 0, movedItem)
  return nextItems
}

function getQuestionPrompt(draft: QuestionDraft) {
  return draft.type === 'FILL_IN_THE_BLANK' ? draft.blankText.trim() : draft.text.trim()
}

function buildQuestionPayload(draft: QuestionDraft, orderIndex: number) {
  return {
    text: getQuestionPrompt(draft),
    type: draft.type,
    description: draft.description.trim() || null,
    points: draft.points,
    orderIndex,
    required: draft.required,
    feedback: draft.feedback.trim() || null,
    configurationJson: buildQuestionConfiguration(draft),
    answers: buildQuestionAnswers(draft).map((answer) => ({
      text: answer.text,
      isCorrect: answer.isCorrect,
    })),
  }
}

function buildQuestionConfiguration(draft: QuestionDraft) {
  const base = { type: draft.type }
  switch (draft.type) {
    case 'SHORT_ANSWER':
      return JSON.stringify({ ...base, acceptedAnswers: splitLines(draft.acceptedAnswers), caseSensitive: draft.caseSensitive })
    case 'LONG_TEXT':
    case 'MANUAL_GRADING':
      return JSON.stringify({ ...base, rubric: draft.rubric.trim() })
    case 'NUMERIC':
      return JSON.stringify({ ...base, correctValue: Number(draft.numericValue), tolerance: Number(draft.numericTolerance || 0) })
    case 'MATCHING':
      return JSON.stringify({ ...base, pairs: draft.pairs.map(({ left, right }) => ({ left: left.trim(), right: right.trim() })) })
    case 'ORDERING':
      return JSON.stringify({ ...base, items: draft.orderingItems.map((item) => item.text.trim()) })
    case 'FILL_IN_THE_BLANK':
      return JSON.stringify({ ...base, text: draft.blankText.trim(), blanks: draft.blanks.map((blank) => splitComma(blank.acceptedAnswers)) })
    case 'FILE_ANSWER':
      return JSON.stringify({ ...base, allowedFileTypes: splitComma(draft.allowedFileTypes), maxFileSizeMb: draft.maxFileSizeMb, rubric: draft.rubric.trim() })
    default:
      return JSON.stringify(base)
  }
}

function buildQuestionAnswers(draft: QuestionDraft) {
  switch (draft.type) {
    case 'SINGLE_CHOICE':
    case 'MULTIPLE_CHOICE':
      return draft.options
        .filter((option) => option.text.trim())
        .map((option) => ({ text: option.text.trim(), isCorrect: option.correct }))
    case 'TRUE_FALSE':
      return [
        { text: 'TRUE', isCorrect: draft.trueFalseAnswer === 'true' },
        { text: 'FALSE', isCorrect: draft.trueFalseAnswer === 'false' },
      ]
    case 'SHORT_ANSWER':
      return splitLines(draft.acceptedAnswers).map((answer) => ({ text: answer, isCorrect: true }))
    case 'NUMERIC':
      return [{ text: draft.numericValue.trim(), isCorrect: true }]
    case 'MATCHING':
      return draft.pairs
        .filter((pair) => pair.left.trim() && pair.right.trim())
        .map((pair) => ({ text: `${pair.left.trim()} -> ${pair.right.trim()}`, isCorrect: true }))
    case 'ORDERING':
      return draft.orderingItems
        .filter((item) => item.text.trim())
        .map((item) => ({ text: item.text.trim(), isCorrect: true }))
    case 'FILL_IN_THE_BLANK':
      return draft.blanks
        .flatMap((blank) => splitComma(blank.acceptedAnswers))
        .map((answer) => ({ text: answer, isCorrect: true }))
    default:
      return []
  }
}

function toQuestionEditorItem(question: QuestionResponse): QuestionEditorItem {
  const draft = createQuestionDraft(question.type)
  draft.text = question.type === 'FILL_IN_THE_BLANK'
    ? extractFillBlankText(question)
    : question.text
  draft.blankText = question.type === 'FILL_IN_THE_BLANK'
    ? extractFillBlankText(question)
    : draft.blankText
  draft.description = question.description ?? ''
  draft.points = question.points
  draft.required = question.required
  draft.feedback = question.feedback ?? ''

  const config = parseQuestionConfiguration(question.configurationJson)
  if (question.type === 'SINGLE_CHOICE' || question.type === 'MULTIPLE_CHOICE') {
    draft.options = question.answers.length > 0
      ? question.answers.map(toOptionDraft)
      : [createOptionDraft(), createOptionDraft()]
  }
  if (question.type === 'TRUE_FALSE') {
    draft.trueFalseAnswer = question.answers.find((answer) => answer.isCorrect)?.text === 'FALSE' ? 'false' : 'true'
  }
  if (question.type === 'SHORT_ANSWER') {
    draft.acceptedAnswers = question.answers.map((answer) => answer.text).join('\n')
    draft.caseSensitive = Boolean(config.caseSensitive)
  }
  if (question.type === 'LONG_TEXT' || question.type === 'MANUAL_GRADING') {
    draft.rubric = typeof config.rubric === 'string' ? config.rubric : ''
  }
  if (question.type === 'NUMERIC') {
    draft.numericValue = question.answers[0]?.text ?? stringifyConfigValue(config.correctValue)
    draft.numericTolerance = stringifyConfigValue(config.tolerance) || '0'
  }
  if (question.type === 'MATCHING') {
    draft.pairs = Array.isArray(config.pairs) && config.pairs.length > 0
      ? config.pairs.map((pair) => {
        const value = pair as { left?: string; right?: string }
        return { id: createLocalId(), left: value.left ?? '', right: value.right ?? '' }
      })
      : [createPairDraft(), createPairDraft()]
  }
  if (question.type === 'ORDERING') {
    draft.orderingItems = Array.isArray(config.items) && config.items.length > 0
      ? config.items.map((item) => ({ id: createLocalId(), text: String(item), correct: true }))
      : question.answers.length > 0
        ? question.answers.map((answer) => ({ id: createLocalId(), text: answer.text, correct: true }))
        : [createOptionDraft(), createOptionDraft()]
  }
  if (question.type === 'FILL_IN_THE_BLANK') {
    draft.blanks = Array.isArray(config.blanks) && config.blanks.length > 0
      ? config.blanks.map((blank) => ({
        id: createLocalId(),
        acceptedAnswers: Array.isArray(blank) ? blank.join(', ') : '',
      }))
      : [createBlankDraft()]
  }
  if (question.type === 'FILE_ANSWER') {
    draft.allowedFileTypes = Array.isArray(config.allowedFileTypes) ? config.allowedFileTypes.join(',') : draft.allowedFileTypes
    draft.maxFileSizeMb = Number(config.maxFileSizeMb ?? draft.maxFileSizeMb)
    draft.rubric = typeof config.rubric === 'string' ? config.rubric : draft.rubric
  }

  return createQuestionEditorItem(draft, question.id)
}

function toOptionDraft(answer: AnswerResponse): QuestionOptionDraft {
  return {
    id: createLocalId(),
    text: answer.text,
    correct: Boolean(answer.isCorrect),
  }
}

function stringifyConfigValue(value: unknown) {
  return typeof value === 'number' || typeof value === 'string' ? String(value) : ''
}

function extractFillBlankText(question: QuestionResponse) {
  const config = parseQuestionConfiguration(question.configurationJson)
  return typeof config.text === 'string' ? config.text : question.text
}

function validateQuestionDraft(draft: QuestionDraft, t: (key: string) => string) {
  if (!getQuestionPrompt(draft)) {
    return t('testing.validation.questionTextRequired')
  }
  if (draft.points < 0) {
    return t('testing.validation.pointsInvalid')
  }
  if (draft.type === 'SINGLE_CHOICE') {
    const filledOptions = draft.options.filter((option) => option.text.trim())
    if (filledOptions.length < 2) {
      return t('testing.validation.twoOptionsRequired')
    }
    if (filledOptions.filter((option) => option.correct).length !== 1) {
      return t('testing.validation.singleChoiceCorrectRequired')
    }
  }
  if (draft.type === 'MULTIPLE_CHOICE') {
    const filledOptions = draft.options.filter((option) => option.text.trim())
    if (filledOptions.length < 2) {
      return t('testing.validation.twoOptionsRequired')
    }
    if (!filledOptions.some((option) => option.correct)) {
      return t('testing.validation.multipleChoiceCorrectRequired')
    }
  }
  if (draft.type === 'SHORT_ANSWER' && splitLines(draft.acceptedAnswers).length === 0) {
    return t('testing.validation.acceptedAnswerRequired')
  }
  if (draft.type === 'NUMERIC' && !draft.numericValue.trim()) {
    return t('testing.validation.numericValueRequired')
  }
  if (draft.type === 'MATCHING' && draft.pairs.filter((pair) => pair.left.trim() && pair.right.trim()).length < 2) {
    return t('testing.validation.matchingPairsRequired')
  }
  if (draft.type === 'ORDERING' && draft.orderingItems.filter((item) => item.text.trim()).length < 2) {
    return t('testing.validation.orderingItemsRequired')
  }
  if (draft.type === 'FILL_IN_THE_BLANK' && draft.blanks.some((blank) => splitComma(blank.acceptedAnswers).length === 0)) {
    return t('testing.validation.blankAnswersRequired')
  }

  return ''
}

function splitLines(value: string) {
  return value.split('\n').map((item) => item.trim()).filter(Boolean)
}

function splitComma(value: string) {
  return value.split(',').map((item) => item.trim()).filter(Boolean)
}

function parseQuestionConfiguration(value: string | null) {
  if (!value) {
    return {} as Record<string, unknown>
  }
  try {
    const parsed = JSON.parse(value)
    return typeof parsed === 'object' && parsed !== null ? parsed as Record<string, unknown> : {}
  } catch {
    return {}
  }
}

function renderStudentAnswerSurface(
  question: TestQuestionViewResponse,
  answerValue: StudentAnswerValue | undefined,
  config: Record<string, unknown>,
  onAnswerChange: ((questionId: string, value: StudentAnswerValue) => void) | undefined,
  t: (key: string, values?: Record<string, unknown>) => string,
) {
  if (question.type === 'SINGLE_CHOICE' || question.type === 'TRUE_FALSE') {
    return (
      <div className="space-y-2">
        {question.answers.map((answer) => (
          <label key={answer.id} className="flex items-center gap-3 rounded-[14px] border border-border bg-surface-muted px-3 py-2 text-sm text-text-secondary">
            <input
              checked={answerValue === answer.id}
              name={question.id}
              type="radio"
              onChange={() => onAnswerChange?.(question.id, answer.id)}
            />
            <span>{localizeAnswerText(answer.text, t)}</span>
          </label>
        ))}
      </div>
    )
  }
  if (question.type === 'MULTIPLE_CHOICE') {
    const selectedIds = Array.isArray(answerValue) ? answerValue : []
    return (
      <div className="space-y-2">
        {question.answers.map((answer) => {
          const checked = selectedIds.includes(answer.id)
          return (
            <label key={answer.id} className="flex items-center gap-3 rounded-[14px] border border-border bg-surface-muted px-3 py-2 text-sm text-text-secondary">
              <input
                checked={checked}
                name={question.id}
                type="checkbox"
                onChange={(event) => onAnswerChange?.(
                  question.id,
                  event.target.checked
                    ? [...selectedIds, answer.id]
                    : selectedIds.filter((value) => value !== answer.id),
                )}
              />
              <span>{localizeAnswerText(answer.text, t)}</span>
            </label>
          )
        })}
      </div>
    )
  }
  if (question.type === 'SHORT_ANSWER' || question.type === 'NUMERIC') {
    return <Input aria-label={t('testing.answerText')} value={typeof answerValue === 'string' ? answerValue : ''} onChange={(event) => onAnswerChange?.(question.id, event.target.value)} />
  }
  if (question.type === 'LONG_TEXT' || question.type === 'MANUAL_GRADING') {
    return <Textarea aria-label={t('testing.answerText')} value={typeof answerValue === 'string' ? answerValue : ''} onChange={(event) => onAnswerChange?.(question.id, event.target.value)} />
  }
  if (question.type === 'FILE_ANSWER') {
    const allowedFileTypes = Array.isArray(config.allowedFileTypes) ? config.allowedFileTypes.join(', ') : ''
    return (
      <div className="space-y-2">
        <Input aria-label={t('testing.fileAnswer')} type="file" />
        <p className="text-xs text-text-muted">
          {allowedFileTypes ? t('testing.allowedFileTypesValue', { value: allowedFileTypes }) : t('testing.manualGradingRequired')}
        </p>
      </div>
    )
  }
  if (question.type === 'MATCHING') {
    const pairs = Array.isArray(config.pairs) ? config.pairs : []
    const selectedMatches = typeof answerValue === 'object' && answerValue !== null && !Array.isArray(answerValue)
      ? answerValue as Record<string, string>
      : {}
    const rightOptions = pairs
      .map((pair) => {
        const value = pair as { right?: string }
        return value.right?.trim() ?? ''
      })
      .filter(Boolean)

    return (
      <div className="space-y-3">
        {pairs.map((pair, index) => {
          const value = pair as { left?: string; right?: string }
          const pairKey = `${index}`
          return (
            <div key={`${question.id}-${index}`} className="grid gap-3 rounded-[14px] border border-border bg-surface-muted p-3 md:grid-cols-[minmax(0,1fr)_minmax(220px,280px)]">
              <div className="text-sm font-medium text-text-primary">
                {value.left ?? t('testing.leftItem', { number: index + 1 })}
              </div>
              <select
                className="field-control min-h-11 px-3"
                value={selectedMatches[pairKey] ?? ''}
                onChange={(event) => onAnswerChange?.(question.id, {
                  ...selectedMatches,
                  [pairKey]: event.target.value,
                })}
              >
                <option value="">{t('testing.selectMatch')}</option>
                {rightOptions.map((option) => (
                  <option key={`${pairKey}-${option}`} value={option}>
                    {option}
                  </option>
                ))}
              </select>
            </div>
          )
        })}
      </div>
    )
  }
  if (question.type === 'ORDERING') {
    const items = Array.isArray(config.items) ? config.items : []
    return (
      <div className="space-y-2">
        {items.map((item, index) => (
          <div key={`${question.id}-${index}`} className="rounded-[14px] border border-border bg-surface-muted px-3 py-2 text-sm text-text-secondary">
            {index + 1}. {String(item)}
          </div>
        ))}
      </div>
    )
  }
  if (question.type === 'FILL_IN_THE_BLANK') {
    return <Textarea aria-label={t('testing.answerText')} value={typeof answerValue === 'string' ? answerValue : ''} onChange={(event) => onAnswerChange?.(question.id, event.target.value)} />
  }

  return null
}

function localizeAnswerText(value: string, t: (key: string) => string) {
  if (value === 'TRUE') {
    return t('testing.booleanTrue')
  }
  if (value === 'FALSE') {
    return t('testing.booleanFalse')
  }
  return value
}

function useNow(intervalMs: number | null) {
  const [now, setNow] = useState(Date.now())

  useEffect(() => {
    if (!intervalMs) {
      return undefined
    }

    const intervalId = window.setInterval(() => setNow(Date.now()), intervalMs)
    return () => window.clearInterval(intervalId)
  }, [intervalMs])

  return now
}

function formatTimeLeft(ms: number, t: (key: string, values?: Record<string, unknown>) => string) {
  const totalSeconds = Math.ceil(ms / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return t('testing.timeLeftValue', {
    minutes,
    seconds: seconds.toString().padStart(2, '0'),
  })
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
  const closesAt = availability.availableUntil ? new Date(availability.availableUntil).getTime() : null

  if (closesAt && closesAt < now) {
    return t('availability.closed')
  }
  return t('availability.open')
}

function loadStudentAttemptSession(testId: string) {
  if (typeof window === 'undefined') {
    return null
  }

  const rawValue = window.sessionStorage.getItem(getStudentAttemptStorageKey(testId))
  if (!rawValue) {
    return null
  }

  try {
    return JSON.parse(rawValue) as StudentAttemptSession
  } catch {
    return null
  }
}

function persistStudentAttemptSession(testId: string, session: StudentAttemptSession) {
  if (typeof window === 'undefined') {
    return
  }

  window.sessionStorage.setItem(getStudentAttemptStorageKey(testId), JSON.stringify(session))
}

function getStudentAttemptStorageKey(testId: string) {
  return `studium:test-attempt:${testId}`
}
