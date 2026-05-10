import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { convertToHtml } from 'mammoth/mammoth.browser'

import { useAuth } from '@/features/auth/useAuth'
import { AssignmentAccessPanel } from '@/features/assignments/access/AssignmentAccessPanel'
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

export function AssignmentsPage() {
  const { primaryRole } = useAuth()
  const { assignmentId } = useParams()

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
  const [submissionSearch, setSubmissionSearch] = useState('')
  const debouncedSubmissionSearch = useDebouncedValue(submissionSearch.trim(), 250)
  const [selectedSubmissionId, setSelectedSubmissionId] = useState('')
  const requestedSubmissionId = searchParams.get('submissionId') ?? ''
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
  const [assignmentAttachmentUpload, setAssignmentAttachmentUpload] = useState<File | null>(null)
  const [assignmentAttachmentName, setAssignmentAttachmentName] = useState('')
  const [submissionAttachmentUpload, setSubmissionAttachmentUpload] = useState<File | null>(null)
  const [submissionAttachmentName, setSubmissionAttachmentName] = useState('')
  const [selectedSubmissionAttachmentId, setSelectedSubmissionAttachmentId] = useState('')
  const [gradeDraftBySubmissionId, setGradeDraftBySubmissionId] = useState<Record<string, { score: number; feedback: string }>>({})
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
  const latestStudentSubmissionId = mySubmissionsQuery.data?.[0]?.id ?? ''
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
      setUpload(null)
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
      setAssignmentAttachmentUpload(null)
      setAssignmentAttachmentName('')
    },
  })
  const removeAssignmentAttachmentMutation = useMutation({
    mutationFn: (attachmentId: string) => assignmentService.removeAssignmentAttachment(assignmentId, attachmentId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['assignment', assignmentId, 'attachments'] })
    },
  })
  const addSubmissionAttachmentMutation = useMutation({
    mutationFn: async () => {
      if (!latestStudentSubmissionId || !submissionAttachmentUpload) {
        return null
      }
      const storedFile = await fileService.uploadFile(submissionAttachmentUpload, 'ATTACHMENT')
      return assignmentService.addSubmissionAttachment(latestStudentSubmissionId, {
        fileId: storedFile.id,
        displayName: submissionAttachmentName.trim() || undefined,
      })
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['assignment', assignmentId, 'my-submissions'] })
      await queryClient.invalidateQueries({ queryKey: ['assignment', assignmentId, 'submission-attachments', latestStudentSubmissionId] })
      await queryClient.invalidateQueries({ queryKey: ['assignment', assignmentId, 'submission', latestStudentSubmissionId] })
      setSubmissionAttachmentUpload(null)
      setSubmissionAttachmentName('')
    },
  })
  const removeSubmissionAttachmentMutation = useMutation({
    mutationFn: (attachmentId: string) => {
      if (!latestStudentSubmissionId) {
        return Promise.resolve()
      }
      return assignmentService.removeSubmissionAttachment(latestStudentSubmissionId, attachmentId)
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['assignment', assignmentId, 'my-submissions'] })
      await queryClient.invalidateQueries({ queryKey: ['assignment', assignmentId, 'submission-attachments', latestStudentSubmissionId] })
      await queryClient.invalidateQueries({ queryKey: ['assignment', assignmentId, 'submission', latestStudentSubmissionId] })
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
  const gradeMutation = useMutation({
    mutationFn: () => assignmentService.createGrade(gradeForm),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['assignment', assignmentId, 'submissions'] })
      await queryClient.invalidateQueries({ queryKey: ['assignment', assignmentId, 'submission', activeSubmissionId] })
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
  const activeSubmissionId = filteredSubmissions.some((submission) => submission.id === selectedSubmissionId)
    ? selectedSubmissionId
    : filteredSubmissions.some((submission) => submission.id === requestedSubmissionId)
      ? requestedSubmissionId
      : filteredSubmissions[0]?.id ?? ''
  const selectedSubmissionQuery = useQuery({
    queryKey: ['assignment', assignmentId, 'submission', activeSubmissionId],
    queryFn: () => assignmentService.getSubmission(activeSubmissionId),
    enabled: Boolean(activeSubmissionId),
  })
  const studentSubmissionAttachmentsQuery = useQuery({
    queryKey: ['assignment', assignmentId, 'submission-attachments', latestStudentSubmissionId],
    queryFn: () => assignmentService.listSubmissionAttachments(latestStudentSubmissionId),
    enabled: Boolean(isStudent && latestStudentSubmissionId),
  })
  const selectedSubmissionAttachmentsQuery = useQuery({
    queryKey: ['assignment', assignmentId, 'submission-attachments', activeSubmissionId],
    queryFn: () => assignmentService.listSubmissionAttachments(activeSubmissionId),
    enabled: Boolean(!isStudent && activeSubmissionId),
  })
  const studentSubmissionAttachments = studentSubmissionAttachmentsQuery.data ?? []
  const selectedSubmissionAttachments = useMemo(
    () => selectedSubmissionAttachmentsQuery.data ?? [],
    [selectedSubmissionAttachmentsQuery.data],
  )
  const effectiveSelectedSubmissionAttachmentId = selectedSubmissionAttachments.some(
    (attachment) => attachment.id === selectedSubmissionAttachmentId,
  )
    ? selectedSubmissionAttachmentId
    : selectedSubmissionAttachments[0]?.id ?? ''
  const selectedSubmissionAttachment = selectedSubmissionAttachments.find(
    (attachment) => attachment.id === effectiveSelectedSubmissionAttachmentId,
  ) ?? null
  const submissionPreviewQuery = useQuery({
    queryKey: [
      'assignment',
      assignmentId,
      'submission-preview',
      activeSubmissionId,
      selectedSubmissionAttachment?.id,
      selectedSubmissionAttachment?.contentType,
      selectedSubmissionAttachment?.originalFileName,
    ],
    queryFn: async () => {
      if (!activeSubmissionId || !selectedSubmissionAttachment) {
        return { type: 'empty' as const }
      }
      const contentType = (selectedSubmissionAttachment.contentType ?? '').toLowerCase()

      if (contentType.includes('pdf')) {
        const fileBlob = await assignmentService.previewSubmissionAttachment(activeSubmissionId, selectedSubmissionAttachment.id)
        return { type: 'pdf' as const, url: URL.createObjectURL(fileBlob) }
      }

      if (contentType.startsWith('image/')) {
        const fileBlob = await assignmentService.previewSubmissionAttachment(activeSubmissionId, selectedSubmissionAttachment.id)
        return { type: 'image' as const, url: URL.createObjectURL(fileBlob) }
      }

      if (
        contentType.includes('wordprocessingml.document')
        || selectedSubmissionAttachment.originalFileName.toLowerCase().endsWith('.docx')
      ) {
        const fileBlob = await assignmentService.downloadSubmissionAttachment(activeSubmissionId, selectedSubmissionAttachment.id)
        const result = await convertToHtml({ arrayBuffer: await fileBlob.arrayBuffer() })
        return { type: 'docx' as const, html: result.value }
      }

      return { type: 'unsupported' as const }
    },
    enabled: Boolean(!isStudent && activeSubmissionId && selectedSubmissionAttachment),
  })
  const activeGradeDraft = activeSubmissionId ? gradeDraftBySubmissionId[activeSubmissionId] : undefined
  const gradeForm = {
    submissionId: activeSubmissionId,
    score: activeGradeDraft?.score ?? selectedSubmissionQuery.data?.score ?? 0,
    feedback: activeGradeDraft?.feedback ?? selectedSubmissionQuery.data?.feedback ?? '',
  }
  const backTarget = assignmentSubjectQuery.data ? `/subjects/${assignmentSubjectQuery.data.id}` : '/subjects'
  const backLabel = assignmentSubjectQuery.data
    ? t('assignments.backToCourse')
    : t('assignments.backToAssignments')

  useEffect(() => {
    if (!activeSubmissionId) {
      return
    }
    if (searchParams.get('submissionId') === activeSubmissionId) {
      return
    }
    setSearchParams((current) => {
      const next = new URLSearchParams(current)
      next.set('submissionId', activeSubmissionId)
      return next
    }, { replace: true })
  }, [activeSubmissionId, searchParams, setSearchParams])

  useEffect(() => {
    const preview = submissionPreviewQuery.data
    if (!preview || (preview.type !== 'pdf' && preview.type !== 'image')) {
      return undefined
    }
    return () => {
      URL.revokeObjectURL(preview.url)
    }
  }, [submissionPreviewQuery.data])

  if (
    assignmentQuery.isLoading
    || assignmentAttachmentsQuery.isLoading
    || (isStudent && mySubmissionsQuery.isLoading)
    || (isStudent && studentSubmissionAttachmentsQuery.isLoading)
    || (!isStudent && (
      submissionsQuery.isLoading
      || availabilityQuery.isLoading
      || subjectScopeQuery.isLoading
      || assignmentSubjectQuery.isLoading
      || connectedGroupsQuery.isLoading
      || submissionStudentsQuery.isLoading
      || groupStudentsQuery.isLoading
      || selectedSubmissionQuery.isLoading
      || selectedSubmissionAttachmentsQuery.isLoading
    ))
  ) {
    return <LoadingState />
  }

  if (
    assignmentQuery.isError
    || assignmentAttachmentsQuery.isError
    || !assignment
    || (isStudent && mySubmissionsQuery.isError)
    || (isStudent && studentSubmissionAttachmentsQuery.isError)
    || (!isStudent && (
      submissionsQuery.isError
      || availabilityQuery.isError
      || subjectScopeQuery.isError
      || assignmentSubjectQuery.isError
      || connectedGroupsQuery.isError
      || submissionStudentsQuery.isError
      || groupStudentsQuery.isError
      || selectedSubmissionQuery.isError
      || selectedSubmissionAttachmentsQuery.isError
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
            ?? selectedSubmissionQuery.error
            ?? selectedSubmissionAttachmentsQuery.error
            ?? studentSubmissionAttachmentsQuery.error
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
              { label: assignment.title },
            ]
          : [{ label: t('navigation.shared.assignments'), to: '/assignments' }, { label: assignment.title }]}
      />
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
      <PageHeader description={assignment.description ?? ''} title={assignment.title} />
      <Card className="space-y-4">
        <div className="flex flex-wrap items-center gap-3">
          <StatusBadge value={assignment.status} />
          <DeadlineBadge deadline={assignment.deadline} />
        </div>
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4 text-sm text-text-secondary">
          <p>{t('assignments.allowLateSubmissions')}: {assignment.allowLateSubmissions ? t('assignments.yes') : t('assignments.no')}</p>
          <p>{t('assignments.maxSubmissions')}: {assignment.maxSubmissions}</p>
          <p>{t('assignments.allowResubmit')}: {assignment.allowResubmit ? t('assignments.yes') : t('assignments.no')}</p>
          <p>{t('assignments.maxPoints')}: {assignment.maxPoints}</p>
          <p>{t('assignments.maxFileSizeMb')}: {assignment.maxFileSizeMb ?? '-'}</p>
        </div>
        <p className="text-sm text-text-secondary">{t('assignments.acceptedFileTypes')}: {(assignment.acceptedFileTypes ?? []).join(', ')}</p>
        {isStudent ? (
          <div className="space-y-4">
            {canSubmitAssignment ? null : (
              <div className="rounded-[14px] border border-warning/30 bg-warning/5 px-4 py-3">
                <p className="text-sm font-semibold text-warning">{t('assignments.submissionClosed')}</p>
              </div>
            )}
            <FormField label={t('assignments.uploadSubmission')}>
              <Input type="file" onChange={(event) => setUpload(event.target.files?.[0] ?? null)} />
            </FormField>
            <Button disabled={!upload || submitMutation.isPending || !canSubmitAssignment} onClick={() => submitMutation.mutate()}>
              {t('assignments.submitAssignment')}
            </Button>
            {(mySubmissionsQuery.data ?? []).length > 0 ? (
              <div className="space-y-4 rounded-[16px] border border-border bg-surface-muted p-4">
                <PageHeader title={t('assignments.mySubmissions')} />
                {(mySubmissionsQuery.data ?? []).map((submission) => (
                  <div key={submission.id} className="rounded-[14px] border border-border bg-surface p-4">
                    <div className="flex flex-wrap items-center justify-between gap-3">
                      <div>
                        <p className="font-semibold text-text-primary">{t('assignments.submission')}</p>
                        <p className="mt-1 text-sm text-text-secondary">{formatDateTime(submission.submittedAt)}</p>
                      </div>
                      <span className={submission.reviewed ? 'rounded-full bg-success/10 px-2.5 py-1 text-xs font-semibold text-success' : 'rounded-full bg-warning/10 px-2.5 py-1 text-xs font-semibold text-warning'}>
                        {submission.reviewed ? t('assignments.reviewed') : t('assignments.pendingReview')}
                      </span>
                    </div>
                    {submission.score !== null ? (
                      <p className="mt-2 text-sm font-semibold text-text-primary">
                        {t('common.labels.score')}: {submission.score}/{assignment.maxPoints}
                      </p>
                    ) : null}
                    {submission.feedback ? <p className="mt-2 text-sm text-text-secondary">{submission.feedback}</p> : null}
                  </div>
                ))}
                <div className="space-y-3 rounded-[14px] border border-border bg-surface p-4">
                  <PageHeader title={t('assignments.submissionFiles')} />
                  {studentSubmissionAttachments.length === 0 ? (
                    <p className="text-sm text-text-secondary">{t('assignments.noFilesAttached')}</p>
                  ) : (
                    <div className="space-y-2">
                      {studentSubmissionAttachments.map((attachment) => (
                        <div key={attachment.id} className="flex flex-wrap items-center justify-between gap-3 rounded-[12px] border border-border bg-surface-muted px-3 py-2">
                          <div>
                            <p className="text-sm font-semibold text-text-primary">{attachment.displayName?.trim() || attachment.originalFileName}</p>
                            <p className="text-xs text-text-muted">
                              {formatFileType(attachment.contentType, attachment.originalFileName)} · {formatFileSize(attachment.sizeBytes)} · {formatDateTime(attachment.createdAt)}
                            </p>
                          </div>
                          <div className="flex flex-wrap gap-2">
                            {attachment.previewAvailable ? (
                              <Button
                                variant="ghost"
                                onClick={async () => {
                                  if (!latestStudentSubmissionId) {
                                    return
                                  }
                                  const blob = await assignmentService.previewSubmissionAttachment(latestStudentSubmissionId, attachment.id)
                                  openBlobPreview(blob)
                                }}
                              >
                                {t('assignments.previewFile')}
                              </Button>
                            ) : null}
                            <Button
                              variant="secondary"
                              onClick={async () => {
                                if (!latestStudentSubmissionId) {
                                  return
                                }
                                const blob = await assignmentService.downloadSubmissionAttachment(latestStudentSubmissionId, attachment.id)
                                downloadBlob(blob, attachment.originalFileName)
                              }}
                            >
                              {t('assignments.downloadFile')}
                            </Button>
                            <Button
                              variant="ghost"
                              disabled={!canSubmitAssignment || removeSubmissionAttachmentMutation.isPending}
                              onClick={() => {
                                if (window.confirm(t('assignments.removeSubmissionFileWarning'))) {
                                  removeSubmissionAttachmentMutation.mutate(attachment.id)
                                }
                              }}
                            >
                              {t('assignments.removeFile')}
                            </Button>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                  <FormField label={t('assignments.fileName')}>
                    <Input value={submissionAttachmentName} onChange={(event) => setSubmissionAttachmentName(event.target.value)} />
                  </FormField>
                  <FormField label={t('assignments.attachFile')}>
                    <Input
                      disabled={!canSubmitAssignment}
                      type="file"
                      onChange={(event) => setSubmissionAttachmentUpload(event.target.files?.[0] ?? null)}
                    />
                  </FormField>
                  <Button
                    disabled={!canSubmitAssignment || !latestStudentSubmissionId || !submissionAttachmentUpload || addSubmissionAttachmentMutation.isPending}
                    onClick={() => addSubmissionAttachmentMutation.mutate()}
                  >
                    {t('assignments.attachFile')}
                  </Button>
                </div>
              </div>
            ) : null}
          </div>
        ) : (
          <div className="flex flex-wrap gap-3">
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
          </div>
        )}
      </Card>

      <Card className="space-y-4">
        <PageHeader title={t('assignments.taskFiles')} />
        {assignmentAttachments.length === 0 ? (
          <EmptyState title={t('assignments.taskFiles')} description={t('assignments.noFilesAttached')} />
        ) : (
          <div className="space-y-3">
            {assignmentAttachments.map((attachment) => (
              <div key={attachment.id} className="rounded-[14px] border border-border bg-surface-muted px-4 py-3">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p className="font-semibold text-text-primary">{attachment.displayName?.trim() || attachment.originalFileName}</p>
                    <p className="mt-1 text-sm text-text-secondary">{attachment.originalFileName}</p>
                    <p className="mt-1 text-xs text-text-muted">
                      {formatFileType(attachment.contentType, attachment.originalFileName)} · {formatFileSize(attachment.sizeBytes)} · {formatDateTime(attachment.createdAt)}
                    </p>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {attachment.previewAvailable ? (
                      <Button
                        variant="ghost"
                        onClick={async () => {
                          const blob = await assignmentService.previewAssignmentAttachment(assignmentId, attachment.id)
                          openBlobPreview(blob)
                        }}
                      >
                        {t('assignments.previewFile')}
                      </Button>
                    ) : null}
                    <Button
                      variant="secondary"
                      onClick={async () => {
                        const blob = await assignmentService.downloadAssignmentAttachment(assignmentId, attachment.id)
                        downloadBlob(blob, attachment.originalFileName)
                      }}
                    >
                      {t('assignments.downloadFile')}
                    </Button>
                    {canManageAssignmentFiles ? (
                      <Button
                        variant="ghost"
                        disabled={!canEditAssignmentFiles || removeAssignmentAttachmentMutation.isPending}
                        onClick={() => {
                          if (
                            shouldWarnAssignmentAttachmentRemoval
                            && !window.confirm(t('assignments.removeAssignmentFileWarning'))
                          ) {
                            return
                          }
                          removeAssignmentAttachmentMutation.mutate(attachment.id)
                        }}
                      >
                        {t('assignments.removeFile')}
                      </Button>
                    ) : null}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
        {canManageAssignmentFiles ? (
          <div className="space-y-3 rounded-[14px] border border-border bg-surface-muted p-4">
            <FormField label={t('assignments.fileName')}>
              <Input
                disabled={!canEditAssignmentFiles}
                value={assignmentAttachmentName}
                onChange={(event) => setAssignmentAttachmentName(event.target.value)}
              />
            </FormField>
            <FormField label={t('assignments.attachFile')}>
              <Input
                disabled={!canEditAssignmentFiles}
                type="file"
                onChange={(event) => setAssignmentAttachmentUpload(event.target.files?.[0] ?? null)}
              />
            </FormField>
            <Button
              disabled={!canEditAssignmentFiles || !assignmentAttachmentUpload || addAssignmentAttachmentMutation.isPending}
              onClick={() => addAssignmentAttachmentMutation.mutate()}
            >
              {t('assignments.attachFile')}
            </Button>
          </div>
        ) : null}
      </Card>

      {!isStudent ? (
        <Card className="space-y-4">
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
        </Card>
      ) : null}

      {!isStudent ? (
        <>
          <Card className="space-y-4">
            <PageHeader description={t('assignments.submissionsDescription')} title={t('assignments.submissions')} />
            <FormField label={t('common.actions.search')}>
              <Input value={submissionSearch} onChange={(event) => setSubmissionSearch(event.target.value)} />
            </FormField>
            {groupedSubmissions.length === 0 ? (
              <EmptyState description={t('assignments.noSubmissionsToGrade')} title={t('assignments.submissions')} />
            ) : (
              <div className="grid gap-6 xl:grid-cols-[minmax(0,340px)_minmax(0,1fr)]">
                <div className="space-y-4">
                  {groupedSubmissions.map(([groupName, submissions]) => (
                    <div key={groupName} className="space-y-3">
                      <h3 className="text-sm font-semibold uppercase tracking-[0.12em] text-text-muted">{groupName}</h3>
                      {submissions.map((submission) => (
                        <button
                          key={submission.id}
                          className={`w-full rounded-[14px] border px-4 py-3 text-left ${activeSubmissionId === submission.id ? 'border-accent bg-accent/5' : 'border-border bg-surface'}`}
                          type="button"
                          onClick={() => setSelectedSubmissionId(submission.id)}
                        >
                          <p className="font-semibold text-text-primary">{submissionStudentById.get(submission.userId)?.username ?? t('education.unknownStudent')}</p>
                          <p className="mt-1 text-sm text-text-secondary">{formatDateTime(submission.submittedAt)}</p>
                          <p className="mt-2 text-xs text-text-muted">
                            {submission.reviewed ? t('assignments.reviewed') : t('assignments.pendingReview')}
                          </p>
                        </button>
                      ))}
                    </div>
                  ))}
                </div>
                {selectedSubmissionQuery.data ? (
                  <div className="space-y-4 rounded-[16px] border border-border bg-surface-muted p-4">
                    <PageHeader title={t('assignments.gradeSubmission')} />
                    <div className="space-y-2 text-sm text-text-secondary">
                      <p>{t('testing.student')}: {submissionStudentById.get(selectedSubmissionQuery.data.userId)?.username ?? t('education.unknownStudent')}</p>
                      <p>{t('assignments.submittedAt')}: {formatDateTime(selectedSubmissionQuery.data.submittedAt)}</p>
                    </div>
                    <div className="space-y-2 rounded-[14px] border border-border bg-surface p-3">
                      <p className="text-sm font-semibold text-text-primary">{t('assignments.submissionFiles')}</p>
                      {selectedSubmissionAttachments.length === 0 ? (
                        <p className="text-sm text-text-secondary">{t('assignments.noFilesAttached')}</p>
                      ) : (
                        <div className="space-y-2">
                          {selectedSubmissionAttachments.map((attachment) => (
                            <button
                              key={attachment.id}
                              className={`w-full rounded-[12px] border px-3 py-2 text-left ${
                                selectedSubmissionAttachment?.id === attachment.id
                                  ? 'border-accent bg-accent/5'
                                  : 'border-border bg-surface-muted'
                              }`}
                              type="button"
                              onClick={() => setSelectedSubmissionAttachmentId(attachment.id)}
                            >
                              <p className="text-sm font-semibold text-text-primary">{attachment.displayName?.trim() || attachment.originalFileName}</p>
                              <p className="text-xs text-text-muted">
                                {formatFileType(attachment.contentType, attachment.originalFileName)} · {formatFileSize(attachment.sizeBytes)} · {formatDateTime(attachment.createdAt)}
                              </p>
                            </button>
                          ))}
                        </div>
                      )}
                    </div>
                    <div className="space-y-2 rounded-[14px] border border-border bg-surface p-3">
                      <p className="text-sm font-semibold text-text-primary">{t('assignments.filePreview')}</p>
                      {submissionPreviewQuery.isLoading ? <p className="text-sm text-text-secondary">{t('common.states.loading')}</p> : null}
                      {submissionPreviewQuery.data?.type === 'pdf' ? (
                        <iframe className="h-[480px] w-full rounded-[10px] border border-border" src={submissionPreviewQuery.data.url} title={t('assignments.filePreview')} />
                      ) : null}
                      {submissionPreviewQuery.data?.type === 'image' ? (
                        <img className="max-h-[520px] w-full rounded-[10px] border border-border object-contain" src={submissionPreviewQuery.data.url} alt={t('assignments.filePreview')} />
                      ) : null}
                      {submissionPreviewQuery.data?.type === 'docx' ? (
                        <div
                          className="prose max-w-none rounded-[10px] border border-border bg-surface-muted p-4 text-sm text-text-primary"
                          dangerouslySetInnerHTML={{ __html: submissionPreviewQuery.data.html }}
                        />
                      ) : null}
                      {!submissionPreviewQuery.isLoading
                      && submissionPreviewQuery.data?.type !== 'pdf'
                      && submissionPreviewQuery.data?.type !== 'docx'
                      && submissionPreviewQuery.data?.type !== 'image' ? (
                        <p className="text-sm text-text-secondary">
                          {submissionPreviewQuery.isError ? t('common.states.error') : t('assignments.previewUnavailable')}
                        </p>
                      ) : null}
                    </div>
                    <div className="flex flex-wrap gap-3">
                      <Button
                        disabled={!selectedSubmissionAttachment}
                        variant="secondary"
                        onClick={async () => {
                          if (!selectedSubmissionAttachment) {
                            return
                          }
                          const fileBlob = await assignmentService.downloadSubmissionAttachment(
                            selectedSubmissionQuery.data.id,
                            selectedSubmissionAttachment.id,
                          )
                          downloadBlob(fileBlob, selectedSubmissionAttachment.originalFileName)
                        }}
                      >
                        {t('assignments.downloadFile')}
                      </Button>
                    </div>
                    <div className="grid gap-4 xl:grid-cols-2">
                      <FormField label={t('common.labels.score')}>
                        <Input
                          max={assignment.maxPoints}
                          min={0}
                          type="number"
                          value={gradeForm.score}
                          onChange={(event) => {
                            if (!gradeForm.submissionId) {
                              return
                            }
                            setGradeDraftBySubmissionId((current) => ({
                              ...current,
                              [gradeForm.submissionId]: {
                                score: Number(event.target.value),
                                feedback: gradeForm.feedback,
                              },
                            }))
                          }}
                        />
                      </FormField>
                      <FormField label={t('assignments.feedback')}>
                        <Input
                          value={gradeForm.feedback}
                          onChange={(event) => {
                            if (!gradeForm.submissionId) {
                              return
                            }
                            setGradeDraftBySubmissionId((current) => ({
                              ...current,
                              [gradeForm.submissionId]: {
                                score: gradeForm.score,
                                feedback: event.target.value,
                              },
                            }))
                          }}
                        />
                      </FormField>
                    </div>
                    <Button disabled={!gradeForm.submissionId || gradeForm.score > assignment.maxPoints} onClick={() => gradeMutation.mutate()}>
                      {t('assignments.gradeSubmission')}
                    </Button>
                  </div>
                ) : (
                  <EmptyState description={t('assignments.selectSubmissionToReview')} title={t('assignments.gradeSubmission')} />
                )}
              </div>
            )}
          </Card>
        </>
      ) : null}
    </div>
  )
}

function openBlobPreview(blob: Blob) {
  const previewUrl = URL.createObjectURL(blob)
  window.open(previewUrl, '_blank', 'noopener,noreferrer')
  window.setTimeout(() => URL.revokeObjectURL(previewUrl), 60_000)
}

function formatFileSize(sizeBytes: number) {
  if (!Number.isFinite(sizeBytes) || sizeBytes <= 0) {
    return '0 B'
  }
  const units = ['B', 'KB', 'MB', 'GB']
  let value = sizeBytes
  let unitIndex = 0
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024
    unitIndex += 1
  }
  const precision = unitIndex === 0 ? 0 : value >= 10 ? 1 : 2
  return `${value.toFixed(precision)} ${units[unitIndex]}`
}

function formatFileType(contentType: string | null, originalFileName: string) {
  if (contentType && contentType.trim().length > 0) {
    return contentType
  }
  const extensionIndex = originalFileName.lastIndexOf('.')
  if (extensionIndex <= 0 || extensionIndex === originalFileName.length - 1) {
    return originalFileName
  }
  return originalFileName.slice(extensionIndex + 1).toUpperCase()
}
