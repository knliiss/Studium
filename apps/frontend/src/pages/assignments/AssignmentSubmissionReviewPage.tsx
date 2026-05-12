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
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { Textarea } from '@/shared/ui/Textarea'
import { AccessDeniedPage } from '@/pages/shared/AccessDeniedPage'
import { Breadcrumbs } from '@/widgets/common/Breadcrumbs'

interface AssignmentSubmissionReviewPageProps {
  assignmentId: string
  submissionId: string
}

export function AssignmentSubmissionReviewPage({ assignmentId, submissionId }: AssignmentSubmissionReviewPageProps) {
  const { t } = useTranslation()
  const { primaryRole, session } = useAuth()
  const queryClient = useQueryClient()
  const isStudent = primaryRole === 'STUDENT'

  const assignmentQuery = useQuery({
    queryKey: ['assignment', assignmentId],
    queryFn: () => assignmentService.getAssignment(assignmentId),
  })
  const submissionQuery = useQuery({
    queryKey: ['assignment', assignmentId, 'submission', submissionId],
    queryFn: () => assignmentService.getSubmission(submissionId),
  })
  const submissionAttachmentsQuery = useQuery({
    queryKey: ['assignment', assignmentId, 'submission-attachments', submissionId],
    queryFn: () => assignmentService.listSubmissionAttachments(submissionId),
  })
  const submissionStudentQuery = useQuery({
    queryKey: ['assignment', assignmentId, 'submission-student', submissionQuery.data?.userId],
    queryFn: async () => {
      if (!submissionQuery.data) {
        return null
      }
      const students = await userDirectoryService.lookup([submissionQuery.data.userId])
      return students[0] ?? null
    },
    enabled: Boolean(submissionQuery.data?.userId),
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
    queryKey: ['education', 'assignment-review-subject', assignmentId, assignmentQuery.data?.topicId, subjectScopeQuery.data?.length],
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
    queryKey: ['assignment', assignmentId, 'review-group-students', connectedGroupsQuery.data?.map((group) => group.id).join(',')],
    queryFn: async () => Promise.all(
      (connectedGroupsQuery.data ?? []).map(async (group) => ({
        group,
        students: await educationService.listGroupStudents(group.id),
      })),
    ),
    enabled: Boolean(connectedGroupsQuery.data?.length),
  })

  const [selectedAttachmentId, setSelectedAttachmentId] = useState('')
  const [gradeDraft, setGradeDraft] = useState<{ submissionId: string | null; score: number; feedback: string }>({
    submissionId: null,
    score: 0,
    feedback: '',
  })

  const gradeMutation = useMutation({
    mutationFn: () => assignmentService.createGrade({
      submissionId,
      score: effectiveScore,
      feedback: effectiveFeedback.trim() || undefined,
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['assignment', assignmentId, 'submission', submissionId] })
      await queryClient.invalidateQueries({ queryKey: ['assignment', assignmentId, 'submissions'] })
      setGradeDraft({ submissionId: null, score: 0, feedback: '' })
    },
  })

  const attachmentItems: FileAttachmentItem[] = useMemo(
    () => (submissionAttachmentsQuery.data ?? []).map((attachment) => ({
      id: attachment.id,
      fileId: attachment.fileId,
      displayName: attachment.displayName,
      originalFileName: attachment.originalFileName,
      contentType: attachment.contentType,
      sizeBytes: attachment.sizeBytes,
      previewAvailable: attachment.previewAvailable,
      createdAt: attachment.createdAt,
    })),
    [submissionAttachmentsQuery.data],
  )

  const selectedAttachment = attachmentItems.find((attachment) => attachment.id === selectedAttachmentId)
    ?? attachmentItems[0]
    ?? null

  const studentGroups = useMemo(() => {
    if (!submissionQuery.data) {
      return [] as string[]
    }

    const groupNames: string[] = []
    for (const groupEntry of groupStudentsQuery.data ?? []) {
      if (groupEntry.students.some((student) => student.userId === submissionQuery.data!.userId)) {
        groupNames.push(groupEntry.group.name)
      }
    }
    return groupNames
  }, [groupStudentsQuery.data, submissionQuery.data])

  if (isStudent) {
    return <AccessDeniedPage />
  }

  if (
    assignmentQuery.isLoading
    || submissionQuery.isLoading
    || submissionAttachmentsQuery.isLoading
    || subjectScopeQuery.isLoading
    || assignmentSubjectQuery.isLoading
    || connectedGroupsQuery.isLoading
    || groupStudentsQuery.isLoading
    || submissionStudentQuery.isLoading
  ) {
    return <LoadingState />
  }

  if (
    assignmentQuery.isError
    || submissionQuery.isError
    || submissionAttachmentsQuery.isError
    || subjectScopeQuery.isError
    || assignmentSubjectQuery.isError
    || connectedGroupsQuery.isError
    || groupStudentsQuery.isError
    || submissionStudentQuery.isError
    || !assignmentQuery.data
    || !submissionQuery.data
  ) {
    return (
      <ErrorState
        description={getLocalizedRequestErrorMessage(
          assignmentQuery.error
            ?? submissionQuery.error
            ?? submissionAttachmentsQuery.error
            ?? subjectScopeQuery.error
            ?? assignmentSubjectQuery.error
            ?? connectedGroupsQuery.error
            ?? groupStudentsQuery.error
            ?? submissionStudentQuery.error,
          t,
        )}
        title={t('assignments.submissionReview')}
      />
    )
  }

  const assignment = assignmentQuery.data
  const submission = submissionQuery.data
  const submissionStudent = submissionStudentQuery.data
  const effectiveScore = gradeDraft.submissionId === submission.id ? gradeDraft.score : (submission.score ?? 0)
  const effectiveFeedback = gradeDraft.submissionId === submission.id ? gradeDraft.feedback : (submission.feedback ?? '')
  const isLate = new Date(submission.submittedAt).getTime() > new Date(assignment.deadline).getTime()

  return (
    <div className="space-y-6">
      <Breadcrumbs
        items={assignmentSubjectQuery.data
          ? [
              { label: t('navigation.shared.subjects'), to: '/subjects' },
              { label: assignmentSubjectQuery.data.name, to: `/subjects/${assignmentSubjectQuery.data.id}` },
              { label: t('navigation.shared.assignments'), to: `/subjects/${assignmentSubjectQuery.data.id}?tab=assignments` },
              { label: assignment.title, to: `/assignments/${assignment.id}` },
              { label: t('assignments.submissions') },
            ]
          : [
              { label: t('navigation.shared.assignments'), to: '/assignments' },
              { label: assignment.title, to: `/assignments/${assignment.id}` },
              { label: t('assignments.submissions') },
            ]}
      />

      <Card className="space-y-4 rounded-2xl border border-border-strong bg-surface p-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="space-y-2">
            <h1 className="text-2xl font-bold tracking-[-0.03em] text-text-primary">
              {t('assignments.submissionReviewTitle', {
                student: submissionStudent?.username ?? t('education.unknownStudent'),
              })}
            </h1>
            <div className="grid gap-2 text-sm text-text-secondary sm:grid-cols-2">
              <p>{t('testing.student')}: <span className="font-medium text-text-primary">{submissionStudent?.username ?? t('education.unknownStudent')}</span></p>
              <p>{t('education.subjectGroupsLabel')}: <span className="font-medium text-text-primary">{studentGroups.join(', ') || '—'}</span></p>
              <p>{t('assignments.submittedAt')}: <span className="font-medium text-text-primary">{formatDateTime(submission.submittedAt)}</span></p>
              <p>{t('assignments.deadline')}: <span className="font-medium text-text-primary">{formatDateTime(assignment.deadline)}</span></p>
              <p>{t('common.labels.status')}: <span className="font-medium text-text-primary">{isLate ? t('assignments.lateSubmission') : t('assignments.onTimeSubmission')}</span></p>
              <p>{t('common.labels.score')}: <span className="font-medium text-text-primary">{submission.score ?? 0}/{assignment.maxPoints}</span></p>
            </div>
          </div>
          <Link to={`/assignments/${assignmentId}?tab=submissions`}>
            <Button variant="secondary">{t('assignments.backToSubmissions')}</Button>
          </Link>
        </div>
      </Card>

      {attachmentItems.length === 0 ? (
        <EmptyState description={t('assignments.noFilesAttached')} title={t('assignments.submissionFiles')} />
      ) : (
        <div className="grid gap-6 xl:grid-cols-[minmax(0,320px)_minmax(0,1fr)_320px]">
          <Card className="space-y-4 rounded-2xl border border-border bg-surface p-4">
            <PageHeader title={t('assignments.submissionFiles')} />
            <FileAttachmentList
              files={attachmentItems}
              selectedFileId={selectedAttachment?.id ?? null}
              onDownload={async (attachment) => {
                const blob = await assignmentService.downloadSubmissionAttachment(submissionId, attachment.id)
                downloadBlob(blob, attachment.originalFileName)
              }}
              onSelect={(attachment) => setSelectedAttachmentId(attachment.id)}
            />
          </Card>

          <Card className="space-y-4 rounded-2xl border border-border bg-surface p-4">
            <FilePreviewPanel
              fetchDownloadBlob={(attachment) => assignmentService.downloadSubmissionAttachment(submissionId, attachment.id)}
              fetchPreviewBlob={(attachment) => assignmentService.previewSubmissionAttachment(submissionId, attachment.id)}
              selectedFile={selectedAttachment}
              onDownload={async (attachment) => {
                const blob = await assignmentService.downloadSubmissionAttachment(submissionId, attachment.id)
                downloadBlob(blob, attachment.originalFileName)
              }}
            />
          </Card>

          <Card className="space-y-4 rounded-2xl border border-border bg-surface p-4">
            <PageHeader title={t('assignments.gradeSubmission')} />
            <FormField label={t('common.labels.score')}>
              <Input
                max={assignment.maxPoints}
                min={0}
                type="number"
                value={effectiveScore}
                onChange={(event) => {
                  setGradeDraft({
                    submissionId: submission.id,
                    score: Number(event.target.value),
                    feedback: effectiveFeedback,
                  })
                }}
              />
            </FormField>
            <FormField label={t('assignments.feedback')}>
              <Textarea
                rows={6}
                value={effectiveFeedback}
                onChange={(event) => {
                  setGradeDraft({
                    submissionId: submission.id,
                    score: effectiveScore,
                    feedback: event.target.value,
                  })
                }}
              />
            </FormField>
            <Button
              disabled={effectiveScore < 0 || effectiveScore > assignment.maxPoints || gradeMutation.isPending}
              onClick={() => gradeMutation.mutate()}
            >
              {t('assignments.saveGrade')}
            </Button>
            <div className="rounded-[12px] border border-border bg-surface-muted px-3 py-2 text-sm text-text-secondary">
              <p>{t('assignments.reviewed')}: {submission.reviewed ? t('assignments.yes') : t('assignments.no')}</p>
              <p>{t('assignments.gradedAt')}: {formatDateTime(submission.gradedAt)}</p>
            </div>
          </Card>
        </div>
      )}
    </div>
  )
}
