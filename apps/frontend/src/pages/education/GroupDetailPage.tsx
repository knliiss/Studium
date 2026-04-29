import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import { AccessDeniedPage } from '@/pages/shared/AccessDeniedPage'
import { adminUserService, analyticsService, educationService, scheduleService, userDirectoryService } from '@/shared/api/services'
import { isAccessDeniedApiError } from '@/shared/lib/api-errors'
import { cn } from '@/shared/lib/cn'
import { formatDate, formatDateTime } from '@/shared/lib/format'
import { toSubjectOption, toTeacherOption } from '@/shared/lib/picker-options'
import { hasAnyRole } from '@/shared/lib/roles'
import type { GroupMemberRole, SubgroupValue, SubjectResponse, UserSummaryResponse } from '@/shared/types/api'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { DataTable } from '@/shared/ui/DataTable'
import { EntityPicker } from '@/shared/ui/EntityPicker'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { SectionTabs } from '@/shared/ui/SectionTabs'
import { Select } from '@/shared/ui/Select'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { Textarea } from '@/shared/ui/Textarea'
import { UserAvatar } from '@/shared/ui/UserAvatar'
import { Breadcrumbs } from '@/widgets/common/Breadcrumbs'
import { MetricCard } from '@/widgets/common/MetricCard'
import { RiskBadge } from '@/widgets/common/RiskBadge'

type GroupTab = 'overview' | 'students' | 'subjects' | 'schedule' | 'analytics' | 'settings'

const subgroupValues: SubgroupValue[] = ['ALL', 'FIRST', 'SECOND']
const memberRoleValues: GroupMemberRole[] = ['STUDENT', 'STAROSTA']

export function GroupDetailPage() {
  const { t } = useTranslation()
  const { groupId = '' } = useParams()
  const { roles } = useAuth()
  const queryClient = useQueryClient()
  const canManageGroup = hasAnyRole(roles, ['ADMIN', 'OWNER'])
  const [activeTab, setActiveTab] = useState<GroupTab>('overview')
  const [studentSearch, setStudentSearch] = useState('')
  const [teacherSearch, setTeacherSearch] = useState('')
  const [subjectSearch, setSubjectSearch] = useState('')
  const [selectedStudentIds, setSelectedStudentIds] = useState<string[]>([])
  const [selectedTeacherIds, setSelectedTeacherIds] = useState<string[]>([])
  const [pendingSubjectId, setPendingSubjectId] = useState('')
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'error'; message: string } | null>(null)
  const [importFile, setImportFile] = useState<File | null>(null)
  const [isImporting, setIsImporting] = useState(false)
  const [subjectForm, setSubjectForm] = useState({
    name: '',
    description: '',
    pendingTeacherId: '',
  })
  const weekRange = useMemo(() => buildCurrentWeekRange(), [])

  const groupQuery = useQuery({
    queryKey: ['education', 'group', groupId],
    queryFn: () => educationService.getGroup(groupId),
    enabled: Boolean(groupId),
  })
  const membersQuery = useQuery({
    queryKey: ['education', 'group-members', groupId],
    queryFn: () => educationService.listGroupStudents(groupId),
    enabled: Boolean(groupId),
  })
  const subjectsQuery = useQuery({
    queryKey: ['education', 'group-subjects', groupId],
    queryFn: () => educationService.getSubjectsByGroup(groupId, { page: 0, size: 100, sortBy: 'name', direction: 'asc' }),
    enabled: Boolean(groupId),
  })
  const groupOverviewQuery = useQuery({
    queryKey: ['analytics', 'group-overview', groupId],
    queryFn: () => analyticsService.getGroupOverview(groupId),
    enabled: Boolean(groupId),
  })
  const analyticsStudentsQuery = useQuery({
    queryKey: ['analytics', 'group-students', groupId],
    queryFn: () => analyticsService.getGroupStudents(groupId, { page: 0, size: 50 }),
    enabled: Boolean(groupId),
  })
  const scheduleQuery = useQuery({
    queryKey: ['schedule', 'group-week', groupId, weekRange],
    queryFn: () => scheduleService.getGroupRange(groupId, weekRange.dateFrom, weekRange.dateTo),
    enabled: Boolean(groupId),
  })
  const slotQuery = useQuery({
    queryKey: ['schedule', 'slots'],
    queryFn: () => scheduleService.listSlots(),
  })
  const roomQuery = useQuery({
    queryKey: ['schedule', 'rooms'],
    queryFn: () => scheduleService.listRooms(),
    enabled: true,
  })

  const relatedUserIds = useMemo(() => {
    const memberIds = membersQuery.data?.map((member) => member.userId) ?? []
    const subjectTeacherIds = subjectsQuery.data?.items.flatMap((subject) => subject.teacherIds) ?? []
    const scheduleTeacherIds = scheduleQuery.data?.map((lesson) => lesson.teacherId) ?? []

    return Array.from(new Set([...memberIds, ...subjectTeacherIds, ...scheduleTeacherIds, ...selectedTeacherIds])).filter(Boolean)
  }, [membersQuery.data, scheduleQuery.data, selectedTeacherIds, subjectsQuery.data?.items])
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
  const teacherCandidatesQuery = useQuery({
    queryKey: ['admin', 'teacher-candidates', teacherSearch],
    queryFn: () => adminUserService.list({
      page: 0,
      size: 20,
      role: 'TEACHER',
      search: teacherSearch.trim() || undefined,
      sortBy: 'username',
      direction: 'asc',
    }),
    enabled: canManageGroup,
  })
  const subjectOptionsQuery = useQuery({
    queryKey: ['education', 'subjects', 'picker', subjectSearch],
    queryFn: () => educationService.listSubjects({
      page: 0,
      size: 20,
      q: subjectSearch.trim() || undefined,
      sortBy: 'name',
      direction: 'asc',
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
  const createSubjectMutation = useMutation({
    mutationFn: () => educationService.createSubject({
      name: subjectForm.name,
      description: subjectForm.description,
      groupIds: [groupId],
      teacherIds: selectedTeacherIds,
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'group-subjects', groupId] })
      setSelectedTeacherIds([])
      setSubjectForm({ name: '', description: '', pendingTeacherId: '' })
      setFeedback({ tone: 'success', message: t('education.subjectCreatedSuccess') })
    },
    onError: () => setFeedback({ tone: 'error', message: t('education.subjectCreatedError') }),
  })
  const bindSubjectMutation = useMutation({
    mutationFn: async (subject: SubjectResponse) =>
      educationService.updateSubject(subject.id, {
        name: subject.name,
        description: subject.description ?? '',
        groupIds: Array.from(new Set([...subject.groupIds, groupId])),
        teacherIds: subject.teacherIds,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'group-subjects', groupId] })
      setPendingSubjectId('')
      setFeedback({ tone: 'success', message: t('education.subjectBoundSuccess') })
    },
    onError: () => setFeedback({ tone: 'error', message: t('education.subjectBoundError') }),
  })
  const unbindSubjectMutation = useMutation({
    mutationFn: async (subject: SubjectResponse) => {
      const nextGroupIds = subject.groupIds.filter((value) => value !== groupId)
      if (nextGroupIds.length === 0) {
        throw new Error('last-group')
      }

      return educationService.updateSubject(subject.id, {
        name: subject.name,
        description: subject.description ?? '',
        groupIds: nextGroupIds,
        teacherIds: subject.teacherIds,
      })
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'group-subjects', groupId] })
      setFeedback({ tone: 'success', message: t('education.subjectUnboundSuccess') })
    },
    onError: (error) => {
      const isLastGroup = error instanceof Error && error.message === 'last-group'
      setFeedback({
        tone: 'error',
        message: isLastGroup ? t('education.lastSubjectGroupError') : t('education.subjectUnboundError'),
      })
    },
  })

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

    return <ErrorState description={t('common.states.error')} title={t('navigation.shared.groups')} />
  }

  if (!groupQuery.data) {
    return <ErrorState description={t('common.states.notFound')} title={t('navigation.shared.groups')} />
  }

  const group = groupQuery.data
  const members = membersQuery.data ?? []
  const subjects = subjectsQuery.data?.items ?? []
  const studentSnapshots = analyticsStudentsQuery.data?.items ?? []
  const candidateStudents = studentCandidatesQuery.data?.content ?? []
  const teacherCandidates = teacherCandidatesQuery.data?.content ?? []
  const teacherOptions = teacherCandidates.map((teacher) => toTeacherOption(teacher))
  const subjectOptions = (subjectOptionsQuery.data?.items ?? [])
    .filter((subject) => !subject.groupIds.includes(groupId))
    .map((subject) => toSubjectOption(subject, undefined, t('education.groupsLabel')))
  const userMap = new Map((userDirectoryQuery.data ?? []).map((user) => [user.id, user]))
  const subjectMap = new Map(subjects.map((subject) => [subject.id, subject.name]))
  const slotMap = new Map((slotQuery.data ?? []).map((slot) => [slot.id, slot]))
  const roomMap = new Map((roomQuery.data ?? []).map((room) => [room.id, `${room.building} · ${room.code}`]))
  const selectedTeachers = selectedTeacherIds
    .map((teacherId) => teacherCandidates.find((teacher) => teacher.id === teacherId) ?? userMap.get(teacherId))
    .filter(Boolean) as Array<UserSummaryResponse | typeof teacherCandidates[number]>
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

  return (
    <div className="space-y-6">
      <Breadcrumbs
        items={[
          { label: t('navigation.shared.groups'), to: '/groups' },
          { label: group.name },
        ]}
      />

      <PageHeader
        actions={(
          <div className="flex flex-wrap gap-3">
            <Link to="/groups">
              <Button variant="secondary">{t('common.actions.back')}</Button>
            </Link>
            <Link to="/education">
              <Button variant="secondary">{t('navigation.shared.education')}</Button>
            </Link>
            <Link to="/schedule">
              <Button variant="ghost">{t('navigation.shared.schedule')}</Button>
            </Link>
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
        activeId={activeTab}
        items={[
          { id: 'overview', label: t('education.groupTabs.overview') },
          { id: 'students', label: t('education.groupTabs.students') },
          { id: 'schedule', label: t('education.groupTabs.schedule') },
          { id: 'analytics', label: t('education.groupTabs.analytics') },
          ...(canManageGroup ? [{ id: 'settings', label: t('education.groupTabs.settings') }] : []),
        ]}
        onChange={(tabId) => setActiveTab(tabId as GroupTab)}
      />

      {activeTab === 'overview' ? (
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

      {activeTab === 'students' ? (
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

      {activeTab === 'subjects' ? (
        <div className="space-y-6">
          {canManageGroup ? (
            <div className="grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
              <Card className="space-y-4">
                <PageHeader
                  description={t('education.bindExistingSubjectDescription')}
                  title={t('education.assignExistingSubject')}
                />
                <EntityPicker
                  label={t('navigation.shared.subjects')}
                  value={pendingSubjectId}
                  options={subjectOptions}
                  placeholder={t('education.selectSubject')}
                  emptyLabel={t('education.noAvailableSubjects')}
                  loading={subjectOptionsQuery.isLoading}
                  searchLabel={t('common.actions.search')}
                  searchPlaceholder={t('education.subjectSearchPlaceholder')}
                  searchValue={subjectSearch}
                  onChange={setPendingSubjectId}
                  onSearchChange={setSubjectSearch}
                />
                <Button
                  disabled={!pendingSubjectId || bindSubjectMutation.isPending}
                  onClick={() => {
                    const subject = (subjectOptionsQuery.data?.items ?? []).find((item) => item.id === pendingSubjectId)
                    if (subject) {
                      bindSubjectMutation.mutate(subject)
                    }
                  }}
                >
                  {t('education.assignSubjectToGroup')}
                </Button>
              </Card>

              <Card className="space-y-4">
                <PageHeader
                  description={t('education.groupCreateSubjectDescription')}
                  title={t('education.createSubject')}
                />
                <FormField label={t('common.labels.name')}>
                  <Input
                    value={subjectForm.name}
                    onChange={(event) => setSubjectForm((current) => ({ ...current, name: event.target.value }))}
                  />
                </FormField>
                <FormField label={t('common.labels.description')}>
                  <Textarea
                    value={subjectForm.description}
                    onChange={(event) => setSubjectForm((current) => ({ ...current, description: event.target.value }))}
                  />
                </FormField>
                <EntityPicker
                  label={t('education.subjectTeachers')}
                  value={subjectForm.pendingTeacherId}
                  options={teacherOptions}
                  placeholder={t('education.selectTeacher')}
                  emptyLabel={t('education.noTeacherOptions')}
                  loading={teacherCandidatesQuery.isLoading}
                  searchLabel={t('common.actions.search')}
                  searchPlaceholder={t('education.teacherSearchPlaceholder')}
                  searchValue={teacherSearch}
                  onChange={(value) => setSubjectForm((current) => ({ ...current, pendingTeacherId: value }))}
                  onSearchChange={setTeacherSearch}
                />
                <div className="flex flex-wrap gap-3">
                  <Button
                    variant="secondary"
                    disabled={!subjectForm.pendingTeacherId}
                    onClick={() => {
                      setSelectedTeacherIds((current) => (
                        current.includes(subjectForm.pendingTeacherId)
                          ? current
                          : [...current, subjectForm.pendingTeacherId]
                      ))
                      setSubjectForm((current) => ({ ...current, pendingTeacherId: '' }))
                    }}
                  >
                    {t('common.actions.add')}
                  </Button>
                </div>
                {selectedTeachers.length > 0 ? (
                  <SelectedUsers
                    items={selectedTeachers}
                    onRemove={(userId) => setSelectedTeacherIds((current) => current.filter((value) => value !== userId))}
                  />
                ) : null}
                <Button
                  disabled={!subjectForm.name.trim() || createSubjectMutation.isPending}
                  onClick={() => createSubjectMutation.mutate()}
                >
                  {t('common.actions.create')}
                </Button>
              </Card>
            </div>
          ) : null}

          {subjects.length === 0 ? (
            <EmptyState description={t('education.noSubjectsInGroup')} title={t('education.groupTabs.subjects')} />
          ) : (
            <DataTable
              getRowId={(subject) => subject.id}
              columns={[
                {
                  key: 'name',
                  header: t('common.labels.name'),
                  render: (subject) => (
                    <Link className="font-medium text-accent" to={`/subjects/${subject.id}`}>
                      {subject.name}
                    </Link>
                  ),
                },
                {
                  key: 'teachers',
                  header: t('education.subjectTeachers'),
                  render: (subject) => (
                    <span>
                      {subject.teacherIds.length === 0
                        ? t('education.noTeachersAssigned')
                        : subject.teacherIds.map((teacherId) => userMap.get(teacherId)?.username ?? t('education.unknownTeacher')).join(', ')}
                    </span>
                  ),
                },
                {
                  key: 'groups',
                  header: t('navigation.shared.groups'),
                  render: (subject) => subject.groupIds.length,
                },
                {
                  key: 'actions',
                  header: t('common.labels.actions'),
                  render: (subject) => (
                    canManageGroup ? (
                      <Button variant="ghost" onClick={() => unbindSubjectMutation.mutate(subject)}>
                        {t('education.removeSubjectFromGroup')}
                      </Button>
                    ) : '—'
                  ),
                },
              ]}
              rows={subjects}
            />
          )}
        </div>
      ) : null}

      {activeTab === 'schedule' ? (
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

      {activeTab === 'analytics' ? (
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

      {activeTab === 'settings' ? (
        <Card className="space-y-4">
          <PageHeader
            description={t('education.groupSettingsDescription')}
            title={t('education.groupTabs.settings')}
          />
          <div className="grid gap-4 md:grid-cols-2">
            <MetricCard title={t('common.labels.name')} value={group.name} />
            <MetricCard title={t('education.createdAt')} value={formatDate(group.createdAt)} />
          </div>
        </Card>
      ) : null}
    </div>
  )

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

function SelectedUsers({
  items,
  onRemove,
}: {
  items: Array<Pick<UserSummaryResponse, 'id' | 'username' | 'email'>>
  onRemove: (userId: string) => void
}) {
  return (
    <div className="flex flex-wrap gap-3">
      {items.map((item) => (
        <button
          key={item.id}
          className="inline-flex items-center gap-2 rounded-full border border-border bg-surface-muted px-3 py-2 text-sm text-text-primary"
          type="button"
          onClick={() => onRemove(item.id)}
        >
          <UserAvatar email={item.email} username={item.username} size="sm" />
          <span>{item.username}</span>
        </button>
      ))}
    </div>
  )
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
