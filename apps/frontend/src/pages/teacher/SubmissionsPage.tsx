import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'

import { assignmentService, dashboardService, testingService, userDirectoryService } from '@/shared/api/services'
import { formatDateTime } from '@/shared/lib/format'
import type { AssignmentResponse } from '@/shared/types/api'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { DataTable } from '@/shared/ui/DataTable'
import { EntityPicker } from '@/shared/ui/EntityPicker'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { Textarea } from '@/shared/ui/Textarea'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { UserAvatar } from '@/shared/ui/UserAvatar'
import { Breadcrumbs } from '@/widgets/common/Breadcrumbs'

export function TeacherSubmissionsPage() {
  const { submissionId } = useParams()
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [selectedSubmissionId, setSelectedSubmissionId] = useState(submissionId ?? '')
  const [commentBody, setCommentBody] = useState('')
  const [gradeForm, setGradeForm] = useState({ score: 0, feedback: '' })
  const dashboardQuery = useQuery({
    queryKey: ['dashboard', 'teacher'],
    queryFn: () => dashboardService.getTeacherDashboard(),
  })
  const pendingSubmissions = useMemo(
    () => dashboardQuery.data?.pendingSubmissionsToReview ?? [],
    [dashboardQuery.data?.pendingSubmissionsToReview],
  )
  const studentIds = useMemo(
    () => Array.from(new Set(pendingSubmissions.map((submission) => submission.studentId))),
    [pendingSubmissions],
  )
  const assignmentIds = useMemo(
    () => Array.from(new Set(pendingSubmissions.map((submission) => submission.assignmentId))),
    [pendingSubmissions],
  )
  const activeTestIds = useMemo(
    () => Array.from(new Set((dashboardQuery.data?.activeTests ?? []).map((test) => test.testId))),
    [dashboardQuery.data?.activeTests],
  )
  const assignmentsQuery = useQuery({
    queryKey: ['review', 'assignments', assignmentIds.join(',')],
    queryFn: async () => {
      const assignments = await Promise.all(
        assignmentIds.map(async (assignmentId) => {
          try {
            return await assignmentService.getAssignment(assignmentId)
          } catch {
            return null
          }
        }),
      )
      return assignments.filter((assignment): assignment is AssignmentResponse => Boolean(assignment))
    },
    enabled: assignmentIds.length > 0,
  })
  const commentsQuery = useQuery({
    queryKey: ['submission-comments', selectedSubmissionId],
    queryFn: () => assignmentService.listSubmissionComments(selectedSubmissionId),
    enabled: Boolean(selectedSubmissionId),
  })
  const testResultsQuery = useQuery({
    queryKey: ['review', 'test-results', activeTestIds.join(',')],
    queryFn: async () => {
      const pages = await Promise.all(
        activeTestIds.map((testId) => testingService.getTestResultsByTest(testId, { page: 0, size: 20 })),
      )
      return pages.flatMap((page) => page.items)
    },
    enabled: activeTestIds.length > 0,
  })
  const testResultStudentIds = useMemo(
    () => Array.from(new Set((testResultsQuery.data ?? []).map((result) => result.userId))),
    [testResultsQuery.data],
  )
  const allStudentIds = useMemo(
    () => Array.from(new Set([...studentIds, ...testResultStudentIds])),
    [studentIds, testResultStudentIds],
  )
  const studentsQuery = useQuery({
    queryKey: ['review', 'students', allStudentIds.join(',')],
    queryFn: () => userDirectoryService.lookup(allStudentIds),
    enabled: allStudentIds.length > 0,
  })

  const submissionOptions = useMemo(
    () => {
      const studentById = new Map((studentsQuery.data ?? []).map((student) => [student.id, student]))
      const assignmentById = new Map((assignmentsQuery.data ?? []).map((assignment) => [assignment.id, assignment]))
      const options = (dashboardQuery.data?.pendingSubmissionsToReview ?? []).map((submission) => ({
        value: submission.submissionId,
        label: studentById.get(submission.studentId)?.username ?? t('education.unknownStudent'),
        description: `${assignmentById.get(submission.assignmentId)?.title ?? t('dashboard.assignment')} · ${formatDateTime(submission.submittedAt)}`,
      }))

      if (!selectedSubmissionId || options.some((option) => option.value === selectedSubmissionId)) {
        return options
      }

      return [
        ...options,
        { value: selectedSubmissionId, label: t('assignments.submission'), description: t('assignments.selectSubmission') },
      ]
    },
    [assignmentsQuery.data, dashboardQuery.data?.pendingSubmissionsToReview, selectedSubmissionId, studentsQuery.data, t],
  )

  const commentMutation = useMutation({
    mutationFn: () => assignmentService.createSubmissionComment(selectedSubmissionId, commentBody),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['submission-comments', selectedSubmissionId] })
      setCommentBody('')
    },
  })
  const gradeMutation = useMutation({
    mutationFn: () => assignmentService.createGrade({ submissionId: selectedSubmissionId, ...gradeForm }),
  })

  if (dashboardQuery.isLoading || commentsQuery.isLoading || studentsQuery.isLoading || assignmentsQuery.isLoading || testResultsQuery.isLoading) {
    return <LoadingState />
  }

  if (dashboardQuery.isError || commentsQuery.isError || studentsQuery.isError || assignmentsQuery.isError || testResultsQuery.isError || !dashboardQuery.data) {
    return <ErrorState title={t('navigation.shared.review')} description={t('common.states.error')} />
  }
  const studentById = new Map((studentsQuery.data ?? []).map((student) => [student.id, student]))
  const assignmentById = new Map((assignmentsQuery.data ?? []).map((assignment) => [assignment.id, assignment]))
  const activeTestById = new Map((dashboardQuery.data.activeTests ?? []).map((test) => [test.testId, test]))
  const unreviewedTestResults = (testResultsQuery.data ?? []).filter((result) => !result.reviewedAt)

  return (
    <div className="space-y-6">
      <Breadcrumbs items={[{ label: t('navigation.shared.review') }]} />
      <PageHeader
        description={t('assignments.reviewDescription')}
        title={t('navigation.shared.review')}
      />
      {pendingSubmissions.length === 0 && unreviewedTestResults.length === 0 ? (
        <EmptyState description={t('review.emptyQueue')} title={t('navigation.shared.review')} />
      ) : (
        <div className="grid gap-4 lg:grid-cols-2">
          {pendingSubmissions.map((submission) => {
            const student = studentById.get(submission.studentId)
            const assignment = assignmentById.get(submission.assignmentId)

            return (
              <Card key={submission.submissionId} className="space-y-4">
                <div className="flex items-start justify-between gap-4">
                  <div className="flex min-w-0 items-start gap-3">
                    <UserAvatar email={student?.email} size="md" username={student?.username} />
                    <div className="min-w-0">
                      <p className="truncate font-semibold text-text-primary">
                        {student?.username ?? t('education.unknownStudent')}
                      </p>
                      <p className="truncate text-sm text-text-secondary">
                        {assignment?.title ?? t('dashboard.assignment')}
                      </p>
                    </div>
                  </div>
                  <span className="rounded-full border border-warning/30 bg-warning/10 px-2.5 py-1 text-xs font-semibold text-warning">
                    {t('navigation.shared.review')}
                  </span>
                </div>
                <p className="text-sm text-text-secondary">
                  {t('assignments.submittedAt')}: {formatDateTime(submission.submittedAt)}
                </p>
                <div className="flex flex-wrap gap-2">
                  <Link to={`/assignments/${submission.assignmentId}`}>
                    <Button variant="secondary">{t('navigation.shared.assignments')}</Button>
                  </Link>
                  <Link to={`/review/${submission.submissionId}`}>
                    <Button onClick={() => setSelectedSubmissionId(submission.submissionId)}>
                      {t('common.actions.open')}
                    </Button>
                  </Link>
                </div>
              </Card>
            )
          })}
          {unreviewedTestResults.map((result) => {
            const student = studentById.get(result.userId)
            const test = activeTestById.get(result.testId)

            return (
              <Card key={result.id} className="space-y-4">
                <div className="flex items-start justify-between gap-4">
                  <div className="flex min-w-0 items-start gap-3">
                    <UserAvatar email={student?.email} size="md" username={student?.username} />
                    <div className="min-w-0">
                      <p className="truncate font-semibold text-text-primary">
                        {student?.username ?? t('education.unknownStudent')}
                      </p>
                      <p className="truncate text-sm text-text-secondary">
                        {test?.title ?? t('navigation.shared.tests')}
                      </p>
                    </div>
                  </div>
                  <span className="rounded-full border border-info/30 bg-info/10 px-2.5 py-1 text-xs font-semibold text-info">
                    {t('review.testResult')}
                  </span>
                </div>
                <p className="text-sm text-text-secondary">
                  {t('testing.autoScore')}: {result.autoScore} · {t('common.labels.score')}: {result.score}
                </p>
                <p className="text-sm text-text-secondary">
                  {t('assignments.submittedAt')}: {formatDateTime(result.createdAt)}
                </p>
                <Link to={`/tests/${result.testId}`}>
                  <Button>{t('testing.reviewResult')}</Button>
                </Link>
              </Card>
            )
          })}
        </div>
      )}

      <Card className="space-y-4">
        <PageHeader title={t('assignments.addComment')} />
        <EntityPicker
          value={selectedSubmissionId}
          label={t('assignments.submission')}
          options={submissionOptions}
          placeholder={t('assignments.selectSubmission')}
          emptyLabel={t('assignments.noSubmissionsToReview')}
          onChange={setSelectedSubmissionId}
        />
        <FormField label={t('assignments.comment')}>
          <Textarea value={commentBody} onChange={(event) => setCommentBody(event.target.value)} />
        </FormField>
        <Button disabled={!selectedSubmissionId || !commentBody.trim()} onClick={() => commentMutation.mutate()}>{t('assignments.addComment')}</Button>
        {commentsQuery.data?.length ? (
          <DataTable
            columns={[
              { key: 'authorUserId', header: t('assignments.commentAuthor'), render: (item) => studentById.get(item.authorUserId)?.username ?? t('education.unknownStudent') },
              { key: 'body', header: t('assignments.comment'), render: (item) => item.body },
              { key: 'createdAt', header: t('audit.occurredAt'), render: (item) => formatDateTime(item.createdAt) },
            ]}
            rows={commentsQuery.data}
          />
        ) : null}
      </Card>

      <Card className="space-y-4">
        <PageHeader title={t('assignments.gradeSubmission')} />
        <div className="grid gap-4 xl:grid-cols-3">
          <EntityPicker
            value={selectedSubmissionId}
            label={t('assignments.submission')}
            options={submissionOptions}
            placeholder={t('assignments.selectSubmission')}
            emptyLabel={t('assignments.noSubmissionsToReview')}
            onChange={setSelectedSubmissionId}
          />
          <FormField label={t('common.labels.score')}>
            <Input type="number" value={gradeForm.score} onChange={(event) => setGradeForm((current) => ({ ...current, score: Number(event.target.value) }))} />
          </FormField>
          <FormField label={t('assignments.feedback')}>
            <Input value={gradeForm.feedback} onChange={(event) => setGradeForm((current) => ({ ...current, feedback: event.target.value }))} />
          </FormField>
        </div>
        <Button disabled={!selectedSubmissionId} onClick={() => gradeMutation.mutate()}>{t('assignments.gradeSubmission')}</Button>
      </Card>
    </div>
  )
}
