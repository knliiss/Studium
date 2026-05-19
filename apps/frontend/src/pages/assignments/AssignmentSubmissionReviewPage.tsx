import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import { FileAttachmentList, FilePreviewPanel } from '@/features/files/preview'
import type { FileAttachmentItem } from '@/features/files/preview'
import { loadAccessibleSubjects, loadManagedSubjects } from '@/pages/education/helpers'
import { assignmentService, educationService, userDirectoryService } from '@/shared/api/services'
import { getLocalizedRequestErrorMessage } from '@/shared/lib/api-errors'
import { downloadBlob } from '@/shared/lib/download'
import { formatDateTime } from '@/shared/lib/format'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { Textarea } from '@/shared/ui/Textarea'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { UserAvatar } from '@/shared/ui/UserAvatar'
import { AccessDeniedPage } from '@/pages/shared/AccessDeniedPage'
import { StatusBadge } from '@/widgets/common/StatusBadge'
import { Breadcrumbs } from '@/widgets/common/Breadcrumbs'

interface AssignmentSubmissionReviewPageProps {
  assignmentId?: string
  submissionId: string
}

interface WorkflowAttachmentItem extends FileAttachmentItem {
  submissionId: string
}

export function AssignmentSubmissionReviewPage({ assignmentId, submissionId }: AssignmentSubmissionReviewPageProps) {
  const { t } = useTranslation()
  const { primaryRole, session } = useAuth()
  const queryClient = useQueryClient()
  const isStudent = primaryRole === 'STUDENT'

  const workflowQuery = useQuery({
    queryKey: ['submission-workflow', submissionId],
    queryFn: () => assignmentService.getSubmissionWorkflow(submissionId),
  })

  const resolvedAssignmentId = assignmentId || workflowQuery.data?.assignmentId || ''
  const assignmentQuery = useQuery({
    queryKey: ['assignment', resolvedAssignmentId],
    queryFn: () => assignmentService.getAssignment(resolvedAssignmentId),
    enabled: Boolean(resolvedAssignmentId),
  })

  const submissionStudentQuery = useQuery({
    queryKey: ['submission-workflow', submissionId, 'student', workflowQuery.data?.studentId],
    queryFn: async () => {
      if (!workflowQuery.data?.studentId) {
        return null
      }
      const users = await userDirectoryService.lookup([workflowQuery.data.studentId])
      return users[0] ?? null
    },
    enabled: Boolean(workflowQuery.data?.studentId),
  })

  const subjectScopeQuery = useQuery({
    queryKey: ['education', 'assignment-review-subject-scope', primaryRole, session?.user.id],
    queryFn: () => {
      if (primaryRole === 'STUDENT' || primaryRole === 'TEACHER') {
        return loadAccessibleSubjects(primaryRole, session?.user.id ?? '')
      }
      return loadManagedSubjects()
    },
    enabled: Boolean(assignmentQuery.data && session?.user.id),
  })

  const assignmentSubjectQuery = useQuery({
    queryKey: ['education', 'assignment-review-subject', resolvedAssignmentId, assignmentQuery.data?.topicId, subjectScopeQuery.data?.length],
    queryFn: async () => {
      const assignment = assignmentQuery.data
      if (!assignment) {
        return null
      }

      for (const subject of subjectScopeQuery.data ?? []) {
        const topicsPage = await educationService.getTopicsBySubject(subject.id, {
          page: 0,
          size: 100,
          sortBy: 'orderIndex',
          direction: 'asc',
        })
        if (topicsPage.items.some((topic) => topic.id === assignment.topicId)) {
          return subject
        }
      }

      return null
    },
    enabled: Boolean(assignmentQuery.data && subjectScopeQuery.data),
  })

  const connectedGroupsQuery = useQuery({
    queryKey: ['education', 'assignment-review-groups', assignmentSubjectQuery.data?.groupIds.join(',')],
    queryFn: async () => Promise.all((assignmentSubjectQuery.data?.groupIds ?? []).map((groupId) => educationService.getGroup(groupId))),
    enabled: Boolean(assignmentSubjectQuery.data?.groupIds.length),
  })

  const groupStudentsQuery = useQuery({
    queryKey: ['submission-workflow', submissionId, 'group-students', connectedGroupsQuery.data?.map((group) => group.id).join(',')],
    queryFn: async () => Promise.all(
      (connectedGroupsQuery.data ?? []).map(async (group) => ({
        group,
        students: await educationService.listGroupStudents(group.id),
      })),
    ),
    enabled: Boolean(connectedGroupsQuery.data?.length),
  })

  const [selectedAttachmentId, setSelectedAttachmentId] = useState('')
  const [draftAttemptId, setDraftAttemptId] = useState('')
  const [score, setScore] = useState(0)
  const [comment, setComment] = useState('')
  const [revisionDeadline, setRevisionDeadline] = useState('')

  const reviewMutation = useMutation({
    mutationFn: () => assignmentService.createGrade({
      submissionId: workflowQuery.data?.currentAttempt?.id ?? submissionId,
      score: effectiveScore,
      feedback: effectiveComment.trim() || undefined,
    }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['submission-workflow', submissionId] }),
        queryClient.invalidateQueries({ queryKey: ['review-queue'] }),
        queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
      ])
    },
  })

  const returnMutation = useMutation({
    mutationFn: () => assignmentService.returnSubmissionForRevision(workflowQuery.data?.currentAttempt?.id ?? submissionId, {
      feedback: effectiveComment.trim(),
      revisionDeadline: effectiveRevisionDeadline ? fromLocalDateTimeValue(effectiveRevisionDeadline) : null,
    }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['submission-workflow', submissionId] }),
        queryClient.invalidateQueries({ queryKey: ['review-queue'] }),
        queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
      ])
    },
  })

  const flattenedAttachments = useMemo<WorkflowAttachmentItem[]>(
    () => (workflowQuery.data?.attempts ?? []).flatMap((attempt) => attempt.attachments.map((attachment) => ({
      id: attachment.id,
      submissionId: attempt.id,
      fileId: attachment.fileId,
      displayName: attachment.displayName,
      originalFileName: attachment.originalFileName,
      contentType: attachment.contentType,
      sizeBytes: attachment.sizeBytes,
      previewAvailable: attachment.previewAvailable,
      createdAt: attachment.createdAt,
    }))),
    [workflowQuery.data?.attempts],
  )

  const selectedAttachment = flattenedAttachments.find((attachment) => attachment.id === selectedAttachmentId)
    ?? flattenedAttachments[0]
    ?? null

  const studentGroups = useMemo(() => {
    if (!workflowQuery.data?.studentId) {
      return [] as string[]
    }

    const groupNames: string[] = []
    for (const groupEntry of groupStudentsQuery.data ?? []) {
      if (groupEntry.students.some((student) => student.userId === workflowQuery.data!.studentId)) {
        groupNames.push(groupEntry.group.name)
      }
    }
    return groupNames
  }, [groupStudentsQuery.data, workflowQuery.data])

  if (isStudent) {
    return <AccessDeniedPage />
  }

  if (
    workflowQuery.isLoading
    || assignmentQuery.isLoading
    || submissionStudentQuery.isLoading
    || subjectScopeQuery.isLoading
    || assignmentSubjectQuery.isLoading
    || connectedGroupsQuery.isLoading
    || groupStudentsQuery.isLoading
  ) {
    return <LoadingState />
  }

  if (
    workflowQuery.isError
    || assignmentQuery.isError
    || submissionStudentQuery.isError
    || subjectScopeQuery.isError
    || assignmentSubjectQuery.isError
    || connectedGroupsQuery.isError
    || groupStudentsQuery.isError
    || !workflowQuery.data
    || !assignmentQuery.data
  ) {
    return (
      <ErrorState
        title={t('assignments.submissionReview')}
        description={getLocalizedRequestErrorMessage(
          workflowQuery.error
            ?? assignmentQuery.error
            ?? submissionStudentQuery.error
            ?? subjectScopeQuery.error
            ?? assignmentSubjectQuery.error
            ?? connectedGroupsQuery.error
            ?? groupStudentsQuery.error,
          t,
        )}
      />
    )
  }

  const workflow = workflowQuery.data
  const assignment = assignmentQuery.data
  const submissionStudent = submissionStudentQuery.data
  const currentAttempt = workflow.currentAttempt
  const effectiveScore = draftAttemptId === currentAttempt?.id ? score : (currentAttempt?.score ?? 0)
  const effectiveComment = draftAttemptId === currentAttempt?.id
    ? comment
    : (currentAttempt?.feedback ?? currentAttempt?.reviewComment ?? '')
  const effectiveRevisionDeadline = draftAttemptId === currentAttempt?.id
    ? revisionDeadline
    : toLocalDateTimeValue(currentAttempt?.revisionDeadline)
  const canReviewCurrentAttempt = currentAttempt != null && (currentAttempt.status === 'SUBMITTED' || currentAttempt.status === 'RESUBMITTED')

  return (
    <div className="space-y-6">
      <Breadcrumbs
        items={assignmentSubjectQuery.data
          ? [
              { label: t('navigation.shared.subjects'), to: '/subjects' },
              { label: assignmentSubjectQuery.data.name, to: `/subjects/${assignmentSubjectQuery.data.id}` },
              { label: t('navigation.shared.review'), to: '/review' },
              { label: assignment.title },
            ]
          : [
              { label: t('navigation.shared.review'), to: '/review' },
              { label: assignment.title },
            ]}
      />

      <Card className="space-y-5 rounded-[28px] border border-border-strong bg-surface p-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="space-y-3">
            <Link className="text-sm font-medium text-accent" to="/review">
              {t('assignments.backToSubmissions')}
            </Link>
            <div className="space-y-2">
              <h1 className="text-3xl font-bold tracking-[-0.03em] text-text-primary">
                {assignment.title}
              </h1>
              <p className="text-sm text-text-secondary">
                {assignmentSubjectQuery.data?.name ?? t('navigation.shared.assignments')}
              </p>
            </div>
          </div>
          <StatusBadge value={workflow.currentStatus} />
        </div>

        <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
          <Card className="rounded-[22px] border border-border bg-surface-muted p-5">
            <div className="flex items-start gap-4">
              <UserAvatar email={submissionStudent?.email} size="lg" username={submissionStudent?.username} />
              <div className="space-y-2">
                <p className="text-lg font-semibold text-text-primary">
                  {submissionStudent?.username ?? t('education.unknownStudent')}
                </p>
                <div className="grid gap-2 text-sm text-text-secondary sm:grid-cols-2">
                  <p>{t('education.subjectGroupsLabel')}: <span className="font-medium text-text-primary">{studentGroups.join(', ') || '—'}</span></p>
                  <p>{t('assignments.attemptsUsed')}: <span className="font-medium text-text-primary">{workflow.currentAttemptNumber}/{workflow.maxAttempts}</span></p>
                  <p>{t('assignments.submittedAt')}: <span className="font-medium text-text-primary">{formatDateTime(currentAttempt?.submittedAt ?? null)}</span></p>
                  <p>{t('assignments.revisionDeadline')}: <span className="font-medium text-text-primary">{formatDateTime(workflow.revisionDeadline)}</span></p>
                </div>
              </div>
            </div>
          </Card>

          <Card className="rounded-[22px] border border-border bg-surface-muted p-5">
            <p className="text-xs font-semibold uppercase tracking-[0.2em] text-text-muted">
              {t('assignments.currentAttempt')}
            </p>
            <p className="mt-2 text-3xl font-semibold text-text-primary">
              {workflow.currentAttemptNumber}/{workflow.maxAttempts}
            </p>
            <div className="mt-4 grid gap-2 text-sm text-text-secondary">
              <p>{t('assignments.remainingAttempts')}: <span className="font-medium text-text-primary">{workflow.remainingAttempts}</span></p>
              <p>{t('common.labels.score')}: <span className="font-medium text-text-primary">{workflow.finalScore == null ? '—' : `${workflow.finalScore}/${workflow.maxScore}`}</span></p>
              <p>{t('assignments.reviewedAt')}: <span className="font-medium text-text-primary">{formatDateTime(currentAttempt?.reviewedAt ?? null)}</span></p>
            </div>
          </Card>
        </div>
      </Card>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.2fr)_minmax(0,0.8fr)]">
        <div className="space-y-4">
          <Card className="space-y-4 rounded-[28px] border border-border-strong bg-surface p-5">
            <PageHeader title={t('assignments.attemptHistory')} />
            {workflow.attempts.length === 0 ? (
              <EmptyState title={t('assignments.attemptHistory')} description={t('assignments.noAttemptsYet')} />
            ) : (
              <div className="space-y-4">
                {workflow.attempts.map((attempt) => (
                  <div
                    key={attempt.id}
                    className={`rounded-[22px] border p-4 ${attempt.id === currentAttempt?.id ? 'border-accent/40 bg-accent/5' : 'border-border bg-surface-muted'}`}
                  >
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div>
                        <p className="text-base font-semibold text-text-primary">
                          {t('assignments.attemptLabel', { number: attempt.attemptNumber })}
                        </p>
                        <p className="text-sm text-text-secondary">{formatDateTime(attempt.submittedAt)}</p>
                      </div>
                      <div className="flex flex-wrap items-center gap-2">
                        <StatusBadge value={attempt.status} />
                        {attempt.submittedLate ? (
                          <span className="rounded-full border border-danger/30 bg-danger/10 px-2.5 py-1 text-xs font-semibold text-danger">
                            {t('assignments.lateSubmission')}
                          </span>
                        ) : null}
                      </div>
                    </div>

                    <div className="mt-4 grid gap-3 text-sm text-text-secondary md:grid-cols-2">
                      <p>{t('common.labels.score')}: <span className="font-medium text-text-primary">{attempt.score == null ? '—' : `${attempt.score}/${workflow.maxScore}`}</span></p>
                      <p>{t('assignments.reviewedAt')}: <span className="font-medium text-text-primary">{formatDateTime(attempt.reviewedAt)}</span></p>
                    </div>

                    {attempt.reviewComment ? (
                      <div className="mt-4 rounded-[18px] border border-border bg-surface px-4 py-3">
                        <p className="text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">
                          {t('assignments.teacherComment')}
                        </p>
                        <p className="mt-2 whitespace-pre-line text-sm leading-6 text-text-secondary">
                          {attempt.reviewComment}
                        </p>
                      </div>
                    ) : null}

                    <div className="mt-4">
                      <FileAttachmentList
                        files={attempt.attachments.map((attachment) => ({
                          id: attachment.id,
                          fileId: attachment.fileId,
                          displayName: attachment.displayName,
                          originalFileName: attachment.originalFileName,
                          contentType: attachment.contentType,
                          sizeBytes: attachment.sizeBytes,
                          previewAvailable: attachment.previewAvailable,
                          createdAt: attachment.createdAt,
                        }))}
                        selectedFileId={selectedAttachment?.submissionId === attempt.id ? selectedAttachment.id : null}
                        collapsible
                        defaultExpanded={attempt.id === currentAttempt?.id}
                        onSelect={(attachment) => setSelectedAttachmentId(attachment.id)}
                        onDownload={async (attachment) => {
                          const blob = await assignmentService.downloadSubmissionAttachment(attempt.id, attachment.id)
                          downloadBlob(blob, attachment.originalFileName)
                        }}
                      />
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Card>
        </div>

        <div className="space-y-4">
          <Card className="space-y-4 rounded-[28px] border border-border-strong bg-surface p-5">
            <PageHeader title={t('assignments.reviewPanel')} />
            <FormField label={t('common.labels.score')}>
              <Input
                max={workflow.maxScore}
                min={0}
                type="number"
                value={effectiveScore}
                onChange={(event) => {
                  setDraftAttemptId(currentAttempt?.id ?? '')
                  setScore(Number(event.target.value))
                }}
              />
            </FormField>
            <FormField label={t('assignments.teacherComment')}>
              <Textarea
                rows={7}
                value={effectiveComment}
                onChange={(event) => {
                  setDraftAttemptId(currentAttempt?.id ?? '')
                  setComment(event.target.value)
                }}
              />
            </FormField>
            <FormField label={t('assignments.newRevisionDeadline')}>
              <Input
                type="datetime-local"
                value={effectiveRevisionDeadline}
                onChange={(event) => {
                  setDraftAttemptId(currentAttempt?.id ?? '')
                  setRevisionDeadline(event.target.value)
                }}
              />
            </FormField>

            <div className="grid gap-3">
              <Button
                disabled={!canReviewCurrentAttempt || effectiveScore < 0 || effectiveScore > workflow.maxScore || reviewMutation.isPending || returnMutation.isPending}
                onClick={() => reviewMutation.mutate()}
              >
                {reviewMutation.isPending ? t('common.states.loading') : t('assignments.gradeAndApprove')}
              </Button>
              <Button
                variant="secondary"
                disabled={!canReviewCurrentAttempt || !effectiveComment.trim() || reviewMutation.isPending || returnMutation.isPending}
                onClick={() => returnMutation.mutate()}
              >
                {returnMutation.isPending ? t('common.states.loading') : t('assignments.returnForRevision')}
              </Button>
              <Link to={`/assignments/${workflow.assignmentId}`}>
                <Button fullWidth variant="ghost">{t('assignments.otherActions')}</Button>
              </Link>
            </div>
          </Card>

          <Card className="space-y-4 rounded-[28px] border border-border-strong bg-surface p-5">
            <PageHeader title={t('assignments.filePreview')} />
            <FilePreviewPanel
              fetchDownloadBlob={(attachment) => assignmentService.downloadSubmissionAttachment((attachment as WorkflowAttachmentItem).submissionId, attachment.id)}
              fetchPreviewBlob={(attachment) => assignmentService.previewSubmissionAttachment((attachment as WorkflowAttachmentItem).submissionId, attachment.id)}
              selectedFile={selectedAttachment}
              onDownload={async (attachment) => {
                const workflowAttachment = attachment as WorkflowAttachmentItem
                const blob = await assignmentService.downloadSubmissionAttachment(workflowAttachment.submissionId, workflowAttachment.id)
                downloadBlob(blob, workflowAttachment.originalFileName)
              }}
            />
          </Card>
        </div>
      </div>
    </div>
  )
}

function toLocalDateTimeValue(value?: string | null) {
  if (!value) {
    return ''
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }

  const year = date.getFullYear()
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  const hours = `${date.getHours()}`.padStart(2, '0')
  const minutes = `${date.getMinutes()}`.padStart(2, '0')
  return `${year}-${month}-${day}T${hours}:${minutes}`
}

function fromLocalDateTimeValue(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toISOString()
}
