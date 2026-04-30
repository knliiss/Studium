import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import { assignmentService, dashboardService, educationService, fileService, userDirectoryService } from '@/shared/api/services'
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
    return <ErrorState title={t('navigation.shared.assignments')} description={t('common.states.error')} />
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
  const [query, setQuery] = useState('assignment')
  const [groupSearch, setGroupSearch] = useState('')
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
  const normalizedGroupSearch = groupSearch.trim()

  const teacherDashboardQuery = useQuery({
    queryKey: ['dashboard', 'teacher'],
    queryFn: () => dashboardService.getTeacherDashboard(),
    enabled: isTeacher,
  })
  const adminAssignmentsQuery = useQuery({
    queryKey: ['assignments', 'search', query],
    queryFn: () => assignmentService.searchAssignments({ q: query, page: 0, size: 50 }),
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
    queryKey: ['education', 'assignment-group-search', normalizedGroupSearch],
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
    return <ErrorState title={t('navigation.shared.assignments')} description={t('common.states.error')} />
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
  const [upload, setUpload] = useState<File | null>(null)
  const [gradeForm, setGradeForm] = useState({ submissionId: '', score: 0, feedback: '' })
  const [availabilityGroupSearch, setAvailabilityGroupSearch] = useState('')
  const [availabilityForm, setAvailabilityForm] = useState({
    groupId: '',
    visible: false,
    availableFrom: '',
    deadline: '',
    allowLateSubmissions: false,
    maxSubmissions: 1,
    allowResubmit: false,
  })
  const normalizedAvailabilityGroupSearch = availabilityGroupSearch.trim()
  const accessibleGroupsQuery = useQuery({
    queryKey: ['education', 'assignment-detail-groups', primaryRole, session?.user.id],
    queryFn: () => loadAccessibleGroups(primaryRole, session?.user.id ?? ''),
    enabled: Boolean(!isStudent && isTeacher && session?.user.id),
  })
  const adminGroupSearchQuery = useQuery({
    queryKey: ['education', 'assignment-detail-group-search', normalizedAvailabilityGroupSearch],
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
    },
  })
  const availabilityMutation = useMutation({
    mutationFn: () =>
      assignmentService.upsertAssignmentAvailability(assignmentId, {
        groupId: availabilityForm.groupId,
        visible: availabilityForm.visible,
        availableFrom: availabilityForm.availableFrom ? new Date(availabilityForm.availableFrom).toISOString() : null,
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

  if (
    assignmentQuery.isLoading
    || (!isStudent && (
      submissionsQuery.isLoading
      || availabilityQuery.isLoading
      || subjectScopeQuery.isLoading
      || assignmentSubjectQuery.isLoading
      || connectedGroupsQuery.isLoading
      || submissionStudentsQuery.isLoading
    ))
  ) {
    return <LoadingState />
  }

  if (
    assignmentQuery.isError
    || !assignmentQuery.data
    || (!isStudent && (
      submissionsQuery.isError
      || availabilityQuery.isError
      || subjectScopeQuery.isError
      || assignmentSubjectQuery.isError
      || connectedGroupsQuery.isError
      || submissionStudentsQuery.isError
    ))
  ) {
    return <ErrorState title={t('navigation.shared.assignments')} description={t('common.states.error')} />
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
  const submissionOptions = (submissionsQuery.data?.items ?? []).map((submission) => ({
    value: submission.id,
    label: submissionStudentById.get(submission.userId)?.username ?? t('education.unknownStudent'),
    description: formatDateTime(submission.submittedAt),
  }))
  const availabilitySaveDisabledReason = !availabilityForm.groupId
    ? t('availability.selectGroupReason')
    : !availabilityForm.deadline
      ? t('availability.selectDeadlineReason')
      : ''

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
          <p>{t('assignments.maxFileSizeMb')}: {assignment.maxFileSizeMb ?? '-'}</p>
        </div>
        <p className="text-sm text-text-secondary">{t('assignments.acceptedFileTypes')}: {assignment.acceptedFileTypes.join(', ')}</p>
        {isStudent ? (
          <div className="space-y-4">
            <FormField label={t('assignments.uploadSubmission')}>
              <Input type="file" onChange={(event) => setUpload(event.target.files?.[0] ?? null)} />
            </FormField>
            <Button disabled={!upload || submitMutation.isPending} onClick={() => submitMutation.mutate()}>
              {t('assignments.submitAssignment')}
            </Button>
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
                    availableFrom: toDateTimeLocal(availability?.availableFrom),
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
                      <p className="text-sm text-text-secondary">{t('availability.availableFrom')}: {availability?.availableFrom ? formatDateTime(availability.availableFrom) : t('availability.immediately')}</p>
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
            <FormField label={t('availability.availableFrom')}>
              <Input type="datetime-local" value={availabilityForm.availableFrom} onChange={(event) => setAvailabilityForm((current) => ({ ...current, availableFrom: event.target.value }))} />
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
            <PageHeader title={t('assignments.submissions')} />
            <DataTable
              columns={[
                { key: 'userId', header: t('testing.student'), render: (item) => submissionStudentById.get(item.userId)?.username ?? t('education.unknownStudent') },
                { key: 'fileId', header: t('assignments.file'), render: () => t('assignments.submission') },
                { key: 'submittedAt', header: t('assignments.submittedAt'), render: (item) => formatDateTime(item.submittedAt) },
              ]}
              rows={submissionsQuery.data?.items ?? []}
            />
          </Card>

          <Card className="space-y-4">
            <PageHeader title={t('assignments.gradeSubmission')} />
            <div className="grid gap-4 xl:grid-cols-3">
              <EntityPicker
                label={t('assignments.submission')}
                value={gradeForm.submissionId}
                options={submissionOptions}
                placeholder={t('assignments.selectSubmission')}
                emptyLabel={t('assignments.noSubmissionsToGrade')}
                onChange={(value) => setGradeForm((current) => ({ ...current, submissionId: value }))}
              />
              <FormField label={t('common.labels.score')}>
                <Input type="number" value={gradeForm.score} onChange={(event) => setGradeForm((current) => ({ ...current, score: Number(event.target.value) }))} />
              </FormField>
              <FormField label={t('assignments.feedback')}>
                <Input value={gradeForm.feedback} onChange={(event) => setGradeForm((current) => ({ ...current, feedback: event.target.value }))} />
              </FormField>
            </div>
            <Button disabled={!gradeForm.submissionId} onClick={() => gradeMutation.mutate()}>{t('assignments.gradeSubmission')}</Button>
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
  const opensAt = availability.availableFrom ? new Date(availability.availableFrom).getTime() : null
  const deadlineAt = new Date(availability.deadline).getTime()

  if (opensAt && opensAt > now) {
    return t('availability.opensLater')
  }
  if (deadlineAt < now) {
    return t('availability.deadlinePassed')
  }
  return t('availability.open')
}
