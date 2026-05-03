import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { convertToHtml } from 'mammoth/mammoth.browser'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams, useSearchParams } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import { assignmentService, dashboardService, educationService, fileService, userDirectoryService } from '@/shared/api/services'
import { getLocalizedRequestErrorMessage } from '@/shared/lib/api-errors'
import { formatDateTime } from '@/shared/lib/format'
import { toGroupOption, toSubjectOption, toTopicOption } from '@/shared/lib/picker-options'
import { useDebouncedValue } from '@/shared/lib/useDebouncedValue'
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
import { DeadlineBadge } from '@/widgets/common/DeadlineBadge'
import { StatusBadge } from '@/widgets/common/StatusBadge'
import { loadAccessibleGroups, loadAccessibleSubjects, loadManagedSubjects } from '@/pages/education/helpers'
import type { AssignmentGroupAvailabilityResponse } from '@/shared/types/api'
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
          { key: 'title', header: t('common.labels.title'), render: (item) => <Link className="font-medium text-accent" to={`/assignments/${item.assignmentId}`}>{item.title}</Link> },
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
    description: '',
    deadline: '',
    allowLateSubmissions: true,
    maxSubmissions: 1,
    allowResubmit: true,
    acceptedFileTypes: 'application/pdf,image/png,image/jpeg',
    maxFileSizeMb: 10,
  })
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
  const accessibleGroupsQuery = useQuery({
    queryKey: ['education', 'groups', 'assignment-accessible', primaryRole, session?.user.id],
    queryFn: () => loadAccessibleGroups(primaryRole, session?.user.id ?? ''),
    enabled: Boolean(isTeacher && session?.user.id),
  })
  const accessibleSubjectsQuery = useQuery({
    queryKey: ['education', 'subjects', 'assignment-accessible', primaryRole, session?.user.id],
    queryFn: () => loadAccessibleSubjects(primaryRole, session?.user.id ?? ''),
    enabled: Boolean(isTeacher && session?.user.id),
  })
  const adminGroupSearchQuery = useQuery({
    queryKey: ['education', 'assignment-group-search', debouncedGroupSearch],
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
    queryKey: ['education', 'assignment-subjects', selectedGroupId],
    queryFn: () => educationService.getSubjectsByGroup(selectedGroupId, { page: 0, size: 100 }),
    enabled: Boolean(!isTeacher && selectedGroupId),
  })
  const topicsQuery = useQuery({
    queryKey: ['education', 'assignment-topics', selectedSubjectId],
    queryFn: () => educationService.getTopicsBySubject(selectedSubjectId, { page: 0, size: 100 }),
    enabled: Boolean(selectedSubjectId),
  })

  const createMutation = useMutation({
    mutationFn: () =>
      assignmentService.createAssignment({
        ...form,
        deadline: new Date(form.deadline).toISOString(),
        acceptedFileTypes: form.acceptedFileTypes.split(',').map((value) => value.trim()).filter(Boolean),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      await queryClient.invalidateQueries({ queryKey: ['assignments'] })
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
    (isTeacher && (teacherDashboardQuery.isLoading || accessibleGroupsQuery.isLoading || accessibleSubjectsQuery.isLoading))
    || (!isTeacher && (adminAssignmentsQuery.isLoading || adminSubjectsQuery.isLoading))
  ) {
    return <LoadingState />
  }

  if (
    (isTeacher && (teacherDashboardQuery.isError || accessibleGroupsQuery.isError || accessibleSubjectsQuery.isError))
    || (!isTeacher && (adminAssignmentsQuery.isError || adminSubjectsQuery.isError || adminGroupSearchQuery.isError))
  ) {
    return (
      <ErrorState
        title={t('navigation.shared.assignments')}
        description={getLocalizedRequestErrorMessage(
          teacherDashboardQuery.error
            ?? accessibleGroupsQuery.error
            ?? accessibleSubjectsQuery.error
            ?? adminAssignmentsQuery.error
            ?? adminSubjectsQuery.error
            ?? adminGroupSearchQuery.error,
          t,
        )}
        onRetry={() => {
          void teacherDashboardQuery.refetch()
          void accessibleGroupsQuery.refetch()
          void accessibleSubjectsQuery.refetch()
          void adminAssignmentsQuery.refetch()
          void adminSubjectsQuery.refetch()
          void adminGroupSearchQuery.refetch()
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
        <PageHeader title={t('assignments.createAssignment')} />
        <div className="grid gap-4 xl:grid-cols-3">
          {!isTeacher ? (
            <EntityPicker
              label={t('navigation.shared.groups')}
              value={selectedGroupId}
              options={groupOptions}
              placeholder={t('assignments.selectGroup')}
              emptyLabel={t('education.noGroups')}
              searchLabel={t('common.actions.search')}
              searchPlaceholder={t('assignments.groupSearchPlaceholder')}
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
            placeholder={t('assignments.selectSubject')}
            emptyLabel={!isTeacher && !selectedGroupId ? t('assignments.selectGroupFirst') : t('education.noSubjects')}
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
            placeholder={t('assignments.selectTopic')}
            emptyLabel={!selectedSubjectId ? t('assignments.selectSubjectFirst') : t('education.noTopics')}
            onChange={(value) => setForm((current) => ({ ...current, topicId: value }))}
          />
        </div>
        <div className="grid gap-4 xl:grid-cols-2">
          <FormField label={t('common.labels.title')}>
            <Input value={form.title} onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))} />
          </FormField>
          <FormField label={t('common.labels.deadline')}>
            <Input type="datetime-local" value={form.deadline} onChange={(event) => setForm((current) => ({ ...current, deadline: event.target.value }))} />
          </FormField>
        </div>
        <FormField label={t('common.labels.description')}>
          <Textarea value={form.description} onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))} />
        </FormField>
        <div className="grid gap-4 xl:grid-cols-4">
          <FormField label={t('assignments.maxSubmissions')}>
            <Input type="number" value={form.maxSubmissions} onChange={(event) => setForm((current) => ({ ...current, maxSubmissions: Number(event.target.value) }))} />
          </FormField>
          <FormField label={t('assignments.maxFileSizeMb')}>
            <Input type="number" value={form.maxFileSizeMb} onChange={(event) => setForm((current) => ({ ...current, maxFileSizeMb: Number(event.target.value) }))} />
          </FormField>
          <FormField label={t('assignments.acceptedFileTypes')}>
            <Input value={form.acceptedFileTypes} onChange={(event) => setForm((current) => ({ ...current, acceptedFileTypes: event.target.value }))} />
          </FormField>
        </div>
        <div className="flex flex-wrap gap-3">
          <Button disabled={!form.topicId || createMutation.isPending} onClick={() => createMutation.mutate()}>
            {t('common.actions.create')}
          </Button>
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
            { key: 'title', header: t('common.labels.title'), render: (item) => <Link className="font-medium text-accent" to={`/assignments/${item.id}`}>{item.title}</Link> },
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
  const queryClient = useQueryClient()
  const isStudent = primaryRole === 'STUDENT'
  const isTeacher = primaryRole === 'TEACHER'
  const [searchParams, setSearchParams] = useSearchParams()
  const [submissionSearch, setSubmissionSearch] = useState('')
  const debouncedSubmissionSearch = useDebouncedValue(submissionSearch.trim(), 250)
  const [selectedSubmissionId, setSelectedSubmissionId] = useState('')
  const [previewPdfUrl, setPreviewPdfUrl] = useState<string | null>(null)
  const [previewDocxHtml, setPreviewDocxHtml] = useState<string>('')
  const [previewError, setPreviewError] = useState<string | null>(null)
  const [previewLoading, setPreviewLoading] = useState(false)
  const assignmentQuery = useQuery({
    queryKey: ['assignment', assignmentId],
    queryFn: () => assignmentService.getAssignment(assignmentId),
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
  const [gradeForm, setGradeForm] = useState({ submissionId: '', score: 0, feedback: '' })
  const [availabilityGroupSearch, setAvailabilityGroupSearch] = useState('')
  const debouncedAvailabilityGroupSearch = useDebouncedValue(availabilityGroupSearch.trim(), 350)
  const [availabilityForm, setAvailabilityForm] = useState({
    groupId: '',
    visible: false,
    deadline: '',
    allowLateSubmissions: false,
    maxSubmissions: 1,
    allowResubmit: false,
  })
  const [bulkAvailabilityForm, setBulkAvailabilityForm] = useState({
    visible: true,
    deadline: '',
    allowLateSubmissions: false,
    maxSubmissions: 1,
    allowResubmit: false,
    copyFromGroupId: '',
  })
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
    queryFn: () => isTeacher
      ? loadAccessibleSubjects(primaryRole, session?.user.id ?? '')
      : loadManagedSubjects(),
    enabled: Boolean(!isStudent && assignmentQuery.data),
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
    enabled: Boolean(!isStudent && assignmentQuery.data && subjectScopeQuery.data),
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
  const selectedSubmissionQuery = useQuery({
    queryKey: ['assignment', assignmentId, 'submission', selectedSubmissionId],
    queryFn: () => assignmentService.getSubmission(selectedSubmissionId),
    enabled: Boolean(selectedSubmissionId),
  })
  const selectedSubmissionFileQuery = useQuery({
    queryKey: ['assignment', assignmentId, 'submission-file', selectedSubmissionQuery.data?.fileId],
    queryFn: () => fileService.getMetadata(selectedSubmissionQuery.data!.fileId),
    enabled: Boolean(selectedSubmissionQuery.data?.fileId),
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
    },
  })

  const publishMutation = useMutation({
    mutationFn: () => assignmentService.publishAssignment(assignmentId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['assignment', assignmentId] })
    },
  })
  const archiveMutation = useMutation({
    mutationFn: () => assignmentService.archiveAssignment(assignmentId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['assignment', assignmentId] })
    },
  })
  const gradeMutation = useMutation({
    mutationFn: () => assignmentService.createGrade(gradeForm),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['assignment', assignmentId, 'submissions'] })
      await queryClient.invalidateQueries({ queryKey: ['assignment', assignmentId, 'submission', selectedSubmissionId] })
    },
  })
  const availabilityMutation = useMutation({
    mutationFn: () =>
      assignmentService.upsertAssignmentAvailability(assignmentId, {
        groupId: availabilityForm.groupId,
        visible: availabilityForm.visible,
        availableFrom: null,
        deadline: new Date(availabilityForm.deadline).toISOString(),
        allowLateSubmissions: availabilityForm.allowLateSubmissions,
        maxSubmissions: availabilityForm.maxSubmissions,
        allowResubmit: availabilityForm.allowResubmit,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['assignment', assignmentId, 'availability'] })
      await queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })
  const bulkAvailabilityMutation = useMutation({
    mutationFn: (items: Record<string, unknown>[]) =>
      assignmentService.bulkUpsertAssignmentAvailability(assignmentId, items),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['assignment', assignmentId, 'availability'] })
      await queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })

  if (
    assignmentQuery.isLoading
    || (isStudent && mySubmissionsQuery.isLoading)
    || (!isStudent && (
      submissionsQuery.isLoading
      || availabilityQuery.isLoading
      || subjectScopeQuery.isLoading
      || assignmentSubjectQuery.isLoading
      || connectedGroupsQuery.isLoading
      || submissionStudentsQuery.isLoading
      || groupStudentsQuery.isLoading
      || selectedSubmissionQuery.isLoading
      || selectedSubmissionFileQuery.isLoading
    ))
  ) {
    return <LoadingState />
  }

  if (
    assignmentQuery.isError
    || !assignmentQuery.data
    || (isStudent && mySubmissionsQuery.isError)
    || (!isStudent && (
      submissionsQuery.isError
      || availabilityQuery.isError
      || subjectScopeQuery.isError
      || assignmentSubjectQuery.isError
      || connectedGroupsQuery.isError
      || submissionStudentsQuery.isError
      || groupStudentsQuery.isError
      || selectedSubmissionQuery.isError
      || selectedSubmissionFileQuery.isError
    ))
  ) {
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
            ?? selectedSubmissionFileQuery.error,
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

  const assignment = assignmentQuery.data
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
  const availabilitySaveDisabledReason = !availabilityForm.groupId
    ? t('availability.selectGroupReason')
    : !availabilityForm.deadline
      ? t('availability.selectDeadlineReason')
      : ''
  const bulkDeadlineRequired = !bulkAvailabilityForm.deadline
  const buildBulkAvailabilityPayload = (groupId: string, visible = bulkAvailabilityForm.visible) => ({
    groupId,
    visible,
    availableFrom: null,
    deadline: new Date(bulkAvailabilityForm.deadline || assignment.deadline).toISOString(),
    allowLateSubmissions: bulkAvailabilityForm.allowLateSubmissions,
    maxSubmissions: bulkAvailabilityForm.maxSubmissions,
    allowResubmit: bulkAvailabilityForm.allowResubmit,
  })
  const applyBulkAvailability = (visible = bulkAvailabilityForm.visible) => {
    if (connectedGroups.length === 0 || bulkDeadlineRequired) {
      return
    }

    bulkAvailabilityMutation.mutate(connectedGroups.map((group) => buildBulkAvailabilityPayload(group.id, visible)))
  }
  const copyAvailabilityToAllGroups = () => {
    const source = availabilityRows.find((availability) => availability.groupId === bulkAvailabilityForm.copyFromGroupId)
    if (!source || connectedGroups.length === 0) {
      return
    }

    bulkAvailabilityMutation.mutate(
      connectedGroups
        .filter((group) => group.id !== source.groupId)
        .map((group) => ({
          groupId: group.id,
          visible: source.visible,
          availableFrom: null,
          deadline: source.deadline,
          allowLateSubmissions: source.allowLateSubmissions,
          maxSubmissions: source.maxSubmissions,
          allowResubmit: source.allowResubmit,
        })),
    )
  }

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

  useEffect(() => {
    if (isStudent) {
      return
    }
    const requestedSubmissionId = searchParams.get('submissionId')
    const nextSelectedSubmissionId = filteredSubmissions.find((submission) => submission.id === requestedSubmissionId)?.id
      ?? filteredSubmissions[0]?.id
      ?? ''
    setSelectedSubmissionId((current) => (
      current && filteredSubmissions.some((submission) => submission.id === current)
        ? current
        : nextSelectedSubmissionId
    ))
  }, [filteredSubmissions, isStudent, searchParams])

  useEffect(() => {
    if (!selectedSubmissionId) {
      return
    }
    setSearchParams((current) => {
      const next = new URLSearchParams(current)
      next.set('submissionId', selectedSubmissionId)
      return next
    }, { replace: true })
  }, [selectedSubmissionId, setSearchParams])

  useEffect(() => {
    if (!selectedSubmissionQuery.data) {
      return
    }
    setGradeForm({
      submissionId: selectedSubmissionQuery.data.id,
      score: selectedSubmissionQuery.data.score ?? 0,
      feedback: selectedSubmissionQuery.data.feedback ?? '',
    })
  }, [selectedSubmissionQuery.data])

  useEffect(() => {
    let objectUrlToRevoke: string | null = null
    setPreviewPdfUrl((current) => {
      if (current) {
        URL.revokeObjectURL(current)
      }
      return null
    })
    setPreviewDocxHtml('')
    setPreviewError(null)

    const submission = selectedSubmissionQuery.data
    const metadata = selectedSubmissionFileQuery.data
    if (!submission || !metadata) {
      return () => undefined
    }

    const renderPreview = async () => {
      setPreviewLoading(true)
      try {
        const fileBlob = await fileService.downloadFile(submission.fileId)
        const contentType = (metadata.contentType ?? '').toLowerCase()

        if (contentType.includes('pdf')) {
          const pdfUrl = URL.createObjectURL(fileBlob)
          objectUrlToRevoke = pdfUrl
          setPreviewPdfUrl(pdfUrl)
          return
        }

        if (contentType.includes('wordprocessingml.document')
          || metadata.originalFileName.toLowerCase().endsWith('.docx')) {
          const result = await convertToHtml({ arrayBuffer: await fileBlob.arrayBuffer() })
          setPreviewDocxHtml(result.value)
          return
        }

        setPreviewError(t('assignments.previewUnavailable'))
      } catch {
        setPreviewError(t('common.states.error'))
      } finally {
        setPreviewLoading(false)
      }
    }

    void renderPreview()

    return () => {
      if (objectUrlToRevoke) {
        URL.revokeObjectURL(objectUrlToRevoke)
      }
    }
  }, [selectedSubmissionFileQuery.data, selectedSubmissionQuery.data, t])

  return (
    <div className="space-y-6">
      <Breadcrumbs items={[{ label: t('navigation.shared.assignments'), to: '/assignments' }, { label: assignment.title }]} />
      <Link to="/assignments">
        <Button variant="secondary">{t('assignments.backToAssignments')}</Button>
      </Link>
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
            <FormField label={t('assignments.uploadSubmission')}>
              <Input type="file" onChange={(event) => setUpload(event.target.files?.[0] ?? null)} />
            </FormField>
            <Button disabled={!upload || submitMutation.isPending} onClick={() => submitMutation.mutate()}>
              {t('assignments.submitAssignment')}
            </Button>
            {(mySubmissionsQuery.data ?? []).length > 0 ? (
              <div className="space-y-3 rounded-[16px] border border-border bg-surface-muted p-4">
                <PageHeader title={t('assignments.mySubmissions')} />
                {(mySubmissionsQuery.data ?? []).map((submission) => (
                  <div key={submission.id} className="rounded-[14px] border border-border bg-surface p-4">
                    <div className="flex flex-wrap items-center justify-between gap-3">
                      <div>
                        <p className="font-semibold text-text-primary">{submission.file?.originalFileName ?? t('assignments.submission')}</p>
                        <p className="mt-1 text-sm text-text-secondary">{formatDateTime(submission.submittedAt)}</p>
                      </div>
                      <span className={submission.reviewed ? 'rounded-full bg-success/10 px-2.5 py-1 text-xs font-semibold text-success' : 'rounded-full bg-warning/10 px-2.5 py-1 text-xs font-semibold text-warning'}>
                        {submission.reviewed ? t('assignments.reviewed') : t('assignments.pendingReview')}
                      </span>
                    </div>
                    <p className="mt-3 text-sm text-text-secondary">
                      {t('assignments.fileStatus')}: {submission.file?.status ?? t('common.states.unknown')}
                    </p>
                    {submission.score !== null ? (
                      <p className="mt-2 text-sm font-semibold text-text-primary">
                        {t('common.labels.score')}: {submission.score}/{assignment.maxPoints}
                      </p>
                    ) : null}
                    {submission.feedback ? <p className="mt-2 text-sm text-text-secondary">{submission.feedback}</p> : null}
                  </div>
                ))}
              </div>
            ) : null}
          </div>
        ) : (
          <div className="flex flex-wrap gap-3">
            <Button variant="secondary" onClick={() => publishMutation.mutate()}>
              {t('common.actions.publish')}
            </Button>
            <Button onClick={() => archiveMutation.mutate()}>{t('assignments.archive')}</Button>
          </div>
        )}
      </Card>

      {!isStudent ? (
        <Card className="space-y-4">
          <PageHeader description={t('availability.assignmentDescription')} title={t('availability.title')} />
          {availabilityGroupCards.length === 0 ? (
            <EmptyState description={t('availability.assignmentEmpty')} title={t('availability.title')} />
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
                    deadline: toDateTimeLocal(availability?.deadline ?? assignment.deadline),
                    allowLateSubmissions: availability?.allowLateSubmissions ?? assignment.allowLateSubmissions,
                    maxSubmissions: availability?.maxSubmissions ?? assignment.maxSubmissions,
                    allowResubmit: availability?.allowResubmit ?? assignment.allowResubmit,
                  })}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="font-semibold text-text-primary">{group.name}</p>
                      <p className="mt-1 text-sm text-text-secondary">{t('common.labels.deadline')}: {formatDateTime(availability?.deadline ?? assignment.deadline)}</p>
                      <p className="text-sm text-text-secondary">{t('assignments.maxSubmissions')}: {availability?.maxSubmissions ?? assignment.maxSubmissions}</p>
                    </div>
                    <span className={availability?.visible ? 'rounded-full bg-success/10 px-2.5 py-1 text-xs font-semibold text-success' : 'rounded-full bg-warning/10 px-2.5 py-1 text-xs font-semibold text-warning'}>
                      {availability ? getAssignmentAvailabilityStatus(availability, t) : t('availability.hidden')}
                    </span>
                  </div>
                </button>
              ))}
            </div>
          )}

          <div className="space-y-4 rounded-[16px] border border-border bg-surface-muted p-4">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <h3 className="text-base font-semibold text-text-primary">{t('availability.bulkTitle')}</h3>
                <p className="mt-1 text-sm leading-6 text-text-secondary">{t('availability.bulkDescription')}</p>
              </div>
              <span className="rounded-full border border-border px-2.5 py-1 text-xs font-semibold text-text-secondary">
                {t('availability.connectedGroupsCount', { count: connectedGroups.length })}
              </span>
            </div>
            <div className="grid gap-4 xl:grid-cols-3">
              <FormField label={t('common.labels.status')}>
                <SegmentedControl
                  ariaLabel={t('availability.title')}
                  value={bulkAvailabilityForm.visible ? 'visible' : 'hidden'}
                  options={[
                    { value: 'hidden', label: t('availability.hidden') },
                    { value: 'visible', label: t('availability.visible') },
                  ]}
                  onChange={(value) => setBulkAvailabilityForm((current) => ({ ...current, visible: value === 'visible' }))}
                />
              </FormField>
              <FormField label={t('common.labels.deadline')}>
                <Input type="datetime-local" value={bulkAvailabilityForm.deadline} onChange={(event) => setBulkAvailabilityForm((current) => ({ ...current, deadline: event.target.value }))} />
              </FormField>
              <FormField label={t('assignments.maxSubmissions')}>
                <Input min={1} type="number" value={bulkAvailabilityForm.maxSubmissions} onChange={(event) => setBulkAvailabilityForm((current) => ({ ...current, maxSubmissions: Number(event.target.value) }))} />
              </FormField>
              <FormField label={t('assignments.allowLateSubmissions')}>
                <Input type="checkbox" checked={bulkAvailabilityForm.allowLateSubmissions} onChange={(event) => setBulkAvailabilityForm((current) => ({ ...current, allowLateSubmissions: event.target.checked }))} />
              </FormField>
              <FormField label={t('assignments.allowResubmit')}>
                <Input type="checkbox" checked={bulkAvailabilityForm.allowResubmit} onChange={(event) => setBulkAvailabilityForm((current) => ({ ...current, allowResubmit: event.target.checked }))} />
              </FormField>
            </div>
            {bulkDeadlineRequired ? (
              <p className="text-sm font-semibold text-text-secondary">{t('availability.bulkDeadlineRequired')}</p>
            ) : null}
            <div className="flex flex-wrap gap-3">
              <Button disabled={connectedGroups.length === 0 || bulkDeadlineRequired || bulkAvailabilityMutation.isPending} variant="secondary" onClick={() => applyBulkAvailability(true)}>
                {t('availability.publishAllGroups')}
              </Button>
              <Button disabled={connectedGroups.length === 0 || bulkDeadlineRequired || bulkAvailabilityMutation.isPending} variant="secondary" onClick={() => applyBulkAvailability(false)}>
                {t('availability.hideAllGroups')}
              </Button>
              <Button disabled={connectedGroups.length === 0 || bulkDeadlineRequired || bulkAvailabilityMutation.isPending} onClick={() => applyBulkAvailability()}>
                {t('availability.applySameToAll')}
              </Button>
            </div>
            <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_auto]">
              <EntityPicker
                label={t('availability.copyFromGroup')}
                value={bulkAvailabilityForm.copyFromGroupId}
                options={connectedGroupOptions}
                placeholder={t('availability.selectGroup')}
                emptyLabel={t('availability.assignmentEmpty')}
                onChange={(value) => setBulkAvailabilityForm((current) => ({ ...current, copyFromGroupId: value }))}
              />
              <div className="flex items-end">
                <Button
                  disabled={!bulkAvailabilityForm.copyFromGroupId || bulkAvailabilityMutation.isPending}
                  variant="secondary"
                  onClick={copyAvailabilityToAllGroups}
                >
                  {t('availability.copyAvailability')}
                </Button>
              </div>
            </div>
          </div>

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
            <FormField label={t('common.labels.deadline')}>
              <Input type="datetime-local" value={availabilityForm.deadline} onChange={(event) => setAvailabilityForm((current) => ({ ...current, deadline: event.target.value }))} />
            </FormField>
            <FormField label={t('assignments.maxSubmissions')}>
              <Input type="number" value={availabilityForm.maxSubmissions} onChange={(event) => setAvailabilityForm((current) => ({ ...current, maxSubmissions: Number(event.target.value) }))} />
            </FormField>
            <FormField label={t('assignments.allowLateSubmissions')}>
              <Input type="checkbox" checked={availabilityForm.allowLateSubmissions} onChange={(event) => setAvailabilityForm((current) => ({ ...current, allowLateSubmissions: event.target.checked }))} />
            </FormField>
            <FormField label={t('assignments.allowResubmit')}>
              <Input type="checkbox" checked={availabilityForm.allowResubmit} onChange={(event) => setAvailabilityForm((current) => ({ ...current, allowResubmit: event.target.checked }))} />
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
                          className={`w-full rounded-[14px] border px-4 py-3 text-left ${selectedSubmissionId === submission.id ? 'border-accent bg-accent/5' : 'border-border bg-surface'}`}
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
                      <p>{t('assignments.fileStatus')}: {selectedSubmissionFileQuery.data?.status ?? t('common.states.unknown')}</p>
                    </div>
                    <div className="space-y-2 rounded-[14px] border border-border bg-surface p-3">
                      <p className="text-sm font-semibold text-text-primary">{t('assignments.filePreview')}</p>
                      {previewLoading ? <p className="text-sm text-text-secondary">{t('common.states.loading')}</p> : null}
                      {previewPdfUrl ? (
                        <iframe className="h-[480px] w-full rounded-[10px] border border-border" src={previewPdfUrl} title={t('assignments.filePreview')} />
                      ) : null}
                      {!previewPdfUrl && previewDocxHtml ? (
                        <div
                          className="prose max-w-none rounded-[10px] border border-border bg-surface-muted p-4 text-sm text-text-primary"
                          dangerouslySetInnerHTML={{ __html: previewDocxHtml }}
                        />
                      ) : null}
                      {!previewLoading && !previewPdfUrl && !previewDocxHtml ? (
                        <p className="text-sm text-text-secondary">{previewError ?? t('assignments.previewUnavailable')}</p>
                      ) : null}
                    </div>
                    <div className="flex flex-wrap gap-3">
                      <Button
                        variant="secondary"
                        onClick={async () => {
                          const fileBlob = await fileService.downloadFile(selectedSubmissionQuery.data.fileId)
                          const downloadUrl = URL.createObjectURL(fileBlob)
                          const link = document.createElement('a')
                          link.href = downloadUrl
                          link.download = selectedSubmissionFileQuery.data?.originalFileName ?? 'submission'
                          link.click()
                          URL.revokeObjectURL(downloadUrl)
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
                          onChange={(event) => setGradeForm((current) => ({ ...current, score: Number(event.target.value) }))}
                        />
                      </FormField>
                      <FormField label={t('assignments.feedback')}>
                        <Input value={gradeForm.feedback} onChange={(event) => setGradeForm((current) => ({ ...current, feedback: event.target.value }))} />
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

function toDateTimeLocal(value: string | null | undefined) {
  return value ? value.slice(0, 16) : ''
}

function getAssignmentAvailabilityStatus(
  availability: AssignmentGroupAvailabilityResponse,
  t: (key: string) => string,
) {
  if (!availability.visible) {
    return t('availability.hidden')
  }

  const now = Date.now()
  const deadlineAt = new Date(availability.deadline).getTime()

  if (deadlineAt < now) {
    return t('availability.deadlinePassed')
  }
  return t('availability.open')
}
