import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { TFunction } from 'i18next'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { UploadCloud } from 'lucide-react'

import { useAuth } from '@/features/auth/useAuth'
import { AssignmentAccessPanel } from '@/features/assignments/access/AssignmentAccessPanel'
import { FileAttachmentList, FilePreviewPanel } from '@/features/files/preview'
import type { FileAttachmentItem } from '@/features/files/preview'
import { AssignmentSubmissionReviewPage } from '@/pages/assignments/AssignmentSubmissionReviewPage'
import { assignmentService, dashboardService, educationService, fileService, userDirectoryService } from '@/shared/api/services'
import { getLocalizedRequestErrorMessage, normalizeApiError } from '@/shared/lib/api-errors'
import { downloadBlob } from '@/shared/lib/download'
import { formatDateTime } from '@/shared/lib/format'
import { toGroupOption } from '@/shared/lib/picker-options'
import { useDebouncedValue } from '@/shared/lib/useDebouncedValue'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { DataTable } from '@/shared/ui/DataTable'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { SectionTabs } from '@/shared/ui/SectionTabs'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { DeadlineBadge } from '@/widgets/common/DeadlineBadge'
import { StatusBadge } from '@/widgets/common/StatusBadge'
import { loadAccessibleGroups, loadAccessibleSubjects, loadManagedSubjects } from '@/pages/education/helpers'
import { Breadcrumbs } from '@/widgets/common/Breadcrumbs'

interface AssignmentManagementRow {
  id: string
  title: string
  status: string
  deadline: string
}

type AssignmentTab = 'overview' | 'submissions' | 'grades' | 'settings'

export function AssignmentsPage() {
  const { primaryRole } = useAuth()
  const { assignmentId, submissionId } = useParams()

  if (assignmentId && submissionId) {
    return <AssignmentSubmissionReviewPage assignmentId={assignmentId} submissionId={submissionId} />
  }

  if (assignmentId) {
    return <AssignmentDetailPage assignmentId={assignmentId} />
  }

  if (primaryRole === 'STUDENT') {
    return <StudentAssignmentsPage />
  }

  return <ManagementAssignmentsPage />
}

function StudentAssignmentsPage() {
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
        title={t('navigation.shared.assignments')}
        description={getLocalizedRequestErrorMessage(dashboardQuery.error, t)}
        onRetry={() => void dashboardQuery.refetch()}
      />
    )
  }

  return (
    <div className="space-y-6">
      <PageHeader
        description={t('assignments.studentDescription')}
        title={t('navigation.shared.assignments')}
      />
      <DataTable
        columns={[
          {
            key: 'title',
            header: t('common.labels.title'),
            render: (item) => (
              <Link className="font-medium text-accent" to={`/assignments/${item.assignmentId}`} state={{ fromPath: '/assignments' }}>
                {item.title}
              </Link>
            ),
          },
          { key: 'status', header: t('common.labels.status'), render: (item) => <StatusBadge value={item.status} /> },
          { key: 'deadline', header: t('common.labels.deadline'), render: (item) => <DeadlineBadge deadline={item.deadline} /> },
          { key: 'submitted', header: t('assignments.submitted'), render: (item) => (item.submitted ? t('assignments.yes') : t('assignments.no')) },
        ]}
        rows={dashboardQuery.data.pendingAssignments}
      />
    </div>
  )
}

function ManagementAssignmentsPage() {
  const { t } = useTranslation()
  const { primaryRole } = useAuth()
  const [query, setQuery] = useState('')
  const debouncedQuery = useDebouncedValue(query.trim(), 350)
  const isTeacher = primaryRole === 'TEACHER'

  const teacherDashboardQuery = useQuery({
    queryKey: ['dashboard', 'teacher'],
    queryFn: () => dashboardService.getTeacherDashboard(),
    enabled: isTeacher,
  })
  const adminAssignmentsQuery = useQuery({
    queryKey: ['assignments', 'search', debouncedQuery],
    queryFn: () => assignmentService.searchAssignments({ q: debouncedQuery || ' ', page: 0, size: 30 }),
    enabled: !isTeacher,
  })

  const rows: AssignmentManagementRow[] = isTeacher
    ? (teacherDashboardQuery.data?.activeAssignments ?? []).map((item) => ({
        id: item.assignmentId,
        title: item.title,
        status: item.status,
        deadline: item.deadline,
      }))
    : (adminAssignmentsQuery.data?.items ?? []).map((item) => ({
        id: item.id,
        title: item.title,
        status: item.status,
        deadline: item.deadline,
      }))

  if (
    (isTeacher && teacherDashboardQuery.isLoading)
    || (!isTeacher && adminAssignmentsQuery.isLoading)
  ) {
    return <LoadingState />
  }

  if (
    (isTeacher && teacherDashboardQuery.isError)
    || (!isTeacher && adminAssignmentsQuery.isError)
  ) {
    return (
      <ErrorState
        title={t('navigation.shared.assignments')}
        description={getLocalizedRequestErrorMessage(
          teacherDashboardQuery.error
            ?? adminAssignmentsQuery.error,
          t,
        )}
        onRetry={() => {
          void teacherDashboardQuery.refetch()
          void adminAssignmentsQuery.refetch()
        }}
      />
    )
  }

  return (
    <div className="space-y-6">
      <PageHeader
        description={t('assignments.managementDescription')}
        title={t('navigation.shared.assignments')}
      />

      <Card className="space-y-4">
        <PageHeader
          title={t('assignments.globalCreateUnavailable')}
          description={t('assignments.createFromCourseOnly')}
        />
        <div className="flex flex-wrap gap-3">
          <Link to="/subjects">
            <Button variant="secondary">{t('assignments.backToCourse')}</Button>
          </Link>
        </div>
      </Card>

      {!isTeacher ? (
        <Card className="space-y-4">
          <FormField label={t('common.labels.query')}>
            <Input value={query} onChange={(event) => setQuery(event.target.value)} />
          </FormField>
        </Card>
      ) : null}

      {rows.length === 0 ? (
        <EmptyState description={t('assignments.empty')} title={t('navigation.shared.assignments')} />
      ) : (
        <DataTable
          columns={[
            {
              key: 'title',
              header: t('common.labels.title'),
              render: (item) => (
                <Link className="font-medium text-accent" to={`/assignments/${item.id}`} state={{ fromPath: '/assignments' }}>
                  {item.title}
                </Link>
              ),
            },
            { key: 'status', header: t('common.labels.status'), render: (item) => <StatusBadge value={item.status} /> },
            { key: 'deadline', header: t('common.labels.deadline'), render: (item) => <DeadlineBadge deadline={item.deadline} /> },
          ]}
          rows={rows}
        />
      )}
    </div>
  )
}

function AssignmentDetailPage({ assignmentId }: { assignmentId: string }) {
  const { t } = useTranslation()
  const { primaryRole, session } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const isStudent = primaryRole === 'STUDENT'
  const isTeacher = primaryRole === 'TEACHER'
  const [searchParams, setSearchParams] = useSearchParams()
  const [renderNow] = useState(() => Date.now())
  const [submissionSearch, setSubmissionSearch] = useState('')
  const debouncedSubmissionSearch = useDebouncedValue(submissionSearch.trim(), 250)
  const assignmentQuery = useQuery({
    queryKey: ['assignment', assignmentId],
    queryFn: () => assignmentService.getAssignment(assignmentId),
  })
  const assignmentAttachmentsQuery = useQuery({
    queryKey: ['assignment', assignmentId, 'attachments'],
    queryFn: () => assignmentService.listAssignmentAttachments(assignmentId),
    enabled: Boolean(assignmentId),
  })
  const availabilityQuery = useQuery({
    queryKey: ['assignment', assignmentId, 'availability'],
    queryFn: () => assignmentService.getAssignmentAvailability(assignmentId),
    enabled: !isStudent,
  })
  const submissionsQuery = useQuery({
    queryKey: ['assignment', assignmentId, 'submissions'],
    queryFn: () => assignmentService.getSubmissionsByAssignment(assignmentId, { page: 0, size: 50 }),
    enabled: !isStudent,
  })
  const mySubmissionsQuery = useQuery({
    queryKey: ['assignment', assignmentId, 'my-submissions'],
    queryFn: () => assignmentService.getMySubmissionsByAssignment(assignmentId),
    enabled: isStudent,
  })
  const [upload, setUpload] = useState<File | null>(null)
  const [submissionUploadError, setSubmissionUploadError] = useState('')
  const [assignmentAttachmentUpload, setAssignmentAttachmentUpload] = useState<File | null>(null)
  const [assignmentAttachmentName, setAssignmentAttachmentName] = useState('')
  const [assignmentAttachmentUploadError, setAssignmentAttachmentUploadError] = useState('')
  const [selectedTeacherMaterialId, setSelectedTeacherMaterialId] = useState('')
  const [availabilityGroupSearch, setAvailabilityGroupSearch] = useState('')
  const debouncedAvailabilityGroupSearch = useDebouncedValue(availabilityGroupSearch.trim(), 350)
  const accessibleGroupsQuery = useQuery({
    queryKey: ['education', 'assignment-detail-groups', primaryRole, session?.user.id],
    queryFn: () => loadAccessibleGroups(primaryRole, session?.user.id ?? ''),
    enabled: Boolean(!isStudent && isTeacher && session?.user.id),
  })
  const adminGroupSearchQuery = useQuery({
    queryKey: ['education', 'assignment-detail-group-search', debouncedAvailabilityGroupSearch],
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
    queryKey: ['education', 'assignment-availability-groups', availabilityGroupIds],
    queryFn: async () => Promise.all(availabilityGroupIds.map((groupId) => educationService.getGroup(groupId))),
    enabled: !isStudent && availabilityGroupIds.length > 0,
  })
  const subjectScopeQuery = useQuery({
    queryKey: ['education', 'assignment-detail-subject-scope', primaryRole, session?.user.id],
    queryFn: () => {
      if (primaryRole === 'STUDENT' || primaryRole === 'TEACHER') {
        return loadAccessibleSubjects(primaryRole, session?.user.id ?? '')
      }
      return loadManagedSubjects()
    },
    enabled: Boolean(assignmentQuery.data && session?.user.id),
  })
  const assignmentSubjectQuery = useQuery({
    queryKey: ['education', 'assignment-detail-subject', assignmentId, assignmentQuery.data?.topicId, subjectScopeQuery.data?.length],
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
    queryKey: ['education', 'assignment-connected-groups', assignmentSubjectQuery.data?.groupIds.join(',')],
    queryFn: async () => Promise.all((assignmentSubjectQuery.data?.groupIds ?? []).map((groupId) => educationService.getGroup(groupId))),
    enabled: Boolean(!isStudent && assignmentSubjectQuery.data?.groupIds.length),
  })
  const submissionStudentIds = useMemo(
    () => Array.from(new Set((submissionsQuery.data?.items ?? []).map((submission) => submission.userId))),
    [submissionsQuery.data?.items],
  )
  const submissionStudentsQuery = useQuery({
    queryKey: ['assignment', assignmentId, 'submission-students', submissionStudentIds.join(',')],
    queryFn: () => userDirectoryService.lookup(submissionStudentIds),
    enabled: submissionStudentIds.length > 0,
  })
  const groupStudentsQuery = useQuery({
    queryKey: ['assignment', assignmentId, 'group-students', connectedGroupsQuery.data?.map((group) => group.id).join(',')],
    queryFn: async () => Promise.all(
      (connectedGroupsQuery.data ?? []).map(async (group) => ({
        group,
        students: await educationService.listGroupStudents(group.id),
      })),
    ),
    enabled: !isStudent && Boolean(connectedGroupsQuery.data?.length),
  })
  const submitMutation = useMutation({
    mutationFn: async () => {
      if (!upload) {
        return null
      }
      const storedFile = await fileService.uploadFile(upload, 'ATTACHMENT')
      return assignmentService.submitAssignment({ assignmentId, fileId: storedFile.id })
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      await queryClient.invalidateQueries({ queryKey: ['assignment', assignmentId, 'my-submissions'] })
      await queryClient.invalidateQueries({ queryKey: ['assignment', assignmentId, 'attachments'] })
      setSubmissionUploadError('')
      setUpload(null)
    },
    onError: (error) => {
      setSubmissionUploadError(getAttachmentUploadErrorMessage(error, t))
    },
  })
  const addAssignmentAttachmentMutation = useMutation({
    mutationFn: async () => {
      if (!assignmentAttachmentUpload) {
        return null
      }
      const storedFile = await fileService.uploadFile(assignmentAttachmentUpload, 'ATTACHMENT')
      return assignmentService.addAssignmentAttachment(assignmentId, {
        fileId: storedFile.id,
        displayName: assignmentAttachmentName.trim() || undefined,
      })
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['assignment', assignmentId, 'attachments'] })
      setAssignmentAttachmentUploadError('')
      setAssignmentAttachmentUpload(null)
      setAssignmentAttachmentName('')
    },
    onError: (error) => {
      setAssignmentAttachmentUploadError(getAttachmentUploadErrorMessage(error, t))
    },
  })
  const removeAssignmentAttachmentMutation = useMutation({
    mutationFn: (attachmentId: string) => assignmentService.removeAssignmentAttachment(assignmentId, attachmentId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['assignment', assignmentId, 'attachments'] })
    },
  })

  const publishMutation = useMutation({
    mutationFn: () => assignmentService.publishAssignment(assignmentId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['assignment', assignmentId] })
    },
  })
  const closeMutation = useMutation({
    mutationFn: () => assignmentService.closeAssignment(assignmentId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['assignment', assignmentId] })
      await queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })
  const reopenMutation = useMutation({
    mutationFn: () => assignmentService.reopenAssignment(assignmentId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['assignment', assignmentId] })
      await queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })
  const archiveMutation = useMutation({
    mutationFn: () => assignmentService.archiveAssignment(assignmentId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['assignment', assignmentId] })
      await queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })
  const restoreMutation = useMutation({
    mutationFn: () => assignmentService.restoreAssignment(assignmentId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['assignment', assignmentId] })
      await queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })
  const deleteMutation = useMutation({
    mutationFn: () => assignmentService.deleteAssignment(assignmentId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['assignments'] })
      window.location.assign('/assignments')
    },
  })
  const availabilityMutation = useMutation({
    mutationFn: (payload: Record<string, unknown>) =>
      assignmentService.upsertAssignmentAvailability(assignmentId, payload),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['assignment', assignmentId, 'availability'] })
      await queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      await queryClient.invalidateQueries({ queryKey: ['assignments'] })
    },
  })
  const bulkAvailabilityMutation = useMutation({
    mutationFn: (items: Record<string, unknown>[]) =>
      assignmentService.bulkUpsertAssignmentAvailability(assignmentId, items),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['assignment', assignmentId, 'availability'] })
      await queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      await queryClient.invalidateQueries({ queryKey: ['assignments'] })
    },
  })

  const assignment = assignmentQuery.data
  const assignmentAttachments = assignmentAttachmentsQuery.data ?? []
  const canSubmitAssignment = assignment?.status === 'PUBLISHED'
  const canManageAssignmentFiles = !isStudent
  const canEditAssignmentFiles = canManageAssignmentFiles && assignment?.status !== 'ARCHIVED'
  const shouldWarnAssignmentAttachmentRemoval = assignment?.status === 'PUBLISHED' || assignment?.status === 'CLOSED'
  const availabilityRows = availabilityQuery.data ?? []
  const availabilityByGroupId = new Map(availabilityRows.map((availability) => [availability.groupId, availability]))
  const submissionStudentById = new Map((submissionStudentsQuery.data ?? []).map((student) => [student.id, student]))
  const availabilityGroupCards = (connectedGroupsQuery.data?.length ? connectedGroupsQuery.data : availabilityGroupsQuery.data ?? [])
    .map((group) => ({
      group,
      availability: availabilityByGroupId.get(group.id) ?? null,
    }))
  const groupOptions = isTeacher
    ? (connectedGroupsQuery.data?.length ? connectedGroupsQuery.data : accessibleGroupsQuery.data ?? []).map((group) => toGroupOption(group))
    : (connectedGroupsQuery.data?.length ? connectedGroupsQuery.data : adminGroupSearchQuery.data?.items ?? []).map((group) => toGroupOption(group))
  const connectedGroups = connectedGroupsQuery.data ?? []
  const connectedGroupOptions = connectedGroups.map((group) => toGroupOption(group))

  const submissionGroupsByUserId = new Map<string, string[]>()
  for (const groupEntry of groupStudentsQuery.data ?? []) {
    for (const student of groupEntry.students) {
      const currentGroups = submissionGroupsByUserId.get(student.userId) ?? []
      if (!currentGroups.includes(groupEntry.group.name)) {
        currentGroups.push(groupEntry.group.name)
      }
      submissionGroupsByUserId.set(student.userId, currentGroups)
    }
  }
  const filteredSubmissions = (submissionsQuery.data?.items ?? []).filter((submission) => {
    if (!debouncedSubmissionSearch) {
      return true
    }
    const normalizedQuery = debouncedSubmissionSearch.toLowerCase()
    const studentName = submissionStudentById.get(submission.userId)?.username?.toLowerCase() ?? ''
    const groupNames = (submissionGroupsByUserId.get(submission.userId) ?? []).join(' ').toLowerCase()
    return studentName.includes(normalizedQuery) || groupNames.includes(normalizedQuery)
  })
  const groupedSubmissions = Array.from(
    filteredSubmissions.reduce((accumulator, submission) => {
      const groupNames = submissionGroupsByUserId.get(submission.userId) ?? [t('assignments.ungroupedSubmissions')]
      for (const groupName of groupNames) {
        const currentSubmissions = accumulator.get(groupName) ?? []
        currentSubmissions.push(submission)
        accumulator.set(groupName, currentSubmissions)
      }
      return accumulator
    }, new Map<string, typeof filteredSubmissions>()),
  )
  const assignmentMaterialItems: FileAttachmentItem[] = assignmentAttachments.map((attachment) => ({
    id: attachment.id,
    fileId: attachment.fileId,
    displayName: attachment.displayName,
    originalFileName: attachment.originalFileName,
    contentType: attachment.contentType,
    sizeBytes: attachment.sizeBytes,
    previewAvailable: attachment.previewAvailable,
    createdAt: attachment.createdAt,
  }))
  const selectedTeacherMaterial = assignmentMaterialItems.find((attachment) => attachment.id === selectedTeacherMaterialId)
    ?? assignmentMaterialItems[0]
    ?? null
  const backTarget = assignmentSubjectQuery.data ? `/subjects/${assignmentSubjectQuery.data.id}` : '/subjects'
  const backLabel = assignmentSubjectQuery.data
    ? t('assignments.backToCourse')
    : t('assignments.backToAssignments')
  const assignmentTabs = [
    { id: 'overview', label: t('assignments.assignmentOverview') },
    { id: 'submissions', label: t('assignments.submissions') },
    { id: 'grades', label: t('assignments.grades') },
    ...(!isStudent ? [{ id: 'settings', label: t('education.subjectTabs.settings') }] : []),
  ] as Array<{ id: AssignmentTab; label: string }>
  const activeTab = assignmentTabs.some((tab) => tab.id === searchParams.get('tab'))
    ? searchParams.get('tab') as AssignmentTab
    : 'overview'
  const changeTab = (nextTab: AssignmentTab) => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current)
      if (nextTab === 'overview') {
        next.delete('tab')
      } else {
        next.set('tab', nextTab)
      }
      return next
    }, { replace: true })
  }
  const studentLatestSubmission = (mySubmissionsQuery.data ?? [])[0] ?? null
  const assignmentDeadline = new Date(assignment?.deadline ?? renderNow)
  const deadlineDeltaMs = assignmentDeadline.getTime() - renderNow
  const isOverdue = deadlineDeltaMs < 0 || assignment?.status === 'CLOSED' || assignment?.status === 'ARCHIVED'
  const deadlineDays = Math.abs(Math.ceil(deadlineDeltaMs / (1000 * 60 * 60 * 24)))
  const deadlineLabel = isOverdue
    ? t('assignments.overdueByDays', { count: deadlineDays })
    : t('assignments.daysLeft', { count: deadlineDays })

  if (
    assignmentQuery.isLoading
    || assignmentAttachmentsQuery.isLoading
    || (isStudent && mySubmissionsQuery.isLoading)
    || (!isStudent && (
      submissionsQuery.isLoading
      || availabilityQuery.isLoading
      || subjectScopeQuery.isLoading
      || assignmentSubjectQuery.isLoading
      || connectedGroupsQuery.isLoading
      || submissionStudentsQuery.isLoading
      || groupStudentsQuery.isLoading
    ))
  ) {
    return <LoadingState />
  }

  if (
    assignmentQuery.isError
    || assignmentAttachmentsQuery.isError
    || !assignment
    || (isStudent && mySubmissionsQuery.isError)
    || (!isStudent && (
      submissionsQuery.isError
      || availabilityQuery.isError
      || subjectScopeQuery.isError
      || assignmentSubjectQuery.isError
      || connectedGroupsQuery.isError
      || submissionStudentsQuery.isError
      || groupStudentsQuery.isError
    ))
  ) {
    const assignmentError = normalizeApiError(assignmentQuery.error)
    if (isStudent && assignmentError && (assignmentError.status === 403 || assignmentError.status === 404)) {
      const requestId = assignmentError.requestId
      const message = requestId
        ? `${t('assignments.notAvailableForGroup')} ${t('common.labels.requestId')}: ${requestId}`
        : t('assignments.notAvailableForGroup')
      return (
        <ErrorState
          title={t('navigation.shared.assignments')}
          description={message}
        />
      )
    }

    return (
      <ErrorState
        title={t('navigation.shared.assignments')}
        description={getLocalizedRequestErrorMessage(
          assignmentQuery.error
            ?? submissionsQuery.error
            ?? availabilityQuery.error
            ?? subjectScopeQuery.error
            ?? assignmentSubjectQuery.error
            ?? connectedGroupsQuery.error
            ?? submissionStudentsQuery.error
            ?? mySubmissionsQuery.error
            ?? groupStudentsQuery.error
            ?? assignmentAttachmentsQuery.error,
          t,
        )}
        onRetry={() => {
          void assignmentQuery.refetch()
          void submissionsQuery.refetch()
          void availabilityQuery.refetch()
        }}
      />
    )
  }

  return (
    <div className="space-y-6">
      <Breadcrumbs
        items={assignmentSubjectQuery.data
          ? [
              { label: t('navigation.shared.subjects'), to: '/subjects' },
              { label: assignmentSubjectQuery.data.name, to: `/subjects/${assignmentSubjectQuery.data.id}` },
              { label: t('navigation.shared.assignments'), to: `/subjects/${assignmentSubjectQuery.data.id}?tab=assignments` },
              { label: assignment.title },
            ]
          : [{ label: t('navigation.shared.assignments'), to: '/assignments' }, { label: assignment.title }]}
      />

      <Card className="space-y-4 rounded-2xl border border-border-strong bg-surface p-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="space-y-2">
            <div className="flex flex-wrap items-center gap-2">
              <StatusBadge value={assignment.status} />
              <DeadlineBadge deadline={assignment.deadline} />
            </div>
            <h1 className="text-2xl font-bold tracking-[-0.03em] text-text-primary">{assignment.title}</h1>
            <p className="text-sm text-text-secondary">{assignment.description ?? t('assignments.descriptionFallback')}</p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button
              variant="secondary"
              onClick={() => {
                const fromPath = (location.state as { fromPath?: string } | null)?.fromPath
                if (fromPath && fromPath !== location.pathname) {
                  navigate(fromPath)
                  return
                }
                if (window.history.length > 1) {
                  navigate(-1)
                  return
                }
                navigate(backTarget)
              }}
            >
              {backLabel}
            </Button>
            {isStudent ? (
              <Button
                disabled={!canSubmitAssignment}
                onClick={() => changeTab('submissions')}
              >
                {studentLatestSubmission ? t('assignments.continueSubmission') : t('assignments.openAssignment')}
              </Button>
            ) : (
              <>
                {assignment.status === 'DRAFT' ? (
                  <Button disabled={publishMutation.isPending} variant="secondary" onClick={() => publishMutation.mutate()}>
                    {t('common.actions.publish')}
                  </Button>
                ) : null}
                {assignment.status === 'PUBLISHED' ? (
                  <Button disabled={closeMutation.isPending} variant="secondary" onClick={() => closeMutation.mutate()}>
                    {t('assignments.close')}
                  </Button>
                ) : null}
                {assignment.status === 'CLOSED' ? (
                  <Button disabled={reopenMutation.isPending} variant="secondary" onClick={() => reopenMutation.mutate()}>
                    {t('assignments.reopen')}
                  </Button>
                ) : null}
                {assignment.status !== 'ARCHIVED' ? (
                  <Button disabled={archiveMutation.isPending} onClick={() => archiveMutation.mutate()}>
                    {t('assignments.archive')}
                  </Button>
                ) : null}
                {assignment.status === 'ARCHIVED' ? (
                  <>
                    <Button disabled={restoreMutation.isPending} variant="secondary" onClick={() => restoreMutation.mutate()}>
                      {t('assignments.restore')}
                    </Button>
                    <Button
                      disabled={deleteMutation.isPending}
                      variant="ghost"
                      onClick={() => {
                        if (window.confirm(t('assignments.deletePermanentConfirm'))) {
                          deleteMutation.mutate()
                        }
                      }}
                    >
                      {t('assignments.deletePermanently')}
                    </Button>
                  </>
                ) : null}
              </>
            )}
          </div>
        </div>
      </Card>

      <SectionTabs
        activeId={activeTab}
        items={assignmentTabs}
        onChange={(tabId) => changeTab(tabId as AssignmentTab)}
      />

      {activeTab === 'overview' ? (
        <div className="grid gap-6 2xl:grid-cols-[minmax(0,1fr)_320px]">
          <div className="space-y-4">
            <Card className="space-y-3 rounded-2xl border border-border bg-surface p-5">
              <PageHeader title={t('assignments.assignmentDescription')} />
              <p className="text-sm leading-6 text-text-secondary">{assignment.description ?? t('assignments.descriptionFallback')}</p>
            </Card>

            <Card className="space-y-4 rounded-2xl border border-border bg-surface p-5">
              <PageHeader title={t('assignments.teacherMaterials')} />
              <div className="grid gap-4 xl:grid-cols-[minmax(0,360px)_minmax(0,1fr)]">
                <FileAttachmentList
                  canRemove={canManageAssignmentFiles}
                  collapsible
                  defaultExpanded
                  emptyMessage={t('assignments.noFilesAttached')}
                  files={assignmentMaterialItems}
                  removeDisabled={!canEditAssignmentFiles || removeAssignmentAttachmentMutation.isPending}
                  selectedFileId={selectedTeacherMaterial?.id ?? null}
                  onDownload={async (attachment) => {
                    const blob = await assignmentService.downloadAssignmentAttachment(assignmentId, attachment.id)
                    downloadBlob(blob, attachment.originalFileName)
                  }}
                  onRemove={(attachment) => {
                    if (
                      shouldWarnAssignmentAttachmentRemoval
                      && !window.confirm(t('assignments.removeAssignmentFileWarning'))
                    ) {
                      return
                    }
                    removeAssignmentAttachmentMutation.mutate(attachment.id)
                  }}
                  onSelect={(attachment) => setSelectedTeacherMaterialId(attachment.id)}
                />
                <FilePreviewPanel
                  fetchDownloadBlob={(attachment) => assignmentService.downloadAssignmentAttachment(assignmentId, attachment.id)}
                  fetchPreviewBlob={(attachment) => assignmentService.previewAssignmentAttachment(assignmentId, attachment.id)}
                  selectedFile={selectedTeacherMaterial}
                  title={t('assignments.filePreview')}
                  onDownload={async (attachment) => {
                    const blob = await assignmentService.downloadAssignmentAttachment(assignmentId, attachment.id)
                    downloadBlob(blob, attachment.originalFileName)
                  }}
                />
              </div>
              {canManageAssignmentFiles ? (
                <div className="grid gap-3 rounded-[14px] border border-border bg-surface-muted p-3 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto]">
                  <Input
                    disabled={!canEditAssignmentFiles}
                    placeholder={t('assignments.fileName')}
                    value={assignmentAttachmentName}
                    onChange={(event) => setAssignmentAttachmentName(event.target.value)}
                  />
                  <Input
                    disabled={!canEditAssignmentFiles}
                    type="file"
                    onChange={(event) => {
                      setAssignmentAttachmentUpload(event.target.files?.[0] ?? null)
                      setAssignmentAttachmentUploadError('')
                    }}
                  />
                  <p className="text-xs text-text-muted">{t('files.allowedFileTypesHint')}</p>
                  <Button
                    disabled={!canEditAssignmentFiles || !assignmentAttachmentUpload || addAssignmentAttachmentMutation.isPending}
                    onClick={() => addAssignmentAttachmentMutation.mutate()}
                  >
                    <UploadCloud className="mr-2 h-4 w-4" />
                    {t('assignments.addFile')}
                  </Button>
                </div>
              ) : null}
              {assignmentAttachmentUploadError ? (
                <p className="text-sm text-danger">{assignmentAttachmentUploadError}</p>
              ) : null}
            </Card>

            <Card className="space-y-3 rounded-2xl border border-border bg-surface p-5">
              <PageHeader title={t('assignments.gradingCriteria')} />
              <div className="grid gap-3 text-sm text-text-secondary md:grid-cols-2">
                <p>{t('assignments.maxPoints')}: <span className="font-semibold text-text-primary">{assignment.maxPoints}</span></p>
                <p>{t('assignments.allowedSubmissions')}: <span className="font-semibold text-text-primary">{assignment.maxSubmissions}</span></p>
                <p>{t('assignments.lateSubmissions')}: <span className="font-semibold text-text-primary">{assignment.allowLateSubmissions ? t('assignments.yes') : t('assignments.no')}</span></p>
                <p>{t('assignments.maxFileSize')}: <span className="font-semibold text-text-primary">{assignment.maxFileSizeMb ?? '-'}</span></p>
              </div>
              <p className="text-sm text-text-secondary">
                {t('assignments.acceptedFileTypes')}: {(assignment.acceptedFileTypes ?? []).join(', ') || t('files.allowedFileTypesHint')}
              </p>
            </Card>

            {isStudent ? (
              <Card className="space-y-3 rounded-2xl border border-border bg-surface p-5">
                <PageHeader title={t('assignments.mySubmission')} />
                {studentLatestSubmission ? (
                  <div className="space-y-2 rounded-[12px] border border-border bg-surface-muted p-3">
                    <p className="text-sm text-text-secondary">{formatDateTime(studentLatestSubmission.submittedAt)}</p>
                    <p className="text-sm font-semibold text-text-primary">
                      {studentLatestSubmission.reviewed ? t('assignments.reviewed') : t('assignments.pendingReview')}
                    </p>
                    {studentLatestSubmission.score != null ? (
                      <p className="text-sm text-text-secondary">{t('common.labels.score')}: {studentLatestSubmission.score}/{assignment.maxPoints}</p>
                    ) : null}
                    {studentLatestSubmission.feedback ? (
                      <p className="text-sm text-text-secondary">{studentLatestSubmission.feedback}</p>
                    ) : null}
                  </div>
                ) : (
                  <p className="text-sm text-text-secondary">{t('assignments.noSubmissionYet')}</p>
                )}
              </Card>
            ) : null}
          </div>

          <div className="space-y-4">
            <Card className="space-y-3 rounded-2xl border border-border bg-surface p-5">
              <PageHeader title={t('assignments.details')} />
              <SidebarInfo label={t('assignments.type')} value={t('dashboard.deadlineType.ASSIGNMENT')} />
              <SidebarInfo label={t('assignments.maxPoints')} value={`${assignment.maxPoints}`} />
              <SidebarInfo label={t('assignments.allowedSubmissions')} value={`${assignment.maxSubmissions}`} />
              <SidebarInfo label={t('assignments.lateSubmissions')} value={assignment.allowLateSubmissions ? t('assignments.yes') : t('assignments.no')} />
              <SidebarInfo label={t('common.labels.status')} value={assignment.status} />
              <SidebarInfo label={t('education.subjectGroupsLabel')} value={`${assignmentSubjectQuery.data?.groupIds.length ?? 0}`} />
            </Card>
            <Card className="space-y-3 rounded-2xl border border-border bg-surface p-5">
              <PageHeader title={t('assignments.deadline')} />
              <p className="text-sm font-semibold text-text-primary">{formatDateTime(assignment.deadline)}</p>
              <p className={`text-sm font-semibold ${isOverdue ? 'text-danger' : 'text-warning'}`}>{deadlineLabel}</p>
            </Card>
            <Card className="space-y-3 rounded-2xl border border-border bg-surface p-5">
              <PageHeader title={t('assignments.progress')} />
              {isStudent ? (
                <>
                  <SidebarInfo label={t('assignments.submissions')} value={`${(mySubmissionsQuery.data ?? []).length}`} />
                  <SidebarInfo label={t('assignments.grades')} value={studentLatestSubmission?.score == null ? '-' : `${studentLatestSubmission.score}/${assignment.maxPoints}`} />
                </>
              ) : (
                <>
                  <SidebarInfo label={t('assignments.submissions')} value={`${submissionsQuery.data?.items.length ?? 0}`} />
                  <SidebarInfo label={t('assignments.gradedSubmissions')} value={`${(submissionsQuery.data?.items ?? []).filter((submission) => submission.reviewed).length}`} />
                </>
              )}
            </Card>
            {!isStudent ? (
              <Card className="space-y-3 rounded-2xl border border-border bg-surface p-5">
                <PageHeader title={t('assignments.groupAvailability')} />
                {availabilityGroupCards.length === 0 ? (
                  <p className="text-sm text-text-secondary">{t('availability.noConnectedGroups')}</p>
                ) : (
                  <div className="space-y-2">
                    {availabilityGroupCards.slice(0, 4).map(({ availability, group }) => (
                      <div key={group.id} className="rounded-[12px] border border-border bg-surface-muted px-3 py-2">
                        <p className="text-sm font-semibold text-text-primary">{group.name}</p>
                        <p className="text-xs text-text-muted">
                          {availability?.visible ? t('availability.visible') : t('availability.hidden')} · {formatDateTime(availability?.deadline ?? assignment.deadline)}
                        </p>
                      </div>
                    ))}
                  </div>
                )}
              </Card>
            ) : null}
          </div>
        </div>
      ) : null}

      {activeTab === 'submissions' ? (
        isStudent ? (
          <Card className="space-y-4 rounded-2xl border border-border bg-surface p-5">
            <PageHeader title={t('assignments.mySubmission')} />
            {canSubmitAssignment ? null : (
              <div className="rounded-[14px] border border-warning/30 bg-warning/5 px-4 py-3">
                <p className="text-sm font-semibold text-warning">{t('assignments.submissionClosed')}</p>
              </div>
            )}
            <FormField label={t('assignments.uploadSubmission')}>
              <Input
                type="file"
                onChange={(event) => {
                  setUpload(event.target.files?.[0] ?? null)
                  setSubmissionUploadError('')
                }}
              />
              <p className="text-xs text-text-muted">{t('files.allowedFileTypesHint')}</p>
            </FormField>
            {submissionUploadError ? (
              <p className="text-sm text-danger">{submissionUploadError}</p>
            ) : null}
            <Button disabled={!upload || submitMutation.isPending || !canSubmitAssignment} onClick={() => submitMutation.mutate()}>
              {t('assignments.submitAssignment')}
            </Button>
            {(mySubmissionsQuery.data ?? []).length === 0 ? (
              <EmptyState description={t('assignments.noSubmissionYet')} title={t('assignments.mySubmissions')} />
            ) : (
              <div className="space-y-3">
                {(mySubmissionsQuery.data ?? []).map((submission) => (
                  <div key={submission.id} className="rounded-[14px] border border-border bg-surface-muted px-4 py-3">
                    <div className="flex flex-wrap items-center justify-between gap-3">
                      <p className="font-semibold text-text-primary">{formatDateTime(submission.submittedAt)}</p>
                      <span className={submission.reviewed ? 'rounded-full bg-success/10 px-2.5 py-1 text-xs font-semibold text-success' : 'rounded-full bg-warning/10 px-2.5 py-1 text-xs font-semibold text-warning'}>
                        {submission.reviewed ? t('assignments.reviewed') : t('assignments.pendingReview')}
                      </span>
                    </div>
                    {submission.score !== null ? (
                      <p className="mt-1 text-sm text-text-secondary">{t('common.labels.score')}: {submission.score}/{assignment.maxPoints}</p>
                    ) : null}
                  </div>
                ))}
              </div>
            )}
          </Card>
        ) : (
          <Card className="space-y-4 rounded-2xl border border-border bg-surface p-5">
            <PageHeader description={t('assignments.submissionsDescription')} title={t('assignments.submissions')} />
            <FormField label={t('common.actions.search')}>
              <Input value={submissionSearch} onChange={(event) => setSubmissionSearch(event.target.value)} />
            </FormField>
            {groupedSubmissions.length === 0 ? (
              <EmptyState description={t('assignments.noSubmissionsToGrade')} title={t('assignments.submissions')} />
            ) : (
              <div className="space-y-4">
                {groupedSubmissions.map(([groupName, submissions]) => (
                  <div key={groupName} className="space-y-3">
                    <h3 className="text-sm font-semibold uppercase tracking-[0.12em] text-text-muted">{groupName}</h3>
                    <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
                      {submissions.map((submission) => (
                        <div
                          key={submission.id}
                          className="space-y-3 rounded-[14px] border border-border bg-surface-muted px-4 py-3"
                        >
                          <p className="font-semibold text-text-primary">
                            {submissionStudentById.get(submission.userId)?.username ?? t('education.unknownStudent')}
                          </p>
                          <p className="text-sm text-text-secondary">{formatDateTime(submission.submittedAt)}</p>
                          <p className="text-xs text-text-muted">
                            {submission.reviewed ? t('assignments.reviewed') : t('assignments.pendingReview')}
                          </p>
                          <Link to={`/assignments/${assignmentId}/submissions/${submission.id}/review`}>
                            <Button fullWidth variant="secondary">{t('assignments.openSubmissionReview')}</Button>
                          </Link>
                        </div>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Card>
        )
      ) : null}

      {activeTab === 'grades' ? (
        isStudent ? (
          <Card className="space-y-4 rounded-2xl border border-border bg-surface p-5">
            <PageHeader title={t('assignments.grades')} />
            {(mySubmissionsQuery.data ?? []).filter((submission) => submission.reviewed).length === 0 ? (
              <EmptyState description={t('assignments.noGradesYet')} title={t('assignments.grades')} />
            ) : (
              <div className="space-y-2">
                {(mySubmissionsQuery.data ?? [])
                  .filter((submission) => submission.reviewed)
                  .map((submission) => (
                    <div key={submission.id} className="rounded-[12px] border border-border bg-surface-muted px-4 py-3">
                      <p className="text-sm font-semibold text-text-primary">{formatDateTime(submission.submittedAt)}</p>
                      <p className="text-sm text-text-secondary">{t('common.labels.score')}: {submission.score ?? '-'} / {assignment.maxPoints}</p>
                      {submission.feedback ? <p className="mt-1 text-sm text-text-secondary">{submission.feedback}</p> : null}
                    </div>
                  ))}
              </div>
            )}
          </Card>
        ) : (
          <Card className="space-y-4 rounded-2xl border border-border bg-surface p-5">
            <PageHeader title={t('assignments.grades')} />
            <DataTable
              columns={[
                {
                  key: 'student',
                  header: t('testing.student'),
                  render: (submission) => submissionStudentById.get(submission.userId)?.username ?? t('education.unknownStudent'),
                },
                {
                  key: 'submittedAt',
                  header: t('assignments.submittedAt'),
                  render: (submission) => formatDateTime(submission.submittedAt),
                },
                {
                  key: 'score',
                  header: t('common.labels.score'),
                  render: (submission) => submission.score == null ? '-' : `${submission.score}/${assignment.maxPoints}`,
                },
                {
                  key: 'reviewed',
                  header: t('assignments.reviewed'),
                  render: (submission) => submission.reviewed ? t('assignments.yes') : t('assignments.no'),
                },
              ]}
              rows={submissionsQuery.data?.items ?? []}
            />
          </Card>
        )
      ) : null}

      {activeTab === 'settings' && !isStudent ? (
        <Card className="space-y-4 rounded-2xl border border-border bg-surface p-5">
          <PageHeader title={t('assignments.manageAvailability')} />
          <details className="rounded-[14px] border border-border bg-surface-muted p-3">
            <summary className="cursor-pointer text-sm font-semibold text-text-primary">{t('assignments.bulkAvailability')}</summary>
            <div className="mt-3">
              <AssignmentAccessPanel
                assignment={assignment}
                availabilityGroupCards={availabilityGroupCards}
                availabilityRows={availabilityRows}
                connectedGroupOptions={connectedGroupOptions}
                connectedGroups={connectedGroups}
                groupOptions={groupOptions}
                isBulkSaving={bulkAvailabilityMutation.isPending}
                isSaving={availabilityMutation.isPending}
                isTeacher={isTeacher}
                searchValue={availabilityGroupSearch}
                onBulkUpsertAvailability={(items) => bulkAvailabilityMutation.mutateAsync(items)}
                onSearchChange={setAvailabilityGroupSearch}
                onUpsertAvailability={(payload) => availabilityMutation.mutateAsync(payload)}
              />
            </div>
          </details>
        </Card>
      ) : null}
    </div>
  )
}

function getAttachmentUploadErrorMessage(error: unknown, t: TFunction) {
  const normalized = normalizeApiError(error)
  if (normalized?.status === 415) {
    return t('files.unsupportedUploadType')
  }

  return t('files.uploadFailed')
}

function SidebarInfo({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-[12px] border border-border bg-surface-muted px-3 py-2">
      <p className="text-xs font-semibold uppercase tracking-[0.14em] text-text-muted">{label}</p>
      <p className="mt-1 text-sm font-medium text-text-primary">{value}</p>
    </div>
  )
}
