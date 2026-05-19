import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'

import { AssignmentSubmissionReviewPage } from '@/pages/assignments/AssignmentSubmissionReviewPage'
import { assignmentService, userDirectoryService } from '@/shared/api/services'
import { getLocalizedRequestErrorMessage } from '@/shared/lib/api-errors'
import { formatDateTime } from '@/shared/lib/format'
import type { AssignmentResponse } from '@/shared/types/api'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { DataTable } from '@/shared/ui/DataTable'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { Select } from '@/shared/ui/Select'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { UserAvatar } from '@/shared/ui/UserAvatar'
import { StatusBadge } from '@/widgets/common/StatusBadge'
import { Breadcrumbs } from '@/widgets/common/Breadcrumbs'

const STATUS_FILTERS = ['SUBMITTED', 'RESUBMITTED', 'RETURNED_FOR_REVISION', 'GRADED']

export function TeacherSubmissionsPage() {
  const { submissionId } = useParams()

  if (submissionId) {
    return <AssignmentSubmissionReviewPage submissionId={submissionId} />
  }

  return <TeacherSubmissionJournal />
}

function TeacherSubmissionJournal() {
  const { t } = useTranslation()
  const [assignmentId, setAssignmentId] = useState('')
  const [status, setStatus] = useState('')
  const [search, setSearch] = useState('')

  const reviewQueueQuery = useQuery({
    queryKey: ['review-queue', assignmentId, status],
    queryFn: () => assignmentService.getReviewQueue({
      page: 0,
      size: 100,
      assignmentId: assignmentId || undefined,
      status: status || undefined,
    }),
  })

  const assignmentIds = useMemo(
    () => Array.from(new Set((reviewQueueQuery.data?.items ?? []).map((submission) => submission.assignmentId))),
    [reviewQueueQuery.data?.items],
  )
  const studentIds = useMemo(
    () => Array.from(new Set((reviewQueueQuery.data?.items ?? []).map((submission) => submission.userId))),
    [reviewQueueQuery.data?.items],
  )

  const assignmentsQuery = useQuery({
    queryKey: ['review-queue', 'assignments', assignmentIds.join(',')],
    queryFn: async () => {
      const items = await Promise.all(assignmentIds.map(async (id) => {
        try {
          return await assignmentService.getAssignment(id)
        } catch {
          return null
        }
      }))
      return items.filter((assignment): assignment is AssignmentResponse => assignment !== null)
    },
    enabled: assignmentIds.length > 0,
  })

  const studentsQuery = useQuery({
    queryKey: ['review-queue', 'students', studentIds.join(',')],
    queryFn: () => userDirectoryService.lookup(studentIds),
    enabled: studentIds.length > 0,
  })

  const assignmentById = useMemo(
    () => new Map((assignmentsQuery.data ?? []).map((assignment) => [assignment.id, assignment])),
    [assignmentsQuery.data],
  )
  const studentById = useMemo(
    () => new Map((studentsQuery.data ?? []).map((student) => [student.id, student])),
    [studentsQuery.data],
  )

  const rows = useMemo(() => {
    const normalizedSearch = search.trim().toLowerCase()
    return (reviewQueueQuery.data?.items ?? []).filter((submission) => {
      if (!normalizedSearch) {
        return true
      }
      const student = studentById.get(submission.userId)
      const assignment = assignmentById.get(submission.assignmentId)
      return [
        student?.username,
        student?.email,
        assignment?.title,
      ]
        .filter(Boolean)
        .some((value) => value!.toLowerCase().includes(normalizedSearch))
    })
  }, [assignmentById, reviewQueueQuery.data?.items, search, studentById])

  const assignmentOptions = useMemo(
    () => (assignmentsQuery.data ?? []).map((assignment) => ({
      value: assignment.id,
      label: assignment.title,
    })),
    [assignmentsQuery.data],
  )

  if (reviewQueueQuery.isLoading || assignmentsQuery.isLoading || studentsQuery.isLoading) {
    return <LoadingState />
  }

  if (reviewQueueQuery.isError || assignmentsQuery.isError || studentsQuery.isError) {
    return (
      <ErrorState
        title={t('assignments.submissionJournalTitle')}
        description={getLocalizedRequestErrorMessage(
          reviewQueueQuery.error ?? assignmentsQuery.error ?? studentsQuery.error,
          t,
        )}
      />
    )
  }

  return (
    <div className="space-y-6">
      <Breadcrumbs items={[{ label: t('navigation.shared.review') }]} />

      <Card className="space-y-5 rounded-[28px] border border-border-strong bg-surface p-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="space-y-2">
            <p className="text-xs font-semibold uppercase tracking-[0.22em] text-accent/80">
              {t('assignments.reviewQueueLabel')}
            </p>
            <h1 className="text-3xl font-bold tracking-[-0.03em] text-text-primary">
              {t('assignments.submissionJournalTitle')}
            </h1>
            <p className="max-w-3xl text-sm leading-6 text-text-secondary">
              {t('assignments.submissionJournalDescription')}
            </p>
          </div>
          <div className="rounded-[18px] border border-border bg-surface-muted px-4 py-3 text-right">
            <p className="text-xs uppercase tracking-[0.18em] text-text-muted">
              {t('assignments.activeSubmissionStates')}
            </p>
            <p className="mt-1 text-2xl font-semibold text-text-primary">{rows.length}</p>
          </div>
        </div>

        <div className="grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(0,0.8fr)_minmax(0,0.8fr)]">
          <FormField label={t('common.actions.search')}>
            <Input value={search} placeholder={t('assignments.searchStudentOrWork')} onChange={(event) => setSearch(event.target.value)} />
          </FormField>
          <FormField label={t('assignments.workFilter')}>
            <Select value={assignmentId} onChange={(event) => setAssignmentId(event.target.value)}>
              <option value="">{t('assignments.allWorks')}</option>
              {assignmentOptions.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </Select>
          </FormField>
          <FormField label={t('common.labels.status')}>
            <Select value={status} onChange={(event) => setStatus(event.target.value)}>
              <option value="">{t('assignments.allStatuses')}</option>
              {STATUS_FILTERS.map((value) => (
                <option key={value} value={value}>{t(`common.status.${value}`)}</option>
              ))}
            </Select>
          </FormField>
        </div>
      </Card>

      {rows.length === 0 ? (
        <EmptyState
          title={t('assignments.submissionJournalTitle')}
          description={t('assignments.noSubmissionsToReview')}
        />
      ) : (
        <Card className="space-y-4 rounded-[28px] border border-border-strong bg-surface p-4">
          <PageHeader title={t('assignments.submissionOverview')} />
          <DataTable
            columns={[
              {
                key: 'student',
                header: t('testing.student'),
                render: (submission) => {
                  const student = studentById.get(submission.userId)
                  return (
                    <div className="flex items-center gap-3">
                      <UserAvatar email={student?.email} size="sm" username={student?.username} />
                      <div className="min-w-0">
                        <p className="truncate font-semibold text-text-primary">
                          {student?.username ?? t('education.unknownStudent')}
                        </p>
                        <p className="truncate text-xs text-text-muted">{student?.email ?? '—'}</p>
                      </div>
                    </div>
                  )
                },
              },
              {
                key: 'assignment',
                header: t('assignments.work'),
                render: (submission) => (
                  <div className="min-w-[220px]">
                    <p className="font-semibold text-text-primary">
                      {assignmentById.get(submission.assignmentId)?.title ?? t('dashboard.assignment')}
                    </p>
                    <p className="text-xs text-text-muted">
                      {formatDateTime(assignmentById.get(submission.assignmentId)?.deadline ?? null)}
                    </p>
                  </div>
                ),
              },
              {
                key: 'status',
                header: t('common.labels.status'),
                render: (submission) => (
                  <div className="space-y-2">
                    <StatusBadge value={submission.status} />
                    {submission.submittedLate ? (
                      <p className="text-xs font-medium text-danger">{t('assignments.lateSubmission')}</p>
                    ) : null}
                  </div>
                ),
              },
              {
                key: 'attempt',
                header: t('assignments.attempt'),
                render: (submission) => (
                  <span className="font-medium text-text-primary">
                    {submission.attemptNumber}/{submission.maxAttempts}
                  </span>
                ),
              },
              {
                key: 'grade',
                header: t('common.labels.score'),
                render: (submission) => submission.score == null
                  ? '—'
                  : `${submission.score}/${assignmentById.get(submission.assignmentId)?.maxPoints ?? '—'}`,
              },
              {
                key: 'deadline',
                header: t('common.labels.deadline'),
                render: (submission) => {
                  const assignment = assignmentById.get(submission.assignmentId)
                  const value = submission.revisionDeadline ?? assignment?.deadline ?? null
                  return (
                    <div>
                      <p>{formatDateTime(value)}</p>
                      {submission.revisionDeadline ? (
                        <p className="text-xs text-accent">{t('assignments.newDeadlineLabel')}</p>
                      ) : null}
                    </div>
                  )
                },
              },
              {
                key: 'submittedAt',
                header: t('assignments.submittedAt'),
                render: (submission) => formatDateTime(submission.submittedAt),
              },
              {
                key: 'actions',
                header: t('common.actions.open'),
                render: (submission) => (
                  <Link to={`/assignments/${submission.assignmentId}/submissions/${submission.id}/review`}>
                    <Button variant="secondary">{t('assignments.openSubmissionReview')}</Button>
                  </Link>
                ),
              },
            ]}
            rows={rows}
          />

          <div className="flex flex-wrap gap-2 rounded-[18px] border border-border bg-surface-muted px-4 py-3 text-xs text-text-secondary">
            {STATUS_FILTERS.map((value) => (
              <div key={value} className="flex items-center gap-2">
                <StatusBadge value={value} />
                <span>{t(`common.status.${value}`)}</span>
              </div>
            ))}
          </div>
        </Card>
      )}
    </div>
  )
}
