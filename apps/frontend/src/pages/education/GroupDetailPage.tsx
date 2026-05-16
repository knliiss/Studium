import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus, RotateCcw } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useParams, useSearchParams } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import { loadAccessibleGroups } from '@/pages/education/helpers'
import { AccessDeniedPage } from '@/pages/shared/AccessDeniedPage'
import {
  adminUserService,
  analyticsService,
  educationService,
  groupCurriculumOverrideService,
  resolvedSubjectsService,
  scheduleService,
  specialtyService,
  streamService,
  userDirectoryService,
} from '@/shared/api/services'
import { getLocalizedApiErrorMessage, isAccessDeniedApiError, normalizeApiError } from '@/shared/lib/api-errors'
import { cn } from '@/shared/lib/cn'
import { formatDate, formatDateTime } from '@/shared/lib/format'
import { hasAnyRole } from '@/shared/lib/roles'
import type { GroupMemberRole, GroupSubgroupMode, SubgroupValue } from '@/shared/types/api'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { DataTable } from '@/shared/ui/DataTable'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { SectionTabs } from '@/shared/ui/SectionTabs'
import { Select } from '@/shared/ui/Select'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { UserAvatar } from '@/shared/ui/UserAvatar'
import { Breadcrumbs } from '@/widgets/common/Breadcrumbs'
import { MetricCard } from '@/widgets/common/MetricCard'
import { RiskBadge } from '@/widgets/common/RiskBadge'

type GroupTab = 'overview' | 'students' | 'schedule' | 'analytics' | 'settings'

const subgroupValues: SubgroupValue[] = ['ALL', 'FIRST', 'SECOND']
const memberRoleValues: GroupMemberRole[] = ['STUDENT', 'STAROSTA']
const subgroupModeValues: GroupSubgroupMode[] = ['NONE', 'TWO_SUBGROUPS']

export function GroupDetailPage() {
  const { t } = useTranslation()
  const { groupId = '' } = useParams()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const { primaryRole, roles, session } = useAuth()
  const queryClient = useQueryClient()
  const canManageGroup = hasAnyRole(roles, ['ADMIN', 'OWNER'])
  const readOnlyRosterMode = !canManageGroup
  const [activeTab, setActiveTab] = useState<GroupTab>(resolveInitialGroupTab(searchParams.get('tab')))
  const [studentSearch, setStudentSearch] = useState('')
  const [selectedStudentIds, setSelectedStudentIds] = useState<string[]>([])
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'error'; message: string } | null>(null)
  const [importFile, setImportFile] = useState<File | null>(null)
  const [isImporting, setIsImporting] = useState(false)
  const [settingsForm, setSettingsForm] = useState({
    name: '',
    specialtyId: '',
    studyYear: '',
    streamId: '',
    subgroupMode: 'NONE' as GroupSubgroupMode,
  })
  const [settingsTouched, setSettingsTouched] = useState(false)
  const [resolvedSemesterNumber, setResolvedSemesterNumber] = useState('')
  const [overrideEditorOpen, setOverrideEditorOpen] = useState(false)
  const [editingOverrideId, setEditingOverrideId] = useState<string | null>(null)
  const [overrideForm, setOverrideForm] = useState({
    subjectId: '',
    enabled: true,
    lectureCountOverride: '',
    practiceCountOverride: '',
    labCountOverride: '',
    notes: '',
  })
  const [overrideError, setOverrideError] = useState<string | null>(null)
  const [overrideRequestId, setOverrideRequestId] = useState<string | null>(null)
  const weekRange = useMemo(() => buildCurrentWeekRange(), [])
  const requiresReadOnlyAccessCheck = !canManageGroup && Boolean(session?.user.id)
  const accessibleGroupsQuery = useQuery({
    queryKey: ['education', 'group-detail-access', primaryRole, session?.user.id],
    queryFn: () => loadAccessibleGroups(primaryRole, session?.user.id ?? ''),
    enabled: requiresReadOnlyAccessCheck,
    staleTime: 1000 * 60 * 3,
  })
  const canReadGroupByMembership = canManageGroup
    || (accessibleGroupsQuery.data ?? []).some((group) => group.id === groupId)
  const canLoadGroupQuery = Boolean(groupId)
    && (canManageGroup || (!requiresReadOnlyAccessCheck ? false : canReadGroupByMembership))

  const groupQuery = useQuery({
    queryKey: ['education', 'group', groupId],
    queryFn: () => educationService.getGroup(groupId),
    enabled: canLoadGroupQuery,
  })
  const canLoadGroupData = Boolean(groupId) && groupQuery.status === 'success'
  const membersQuery = useQuery({
    queryKey: ['education', 'group-members', groupId],
    queryFn: () => educationService.listGroupStudents(groupId),
    enabled: canLoadGroupData,
  })
  const subjectsQuery = useQuery({
    queryKey: ['education', 'group-subjects', groupId],
    queryFn: () => educationService.getSubjectsByGroup(groupId, { page: 0, size: 100, sortBy: 'name', direction: 'asc' }),
    enabled: canLoadGroupData,
  })
  const groupOverviewQuery = useQuery({
    queryKey: ['analytics', 'group-overview', groupId],
    queryFn: () => analyticsService.getGroupOverview(groupId),
    enabled: canLoadGroupData,
  })
  const analyticsStudentsQuery = useQuery({
    queryKey: ['analytics', 'group-students', groupId],
    queryFn: () => analyticsService.getGroupStudents(groupId, { page: 0, size: 50 }),
    enabled: canLoadGroupData && canManageGroup,
  })
  const scheduleQuery = useQuery({
    queryKey: ['schedule', 'group-week', groupId, weekRange],
    queryFn: () => scheduleService.getGroupRange(groupId, weekRange.dateFrom, weekRange.dateTo),
    enabled: canLoadGroupData && canManageGroup,
  })
  const slotQuery = useQuery({
    queryKey: ['schedule', 'slots'],
    queryFn: () => scheduleService.listSlots(),
    enabled: canLoadGroupData && canManageGroup,
  })
  const roomQuery = useQuery({
    queryKey: ['schedule', 'rooms'],
    queryFn: () => scheduleService.listRooms(),
    enabled: canLoadGroupData && canManageGroup,
  })
  const specialtiesQuery = useQuery({
    queryKey: ['education', 'specialties', 'group-settings'],
    queryFn: () => specialtyService.list({ active: true }),
    enabled: canManageGroup,
    staleTime: 1000 * 60 * 5,
  })
  const streamSpecialtyFilter = settingsTouched ? settingsForm.specialtyId : (groupQuery.data?.specialtyId ?? '')
  const streamStudyYearFilter = settingsTouched
    ? settingsForm.studyYear
    : (groupQuery.data?.studyYear ? String(groupQuery.data.studyYear) : '')
  const streamsQuery = useQuery({
    queryKey: ['education', 'streams', 'group-settings', streamSpecialtyFilter, streamStudyYearFilter],
    queryFn: () => streamService.list({
      active: true,
      specialtyId: streamSpecialtyFilter || undefined,
      studyYear: streamStudyYearFilter ? Number(streamStudyYearFilter) : undefined,
    }),
    enabled: canManageGroup,
    staleTime: 1000 * 60 * 5,
  })
  const groupOverridesQuery = useQuery({
    queryKey: ['education', 'group-overrides', groupId],
    queryFn: () => groupCurriculumOverrideService.list(groupId),
    enabled: canLoadGroupData,
  })
  const resolvedSubjectsQuery = useQuery({
    queryKey: ['education', 'group-resolved-subjects', groupId, resolvedSemesterNumber],
    queryFn: () => resolvedSubjectsService.listByGroup(
      groupId,
      resolvedSemesterNumber ? Number(resolvedSemesterNumber) : undefined,
    ),
    enabled: canLoadGroupData,
  })

  const relatedUserIds = useMemo(() => {
    const memberIds = membersQuery.data?.map((member) => member.userId) ?? []
    const subjectTeacherIds = subjectsQuery.data?.items.flatMap((subject) => subject.teacherIds) ?? []
    const scheduleTeacherIds = scheduleQuery.data?.map((lesson) => lesson.teacherId) ?? []
    const resolvedTeacherIds = resolvedSubjectsQuery.data?.flatMap((subject) => subject.teacherIds) ?? []

    return Array.from(new Set([...memberIds, ...subjectTeacherIds, ...scheduleTeacherIds, ...resolvedTeacherIds])).filter(Boolean)
  }, [membersQuery.data, resolvedSubjectsQuery.data, scheduleQuery.data, subjectsQuery.data?.items])
  const userDirectoryQuery = useQuery({
    queryKey: ['auth', 'directory', relatedUserIds.join(',')],
    queryFn: () => userDirectoryService.lookup(relatedUserIds),
    enabled: relatedUserIds.length > 0,
  })

  const studentCandidatesQuery = useQuery({
    queryKey: ['admin', 'student-candidates', studentSearch],
    queryFn: () => adminUserService.list({
      page: 0,
      size: 20,
      role: 'STUDENT',
      search: studentSearch.trim() || undefined,
    }),
    enabled: canManageGroup,
  })
  const addStudentMutation = useMutation({
    mutationFn: async (userIds: string[]) => {
      for (const userId of userIds) {
        await educationService.addGroupStudent(groupId, { userId })
      }
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'group-members', groupId] })
      setSelectedStudentIds([])
      setFeedback({ tone: 'success', message: t('education.studentsAddedSuccess') })
    },
    onError: () => setFeedback({ tone: 'error', message: t('education.studentsAddedError') }),
  })
  const updateMemberMutation = useMutation({
    mutationFn: ({ userId, role, subgroup }: { userId: string; role: GroupMemberRole; subgroup: SubgroupValue }) =>
      educationService.updateGroupStudent(groupId, userId, { role, subgroup }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'group-members', groupId] })
      setFeedback({ tone: 'success', message: t('education.membershipUpdatedSuccess') })
    },
    onError: () => setFeedback({ tone: 'error', message: t('education.membershipUpdatedError') }),
  })
  const removeMemberMutation = useMutation({
    mutationFn: (userId: string) => educationService.removeGroupStudent(groupId, userId),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'group-members', groupId] })
      setFeedback({ tone: 'success', message: t('education.studentRemovedSuccess') })
    },
    onError: () => setFeedback({ tone: 'error', message: t('education.studentRemovedError') }),
  })
  const updateGroupMutation = useMutation({
    mutationFn: (payload: {
      name: string
      specialtyId: string | null
      studyYear: number | null
      streamId: string | null
      subgroupMode: GroupSubgroupMode
    }) => educationService.updateGroup(groupId, payload),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'group', groupId] })
      await queryClient.invalidateQueries({ queryKey: ['education', 'group-resolved-subjects', groupId] })
      setSettingsTouched(false)
      setFeedback({ tone: 'success', message: t('academic.groupSettings.saveSuccess') })
    },
    onError: (error) => {
      setFeedback({ tone: 'error', message: getLocalizedApiErrorMessage(normalizeApiError(error), t) })
    },
  })
  const createOverrideMutation = useMutation({
    mutationFn: () => groupCurriculumOverrideService.create(groupId, {
      subjectId: overrideForm.subjectId,
      enabled: overrideForm.enabled,
      lectureCountOverride: normalizeOptionalCount(overrideForm.lectureCountOverride),
      practiceCountOverride: normalizeOptionalCount(overrideForm.practiceCountOverride),
      labCountOverride: normalizeOptionalCount(overrideForm.labCountOverride),
      notes: overrideForm.notes.trim() || null,
    }),
    onSuccess: async () => {
      await invalidateGroupAcademicQueries(queryClient, groupId)
      resetOverrideEditor()
      setFeedback({ tone: 'success', message: t('academic.overrides.createSuccess') })
    },
    onError: (error) => {
      const normalized = normalizeApiError(error)
      setOverrideError(getLocalizedApiErrorMessage(normalized, t))
      setOverrideRequestId(normalized?.requestId ?? null)
    },
  })
  const updateOverrideMutation = useMutation({
    mutationFn: () => groupCurriculumOverrideService.update(groupId, editingOverrideId!, {
      enabled: overrideForm.enabled,
      lectureCountOverride: normalizeOptionalCount(overrideForm.lectureCountOverride),
      practiceCountOverride: normalizeOptionalCount(overrideForm.practiceCountOverride),
      labCountOverride: normalizeOptionalCount(overrideForm.labCountOverride),
      notes: overrideForm.notes.trim() || null,
    }),
    onSuccess: async () => {
      await invalidateGroupAcademicQueries(queryClient, groupId)
      resetOverrideEditor()
      setFeedback({ tone: 'success', message: t('academic.overrides.updateSuccess') })
    },
    onError: (error) => {
      const normalized = normalizeApiError(error)
      setOverrideError(getLocalizedApiErrorMessage(normalized, t))
      setOverrideRequestId(normalized?.requestId ?? null)
    },
  })
  const deleteOverrideMutation = useMutation({
    mutationFn: (overrideId: string) => groupCurriculumOverrideService.remove(groupId, overrideId),
    onSuccess: async () => {
      await invalidateGroupAcademicQueries(queryClient, groupId)
      setFeedback({ tone: 'success', message: t('academic.overrides.deleteSuccess') })
    },
    onError: (error) => {
      setFeedback({ tone: 'error', message: getLocalizedApiErrorMessage(normalizeApiError(error), t) })
    },
  })
  if (requiresReadOnlyAccessCheck && accessibleGroupsQuery.isLoading) {
    return <LoadingState />
  }

  if (requiresReadOnlyAccessCheck && accessibleGroupsQuery.isError) {
    return <ErrorState description={t('education.groupLoadFailed')} title={t('navigation.shared.groups')} />
  }

  if (requiresReadOnlyAccessCheck && !canReadGroupByMembership) {
    return <AccessDeniedPage />
  }

  if (groupQuery.isLoading || membersQuery.isLoading || subjectsQuery.isLoading) {
    return <LoadingState />
  }

  if (groupQuery.isError || membersQuery.isError || subjectsQuery.isError) {
    if (
      isAccessDeniedApiError(groupQuery.error)
      || isAccessDeniedApiError(membersQuery.error)
      || isAccessDeniedApiError(subjectsQuery.error)
    ) {
      return <AccessDeniedPage />
    }

    return <ErrorState description={t('education.groupLoadFailed')} title={t('navigation.shared.groups')} />
  }

  if (!groupQuery.data) {
    return <ErrorState description={t('common.states.notFound')} title={t('navigation.shared.groups')} />
  }

  const group = groupQuery.data
  const visibleTab = readOnlyRosterMode
    ? (activeTab === 'students' ? 'students' : 'overview')
    : activeTab
  const members = membersQuery.data ?? []
  const subjects = subjectsQuery.data?.items ?? []
  const studentSnapshots = analyticsStudentsQuery.data?.items ?? []
  const candidateStudents = studentCandidatesQuery.data?.content ?? []
  const userMap = new Map((userDirectoryQuery.data ?? []).map((user) => [user.id, user]))
  const subjectMap = new Map(subjects.map((subject) => [subject.id, subject.name]))
  const slotMap = new Map((slotQuery.data ?? []).map((slot) => [slot.id, slot]))
  const roomMap = new Map((roomQuery.data ?? []).map((room) => [room.id, `${room.building} · ${room.code}`]))
  const weekDays = buildWeekDays(weekRange.dateFrom)
  const lessonsByDate = new Map<string, typeof scheduleQuery.data>()
  for (const date of weekDays) {
    lessonsByDate.set(date, [])
  }
  for (const lesson of scheduleQuery.data ?? []) {
    const items = lessonsByDate.get(lesson.date) ?? []
    items.push(lesson)
    lessonsByDate.set(lesson.date, items)
  }

  const effectiveSettingsForm = settingsTouched
    ? settingsForm
    : {
        name: group.name,
        specialtyId: group.specialtyId ?? '',
        studyYear: group.studyYear ? String(group.studyYear) : '',
        streamId: group.streamId ?? '',
        subgroupMode: group.subgroupMode ?? 'NONE',
      }
  const specialtiesById = new Map((specialtiesQuery.data ?? []).map((item) => [item.id, item]))
  const streamOptions = (streamsQuery.data ?? []).filter((stream) => {
    if (effectiveSettingsForm.specialtyId && stream.specialtyId !== effectiveSettingsForm.specialtyId) {
      return false
    }
    if (effectiveSettingsForm.studyYear && stream.studyYear !== Number(effectiveSettingsForm.studyYear)) {
      return false
    }
    return true
  })
  const settingsDisabledReason = resolveGroupSettingsDisabledReason(effectiveSettingsForm, t)
  const overrideDisabledReason = resolveOverrideDisabledReason(overrideForm, t)

  return (
    <div className="space-y-6">
      <Breadcrumbs
        items={[
          canManageGroup
            ? { label: t('navigation.groups.academicManagement'), to: '/academic' }
            : { label: t('navigation.shared.groups'), to: '/groups' },
          { label: t('navigation.shared.groups'), to: location.pathname.startsWith('/academic') ? '/academic/groups' : '/groups' },
          { label: group.name },
        ]}
      />

      <PageHeader
        actions={(
          <div className="flex flex-wrap gap-3">
            <Link to={location.pathname.startsWith('/academic') ? '/academic/groups' : '/groups'}>
              <Button variant="secondary">{t('education.backToGroups')}</Button>
            </Link>
            {canManageGroup ? (
              <>
                <Link to="/academic">
                  <Button variant="secondary">{t('navigation.groups.academicManagement')}</Button>
                </Link>
                <Link to={`/schedule/groups/${groupId}`}>
                  <Button variant="ghost">{t('navigation.shared.schedule')}</Button>
                </Link>
              </>
            ) : null}
          </div>
        )}
        description={t('education.groupHubDescription')}
        title={group.name}
      />

      {feedback ? (
        <Card className={cn(
          'space-y-1 border',
          feedback.tone === 'success' ? 'border-success/30 bg-success/5' : 'border-danger/20 bg-danger/5',
        )}
        >
          <p className="text-sm font-semibold text-text-primary">{feedback.message}</p>
        </Card>
      ) : null}

      <SectionTabs
        activeId={visibleTab}
        items={[
          { id: 'overview', label: t('education.groupTabs.overview') },
          { id: 'students', label: t('education.groupTabs.students') },
          ...(canManageGroup ? [{ id: 'schedule', label: t('education.groupTabs.schedule') }] : []),
          ...(canManageGroup ? [{ id: 'analytics', label: t('education.groupTabs.analytics') }] : []),
          ...(canManageGroup ? [{ id: 'settings', label: t('education.groupTabs.settings') }] : []),
        ]}
        onChange={(tabId) => setActiveTab(tabId as GroupTab)}
      />

      {visibleTab === 'overview' ? (
        <div className="space-y-6">
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <MetricCard title={t('education.overview.students')} value={members.length} />
            <MetricCard title={t('education.overview.subjects')} value={subjects.length} />
            <MetricCard title={t('education.overview.starosta')} value={members.filter((member) => member.role === 'STAROSTA').length} />
            <MetricCard title={t('dashboard.metrics.averageScore')} value={groupOverviewQuery.data?.averageScore ?? '-'} />
          </div>

          <div className="grid gap-6 xl:grid-cols-[1.2fr_0.8fr]">
            <Card className="space-y-4">
              <PageHeader title={t('education.groupOverviewTitle')} />
              <div className="space-y-3 text-sm leading-6 text-text-secondary">
                <p>{t('education.groupOverviewDescription')}</p>
                <p>{t('education.groupStudentCountLabel', { count: members.length })}</p>
                <p>{t('education.groupSubjectCountLabel', { count: subjects.length })}</p>
              </div>
            </Card>
            <Card className="space-y-4">
              <PageHeader title={t('education.groupProgressTitle')} />
              {groupOverviewQuery.isLoading ? (
                <LoadingState />
              ) : groupOverviewQuery.data ? (
                <div className="grid gap-4 md:grid-cols-2">
                  <MetricCard title={t('dashboard.metrics.activityScore')} value={groupOverviewQuery.data.averageActivityScore} />
                  <MetricCard title={t('dashboard.metrics.disciplineScore')} value={groupOverviewQuery.data.averageDisciplineScore} />
                  <MetricCard title={t('education.overview.atRiskStudents')} value={groupOverviewQuery.data.highRiskStudentsCount} />
                  <MetricCard title={t('education.overview.missedDeadlinesRaw')} value={groupOverviewQuery.data.totalMissedDeadlines} />
                </div>
              ) : (
                <EmptyState description={t('education.emptyAnalytics')} title={t('education.groupProgressTitle')} />
              )}
            </Card>
          </div>

          <Card className="space-y-4">
            <PageHeader
              description={t('education.groupSubjectsReadonlyDescription')}
              title={t('education.groupSubjectsReadonlyTitle')}
            />
            {subjects.length === 0 ? (
              <EmptyState description={t('education.noSubjectsInGroup')} title={t('education.groupSubjectsReadonlyTitle')} />
            ) : (
              <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
                {subjects.slice(0, 6).map((subject) => (
                  <Link
                    key={subject.id}
                    className="rounded-[16px] border border-border bg-surface-muted p-4 transition hover:border-border-strong"
                    to={`/subjects/${subject.id}`}
                  >
                    <p className="font-semibold text-text-primary">{subject.name}</p>
                    <p className="mt-1 text-sm leading-6 text-text-secondary">
                      {subject.teacherIds.length === 0
                        ? t('education.noTeachersAssigned')
                        : subject.teacherIds.map((teacherId) => userMap.get(teacherId)?.username ?? t('education.unknownTeacher')).join(', ')}
                    </p>
                  </Link>
                ))}
              </div>
            )}
          </Card>
        </div>
      ) : null}

      {visibleTab === 'students' ? (
        <div className="space-y-6">
          {canManageGroup ? (
            <div className="grid gap-6 xl:grid-cols-[1.15fr_0.85fr]">
              <Card className="space-y-4">
                <PageHeader
                  description={t('education.groupStudentsSearchDescription')}
                  title={t('education.addStudents')}
                />
                <FormField label={t('common.labels.query')}>
                  <Input value={studentSearch} onChange={(event) => setStudentSearch(event.target.value)} />
                </FormField>
                {studentCandidatesQuery.isLoading ? <LoadingState /> : null}
                {candidateStudents.length === 0 ? (
                  <EmptyState
                    description={t('education.emptyStudentCandidates')}
                    title={t('education.addStudents')}
                  />
                ) : (
                  <div className="space-y-3">
                    {candidateStudents.map((student) => {
                      const alreadyInGroup = members.some((member) => member.userId === student.id)
                      const selected = selectedStudentIds.includes(student.id)
                      return (
                        <label
                          key={student.id}
                          className={cn(
                            'flex items-start gap-3 rounded-[18px] border border-border bg-surface-muted p-4',
                            selected ? 'border-accent bg-accent-muted/40' : undefined,
                          )}
                        >
                          <input
                            checked={selected}
                            disabled={alreadyInGroup}
                            type="checkbox"
                            onChange={(event) => {
                              setSelectedStudentIds((current) => (
                                event.target.checked
                                  ? [...current, student.id]
                                  : current.filter((value) => value !== student.id)
                              ))
                            }}
                          />
                          <UserAvatar alt={student.username} email={student.email} username={student.username} size="sm" />
                          <div className="min-w-0 flex-1">
                            <p className="font-medium text-text-primary">{student.username}</p>
                            <p className="text-sm text-text-secondary">{student.email}</p>
                            {alreadyInGroup ? (
                              <p className="mt-1 text-xs font-semibold text-warning">{t('education.alreadyInGroup')}</p>
                            ) : null}
                          </div>
                        </label>
                      )
                    })}
                  </div>
                )}
                <Button
                  disabled={selectedStudentIds.length === 0 || addStudentMutation.isPending}
                  onClick={() => addStudentMutation.mutate(selectedStudentIds)}
                >
                  {t('education.addSelectedStudents')}
                </Button>
              </Card>

              <Card className="space-y-4">
                <PageHeader
                  description={t('education.groupImportCsvDescription')}
                  title={t('education.importStudentsCsv')}
                />
                <FormField label={t('education.csvFile')}>
                  <Input
                    accept=".csv,text/csv"
                    type="file"
                    onChange={(event) => setImportFile(event.target.files?.[0] ?? null)}
                  />
                </FormField>
                <p className="text-sm leading-6 text-text-secondary">{t('education.groupImportCsvHint')}</p>
                <Button
                  disabled={!importFile || isImporting}
                  onClick={async () => {
                    if (!importFile) {
                      return
                    }

                    setIsImporting(true)
                    try {
                      const identifiers = await parseStudentCsvIdentifiers(importFile)
                      const matchedStudentIds = await resolveStudentIdentifiers(identifiers)
                      const missingCount = identifiers.length - matchedStudentIds.length
                      const newStudentIds = matchedStudentIds.filter((userId) => !members.some((member) => member.userId === userId))
                      if (newStudentIds.length > 0) {
                        await addStudentMutation.mutateAsync(newStudentIds)
                      }
                      setFeedback({
                        tone: 'success',
                        message: t('education.csvImportSummary', {
                          added: newStudentIds.length,
                          missing: missingCount,
                        }),
                      })
                    } catch {
                      setFeedback({ tone: 'error', message: t('education.csvImportError') })
                    } finally {
                      setIsImporting(false)
                    }
                  }}
                >
                  {t('education.importStudentsCsv')}
                </Button>
              </Card>
            </div>
          ) : null}

          {members.length === 0 ? (
            <EmptyState description={t('education.noGroupStudents')} title={t('education.groupTabs.students')} />
          ) : (
            <DataTable
              getRowId={(member) => member.userId}
              columns={[
                {
                  key: 'student',
                  header: t('education.student'),
                  render: (member) => {
                    const user = userMap.get(member.userId)
                    return (
                        <div className="flex items-start gap-3">
                          <UserAvatar
                            alt={user?.username ?? t('education.unknownStudent')}
                            email={user?.email}
                            username={user?.username}
                            size="sm"
                          />
                        <div className="space-y-1">
                          <p className="font-medium text-text-primary">{user?.username ?? t('education.unknownStudent')}</p>
                          {user?.email ? <p className="text-xs text-text-secondary">{user.email}</p> : null}
                        </div>
                      </div>
                    )
                  },
                },
                {
                  key: 'role',
                  header: t('education.groupRole'),
                  render: (member) => (
                    canManageGroup ? (
                      <Select
                        value={member.role}
                        onChange={(event) => updateMemberMutation.mutate({
                          userId: member.userId,
                          role: event.target.value as GroupMemberRole,
                          subgroup: member.subgroup,
                        })}
                      >
                        {memberRoleValues.map((value) => (
                          <option key={value} value={value}>{t(`education.memberRole.${value}`)}</option>
                        ))}
                      </Select>
                    ) : (
                      <span className="text-sm font-medium text-text-primary">{t(`education.memberRole.${member.role}`)}</span>
                    )
                  ),
                },
                {
                  key: 'subgroup',
                  header: t('education.subgroup'),
                  render: (member) => (
                    canManageGroup ? (
                      <Select
                        value={member.subgroup}
                        onChange={(event) => updateMemberMutation.mutate({
                          userId: member.userId,
                          role: member.role,
                          subgroup: event.target.value as SubgroupValue,
                        })}
                      >
                        {subgroupValues.map((value) => (
                          <option key={value} value={value}>{t(`education.subgroups.${value}`)}</option>
                        ))}
                      </Select>
                    ) : (
                      <span className="text-sm font-medium text-text-primary">{t(`education.subgroups.${member.subgroup}`)}</span>
                    )
                  ),
                },
                {
                  key: 'membershipCount',
                  header: t('education.groupMemberships'),
                  render: (member) => (
                    <div className="space-y-1">
                      <span>{member.groupMembershipCount}</span>
                      {member.groupMembershipCount > 1 ? (
                        <p className="text-xs font-semibold text-warning">{t('education.multiGroupWarning')}</p>
                      ) : null}
                    </div>
                  ),
                },
                {
                  key: 'actions',
                  header: t('common.labels.actions'),
                  render: (member) => (
                    canManageGroup ? (
                      <Button
                        variant="ghost"
                        onClick={(event) => {
                          event.stopPropagation()
                          removeMemberMutation.mutate(member.userId)
                        }}
                      >
                        {t('common.actions.remove')}
                      </Button>
                    ) : '—'
                  ),
                },
              ]}
              rows={members}
            />
          )}
        </div>
      ) : null}

      {visibleTab === 'schedule' ? (
        scheduleQuery.isLoading ? (
          <LoadingState />
        ) : scheduleQuery.isError ? (
          <ErrorState description={t('common.states.error')} title={t('education.groupTabs.schedule')} />
        ) : (
          <div className="grid gap-4 xl:grid-cols-2">
            {weekDays.map((date) => (
              <Card key={date} className="space-y-4">
                <div className="space-y-1">
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">
                    {formatDate(date)}
                  </p>
                </div>
                {(lessonsByDate.get(date) ?? []).length === 0 ? (
                  <p className="text-sm leading-6 text-text-secondary">{t('schedule.noLessonsYet')}</p>
                ) : (
                  <div className="space-y-3">
                    {(lessonsByDate.get(date) ?? [])
                      .slice()
                      .sort((left, right) => (slotMap.get(left.slotId)?.number ?? 99) - (slotMap.get(right.slotId)?.number ?? 99))
                      .map((lesson) => {
                        const slot = slotMap.get(lesson.slotId)
                        return (
                          <div key={`${lesson.date}-${lesson.slotId}-${lesson.teacherId}`} className="rounded-[18px] border border-border bg-surface-muted p-4">
                            <div className="flex flex-wrap items-start justify-between gap-3">
                              <div className="space-y-1">
                                <p className="text-sm font-semibold text-text-primary">
                                  {slot ? t('schedule.pairSummary', { number: slot.number, start: slot.startTime, end: slot.endTime }) : t('schedule.pairFallback')}
                                </p>
                                <p className="text-base font-semibold text-text-primary">
                                  {subjectMap.get(lesson.subjectId) ?? t('education.subject')}
                                </p>
                              </div>
                              <span className="rounded-full bg-accent-muted px-2.5 py-1 text-xs font-semibold text-accent">
                                {t(`schedule.lessonType.${lesson.lessonType}`)}
                              </span>
                            </div>
                            <div className="mt-3 space-y-1 text-sm leading-6 text-text-secondary">
                              <p>{t(`schedule.lessonFormat.${lesson.lessonFormat}`)} · {t(`schedule.weekType.${lesson.weekType}`)}</p>
                              <p>{t('education.teacherLabel')}: {userMap.get(lesson.teacherId)?.username ?? t('education.unknownTeacher')}</p>
                              <p>
                                {lesson.roomId
                                  ? (roomMap.get(lesson.roomId) ?? t('schedule.roomAssigned'))
                                  : (lesson.lessonFormat === 'ONLINE' ? t('schedule.lessonFormat.ONLINE') : t('schedule.linkWillBeAddedLater'))}
                              </p>
                              {lesson.onlineMeetingUrl ? (
                                <a
                                  className="inline-flex min-h-10 items-center rounded-[10px] border border-border bg-surface px-3 text-sm font-medium text-accent transition hover:border-accent/40"
                                  href={lesson.onlineMeetingUrl}
                                  rel="noreferrer"
                                  target="_blank"
                                >
                                  {t('schedule.joinLesson')}
                                </a>
                              ) : null}
                              {lesson.notes ? <p>{lesson.notes}</p> : null}
                            </div>
                          </div>
                        )
                      })}
                  </div>
                )}
              </Card>
            ))}
          </div>
        )
      ) : null}

      {visibleTab === 'analytics' ? (
        analyticsStudentsQuery.isLoading ? (
          <LoadingState />
        ) : analyticsStudentsQuery.isError ? (
          <ErrorState description={t('common.states.error')} title={t('education.groupTabs.analytics')} />
        ) : studentSnapshots.length === 0 ? (
          <EmptyState description={t('education.emptyAnalytics')} title={t('education.groupTabs.analytics')} />
        ) : (
          <DataTable
            columns={[
              {
                key: 'averageScore',
                header: t('dashboard.metrics.averageScore'),
                render: (row) => row.averageScore ?? '-',
              },
              {
                key: 'activityScore',
                header: t('dashboard.metrics.activityScore'),
                render: (row) => row.activityScore,
              },
              {
                key: 'disciplineScore',
                header: t('dashboard.metrics.disciplineScore'),
                render: (row) => row.disciplineScore,
              },
              {
                key: 'riskLevel',
                header: t('analytics.riskLevel.label'),
                render: (row) => <RiskBadge value={row.riskLevel} />,
              },
              {
                key: 'updatedAt',
                header: t('analytics.updatedAt'),
                render: (row) => formatDateTime(row.updatedAt),
              },
            ]}
            rows={studentSnapshots}
          />
        )
      ) : null}

      {visibleTab === 'settings' ? (
        <div className="space-y-6">
          <Card className="space-y-4">
            <PageHeader
              description={t('academic.groupSettings.description')}
              title={t('academic.groupSettings.title')}
            />
            {!effectiveSettingsForm.specialtyId || !effectiveSettingsForm.studyYear ? (
              <div className="rounded-[14px] border border-warning/30 bg-warning/5 px-3 py-2">
                <p className="text-sm text-text-primary">{t('academic.groupSettings.incompleteContextWarning')}</p>
              </div>
            ) : null}
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
              <FormField label={t('common.labels.name')}>
                <Input
                  value={effectiveSettingsForm.name}
                  onChange={(event) => {
                    setSettingsTouched(true)
                    setSettingsForm({ ...effectiveSettingsForm, name: event.target.value })
                  }}
                />
              </FormField>
              <FormField label={t('academic.specialties.specialty')}>
                <Select
                  value={effectiveSettingsForm.specialtyId}
                  onChange={(event) => {
                    setSettingsTouched(true)
                    const specialtyId = event.target.value
                    setSettingsForm({
                      ...effectiveSettingsForm,
                      specialtyId,
                      streamId: specialtyId ? effectiveSettingsForm.streamId : '',
                    })
                  }}
                >
                  <option value="">{t('academic.groupSettings.noSpecialty')}</option>
                  {(specialtiesQuery.data ?? []).map((specialty) => (
                    <option key={specialty.id} value={specialty.id}>
                      {specialty.code} · {specialty.name}
                    </option>
                  ))}
                </Select>
              </FormField>
              <FormField label={t('academic.studyYear')}>
                <Input
                  max={8}
                  min={1}
                  type="number"
                  value={effectiveSettingsForm.studyYear}
                  onChange={(event) => {
                    setSettingsTouched(true)
                    setSettingsForm({ ...effectiveSettingsForm, studyYear: event.target.value, streamId: '' })
                  }}
                />
              </FormField>
              <FormField
                hint={effectiveSettingsForm.specialtyId && effectiveSettingsForm.studyYear ? undefined : t('academic.groupSettings.streamHint')}
                label={t('academic.streams.stream')}
              >
                <Select
                  value={effectiveSettingsForm.streamId}
                  onChange={(event) => {
                    setSettingsTouched(true)
                    setSettingsForm({ ...effectiveSettingsForm, streamId: event.target.value })
                  }}
                >
                  <option value="">{t('academic.groupSettings.noStream')}</option>
                  {streamOptions.map((stream) => (
                    <option key={stream.id} value={stream.id}>
                      {stream.name}
                    </option>
                  ))}
                </Select>
              </FormField>
              <FormField label={t('academic.subgroupMode.label')}>
                <Select
                  value={effectiveSettingsForm.subgroupMode}
                  onChange={(event) => {
                    setSettingsTouched(true)
                    setSettingsForm({ ...effectiveSettingsForm, subgroupMode: event.target.value as GroupSubgroupMode })
                  }}
                >
                  {subgroupModeValues.map((value) => (
                    <option key={value} value={value}>
                      {t(`academic.subgroupMode.${value}`)}
                    </option>
                  ))}
                </Select>
              </FormField>
            </div>
            <div className="flex flex-wrap items-center gap-3">
              <Button
                disabled={Boolean(settingsDisabledReason) || updateGroupMutation.isPending}
                onClick={() => updateGroupMutation.mutate({
                  name: effectiveSettingsForm.name.trim(),
                  specialtyId: effectiveSettingsForm.specialtyId || null,
                  studyYear: effectiveSettingsForm.studyYear ? Number(effectiveSettingsForm.studyYear) : null,
                  streamId: effectiveSettingsForm.streamId || null,
                  subgroupMode: effectiveSettingsForm.subgroupMode,
                })}
              >
                {t('common.actions.save')}
              </Button>
              <Button
                variant="secondary"
                onClick={() => {
                  setSettingsTouched(false)
                  setSettingsForm({
                    name: group.name,
                    specialtyId: group.specialtyId ?? '',
                    studyYear: group.studyYear ? String(group.studyYear) : '',
                    streamId: group.streamId ?? '',
                    subgroupMode: group.subgroupMode ?? 'NONE',
                  })
                }}
              >
                <RotateCcw className="mr-2 h-4 w-4" />
                {t('common.actions.reset')}
              </Button>
            </div>
            {settingsDisabledReason ? (
              <p className="text-sm text-text-secondary">{settingsDisabledReason}</p>
            ) : null}
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
              <MetricCard title={t('academic.specialties.specialty')} value={group.specialtyId ? (specialtiesById.get(group.specialtyId)?.name ?? t('academic.specialties.specialtyUnknown')) : t('academic.groupSettings.noSpecialty')} />
              <MetricCard title={t('academic.studyYear')} value={group.studyYear ? t('academic.studyYearValue', { year: group.studyYear }) : t('academic.groupSettings.noStudyYear')} />
              <MetricCard title={t('academic.streams.stream')} value={group.streamId ? (streamsQuery.data?.find((stream) => stream.id === group.streamId)?.name ?? t('academic.groupSettings.noStream')) : t('academic.groupSettings.noStream')} />
              <MetricCard title={t('academic.subgroupMode.label')} value={t(`academic.subgroupMode.${group.subgroupMode ?? 'NONE'}`)} />
            </div>
          </Card>

          <Card className="space-y-4">
            <PageHeader
              className="mb-0"
              description={t('academic.resolvedSubjects.description')}
              title={t('academic.resolvedSubjects.title')}
            />
            <div className="grid gap-4 md:grid-cols-[220px_1fr]">
              <FormField label={t('academic.curriculum.semester')}>
                <Select value={resolvedSemesterNumber} onChange={(event) => setResolvedSemesterNumber(event.target.value)}>
                  <option value="">{t('academic.resolvedSubjects.currentSemester')}</option>
                  <option value="1">{t('academic.curriculum.semesterValue', { semester: 1 })}</option>
                  <option value="2">{t('academic.curriculum.semesterValue', { semester: 2 })}</option>
                </Select>
              </FormField>
              {!group.specialtyId || !group.studyYear ? (
                <div className="rounded-[14px] border border-warning/30 bg-warning/5 px-3 py-2">
                  <p className="text-sm text-text-primary">{t('academic.groupSettings.incompleteContextWarning')}</p>
                </div>
              ) : null}
            </div>
            {resolvedSubjectsQuery.isLoading ? <LoadingState /> : null}
            {resolvedSubjectsQuery.isError ? (
              <ErrorState
                description={t('common.states.error')}
                title={t('academic.resolvedSubjects.title')}
                onRetry={() => void resolvedSubjectsQuery.refetch()}
              />
            ) : null}
            {!resolvedSubjectsQuery.isLoading && !resolvedSubjectsQuery.isError && (resolvedSubjectsQuery.data?.length ?? 0) === 0 ? (
              <EmptyState
                description={t('academic.resolvedSubjects.empty')}
                title={t('academic.resolvedSubjects.title')}
              />
            ) : null}
            {(resolvedSubjectsQuery.data?.length ?? 0) > 0 ? (
              <div className="grid gap-3">
                {(resolvedSubjectsQuery.data ?? []).map((subject) => (
                  <div key={subject.subjectId} className="space-y-3 rounded-[16px] border border-border bg-surface px-4 py-4">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div>
                        <p className="text-lg font-semibold text-text-primary">{subject.subjectName}</p>
                        <div className="mt-1 flex flex-wrap gap-2">
                          <span className="rounded-full bg-accent-muted px-2.5 py-1 text-xs font-semibold uppercase tracking-[0.12em] text-accent">
                            {t(`academic.resolvedSubjects.source.${subject.source}`)}
                          </span>
                          {subject.disabledByOverride ? (
                            <span className="rounded-full bg-danger/10 px-2.5 py-1 text-xs font-semibold uppercase tracking-[0.12em] text-danger">
                              {t('academic.overrides.disabledByOverride')}
                            </span>
                          ) : null}
                        </div>
                      </div>
                    </div>
                    <div className="grid gap-3 text-sm text-text-secondary md:grid-cols-3">
                      <div>{t('academic.curriculum.lectureCount')}: <span className="font-semibold text-text-primary">{subject.lectureCount}</span></div>
                      <div>{t('academic.curriculum.practiceCount')}: <span className="font-semibold text-text-primary">{subject.practiceCount}</span></div>
                      <div>{t('academic.curriculum.labCount')}: <span className="font-semibold text-text-primary">{subject.labCount}</span></div>
                    </div>
                    <div className="grid gap-2 text-sm text-text-secondary">
                      <p>{t('academic.resolvedSubjects.teacherCount', { count: subject.teacherIds.length })}</p>
                      {subject.teacherIds.length > 0 ? (
                        <p>
                          {subject.teacherIds.map((teacherId) => userMap.get(teacherId)?.username ?? t('education.unknownTeacher')).join(', ')}
                        </p>
                      ) : null}
                      <p>
                        {subject.supportsStreamLecture
                          ? t('academic.curriculum.supportsStreamLecture')
                          : t('academic.resolvedSubjects.streamLectureNotEnabled')}
                      </p>
                      <p>
                        {subject.requiresSubgroupsForLabs
                          ? t('academic.curriculum.requiresSubgroupsForLabs')
                          : t('academic.resolvedSubjects.subgroupsNotRequired')}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            ) : null}
          </Card>

          <Card className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <PageHeader
                className="mb-0"
                description={t('academic.overrides.description')}
                title={t('academic.overrides.title')}
              />
              <Button
                onClick={() => {
                  setOverrideEditorOpen(true)
                  setEditingOverrideId(null)
                  setOverrideForm({
                    subjectId: '',
                    enabled: true,
                    lectureCountOverride: '',
                    practiceCountOverride: '',
                    labCountOverride: '',
                    notes: '',
                  })
                  setOverrideError(null)
                  setOverrideRequestId(null)
                }}
              >
                <Plus className="mr-2 h-4 w-4" />
                {t('academic.overrides.add')}
              </Button>
            </div>

            {overrideEditorOpen ? (
              <div className="space-y-3 rounded-[16px] border border-accent/30 bg-surface px-4 py-4">
                <div className="grid gap-4 md:grid-cols-2">
                  <FormField label={t('academic.subject')}>
                    <Select
                      disabled={Boolean(editingOverrideId)}
                      value={overrideForm.subjectId}
                      onChange={(event) => setOverrideForm((current) => ({ ...current, subjectId: event.target.value }))}
                    >
                      <option value="">{t('academic.validation.selectSubject')}</option>
                      {subjects.map((subject) => (
                        <option key={subject.id} value={subject.id}>{subject.name}</option>
                      ))}
                    </Select>
                  </FormField>
                  <label className="flex min-h-11 items-center gap-3 rounded-[14px] border border-border bg-surface-muted px-3 text-sm text-text-primary">
                    <input
                      checked={overrideForm.enabled}
                      type="checkbox"
                      onChange={(event) => setOverrideForm((current) => ({ ...current, enabled: event.target.checked }))}
                    />
                    {overrideForm.enabled ? t('academic.overrides.enabledForGroup') : t('academic.overrides.disableSubjectForGroup')}
                  </label>
                </div>
                <div className="grid gap-4 md:grid-cols-3">
                  <FormField label={t('academic.overrides.lectureCountOverride')}>
                    <Input
                      min={0}
                      type="number"
                      value={overrideForm.lectureCountOverride}
                      onChange={(event) => setOverrideForm((current) => ({ ...current, lectureCountOverride: event.target.value }))}
                    />
                  </FormField>
                  <FormField label={t('academic.overrides.practiceCountOverride')}>
                    <Input
                      min={0}
                      type="number"
                      value={overrideForm.practiceCountOverride}
                      onChange={(event) => setOverrideForm((current) => ({ ...current, practiceCountOverride: event.target.value }))}
                    />
                  </FormField>
                  <FormField label={t('academic.overrides.labCountOverride')}>
                    <Input
                      min={0}
                      type="number"
                      value={overrideForm.labCountOverride}
                      onChange={(event) => setOverrideForm((current) => ({ ...current, labCountOverride: event.target.value }))}
                    />
                  </FormField>
                </div>
                <FormField label={t('common.labels.notes')}>
                  <Input
                    value={overrideForm.notes}
                    onChange={(event) => setOverrideForm((current) => ({ ...current, notes: event.target.value }))}
                  />
                </FormField>
                {overrideError ? (
                  <div className="rounded-[14px] border border-danger/20 bg-danger/5 px-3 py-2 text-sm text-text-primary">
                    <p>{overrideError}</p>
                    {overrideRequestId ? (
                      <p className="mt-1 text-xs text-text-muted">{t('common.labels.requestId')}: {overrideRequestId}</p>
                    ) : null}
                  </div>
                ) : null}
                {overrideDisabledReason ? (
                  <p className="text-sm text-text-secondary">{overrideDisabledReason}</p>
                ) : null}
                <div className="flex flex-wrap gap-3">
                  <Button
                    disabled={Boolean(overrideDisabledReason) || createOverrideMutation.isPending || updateOverrideMutation.isPending}
                    onClick={() => {
                      setOverrideError(null)
                      setOverrideRequestId(null)
                      if (editingOverrideId) {
                        updateOverrideMutation.mutate()
                        return
                      }
                      createOverrideMutation.mutate()
                    }}
                  >
                    {editingOverrideId ? t('common.actions.update') : t('common.actions.create')}
                  </Button>
                  <Button
                    variant="secondary"
                    onClick={() => {
                      resetOverrideEditor()
                    }}
                  >
                    {t('common.actions.cancel')}
                  </Button>
                </div>
              </div>
            ) : null}

            {groupOverridesQuery.isLoading ? <LoadingState /> : null}
            {groupOverridesQuery.isError ? (
              <ErrorState
                description={t('common.states.error')}
                title={t('academic.overrides.title')}
                onRetry={() => void groupOverridesQuery.refetch()}
              />
            ) : null}
            {!groupOverridesQuery.isLoading && !groupOverridesQuery.isError && (groupOverridesQuery.data?.length ?? 0) === 0 ? (
              <EmptyState
                description={t('academic.overrides.empty')}
                title={t('academic.overrides.title')}
              />
            ) : null}
            {(groupOverridesQuery.data?.length ?? 0) > 0 ? (
              <div className="grid gap-3">
                {(groupOverridesQuery.data ?? []).map((override) => {
                  const subject = subjects.find((item) => item.id === override.subjectId)
                  return (
                    <div key={override.id} className="space-y-3 rounded-[16px] border border-border bg-surface px-4 py-4">
                      <div className="flex flex-wrap items-start justify-between gap-3">
                        <div>
                          <p className="font-semibold text-text-primary">{subject?.name ?? t('academic.curriculum.subjectUnknown')}</p>
                          <p className="text-sm text-text-secondary">
                            {override.enabled ? t('academic.overrides.enabledForGroup') : t('academic.overrides.disableSubjectForGroup')}
                          </p>
                        </div>
                        {!override.enabled ? (
                          <span className="rounded-full bg-danger/10 px-2.5 py-1 text-xs font-semibold uppercase tracking-[0.12em] text-danger">
                            {t('academic.overrides.disabledByOverride')}
                          </span>
                        ) : null}
                      </div>
                      <div className="grid gap-2 text-sm text-text-secondary md:grid-cols-3">
                        <div>{t('academic.overrides.lectureCountOverride')}: {override.lectureCountOverride ?? '—'}</div>
                        <div>{t('academic.overrides.practiceCountOverride')}: {override.practiceCountOverride ?? '—'}</div>
                        <div>{t('academic.overrides.labCountOverride')}: {override.labCountOverride ?? '—'}</div>
                      </div>
                      {override.notes ? (
                        <p className="text-sm text-text-secondary">{override.notes}</p>
                      ) : null}
                      <div className="flex flex-wrap gap-2">
                        <Button
                          variant="secondary"
                          onClick={() => {
                            setOverrideEditorOpen(true)
                            setEditingOverrideId(override.id)
                            setOverrideForm({
                              subjectId: override.subjectId,
                              enabled: override.enabled,
                              lectureCountOverride: override.lectureCountOverride?.toString() ?? '',
                              practiceCountOverride: override.practiceCountOverride?.toString() ?? '',
                              labCountOverride: override.labCountOverride?.toString() ?? '',
                              notes: override.notes ?? '',
                            })
                            setOverrideError(null)
                            setOverrideRequestId(null)
                          }}
                        >
                          <Pencil className="mr-2 h-4 w-4" />
                          {t('common.actions.edit')}
                        </Button>
                        <Button
                          disabled={deleteOverrideMutation.isPending}
                          variant="ghost"
                          onClick={() => deleteOverrideMutation.mutate(override.id)}
                        >
                          {t('common.actions.delete')}
                        </Button>
                      </div>
                    </div>
                  )
                })}
              </div>
            ) : null}
          </Card>
        </div>
      ) : null}
    </div>
  )

  function resetOverrideEditor() {
    setOverrideEditorOpen(false)
    setEditingOverrideId(null)
    setOverrideForm({
      subjectId: '',
      enabled: true,
      lectureCountOverride: '',
      practiceCountOverride: '',
      labCountOverride: '',
      notes: '',
    })
    setOverrideError(null)
    setOverrideRequestId(null)
  }

  async function resolveStudentIdentifiers(identifiers: string[]) {
    const resolvedIds: string[] = []
    for (const identifier of identifiers) {
      const page = await adminUserService.list({
        page: 0,
        size: 10,
        role: 'STUDENT',
        search: identifier,
      })
      const match = page.content.find((user) => {
        const normalizedIdentifier = identifier.toLowerCase()
        return user.username.toLowerCase() === normalizedIdentifier || user.email.toLowerCase() === normalizedIdentifier
      })
      if (match) {
        resolvedIds.push(match.id)
      }
    }
    return Array.from(new Set(resolvedIds))
  }
}

function buildCurrentWeekRange() {
  const today = new Date()
  const day = today.getDay()
  const mondayOffset = day === 0 ? -6 : 1 - day
  const monday = new Date(today)
  monday.setDate(today.getDate() + mondayOffset)
  monday.setHours(0, 0, 0, 0)

  const sunday = new Date(monday)
  sunday.setDate(monday.getDate() + 6)

  return {
    dateFrom: monday.toISOString().slice(0, 10),
    dateTo: sunday.toISOString().slice(0, 10),
  }
}

function buildWeekDays(dateFrom: string) {
  const start = new Date(dateFrom)
  const dates: string[] = []
  for (let index = 0; index < 7; index += 1) {
    const current = new Date(start)
    current.setDate(start.getDate() + index)
    dates.push(current.toISOString().slice(0, 10))
  }
  return dates
}

async function parseStudentCsvIdentifiers(file: File) {
  const text = await file.text()
  const lines = text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
  if (lines.length === 0) {
    return []
  }

  const delimiter = lines[0].includes(';') ? ';' : ','
  const rows = lines.map((line) => line.split(delimiter).map((value) => value.trim()))
  const header = rows[0].map((value) => value.toLowerCase())
  const usernameIndex = header.indexOf('username')
  const emailIndex = header.indexOf('email')
  const hasHeader = usernameIndex >= 0 || emailIndex >= 0
  const dataRows = hasHeader ? rows.slice(1) : rows

  return dataRows
    .map((row) => {
      if (emailIndex >= 0 && row[emailIndex]) {
        return row[emailIndex]
      }
      if (usernameIndex >= 0 && row[usernameIndex]) {
        return row[usernameIndex]
      }
      return row[0]
    })
    .filter(Boolean)
}

function resolveGroupSettingsDisabledReason(
  form: {
    name: string
    specialtyId: string
    studyYear: string
    streamId: string
    subgroupMode: GroupSubgroupMode
  },
  t: (key: string, options?: Record<string, unknown>) => string,
) {
  if (!form.name.trim()) {
    return t('validation:required')
  }
  if (!form.specialtyId && form.studyYear) {
    return t('academic.validation.selectSpecialtyForStudyYear')
  }
  if (form.specialtyId && !form.studyYear) {
    return t('academic.validation.enterStudyYearForSpecialty')
  }
  if (form.studyYear) {
    const studyYear = Number(form.studyYear)
    if (!Number.isInteger(studyYear) || studyYear < 1 || studyYear > 8) {
      return t('academic.validation.enterStudyYear')
    }
  }
  if (form.streamId && (!form.specialtyId || !form.studyYear)) {
    return t('academic.groupSettings.streamHint')
  }

  return null
}

function resolveOverrideDisabledReason(
  form: {
    subjectId: string
    enabled: boolean
    lectureCountOverride: string
    practiceCountOverride: string
    labCountOverride: string
  },
  t: (key: string, options?: Record<string, unknown>) => string,
) {
  if (!form.subjectId) {
    return t('academic.validation.selectSubject')
  }

  for (const value of [form.lectureCountOverride, form.practiceCountOverride, form.labCountOverride]) {
    if (!value.trim()) {
      continue
    }
    const count = Number(value)
    if (!Number.isInteger(count) || count < 0) {
      return t('academic.validation.countsNonNegative')
    }
  }

  return null
}

function normalizeOptionalCount(value: string) {
  if (!value.trim()) {
    return null
  }

  return Number(value)
}

async function invalidateGroupAcademicQueries(queryClient: ReturnType<typeof useQueryClient>, groupId: string) {
  await queryClient.invalidateQueries({ queryKey: ['education', 'group-overrides', groupId] })
  await queryClient.invalidateQueries({ queryKey: ['education', 'group-resolved-subjects', groupId] })
}

function resolveInitialGroupTab(value: string | null): GroupTab {
  if (value === 'overview' || value === 'students' || value === 'schedule' || value === 'analytics' || value === 'settings') {
    return value
  }
  return 'overview'
}
