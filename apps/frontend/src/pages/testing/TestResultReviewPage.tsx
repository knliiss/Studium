import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import { loadAccessibleSubjects, loadManagedSubjects } from '@/pages/education/helpers'
import { educationService, testingService, userDirectoryService } from '@/shared/api/services'
import { getLocalizedRequestErrorMessage } from '@/shared/lib/api-errors'
import { cn } from '@/shared/lib/cn'
import { formatDateTime } from '@/shared/lib/format'
import type { QuestionResponse, QuestionType, TestResultQuestionResponse } from '@/shared/types/api'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { SectionTabs } from '@/shared/ui/SectionTabs'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { Textarea } from '@/shared/ui/Textarea'
import { Breadcrumbs } from '@/widgets/common/Breadcrumbs'
import { MetricCard } from '@/widgets/common/MetricCard'

type ReviewTab = 'answers' | 'attemptInfo' | 'history'

type PendingChange = {
  score: number
  comment: string
}

type ReviewStatus = 'CORRECT' | 'PARTIAL' | 'INCORRECT' | 'NOT_REVIEWED'

const MANUAL_REVIEW_TYPES: QuestionType[] = ['LONG_TEXT', 'MANUAL_GRADING', 'FILE_ANSWER']

export function TestResultReviewPage({ resultId, testId }: { resultId: string; testId: string }) {
  const { t } = useTranslation()
  const { primaryRole, session } = useAuth()
  const queryClient = useQueryClient()
  const managementEnabled = primaryRole !== 'STUDENT'
  const [activeTab, setActiveTab] = useState<ReviewTab>('answers')
  const [selectedQuestionId, setSelectedQuestionId] = useState('')
  const [pendingChanges, setPendingChanges] = useState<Record<string, PendingChange>>({})
  const [approveConfirmOpen, setApproveConfirmOpen] = useState(false)

  const reviewQuery = useQuery({
    queryKey: ['tests', testId, 'results', resultId, 'review'],
    queryFn: () => testingService.getTestResultReview(resultId),
    enabled: managementEnabled,
  })
  const testQuery = useQuery({
    queryKey: ['tests', testId],
    queryFn: () => testingService.getTest(testId),
    enabled: managementEnabled,
  })
  const resultsQuery = useQuery({
    queryKey: ['tests', testId, 'results', 'for-attempt-number'],
    queryFn: () => testingService.getTestResultsByTest(testId, { page: 0, size: 100 }),
    enabled: managementEnabled,
  })
  const questionsQuery = useQuery({
    queryKey: ['tests', testId, 'questions', 'for-review'],
    queryFn: () => testingService.getQuestionsByTest(testId),
    enabled: managementEnabled,
  })
  const subjectScopeQuery = useQuery({
    queryKey: ['education', 'test-review-subject-scope', primaryRole, session?.user.id],
    queryFn: () => {
      if (primaryRole === 'TEACHER') {
        return loadAccessibleSubjects(primaryRole, session?.user.id ?? '')
      }
      return loadManagedSubjects()
    },
    enabled: Boolean(managementEnabled && session?.user.id),
  })
  const subjectQuery = useQuery({
    queryKey: ['education', 'test-review-subject', testId, testQuery.data?.topicId, subjectScopeQuery.data?.length],
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
  const userIdsQuery = useQuery({
    queryKey: ['tests', testId, 'result-review-users', reviewQuery.data?.userId, reviewQuery.data?.reviewedByUserId],
    queryFn: () => {
      const ids = [
        reviewQuery.data?.userId,
        reviewQuery.data?.reviewedByUserId,
        ...(reviewQuery.data?.questions.map((question) => question.reviewedByUserId).filter(Boolean) as string[]),
      ].filter(Boolean) as string[]
      return userDirectoryService.lookup(Array.from(new Set(ids)))
    },
    enabled: Boolean(managementEnabled && reviewQuery.data),
  })
  const subjectGroupsQuery = useQuery({
    queryKey: ['tests', testId, 'result-review-subject-groups', subjectQuery.data?.groupIds.join(',')],
    queryFn: async () => Promise.all((subjectQuery.data?.groupIds ?? []).map((groupId) => educationService.getGroup(groupId))),
    enabled: Boolean(managementEnabled && subjectQuery.data?.groupIds.length),
  })

  const approveMutation = useMutation({
    mutationFn: async ({ applyPending }: { applyPending: boolean }) => {
      const review = reviewQuery.data
      if (!review) {
        return null
      }

      if (applyPending) {
        const entries = Object.entries(pendingChanges)
        for (const [resultQuestionId, change] of entries) {
          await testingService.updateTestResultQuestionScore(resultId, resultQuestionId, {
            score: change.score,
            comment: change.comment.trim() || undefined,
          })
        }
      }

      return testingService.approveTestResult(resultId)
    },
    onSuccess: async () => {
      setPendingChanges({})
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['tests', testId, 'results'] }),
        queryClient.invalidateQueries({ queryKey: ['tests', testId, 'results', resultId, 'review'] }),
        queryClient.invalidateQueries({ queryKey: ['tests', testId, 'results', 'for-attempt-number'] }),
      ])
    },
  })

  const orderedQuestionsForSelection = useMemo(
    () => [...(reviewQuery.data?.questions ?? [])].sort((left, right) => left.questionOrderIndex - right.questionOrderIndex),
    [reviewQuery.data?.questions],
  )

  if (!managementEnabled) {
    return <ErrorState title={t('testing.resultReviewTitle')} description={t('errors:ACCESS_DENIED')} />
  }

  if (
    reviewQuery.isLoading
    || testQuery.isLoading
    || resultsQuery.isLoading
    || questionsQuery.isLoading
    || subjectScopeQuery.isLoading
    || subjectQuery.isLoading
    || userIdsQuery.isLoading
    || subjectGroupsQuery.isLoading
  ) {
    return <LoadingState />
  }

  if (
    reviewQuery.isError
    || testQuery.isError
    || resultsQuery.isError
    || questionsQuery.isError
    || subjectScopeQuery.isError
    || subjectQuery.isError
    || userIdsQuery.isError
    || subjectGroupsQuery.isError
    || !reviewQuery.data
    || !testQuery.data
  ) {
    return (
      <ErrorState
        title={t('testing.resultReviewTitle')}
        description={getLocalizedRequestErrorMessage(
          reviewQuery.error
            ?? testQuery.error
            ?? resultsQuery.error
            ?? questionsQuery.error
            ?? subjectScopeQuery.error
            ?? subjectQuery.error
            ?? userIdsQuery.error
            ?? subjectGroupsQuery.error,
          t,
        )}
        onRetry={() => {
          void reviewQuery.refetch()
          void testQuery.refetch()
          void resultsQuery.refetch()
          void questionsQuery.refetch()
        }}
      />
    )
  }

  const review = reviewQuery.data
  const test = testQuery.data
  const usersById = new Map((userIdsQuery.data ?? []).map((user) => [user.id, user]))
  const questionDefinitions = new Map((questionsQuery.data ?? []).map((question) => [question.id, question]))
  const studentName = usersById.get(review.userId)?.username ?? t('education.unknownStudent')
  const reviewedByName = review.reviewedByUserId ? usersById.get(review.reviewedByUserId)?.username ?? '-' : '-'
  const reviewQuestions = orderedQuestionsForSelection
  const attemptNumber = getAttemptNumber(resultsQuery.data?.items ?? [], review.userId, review.resultId)
  const groupNames = subjectGroupsQuery.data?.map((group) => group.name).filter(Boolean) ?? []
  const effectiveSelectedQuestionId = reviewQuestions.some((question) => question.id === selectedQuestionId)
    ? selectedQuestionId
    : reviewQuestions[0]?.id ?? ''
  const selectedQuestion = reviewQuestions.find((question) => question.id === effectiveSelectedQuestionId) ?? null
  const pendingCount = Object.keys(pendingChanges).length
  const scoreBeforeChanges = review.score
  const scoreAfterChanges = computeScoreAfterPending(reviewQuestions, pendingChanges, review.maxPoints)
  const totalSavedScore = reviewQuestions.reduce((sum, question) => sum + question.score, 0)
  const safeMaxPoints = Math.max(review.maxPoints, totalSavedScore, scoreAfterChanges)

  const selectedSavedScore = selectedQuestion?.score ?? 0
  const selectedSavedComment = selectedQuestion?.reviewComment ?? ''
  const selectedPending = selectedQuestion ? pendingChanges[selectedQuestion.id] : undefined
  const draftScore = selectedPending?.score ?? selectedSavedScore
  const draftComment = selectedPending?.comment ?? selectedSavedComment
  const draftScoreValidation = selectedQuestion
    ? validateDraftScore(draftScore, selectedQuestion.maxPoints, t)
    : t('testing.selectQuestionToReview')

  const applyDraftChange = (next: PendingChange) => {
    if (!selectedQuestion) {
      return
    }
    const normalizedComment = next.comment
    const savedComment = selectedSavedComment
    if (next.score === selectedSavedScore && normalizedComment === savedComment) {
      setPendingChanges((current) => {
        const next = { ...current }
        delete next[selectedQuestion.id]
        return next
      })
      return
    }
    setPendingChanges((current) => ({
      ...current,
      [selectedQuestion.id]: next,
    }))
  }

  const handleApproveRequest = () => {
    if (approveMutation.isPending) {
      return
    }
    if (pendingCount > 0) {
      setApproveConfirmOpen(true)
      return
    }
    approveMutation.mutate({ applyPending: false })
  }

  const handleApproveConfirmed = () => {
    setApproveConfirmOpen(false)
    approveMutation.mutate({ applyPending: true })
  }

  return (
    <div className="space-y-6">
      <Breadcrumbs
        items={subjectQuery.data
          ? [
              { label: t('navigation.shared.subjects'), to: '/subjects' },
              { label: subjectQuery.data.name, to: `/subjects/${subjectQuery.data.id}` },
              { label: t('navigation.shared.tests'), to: `/subjects/${subjectQuery.data.id}?tab=tests` },
              { label: test.title, to: `/tests/${testId}` },
              { label: t('testing.testResultsTitle'), to: `/tests/${testId}?tab=results` },
              { label: t('testing.resultReviewTitle') },
            ]
          : [
              { label: t('navigation.shared.tests'), to: '/tests' },
              { label: test.title, to: `/tests/${testId}` },
              { label: t('testing.testResultsTitle'), to: `/tests/${testId}?tab=results` },
              { label: t('testing.resultReviewTitle') },
            ]}
      />

      <PageHeader
        title={t('testing.resultReviewHeader', { student: studentName, attempt: attemptNumber })}
        description={t('testing.resultReviewRouteDescription')}
      />

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <MetricCard title={t('testing.student')} value={studentName} />
        <MetricCard title={t('navigation.shared.groups')} value={groupNames.length > 0 ? groupNames.join(', ') : '-'} />
        <MetricCard title={t('testing.attemptNumber')} value={String(attemptNumber)} />
        <MetricCard title={t('testing.reviewStatus')} value={review.reviewedAt ? t('testing.reviewed') : t('testing.pendingReview')} />
        <MetricCard title={t('testing.startedAt')} value={formatDateTime(review.attemptStartedAt)} />
        <MetricCard title={t('testing.submittedAt')} value={formatDateTime(review.submittedAt)} />
        <MetricCard title={t('testing.timeSpent')} value={formatDuration(review.totalTimeSpentSeconds, t)} />
        <MetricCard title={t('testing.reviewedBy')} value={reviewedByName} />
        <MetricCard title={t('testing.scoreBeforeChanges')} value={`${scoreBeforeChanges}/${safeMaxPoints}`} />
        <MetricCard
          title={t('testing.scoreAfterChanges')}
          value={`${scoreAfterChanges}/${safeMaxPoints}${pendingCount > 0 ? ` (${t('testing.pendingChangesCount', { count: pendingCount })})` : ''}`}
        />
      </div>

      <div className="flex flex-wrap gap-3">
        <Link to={`/tests/${testId}?tab=results`}>
          <Button variant="secondary">{t('testing.backToResults')}</Button>
        </Link>
        <Button disabled={approveMutation.isPending} onClick={handleApproveRequest}>
          {t('testing.saveAndApprove')}
        </Button>
      </div>

      <SectionTabs
        activeId={activeTab}
        items={[
          { id: 'answers', label: t('testing.reviewTabs.answers') },
          { id: 'attemptInfo', label: t('testing.reviewTabs.attemptInfo') },
          { id: 'history', label: t('testing.reviewTabs.history') },
        ]}
        onChange={(next) => setActiveTab(next as ReviewTab)}
      />

      {activeTab === 'answers' ? (
        <div className="grid gap-4 xl:grid-cols-[280px_minmax(0,1fr)_320px]">
          <Card className="space-y-3 rounded-2xl border border-border bg-surface-muted p-4">
            <PageHeader title={t('testing.questions')} />
            {reviewQuestions.length === 0 ? (
              <EmptyState description={t('testing.noResultsToReview')} title={t('testing.questions')} />
            ) : (
              reviewQuestions.map((question, index) => {
                const status = deriveQuestionStatus(question, pendingChanges[question.id]?.score ?? question.score)
                const hasPendingChange = Boolean(pendingChanges[question.id])
                return (
                  <button
                    key={question.id}
                    className={cn(
                      'w-full rounded-[14px] border px-3 py-3 text-left transition',
                      question.id === effectiveSelectedQuestionId
                        ? 'border-accent bg-accent-muted'
                        : 'border-border bg-surface hover:border-border-strong',
                    )}
                    type="button"
                    onClick={() => setSelectedQuestionId(question.id)}
                  >
                    <p className="line-clamp-2 text-sm font-semibold text-text-primary">
                      {index + 1}. {question.questionText}
                    </p>
                    <p className="mt-1 text-xs text-text-secondary">
                      {t(`testing.questionType.${question.questionType}`)}
                      {' · '}
                      {(pendingChanges[question.id]?.score ?? question.score)}/{question.maxPoints}
                    </p>
                    <div className="mt-2 flex flex-wrap items-center gap-2">
                      <span className={cn('rounded-full px-2.5 py-1 text-xs font-semibold', questionStatusClassName(status))}>
                        {t(`testing.reviewQuestionStatus.${status}`)}
                      </span>
                      {hasPendingChange ? (
                        <span className="rounded-full border border-warning/30 bg-warning/10 px-2.5 py-1 text-xs font-semibold text-warning">
                          {t('testing.pendingChange')}
                        </span>
                      ) : null}
                    </div>
                  </button>
                )
              })
            )}
          </Card>

          <Card className="space-y-3 rounded-2xl border border-border bg-surface p-4">
            {selectedQuestion ? (
              <>
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="text-xs font-semibold uppercase tracking-[0.14em] text-text-muted">
                    {t(`testing.questionType.${selectedQuestion.questionType}`)}
                  </p>
                  <span className={cn(
                    'rounded-full px-2.5 py-1 text-xs font-semibold',
                    review.reviewedAt
                      ? 'bg-success/10 text-success'
                      : 'bg-warning/10 text-warning',
                  )}
                  >
                    {review.reviewedAt ? t('testing.reviewed') : t('testing.pendingReview')}
                  </span>
                </div>
                <h2 className="text-base font-semibold text-text-primary">{selectedQuestion.questionText}</h2>
                <ReviewValueCard
                  label={t('testing.studentAnswer')}
                  value={renderAnswerValue(selectedQuestion, selectedQuestion.submittedValueJson, questionDefinitions, 'student', t)}
                />
                <ReviewValueCard
                  label={t('testing.correctAnswer')}
                  value={renderAnswerValue(selectedQuestion, selectedQuestion.correctValueJson, questionDefinitions, 'correct', t)}
                />
                <div className="grid gap-2 text-sm text-text-secondary md:grid-cols-2">
                  <p>{t('testing.autoScore')}: {selectedQuestion.autoScore}</p>
                  <p>{t('testing.timeSpent')}: {formatDuration(selectedQuestion.timeSpentSeconds, t)}</p>
                  <p>{t('testing.currentSavedScore')}: {selectedQuestion.score}/{selectedQuestion.maxPoints}</p>
                  <p>{t('testing.reviewedAt')}: {selectedQuestion.reviewedAt ? formatDateTime(selectedQuestion.reviewedAt) : t('testing.notReviewed')}</p>
                </div>
              </>
            ) : (
              <EmptyState description={t('testing.selectQuestionToReview')} title={t('testing.questions')} />
            )}
          </Card>

          <Card className="space-y-3 rounded-2xl border border-border bg-surface p-4">
            <PageHeader title={t('testing.questionSpecificGradingTitle')} />
            {selectedQuestion ? (
              <>
                <p className="text-sm text-text-secondary">
                  {t('testing.questionScoreHint')}
                </p>
                <FormField label={t('testing.currentSavedScore')}>
                  <Input disabled value={`${selectedQuestion.score}/${selectedQuestion.maxPoints}`} />
                </FormField>
                <FormField label={t('testing.newScore')}>
                  <Input
                    max={selectedQuestion.maxPoints}
                    min={0}
                    step={1}
                    type="number"
                    value={draftScore}
                    onChange={(event) => {
                      const rawValue = Number(event.target.value)
                      const boundedValue = Number.isNaN(rawValue)
                        ? 0
                        : Math.max(0, Math.min(selectedQuestion.maxPoints, Math.round(rawValue)))
                      applyDraftChange({ score: boundedValue, comment: draftComment })
                    }}
                  />
                </FormField>
                <div className="flex flex-wrap gap-2">
                  <Button
                    variant="secondary"
                    onClick={() => applyDraftChange({
                      score: Math.max(0, draftScore - 1),
                      comment: draftComment,
                    })}
                  >
                    {t('testing.minusOnePoint')}
                  </Button>
                  <Button
                    variant="secondary"
                    onClick={() => applyDraftChange({
                      score: Math.min(selectedQuestion.maxPoints, draftScore + 1),
                      comment: draftComment,
                    })}
                  >
                    {t('testing.plusOnePoint')}
                  </Button>
                </div>
                <FormField label={t('testing.reviewComment')}>
                  <Textarea
                    value={draftComment}
                    onChange={(event) => applyDraftChange({
                      score: draftScore,
                      comment: event.target.value,
                    })}
                  />
                </FormField>
                <p className="text-sm text-text-secondary">{t('testing.maxPoints')}: {selectedQuestion.maxPoints}</p>
                {pendingChanges[selectedQuestion.id] ? (
                  <p className="rounded-[12px] border border-warning/30 bg-warning/10 px-3 py-2 text-sm font-semibold text-warning">
                    {t('testing.pendingChange')}
                  </p>
                ) : (
                  <p className="rounded-[12px] border border-border bg-surface-muted px-3 py-2 text-sm text-text-secondary">
                    {t('testing.noPendingChangeForQuestion')}
                  </p>
                )}
                {draftScoreValidation ? (
                  <p className="text-sm font-semibold text-danger">{draftScoreValidation}</p>
                ) : null}
                <Button
                  disabled={!pendingChanges[selectedQuestion.id]}
                  variant="secondary"
                  onClick={() => applyDraftChange({
                    score: selectedSavedScore,
                    comment: selectedSavedComment,
                  })}
                >
                  {t('testing.resetQuestionChange')}
                </Button>
              </>
            ) : (
              <EmptyState description={t('testing.selectQuestionToReview')} title={t('testing.questionSpecificGradingTitle')} />
            )}
          </Card>
        </div>
      ) : null}

      {activeTab === 'attemptInfo' ? (
        <Card className="space-y-4 rounded-2xl border border-border bg-surface p-4">
          <PageHeader title={t('testing.reviewTabs.attemptInfo')} />
          <div className="grid gap-3 text-sm text-text-secondary md:grid-cols-2 xl:grid-cols-3">
            <p>{t('testing.resultId')}: {review.resultId}</p>
            <p>{t('testing.attemptId')}: {review.attemptId ?? '-'}</p>
            <p>{t('testing.testTitle')}: {review.testTitle}</p>
            <p>{t('testing.startedAt')}: {formatDateTime(review.attemptStartedAt)}</p>
            <p>{t('testing.submittedAt')}: {formatDateTime(review.submittedAt)}</p>
            <p>{t('testing.timeSpent')}: {formatDuration(review.totalTimeSpentSeconds, t)}</p>
            <p>{t('testing.scoreBeforeChanges')}: {scoreBeforeChanges}/{safeMaxPoints}</p>
            <p>{t('testing.autoScore')}: {review.autoScore}/{safeMaxPoints}</p>
            <p>{t('testing.reviewedAt')}: {review.reviewedAt ? formatDateTime(review.reviewedAt) : t('testing.notReviewed')}</p>
          </div>
        </Card>
      ) : null}

      {activeTab === 'history' ? (
        <Card className="space-y-4 rounded-2xl border border-border bg-surface p-4">
          <PageHeader title={t('testing.reviewTabs.history')} />
          {reviewQuestions.some((question) => question.reviewedAt) ? (
            <div className="space-y-3">
              {reviewQuestions
                .filter((question) => question.reviewedAt)
                .map((question) => (
                  <div key={question.id} className="rounded-[14px] border border-border bg-surface-muted p-3">
                    <p className="text-sm font-semibold text-text-primary">
                      {question.questionOrderIndex + 1}. {question.questionText}
                    </p>
                    <p className="mt-1 text-sm text-text-secondary">
                      {t('testing.scoreChangeValue', { score: question.score, max: question.maxPoints })}
                    </p>
                    <p className="text-xs text-text-muted">
                      {t('testing.reviewedAt')}: {formatDateTime(question.reviewedAt)}
                      {' · '}
                      {t('testing.reviewedBy')}: {question.reviewedByUserId ? usersById.get(question.reviewedByUserId)?.username ?? '-' : '-'}
                    </p>
                  </div>
                ))}
            </div>
          ) : (
            <EmptyState description={t('testing.noScoreHistory')} title={t('testing.reviewTabs.history')} />
          )}
          {pendingCount > 0 ? (
            <div className="rounded-[14px] border border-warning/30 bg-warning/10 p-3">
              <p className="text-sm font-semibold text-warning">{t('testing.pendingChangesCount', { count: pendingCount })}</p>
              <p className="mt-1 text-sm text-warning">{t('testing.pendingChangesWillApplyOnApprove')}</p>
            </div>
          ) : null}
        </Card>
      ) : null}

      <ConfirmDialog
        title={t('testing.approveResultConfirmTitle')}
        description={t('testing.approveResultConfirmDescription')}
        confirmLabel={t('testing.approveResultConfirmAction')}
        open={approveConfirmOpen}
        onCancel={() => setApproveConfirmOpen(false)}
        onConfirm={handleApproveConfirmed}
      />
    </div>
  )
}

function renderAnswerValue(
  resultQuestion: TestResultQuestionResponse,
  valueJson: string | null,
  questionDefinitions: Map<string, QuestionResponse>,
  mode: 'student' | 'correct',
  t: (key: string) => string,
) {
  if (!valueJson) {
    return t('common.states.empty')
  }

  const definition = questionDefinitions.get(resultQuestion.questionId)
  const parsedValue = parseJsonSafe(valueJson)
  if (parsedValue == null) {
    return valueJson
  }

  if (resultQuestion.questionType === 'SINGLE_CHOICE' || resultQuestion.questionType === 'TRUE_FALSE') {
    if (typeof parsedValue !== 'string') {
      return stringifyValue(parsedValue, t)
    }
    const textById = new Map((definition?.answers ?? []).map((answer) => [answer.id, answer.text]))
    return localizeBoolean(textById.get(parsedValue) ?? parsedValue, t)
  }

  if (resultQuestion.questionType === 'MULTIPLE_CHOICE') {
    if (!Array.isArray(parsedValue)) {
      return stringifyValue(parsedValue, t)
    }
    const textById = new Map((definition?.answers ?? []).map((answer) => [answer.id, answer.text]))
    const labels = parsedValue.map((item) => (typeof item === 'string' ? localizeBoolean(textById.get(item) ?? item, t) : String(item)))
    return labels.length > 0 ? labels.map((label) => `• ${label}`).join('\n') : t('common.states.empty')
  }

  if (resultQuestion.questionType === 'MATCHING') {
    const config = parseQuestionConfig(definition?.configurationJson ?? null)
    if (mode === 'correct') {
      return renderCorrectMatching(config, t)
    }
    if (typeof parsedValue === 'object' && parsedValue != null && !Array.isArray(parsedValue)) {
      return renderSubmittedMatching(parsedValue as Record<string, unknown>, config, t)
    }
  }

  if (resultQuestion.questionType === 'ORDERING') {
    const config = parseQuestionConfig(definition?.configurationJson ?? null)
    const itemLabelById = new Map(
      Array.isArray(config.items)
        ? config.items.map((item) => {
            if (typeof item === 'object' && item != null) {
              const value = item as { id?: string; text?: string; label?: string }
              return [value.id ?? '', value.text ?? value.label ?? value.id ?? '']
            }
            return [String(item), String(item)]
          })
        : [],
    )
    if (Array.isArray(parsedValue)) {
      const labels = parsedValue.map((item, index) => {
        const id = String(item)
        const label = itemLabelById.get(id) ?? id
        return `${index + 1}. ${label}`
      })
      return labels.join('\n')
    }
  }

  if (resultQuestion.questionType === 'FILL_IN_THE_BLANK') {
    if (typeof parsedValue === 'object' && parsedValue != null && !Array.isArray(parsedValue)) {
      return Object.entries(parsedValue as Record<string, unknown>)
        .map(([blankId, answer]) => `${blankId}: ${String(answer ?? '')}`)
        .join('\n')
    }
  }

  return stringifyValue(parsedValue, t)
}

function renderCorrectMatching(config: Record<string, unknown>, t: (key: string) => string) {
  const leftItems = new Map(
    Array.isArray(config.leftItems)
      ? config.leftItems.map((item) => {
          if (typeof item === 'object' && item != null) {
            const value = item as { id?: string; text?: string; label?: string }
            return [value.id ?? '', value.text ?? value.label ?? value.id ?? '']
          }
          return [String(item), String(item)]
        })
      : [],
  )
  const rightItems = new Map(
    Array.isArray(config.rightItems)
      ? config.rightItems.map((item) => {
          if (typeof item === 'object' && item != null) {
            const value = item as { id?: string; text?: string; label?: string }
            return [value.id ?? '', value.text ?? value.label ?? value.id ?? '']
          }
          return [String(item), String(item)]
        })
      : [],
  )
  if (!Array.isArray(config.pairs) || config.pairs.length === 0) {
    return t('common.states.empty')
  }

  return config.pairs
    .map((pair) => {
      if (typeof pair !== 'object' || pair == null) {
        return String(pair)
      }
      const value = pair as { leftId?: string; rightId?: string; left?: string; right?: string }
      const leftLabel = value.leftId ? leftItems.get(value.leftId) ?? value.leftId : value.left ?? ''
      const rightLabel = value.rightId ? rightItems.get(value.rightId) ?? value.rightId : value.right ?? ''
      return `${leftLabel} -> ${rightLabel}`
    })
    .join('\n')
}

function renderSubmittedMatching(
  submittedValue: Record<string, unknown>,
  config: Record<string, unknown>,
  t: (key: string) => string,
) {
  const leftItems = new Map(
    Array.isArray(config.leftItems)
      ? config.leftItems.map((item) => {
          if (typeof item === 'object' && item != null) {
            const value = item as { id?: string; text?: string; label?: string }
            return [value.id ?? '', value.text ?? value.label ?? value.id ?? '']
          }
          return [String(item), String(item)]
        })
      : [],
  )
  const rightItems = new Map(
    Array.isArray(config.rightItems)
      ? config.rightItems.map((item) => {
          if (typeof item === 'object' && item != null) {
            const value = item as { id?: string; text?: string; label?: string }
            return [value.id ?? '', value.text ?? value.label ?? value.id ?? '']
          }
          return [String(item), String(item)]
        })
      : [],
  )
  const entries = Object.entries(submittedValue)
  if (entries.length === 0) {
    return t('common.states.empty')
  }
  return entries.map(([leftId, rightId]) => {
    const leftLabel = leftItems.get(leftId) ?? leftId
    const rightLabel = rightItems.get(String(rightId)) ?? String(rightId)
    return `${leftLabel} -> ${rightLabel}`
  }).join('\n')
}

function localizeBoolean(value: string, t: (key: string) => string) {
  if (value === 'TRUE') {
    return t('testing.booleanTrue')
  }
  if (value === 'FALSE') {
    return t('testing.booleanFalse')
  }
  return value
}

function stringifyValue(value: unknown, t: (key: string) => string): string {
  if (value == null) {
    return t('common.states.empty')
  }
  if (typeof value === 'string') {
    return value
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value)
  }
  if (Array.isArray(value)) {
    if (value.length === 0) {
      return t('common.states.empty')
    }
    return value.map((item) => `• ${stringifyValue(item, t)}`).join('\n')
  }
  if (typeof value === 'object') {
    const entries = Object.entries(value as Record<string, unknown>)
    if (entries.length === 0) {
      return t('common.states.empty')
    }
    return entries.map(([key, entryValue]) => `${key}: ${stringifyValue(entryValue, t)}`).join('\n')
  }
  return String(value)
}

function parseJsonSafe(value: string): unknown {
  try {
    return JSON.parse(value) as unknown
  } catch {
    return null
  }
}

function parseQuestionConfig(value: string | null): Record<string, unknown> {
  if (!value) {
    return {}
  }
  try {
    const parsed = JSON.parse(value) as unknown
    return typeof parsed === 'object' && parsed != null ? parsed as Record<string, unknown> : {}
  } catch {
    return {}
  }
}

function validateDraftScore(
  score: number,
  maxScore: number,
  t: (key: string, values?: Record<string, unknown>) => string,
) {
  if (!Number.isFinite(score) || score < 0) {
    return t('testing.validation.scoreNonNegative')
  }
  if (score > maxScore) {
    return t('testing.validation.scoreTooLarge', { max: maxScore })
  }
  if (!Number.isInteger(score)) {
    return t('testing.validation.scoreInteger')
  }
  return ''
}

function deriveQuestionStatus(question: TestResultQuestionResponse, effectiveScore: number): ReviewStatus {
  if (MANUAL_REVIEW_TYPES.includes(question.questionType) && !question.reviewedAt && question.autoScore === 0 && effectiveScore === 0) {
    return 'NOT_REVIEWED'
  }
  if (effectiveScore >= question.maxPoints) {
    return 'CORRECT'
  }
  if (effectiveScore > 0) {
    return 'PARTIAL'
  }
  return 'INCORRECT'
}

function questionStatusClassName(status: ReviewStatus) {
  if (status === 'CORRECT') {
    return 'bg-success/10 text-success'
  }
  if (status === 'PARTIAL') {
    return 'bg-warning/10 text-warning'
  }
  if (status === 'NOT_REVIEWED') {
    return 'bg-accent-muted text-text-primary'
  }
  return 'bg-danger/10 text-danger'
}

function computeScoreAfterPending(
  questions: TestResultQuestionResponse[],
  pendingChanges: Record<string, PendingChange>,
  maxPoints: number,
) {
  const total = questions.reduce((sum, question) => sum + (pendingChanges[question.id]?.score ?? question.score), 0)
  return Math.min(total, maxPoints)
}

function getAttemptNumber(
  results: Array<{ id: string; userId: string; createdAt: string }>,
  userId: string,
  resultId: string,
) {
  const userResults = results
    .filter((item) => item.userId === userId)
    .sort((left, right) => left.createdAt.localeCompare(right.createdAt))
  const index = userResults.findIndex((item) => item.id === resultId)
  return index >= 0 ? index + 1 : 1
}

function formatDuration(
  seconds: number | null,
  t: (key: string, values?: Record<string, unknown>) => string,
) {
  if (seconds == null || seconds < 0) {
    return '-'
  }
  const minutes = Math.floor(seconds / 60)
  const remainder = seconds % 60
  return t('testing.timeSpentValue', { minutes, seconds: remainder.toString().padStart(2, '0') })
}

function ReviewValueCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-[14px] border border-border bg-surface-muted px-3 py-3">
      <p className="text-xs font-semibold uppercase tracking-[0.12em] text-text-muted">{label}</p>
      <p className="mt-1 whitespace-pre-wrap break-words text-sm text-text-primary">{value}</p>
    </div>
  )
}
