import { useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'
import { ArrowDown, ArrowUp, ClipboardCheck, FileText } from 'lucide-react'

import { useAuth } from '@/features/auth/useAuth'
import { AccessDeniedPage } from '@/pages/shared/AccessDeniedPage'
import {
  adminUserService,
  assignmentService,
  analyticsService,
  educationService,
  scheduleService,
  testingService,
  userDirectoryService,
} from '@/shared/api/services'
import { isAccessDeniedApiError } from '@/shared/lib/api-errors'
import { formatDate, formatDateTime } from '@/shared/lib/format'
import { toGroupOption, toTeacherOption } from '@/shared/lib/picker-options'
import { hasAnyRole } from '@/shared/lib/roles'
import type { GroupStudentMembershipResponse, TopicResponse } from '@/shared/types/api'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { DataTable } from '@/shared/ui/DataTable'
import { EntityPicker } from '@/shared/ui/EntityPicker'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { SectionTabs } from '@/shared/ui/SectionTabs'
import { Select } from '@/shared/ui/Select'
import { Textarea } from '@/shared/ui/Textarea'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { Breadcrumbs } from '@/widgets/common/Breadcrumbs'
import { DeadlineBadge } from '@/widgets/common/DeadlineBadge'
import { MetricCard } from '@/widgets/common/MetricCard'
import { UserAvatar } from '@/shared/ui/UserAvatar'

type SubjectTab =
  | 'overview'
  | 'topics'
  | 'materials'
  | 'assignments'
  | 'tests'
  | 'schedule'
  | 'students'
  | 'analytics'
  | 'settings'

type CourseBuilderMode = 'topic' | 'assignment' | 'test'
type CourseFlowItem = {
  id: string
  topicId: string
  orderIndex: number
  kind: 'assignment' | 'test'
  title: string
  meta: string
  status?: string
  to: string
  type: string
  icon: typeof FileText
  actions?: ReactNode
}

function buildRange() {
  const today = new Date()
  const until = new Date(today)
  until.setDate(today.getDate() + 35)

  return {
    dateFrom: today.toISOString().slice(0, 10),
    dateTo: until.toISOString().slice(0, 10),
  }
}

export function SubjectDetailPage() {
  const { t } = useTranslation()
  const { subjectId = '' } = useParams()
  const { roles, session } = useAuth()
  const queryClient = useQueryClient()
  const range = useMemo(() => buildRange(), [])
  const isStudent = hasAnyRole(roles, ['STUDENT']) && !hasAnyRole(roles, ['TEACHER', 'ADMIN', 'OWNER'])
  const canManageAssessments = hasAnyRole(roles, ['TEACHER', 'ADMIN', 'OWNER'])
  const canCreateTopics = hasAnyRole(roles, ['TEACHER', 'ADMIN', 'OWNER'])
  const canManageSettings = hasAnyRole(roles, ['ADMIN', 'OWNER'])

  const [activeTab, setActiveTab] = useState<SubjectTab>('overview')
  const [groupSearch, setGroupSearch] = useState('')
  const [teacherSearch, setTeacherSearch] = useState('')
  const [topicForm, setTopicForm] = useState({ title: '', orderIndex: 0 })
  const [assignmentForm, setAssignmentForm] = useState({
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
  const [testForm, setTestForm] = useState({
    topicId: '',
    title: '',
    maxPoints: 100,
    maxAttempts: 1,
    timeLimitMinutes: 30,
    availableFrom: '',
    availableUntil: '',
    showCorrectAnswersAfterSubmit: false,
    shuffleQuestions: false,
    shuffleAnswers: false,
  })
  const [courseBuilder, setCourseBuilder] = useState<{ mode: CourseBuilderMode; topicId?: string } | null>(null)
  const [settingsForm, setSettingsForm] = useState({
    name: '',
    description: '',
    pendingGroupId: '',
    pendingTeacherId: '',
    selectedGroupIds: [] as string[],
    selectedTeacherIds: [] as string[],
  })

  const subjectQuery = useQuery({
    queryKey: ['education', 'subject', subjectId],
    queryFn: () => educationService.getSubject(subjectId),
    enabled: Boolean(subjectId),
  })
  const topicsQuery = useQuery({
    queryKey: ['education', 'subject-topics', subjectId],
    queryFn: () => educationService.getTopicsBySubject(subjectId, { page: 0, size: 100 }),
    enabled: Boolean(subjectId),
  })
  const scheduleQuery = useQuery({
    queryKey: ['education', 'subject-schedule', subjectId, range],
    queryFn: async () => {
      const subject = subjectQuery.data
      if (!subject) {
        return []
      }

      const pages = await Promise.all(
        subject.groupIds.map((groupId) => scheduleService.getGroupRange(groupId, range.dateFrom, range.dateTo)),
      )

      return pages
        .flat()
        .filter((lesson) => lesson.subjectId === subjectId)
    },
    enabled: Boolean(subjectQuery.data?.groupIds.length),
  })
  const assignmentsQuery = useQuery({
    queryKey: ['education', 'subject-assignments', subjectId],
    queryFn: async () => {
      const topics = topicsQuery.data?.items ?? []
      const pages = await Promise.all(
        topics.map((topic) => assignmentService.getAssignmentsByTopic(topic.id, { page: 0, size: 50, sortBy: 'orderIndex', direction: 'asc' })),
      )

      return pages.flatMap((page) => page.items)
    },
    enabled: Boolean(topicsQuery.data),
  })
  const testsQuery = useQuery({
    queryKey: ['education', 'subject-tests', subjectId],
    queryFn: async () => {
      const topics = topicsQuery.data?.items ?? []
      const pages = await Promise.all(
        topics.map((topic) => testingService.getTestsByTopic(topic.id, { page: 0, size: 50, sortBy: 'orderIndex', direction: 'asc' })),
      )

      return pages.flatMap((page) => page.items)
    },
    enabled: Boolean(topicsQuery.data),
  })
  const studentsQuery = useQuery({
    queryKey: ['education', 'subject-students', subjectId],
    queryFn: async () => {
      const subject = subjectQuery.data
      if (!subject) {
        return [] as GroupStudentMembershipResponse[]
      }

      const pages = await Promise.all(subject.groupIds.map((groupId) => educationService.listGroupStudents(groupId)))
      return Array.from(
        new Map(
          pages
            .flat()
            .map((membership) => [membership.userId, membership]),
        ).values(),
      )
    },
    enabled: Boolean(subjectQuery.data?.groupIds.length),
  })
  const studentSubjectAnalyticsQuery = useQuery({
    queryKey: ['education', 'subject-student-analytics', subjectId, session?.user.id],
    queryFn: async () => {
      if (!session?.user.id) {
        return null
      }

      const rows = await analyticsService.getStudentSubjects(session.user.id)
      return rows.find((row) => row.subjectId === subjectId) ?? null
    },
    enabled: Boolean(isStudent && session?.user.id),
  })
  const subjectAnalyticsQuery = useQuery({
    queryKey: ['education', 'subject-analytics', subjectId],
    queryFn: () => analyticsService.getSubjectAnalytics(subjectId, { page: 0, size: 20 }),
    enabled: Boolean(subjectId && !isStudent),
  })
  const groupsQuery = useQuery({
    queryKey: ['education', 'subject-groups', subjectId, subjectQuery.data?.groupIds.join(',')],
    queryFn: async () => {
      const subject = subjectQuery.data
      if (!subject) {
        return []
      }

      return Promise.all(subject.groupIds.map((groupId) => educationService.getGroup(groupId)))
    },
    enabled: Boolean(subjectQuery.data?.groupIds.length),
  })
  const slotQuery = useQuery({
    queryKey: ['schedule', 'slots'],
    queryFn: () => scheduleService.listSlots(),
  })
  const roomQuery = useQuery({
    queryKey: ['schedule', 'rooms'],
    queryFn: () => scheduleService.listRooms(),
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
    enabled: canManageSettings,
  })
  const groupOptionsQuery = useQuery({
    queryKey: ['education', 'subject-group-options', groupSearch],
    queryFn: () => educationService.listGroups({
      page: 0,
      size: 20,
      q: groupSearch.trim() || undefined,
      sortBy: 'name',
      direction: 'asc',
    }),
    enabled: canManageSettings,
  })
  const relatedUserIds = useMemo(
    () => Array.from(new Set([
      ...(subjectQuery.data?.teacherIds ?? []),
      ...settingsForm.selectedTeacherIds,
      ...(studentsQuery.data?.map((student) => student.userId) ?? []),
      ...(scheduleQuery.data?.map((lesson) => lesson.teacherId) ?? []),
    ])),
    [scheduleQuery.data, settingsForm.selectedTeacherIds, studentsQuery.data, subjectQuery.data?.teacherIds],
  )
  const userDirectoryQuery = useQuery({
    queryKey: ['auth', 'directory', relatedUserIds.join(',')],
    queryFn: () => userDirectoryService.lookup(relatedUserIds),
    enabled: relatedUserIds.length > 0,
  })

  const createTopicMutation = useMutation({
    mutationFn: () =>
      educationService.createTopic({
        subjectId,
        title: topicForm.title,
        orderIndex: topicForm.orderIndex,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'subject-topics', subjectId] })
      setTopicForm({ title: '', orderIndex: (topicsQuery.data?.items.length ?? 0) + 1 })
      setCourseBuilder(null)
    },
  })
  const reorderTopicsMutation = useMutation({
    mutationFn: (nextTopics: TopicResponse[]) =>
      educationService.reorderTopics(
        subjectId,
        nextTopics.map((topic, index) => ({ topicId: topic.id, orderIndex: index })),
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'subject-topics', subjectId] })
    },
  })
  const createAssignmentMutation = useMutation({
    mutationFn: () =>
      assignmentService.createAssignment({
        ...assignmentForm,
        topicId: assignmentForm.topicId || defaultTopicId,
        orderIndex: nextCourseItemOrderIndex(assignmentForm.topicId || defaultTopicId),
        deadline: new Date(assignmentForm.deadline).toISOString(),
        acceptedFileTypes: assignmentForm.acceptedFileTypes.split(',').map((value) => value.trim()).filter(Boolean),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'subject-assignments', subjectId] })
      await queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      setAssignmentForm((current) => ({
        ...current,
        title: '',
        description: '',
        deadline: '',
      }))
      setCourseBuilder(null)
    },
  })
  const createTestMutation = useMutation({
    mutationFn: () =>
      testingService.createTest({
        ...testForm,
        topicId: testForm.topicId || defaultTopicId,
        orderIndex: nextCourseItemOrderIndex(testForm.topicId || defaultTopicId),
        availableFrom: testForm.availableFrom ? new Date(testForm.availableFrom).toISOString() : null,
        availableUntil: testForm.availableUntil ? new Date(testForm.availableUntil).toISOString() : null,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'subject-tests', subjectId] })
      await queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      setTestForm((current) => ({
        ...current,
        title: '',
        availableFrom: '',
        availableUntil: '',
      }))
      setCourseBuilder(null)
    },
  })
  const updateSubjectMutation = useMutation({
    mutationFn: () => {
      const subject = subjectQuery.data
      return educationService.updateSubject(subjectId, {
        name: settingsForm.name.trim() || (subject?.name ?? ''),
        description: settingsForm.description || (subject?.description ?? ''),
        groupIds: settingsForm.selectedGroupIds.length > 0 ? settingsForm.selectedGroupIds : (subject?.groupIds ?? []),
        teacherIds: settingsForm.selectedTeacherIds.length > 0 ? settingsForm.selectedTeacherIds : (subject?.teacherIds ?? []),
      })
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'subject', subjectId] })
      await queryClient.invalidateQueries({ queryKey: ['education', 'subject-groups', subjectId] })
    },
  })

  if (subjectQuery.isLoading || topicsQuery.isLoading) {
    return <LoadingState />
  }

  if (subjectQuery.isError || topicsQuery.isError) {
    if (isAccessDeniedApiError(subjectQuery.error) || isAccessDeniedApiError(topicsQuery.error)) {
      return <AccessDeniedPage />
    }

    return <ErrorState description={t('common.states.error')} title={t('navigation.shared.subjects')} />
  }

  if (!subjectQuery.data) {
    return <ErrorState description={t('common.states.notFound')} title={t('navigation.shared.subjects')} />
  }

  const subject = subjectQuery.data
  const topics = topicsQuery.data?.items ?? []
  const assignments = assignmentsQuery.data ?? []
  const tests = testsQuery.data ?? []
  const defaultTopicId = topics[0]?.id ?? ''
  const settingsName = settingsForm.name || subject.name
  const settingsDescription = settingsForm.description || (subject.description ?? '')
  const settingsGroupIds = settingsForm.selectedGroupIds.length > 0 ? settingsForm.selectedGroupIds : subject.groupIds
  const settingsTeacherIds = settingsForm.selectedTeacherIds.length > 0 ? settingsForm.selectedTeacherIds : subject.teacherIds
  const topicTitleById = new Map(topics.map((topic) => [topic.id, topic.title]))
  const analyticsRows = isStudent ? [] : subjectAnalyticsQuery.data?.items ?? []
  const subjectAnalyticsRow = isStudent ? studentSubjectAnalyticsQuery.data : analyticsRows[0]
  const groupMap = new Map((groupsQuery.data ?? []).map((group) => [group.id, group.name]))
  const userMap = new Map((userDirectoryQuery.data ?? []).map((user) => [user.id, user]))
  const slotMap = new Map((slotQuery.data ?? []).map((slot) => [slot.id, slot]))
  const roomMap = new Map((roomQuery.data ?? []).map((room) => [room.id, `${room.building} · ${room.code}`]))
  const groupedStudents = studentsQuery.data ?? []
  const teacherOptions = (teacherCandidatesQuery.data?.content ?? []).map((teacher) => toTeacherOption(teacher))
  const groupOptions = (groupOptionsQuery.data?.items ?? []).map((group) => toGroupOption(group))
  const weekDays = buildWeekDays(range.dateFrom)
  const lessonsByDate = new Map<string, typeof scheduleQuery.data>()
  for (const date of weekDays) {
    lessonsByDate.set(date, [])
  }
  for (const lesson of scheduleQuery.data ?? []) {
    const items = lessonsByDate.get(lesson.date) ?? []
    items.push(lesson)
    lessonsByDate.set(lesson.date, items)
  }
  function moveTopic(topicId: string, direction: -1 | 1) {
    const currentIndex = topics.findIndex((topic) => topic.id === topicId)
    const targetIndex = currentIndex + direction
    if (currentIndex < 0 || targetIndex < 0 || targetIndex >= topics.length) {
      return
    }

    const nextTopics = topics.slice()
    const [topic] = nextTopics.splice(currentIndex, 1)
    nextTopics.splice(targetIndex, 0, topic)
    reorderTopicsMutation.mutate(nextTopics)
  }
  function nextCourseItemOrderIndex(topicId: string) {
    return assignments.filter((assignment) => assignment.topicId === topicId).length
      + tests.filter((test) => test.topicId === topicId).length
  }
  async function moveCourseItem(item: CourseFlowItem, topicId: string, orderIndex: number) {
    if (item.kind === 'assignment') {
      await assignmentService.moveAssignment(item.id, { topicId, orderIndex })
    } else {
      await testingService.moveTest(item.id, { topicId, orderIndex })
    }
  }
  async function moveCourseItemWithinTopic(items: CourseFlowItem[], index: number, direction: -1 | 1) {
    const targetIndex = index + direction
    const current = items[index]
    const target = items[targetIndex]
    if (!current || !target) {
      return
    }

    await Promise.all([
      moveCourseItem(current, target.topicId, targetIndex),
      moveCourseItem(target, current.topicId, index),
    ])
    await queryClient.invalidateQueries({ queryKey: ['education', 'subject-assignments', subjectId] })
    await queryClient.invalidateQueries({ queryKey: ['education', 'subject-tests', subjectId] })
  }
  async function moveCourseItemToTopic(item: CourseFlowItem, topicId: string) {
    await moveCourseItem(item, topicId, nextCourseItemOrderIndex(topicId))
    await queryClient.invalidateQueries({ queryKey: ['education', 'subject-assignments', subjectId] })
    await queryClient.invalidateQueries({ queryKey: ['education', 'subject-tests', subjectId] })
  }
  const visibleTabs = [
    { id: 'overview', label: t('education.subjectTabs.overview') },
    { id: 'schedule', label: t('education.subjectTabs.schedule') },
    { id: 'students', label: t('education.subjectTabs.students') },
    { id: 'analytics', label: t('education.subjectTabs.analytics') },
    ...(canManageSettings ? [{ id: 'settings', label: t('education.subjectTabs.settings') }] : []),
  ] as Array<{ id: SubjectTab; label: string }>

  return (
    <div className="space-y-6">
      <Breadcrumbs
        items={[
          { label: t('navigation.shared.subjects'), to: '/subjects' },
          { label: subject.name },
        ]}
      />

      <PageHeader
        actions={(
          <div className="flex flex-wrap gap-3">
            <Link to="/subjects">
              <Button variant="secondary">{t('common.actions.back')}</Button>
            </Link>
            {subject.groupIds[0] ? (
              <Link to={`/groups/${subject.groupIds[0]}`}>
                <Button variant="secondary">{t('navigation.shared.groups')}</Button>
              </Link>
            ) : null}
            <Link to="/schedule">
              <Button variant="ghost">{t('navigation.shared.schedule')}</Button>
            </Link>
          </div>
        )}
        description={subject.description ?? t('education.subjectDescriptionFallback')}
        title={subject.name}
      />

      <SectionTabs activeId={activeTab} items={visibleTabs} onChange={(tabId) => setActiveTab(tabId as SubjectTab)} />

      {activeTab === 'overview' ? (
        <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_340px]">
          <main className="space-y-5">
            <Card className="space-y-4">
              <PageHeader
                description={subject.description ?? t('education.subjectOverviewFallback')}
                title={t('education.courseGeneralSection')}
              />
              <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <MetricCard title={t('education.overview.topics')} value={topics.length} />
                <MetricCard title={t('education.overview.assignments')} value={assignments.length} />
                <MetricCard title={t('education.overview.tests')} value={tests.length} />
                <MetricCard title={t('navigation.shared.groups')} value={subject.groupIds.length} />
              </div>
            </Card>

            <Card className="space-y-4">
              <PageHeader
                actions={canCreateTopics ? (
                  <Button
                    variant="secondary"
                    onClick={() => {
                      setTopicForm((current) => ({
                        ...current,
                        orderIndex: topics.length,
                      }))
                      setCourseBuilder({ mode: 'topic' })
                    }}
                  >
                    {t('education.createTopic')}
                  </Button>
                ) : undefined}
                description={t('education.courseFlowDescription')}
                title={t('education.courseContentFlow')}
              />

              {courseBuilder ? (
                <Card className="space-y-4 border-accent/25 bg-accent-muted/20">
                  {courseBuilder.mode === 'topic' ? (
                    <>
                      <PageHeader
                        description={t('courseBuilder.addTopicDescription')}
                        title={t('education.createTopic')}
                      />
                      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_160px]">
                        <FormField label={t('common.labels.title')}>
                          <Input
                            value={topicForm.title}
                            onChange={(event) => setTopicForm((current) => ({ ...current, title: event.target.value }))}
                          />
                        </FormField>
                        <FormField label={t('education.orderIndex')}>
                          <Input
                            type="number"
                            value={topicForm.orderIndex}
                            onChange={(event) => setTopicForm((current) => ({ ...current, orderIndex: Number(event.target.value) }))}
                          />
                        </FormField>
                      </div>
                      <div className="flex flex-wrap gap-3">
                        <Button
                          disabled={!topicForm.title.trim() || createTopicMutation.isPending}
                          onClick={() => createTopicMutation.mutate()}
                        >
                          {t('common.actions.create')}
                        </Button>
                        <Button variant="secondary" onClick={() => setCourseBuilder(null)}>
                          {t('common.actions.cancel')}
                        </Button>
                      </div>
                      {!topicForm.title.trim() ? (
                        <p className="text-sm font-medium text-text-secondary">{t('courseBuilder.titleRequired')}</p>
                      ) : null}
                    </>
                  ) : null}

                  {courseBuilder.mode === 'assignment' ? (
                    <>
                      <PageHeader
                        description={t('courseBuilder.addAssignmentDescription')}
                        title={t('assignments.createAssignment')}
                      />
                      <p className="text-sm font-semibold text-text-secondary">
                        {t('education.topic')}: {topicTitleById.get(assignmentForm.topicId || courseBuilder.topicId || defaultTopicId) ?? t('education.topic')}
                      </p>
                      <div className="grid gap-4 xl:grid-cols-2">
                        <FormField label={t('common.labels.title')}>
                          <Input
                            value={assignmentForm.title}
                            onChange={(event) => setAssignmentForm((current) => ({ ...current, title: event.target.value }))}
                          />
                        </FormField>
                        <FormField label={t('common.labels.deadline')}>
                          <Input
                            type="datetime-local"
                            value={assignmentForm.deadline}
                            onChange={(event) => setAssignmentForm((current) => ({ ...current, deadline: event.target.value }))}
                          />
                        </FormField>
                      </div>
                      <FormField label={t('common.labels.description')}>
                        <Textarea
                          value={assignmentForm.description}
                          onChange={(event) => setAssignmentForm((current) => ({ ...current, description: event.target.value }))}
                        />
                      </FormField>
                      <div className="flex flex-wrap gap-3">
                        <Button
                          disabled={!assignmentForm.title.trim() || !assignmentForm.deadline || createAssignmentMutation.isPending}
                          onClick={() => createAssignmentMutation.mutate()}
                        >
                          {t('common.actions.create')}
                        </Button>
                        <Button variant="secondary" onClick={() => setCourseBuilder(null)}>
                          {t('common.actions.cancel')}
                        </Button>
                      </div>
                      {!assignmentForm.title.trim() || !assignmentForm.deadline ? (
                        <p className="text-sm font-medium text-text-secondary">{t('courseBuilder.assignmentRequired')}</p>
                      ) : null}
                    </>
                  ) : null}

                  {courseBuilder.mode === 'test' ? (
                    <>
                      <PageHeader
                        description={t('courseBuilder.addTestDescription')}
                        title={t('testing.createTest')}
                      />
                      <p className="text-sm font-semibold text-text-secondary">
                        {t('education.topic')}: {topicTitleById.get(testForm.topicId || courseBuilder.topicId || defaultTopicId) ?? t('education.topic')}
                      </p>
                      <div className="grid gap-4 xl:grid-cols-3">
                        <FormField label={t('common.labels.title')}>
                          <Input
                            value={testForm.title}
                            onChange={(event) => setTestForm((current) => ({ ...current, title: event.target.value }))}
                          />
                        </FormField>
                        <FormField label={t('testing.maxPoints')}>
                          <Input
                            type="number"
                            value={testForm.maxPoints}
                            onChange={(event) => setTestForm((current) => ({ ...current, maxPoints: Number(event.target.value) }))}
                          />
                        </FormField>
                        <FormField label={t('testing.availableUntil')}>
                          <Input
                            type="datetime-local"
                            value={testForm.availableUntil}
                            onChange={(event) => setTestForm((current) => ({ ...current, availableUntil: event.target.value }))}
                          />
                        </FormField>
                      </div>
                      <div className="flex flex-wrap gap-3">
                        <Button
                          disabled={!testForm.title.trim() || createTestMutation.isPending}
                          onClick={() => createTestMutation.mutate()}
                        >
                          {t('common.actions.create')}
                        </Button>
                        <Button variant="secondary" onClick={() => setCourseBuilder(null)}>
                          {t('common.actions.cancel')}
                        </Button>
                      </div>
                      {!testForm.title.trim() ? (
                        <p className="text-sm font-medium text-text-secondary">{t('courseBuilder.titleRequired')}</p>
                      ) : null}
                    </>
                  ) : null}
                </Card>
              ) : null}

              <CourseFlowSection
                actions={canCreateTopics ? (
                  <Button
                    variant="ghost"
                    onClick={() => {
                      setTopicForm((current) => ({
                        ...current,
                        orderIndex: topics.length,
                      }))
                      setCourseBuilder({ mode: 'topic' })
                    }}
                  >
                    {t('courseBuilder.addSection')}
                  </Button>
                ) : undefined}
                description={subject.description ?? t('education.subjectOverviewFallback')}
                items={[]}
                title={t('education.courseGeneralSection')}
              />

              {topics.length === 0 ? (
                <EmptyState
                  description={canCreateTopics ? t('education.noTopicsTeacher') : t('education.noContentAvailable')}
                  title={t('education.courseContentFlow')}
                />
              ) : (
                <div className="space-y-4">
                  {topics
                    .slice()
                    .sort((left, right) => left.orderIndex - right.orderIndex)
                    .map((topic, index) => {
                      const topicAssignments = assignments.filter((assignment) => assignment.topicId === topic.id)
                      const topicTests = tests.filter((test) => test.topicId === topic.id)
                      const topicItems = [
                        ...topicAssignments.map((assignment) => ({
                          id: assignment.id,
                          topicId: assignment.topicId,
                          orderIndex: assignment.orderIndex,
                          kind: 'assignment' as const,
                          title: assignment.title,
                          meta: `${t('common.labels.deadline')}: ${formatDateTime(assignment.deadline)}`,
                          status: t(`common.status.${assignment.status}`),
                          to: `/assignments/${assignment.id}`,
                          type: t('navigation.shared.assignments'),
                          icon: ClipboardCheck,
                        })),
                        ...topicTests.map((test) => ({
                          id: test.id,
                          topicId: test.topicId,
                          orderIndex: test.orderIndex,
                          kind: 'test' as const,
                          title: test.title,
                          meta: test.availableUntil
                            ? `${t('testing.availableUntil')}: ${formatDateTime(test.availableUntil)}`
                            : t('analytics.notEnoughDataYet'),
                          status: t(`common.status.${test.status}`),
                          to: `/tests/${test.id}`,
                          type: t('navigation.shared.tests'),
                          icon: FileText,
                        })),
                      ].sort((left, right) => left.orderIndex - right.orderIndex)
                      const courseItems = topicItems.map((item, itemIndex) => ({
                        ...item,
                        actions: canManageAssessments ? (
                          <div className="flex flex-wrap items-center justify-end gap-2">
                            <Button
                              disabled={itemIndex === 0}
                              variant="ghost"
                              onClick={() => void moveCourseItemWithinTopic(topicItems, itemIndex, -1)}
                            >
                              <ArrowUp className="h-4 w-4" />
                            </Button>
                            <Button
                              disabled={itemIndex === topicItems.length - 1}
                              variant="ghost"
                              onClick={() => void moveCourseItemWithinTopic(topicItems, itemIndex, 1)}
                            >
                              <ArrowDown className="h-4 w-4" />
                            </Button>
                            <Select
                              value={item.topicId}
                              onChange={(event) => void moveCourseItemToTopic(item, event.target.value)}
                            >
                              {topics.map((targetTopic) => (
                                <option key={targetTopic.id} value={targetTopic.id}>
                                  {targetTopic.title}
                                </option>
                              ))}
                            </Select>
                          </div>
                        ) : undefined,
                      }))

                      return (
                        <CourseFlowSection
                          key={topic.id}
                          actions={canManageAssessments ? (
                            <div className="flex flex-wrap gap-2">
                              <Button
                                disabled={index === 0 || reorderTopicsMutation.isPending}
                                variant="ghost"
                                onClick={() => moveTopic(topic.id, -1)}
                              >
                                <ArrowUp className="mr-2 h-4 w-4" />
                                {t('courseBuilder.moveUp')}
                              </Button>
                              <Button
                                disabled={index === topics.length - 1 || reorderTopicsMutation.isPending}
                                variant="ghost"
                                onClick={() => moveTopic(topic.id, 1)}
                              >
                                <ArrowDown className="mr-2 h-4 w-4" />
                                {t('courseBuilder.moveDown')}
                              </Button>
                              <Button
                                variant="secondary"
                                onClick={() => {
                                  setAssignmentForm((current) => ({ ...current, topicId: topic.id }))
                                  setCourseBuilder({ mode: 'assignment', topicId: topic.id })
                                }}
                              >
                                {t('assignments.createAssignment')}
                              </Button>
                              <Button
                                variant="secondary"
                                onClick={() => {
                                  setTestForm((current) => ({ ...current, topicId: topic.id }))
                                  setCourseBuilder({ mode: 'test', topicId: topic.id })
                                }}
                              >
                                {t('testing.createTest')}
                              </Button>
                            </div>
                          ) : undefined}
                          description={t('education.topicWorkloadSummary', {
                            assignments: topicAssignments.length,
                            tests: topicTests.length,
                          })}
                          emptyLabel={canManageAssessments ? t('education.emptyTopicTeacher') : t('education.noContentAvailable')}
                          items={courseItems}
                          title={topic.title}
                        />
                      )
                    })}
                </div>
              )}
            </Card>
          </main>

          <aside className="space-y-5">
            <Card className="space-y-4">
              <PageHeader title={t('education.courseInfoAside')} />
              <div className="space-y-3 text-sm leading-6 text-text-secondary">
                <p>
                  <span className="font-semibold text-text-primary">{t('education.subjectTeachersLabel')}:</span>{' '}
                  {subject.teacherIds.length === 0
                    ? t('education.noTeachersAssigned')
                    : subject.teacherIds.map((teacherId) => userMap.get(teacherId)?.username ?? t('education.unknownTeacher')).join(', ')}
                </p>
                <p>
                  <span className="font-semibold text-text-primary">{t('education.subjectGroupsLabel')}:</span>{' '}
                  {subject.groupIds.map((groupId) => groupMap.get(groupId) ?? t('education.group')).join(', ')}
                </p>
                <p>
                  <span className="font-semibold text-text-primary">{t('education.nextLesson')}:</span>{' '}
                  {scheduleQuery.data?.[0] ? formatDate(scheduleQuery.data[0].date) : t('analytics.notEnoughDataYet')}
                </p>
                <p>
                  <span className="font-semibold text-text-primary">{t('education.nearestDeadline')}:</span>{' '}
                  {assignments[0] ? formatDateTime(assignments[0].deadline) : t('analytics.notEnoughDataYet')}
                </p>
              </div>
            </Card>

            <Card className="space-y-4">
              <PageHeader title={t('education.progressTitle')} />
              {subjectAnalyticsRow ? (
                <div className="grid gap-4">
                  {'averageScore' in subjectAnalyticsRow ? (
                    <MetricCard title={t('dashboard.metrics.averageScore')} value={subjectAnalyticsRow.averageScore ?? '-'} />
                  ) : null}
                  {'completionRate' in subjectAnalyticsRow ? (
                    <MetricCard title={t('analytics.completionRate')} value={`${Math.round(subjectAnalyticsRow.completionRate * 100)}%`} />
                  ) : null}
                  {'atRiskStudentsCount' in subjectAnalyticsRow ? (
                    <MetricCard title={t('education.overview.atRiskStudents')} value={subjectAnalyticsRow.atRiskStudentsCount} />
                  ) : null}
                </div>
              ) : (
                <EmptyState description={t('analytics.notEnoughDataYet')} title={t('education.progressTitle')} />
              )}
            </Card>

            {canManageAssessments || canManageSettings ? (
              <Card className="space-y-3">
                <PageHeader title={t('education.courseManagementActions')} />
                <div className="grid gap-2">
                  {canCreateTopics ? (
                    <Button
                      variant="secondary"
                      onClick={() => {
                        setTopicForm((current) => ({
                          ...current,
                          orderIndex: topics.length,
                        }))
                        setCourseBuilder({ mode: 'topic' })
                      }}
                    >
                      {t('education.createTopic')}
                    </Button>
                  ) : null}
                  {canManageAssessments ? (
                    <Button
                      variant="secondary"
                      onClick={() => {
                        setAssignmentForm((current) => ({ ...current, topicId: defaultTopicId }))
                        setCourseBuilder({ mode: 'assignment', topicId: defaultTopicId })
                      }}
                    >
                      {t('assignments.createAssignment')}
                    </Button>
                  ) : null}
                  {canManageAssessments ? (
                    <Button
                      variant="secondary"
                      onClick={() => {
                        setTestForm((current) => ({ ...current, topicId: defaultTopicId }))
                        setCourseBuilder({ mode: 'test', topicId: defaultTopicId })
                      }}
                    >
                      {t('testing.createTest')}
                    </Button>
                  ) : null}
                  {canManageSettings ? <Button variant="secondary" onClick={() => setActiveTab('settings')}>{t('education.manageSubjectSettings')}</Button> : null}
                </div>
              </Card>
            ) : null}
          </aside>
        </div>
      ) : null}

      {activeTab === 'topics' ? (
        <div className="space-y-6">
          {canCreateTopics ? (
            <Card className="space-y-4">
              <PageHeader
                description={t('education.topicCreateDescription')}
                title={t('education.createTopic')}
              />
              <div className="grid gap-4 xl:grid-cols-3">
                <FormField label={t('common.labels.title')}>
                  <Input
                    value={topicForm.title}
                    onChange={(event) => setTopicForm((current) => ({ ...current, title: event.target.value }))}
                  />
                </FormField>
                <FormField label={t('education.orderIndex')}>
                  <Input
                    type="number"
                    value={topicForm.orderIndex}
                    onChange={(event) => setTopicForm((current) => ({ ...current, orderIndex: Number(event.target.value) }))}
                  />
                </FormField>
              </div>
              <Button disabled={createTopicMutation.isPending} onClick={() => createTopicMutation.mutate()}>
                {t('common.actions.create')}
              </Button>
            </Card>
          ) : null}

          {topics.length === 0 ? (
            <EmptyState
              description={t('education.noTopics')}
              title={t('education.subjectTabs.topics')}
            />
          ) : (
            <DataTable
              columns={[
                {
                  key: 'title',
                  header: t('common.labels.title'),
                  render: (topic) => (
                    <Link className="font-medium text-accent" to={`/subjects/${subjectId}/topics/${topic.id}`}>
                      {topic.title}
                    </Link>
                  ),
                },
                { key: 'orderIndex', header: t('education.orderIndex'), render: (topic) => topic.orderIndex },
                {
                  key: 'items',
                  header: t('education.topicWorkload'),
                  render: (topic) => (
                    <span>
                      {assignments.filter((assignment) => assignment.topicId === topic.id).length}
                      {' / '}
                      {tests.filter((test) => test.topicId === topic.id).length}
                    </span>
                  ),
                },
              ]}
              rows={topics}
            />
          )}
        </div>
      ) : null}

      {activeTab === 'materials' ? (
        <EmptyState
          description={t('education.materialsUnavailable')}
          title={t('education.subjectTabs.materials')}
        />
      ) : null}

      {activeTab === 'assignments' ? (
        <div className="space-y-6">
          {canManageAssessments && topics.length > 0 ? (
            <Card className="space-y-4">
              <PageHeader
                description={t('assignments.subjectCreateDescription')}
                title={t('assignments.createAssignment')}
              />
              <div className="grid gap-4 xl:grid-cols-2">
                <FormField label={t('education.topic')}>
                  <Select
                    value={assignmentForm.topicId || defaultTopicId}
                    onChange={(event) => setAssignmentForm((current) => ({ ...current, topicId: event.target.value }))}
                  >
                    {topics.map((topic) => (
                      <option key={topic.id} value={topic.id}>{topic.title}</option>
                    ))}
                  </Select>
                </FormField>
                <FormField label={t('common.labels.title')}>
                  <Input
                    value={assignmentForm.title}
                    onChange={(event) => setAssignmentForm((current) => ({ ...current, title: event.target.value }))}
                  />
                </FormField>
              </div>
              <FormField label={t('common.labels.description')}>
                <Textarea
                  value={assignmentForm.description}
                  onChange={(event) => setAssignmentForm((current) => ({ ...current, description: event.target.value }))}
                />
              </FormField>
              <div className="grid gap-4 xl:grid-cols-4">
                <FormField label={t('common.labels.deadline')}>
                  <Input
                    type="datetime-local"
                    value={assignmentForm.deadline}
                    onChange={(event) => setAssignmentForm((current) => ({ ...current, deadline: event.target.value }))}
                  />
                </FormField>
                <FormField label={t('assignments.maxSubmissions')}>
                  <Input
                    type="number"
                    value={assignmentForm.maxSubmissions}
                    onChange={(event) => setAssignmentForm((current) => ({ ...current, maxSubmissions: Number(event.target.value) }))}
                  />
                </FormField>
                <FormField label={t('assignments.maxFileSizeMb')}>
                  <Input
                    type="number"
                    value={assignmentForm.maxFileSizeMb}
                    onChange={(event) => setAssignmentForm((current) => ({ ...current, maxFileSizeMb: Number(event.target.value) }))}
                  />
                </FormField>
                <FormField label={t('assignments.acceptedFileTypes')}>
                  <Input
                    value={assignmentForm.acceptedFileTypes}
                    onChange={(event) => setAssignmentForm((current) => ({ ...current, acceptedFileTypes: event.target.value }))}
                  />
                </FormField>
              </div>
              <Button disabled={createAssignmentMutation.isPending} onClick={() => createAssignmentMutation.mutate()}>
                {t('common.actions.create')}
              </Button>
            </Card>
          ) : null}

          {assignmentsQuery.isLoading ? <LoadingState /> : null}
          {assignmentsQuery.isError ? (
            <ErrorState description={t('common.states.error')} title={t('navigation.shared.assignments')} />
          ) : assignments.length === 0 ? (
            <EmptyState
              description={t('assignments.empty')}
              title={t('navigation.shared.assignments')}
            />
          ) : (
            <DataTable
              columns={[
                {
                  key: 'title',
                  header: t('common.labels.title'),
                  render: (assignment) => (
                    <Link className="font-medium text-accent" to={`/assignments/${assignment.id}`}>
                      {assignment.title}
                    </Link>
                  ),
                },
                {
                  key: 'topic',
                  header: t('education.topic'),
                  render: (assignment) => topicTitleById.get(assignment.topicId) ?? assignment.topicId,
                },
                {
                  key: 'deadline',
                  header: t('common.labels.deadline'),
                  render: (assignment) => <DeadlineBadge deadline={assignment.deadline} />,
                },
                { key: 'status', header: t('common.labels.status'), render: (assignment) => assignment.status },
              ]}
              rows={assignments}
            />
          )}
        </div>
      ) : null}

      {activeTab === 'tests' ? (
        <div className="space-y-6">
          {canManageAssessments && topics.length > 0 ? (
            <Card className="space-y-4">
              <PageHeader
                description={t('testing.subjectCreateDescription')}
                title={t('testing.createTest')}
              />
              <div className="grid gap-4 xl:grid-cols-2">
                <FormField label={t('education.topic')}>
                  <Select
                    value={testForm.topicId || defaultTopicId}
                    onChange={(event) => setTestForm((current) => ({ ...current, topicId: event.target.value }))}
                  >
                    {topics.map((topic) => (
                      <option key={topic.id} value={topic.id}>{topic.title}</option>
                    ))}
                  </Select>
                </FormField>
                <FormField label={t('common.labels.title')}>
                  <Input
                    value={testForm.title}
                    onChange={(event) => setTestForm((current) => ({ ...current, title: event.target.value }))}
                  />
                </FormField>
              </div>
              <div className="grid gap-4 xl:grid-cols-4">
                <FormField label={t('testing.maxAttempts')}>
                  <Input
                    type="number"
                    value={testForm.maxAttempts}
                    onChange={(event) => setTestForm((current) => ({ ...current, maxAttempts: Number(event.target.value) }))}
                  />
                </FormField>
                <FormField label={t('testing.maxPoints')}>
                  <Input
                    type="number"
                    value={testForm.maxPoints}
                    onChange={(event) => setTestForm((current) => ({ ...current, maxPoints: Number(event.target.value) }))}
                  />
                </FormField>
                <FormField label={t('testing.timeLimitMinutes')}>
                  <Input
                    type="number"
                    value={testForm.timeLimitMinutes}
                    onChange={(event) => setTestForm((current) => ({ ...current, timeLimitMinutes: Number(event.target.value) }))}
                  />
                </FormField>
                <FormField label={t('testing.availableFrom')}>
                  <Input
                    type="datetime-local"
                    value={testForm.availableFrom}
                    onChange={(event) => setTestForm((current) => ({ ...current, availableFrom: event.target.value }))}
                  />
                </FormField>
                <FormField label={t('testing.availableUntil')}>
                  <Input
                    type="datetime-local"
                    value={testForm.availableUntil}
                    onChange={(event) => setTestForm((current) => ({ ...current, availableUntil: event.target.value }))}
                  />
                </FormField>
              </div>
              <Button disabled={createTestMutation.isPending} onClick={() => createTestMutation.mutate()}>
                {t('common.actions.create')}
              </Button>
            </Card>
          ) : null}

          {testsQuery.isLoading ? <LoadingState /> : null}
          {testsQuery.isError ? (
            <ErrorState description={t('common.states.error')} title={t('navigation.shared.tests')} />
          ) : tests.length === 0 ? (
            <EmptyState
              description={t('testing.empty')}
              title={t('navigation.shared.tests')}
            />
          ) : (
            <DataTable
              columns={[
                {
                  key: 'title',
                  header: t('common.labels.title'),
                  render: (test) => (
                    <Link className="font-medium text-accent" to={`/tests/${test.id}`}>
                      {test.title}
                    </Link>
                  ),
                },
                {
                  key: 'topic',
                  header: t('education.topic'),
                  render: (test) => topicTitleById.get(test.topicId) ?? test.topicId,
                },
                {
                  key: 'availableUntil',
                  header: t('testing.availableUntil'),
                  render: (test) => formatDateTime(test.availableUntil),
                },
                { key: 'status', header: t('common.labels.status'), render: (test) => test.status },
              ]}
              rows={tests}
            />
          )}
        </div>
      ) : null}

      {activeTab === 'schedule' ? (
        <div className="space-y-6">
          {scheduleQuery.isLoading ? <LoadingState /> : null}
          {scheduleQuery.isError ? (
            <ErrorState description={t('common.states.error')} title={t('navigation.shared.schedule')} />
          ) : (scheduleQuery.data?.length ?? 0) === 0 ? (
            <EmptyState
              description={t('schedule.emptySubject')}
              title={t('navigation.shared.schedule')}
            />
          ) : (
            <div className="grid gap-4 xl:grid-cols-2">
              {weekDays.map((date) => (
                <Card key={date} className="space-y-4">
                  <p className="text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">{formatDate(date)}</p>
                  {(lessonsByDate.get(date) ?? []).length === 0 ? (
                    <p className="text-sm leading-6 text-text-secondary">{t('schedule.noLessonsYet')}</p>
                  ) : (
                    <div className="space-y-3">
                      {(lessonsByDate.get(date) ?? [])
                        .slice()
                        .sort((left, right) => (slotMap.get(left.slotId)?.number ?? 99) - (slotMap.get(right.slotId)?.number ?? 99))
                        .map((lesson) => {
                          const slot = slotMap.get(lesson.slotId)
                          const location = lesson.roomId
                            ? (roomMap.get(lesson.roomId) ?? t('schedule.roomAssigned'))
                            : lesson.lessonFormat === 'ONLINE'
                              ? t('schedule.lessonFormat.ONLINE')
                              : t('schedule.linkWillBeAddedLater')
                          return (
                            <div key={`${lesson.date}-${lesson.slotId}-${lesson.teacherId}`} className="rounded-[18px] border border-border bg-surface-muted p-4">
                              <p className="text-sm font-semibold text-text-primary">
                                {slot ? t('schedule.pairSummary', { number: slot.number, start: slot.startTime, end: slot.endTime }) : t('schedule.pairFallback')}
                              </p>
                              <p className="mt-1 text-sm text-text-secondary">
                                {groupMap.get(lesson.groupId) ?? t('education.group')}
                                {' · '}
                                {userMap.get(lesson.teacherId)?.username ?? t('education.unknownTeacher')}
                              </p>
                              <p className="mt-1 text-sm text-text-secondary">
                                {t(`schedule.lessonType.${lesson.lessonType}`)}
                                {' · '}
                                {t(`schedule.lessonFormat.${lesson.lessonFormat}`)}
                                {' · '}
                                {t(`schedule.weekType.${lesson.weekType}`)}
                              </p>
                              <p className="mt-1 text-sm text-text-secondary">{location}</p>
                              {lesson.onlineMeetingUrl ? (
                                <a
                                  className="mt-3 inline-flex min-h-10 items-center rounded-[10px] border border-border bg-surface px-3 text-sm font-medium text-accent transition hover:border-accent/40"
                                  href={lesson.onlineMeetingUrl}
                                  rel="noreferrer"
                                  target="_blank"
                                >
                                  {t('schedule.joinLesson')}
                                </a>
                              ) : null}
                              {lesson.notes ? <p className="mt-2 text-sm text-text-secondary">{lesson.notes}</p> : null}
                            </div>
                          )
                        })}
                    </div>
                  )}
                </Card>
              ))}
            </div>
          )}
        </div>
      ) : null}

      {activeTab === 'students' ? (
        <div className="space-y-6">
          {studentsQuery.isLoading ? <LoadingState /> : null}
          {studentsQuery.isError ? (
            <ErrorState description={t('common.states.error')} title={t('education.subjectTabs.students')} />
          ) : groupedStudents.length ? (
            <>
              <Card className="space-y-3">
                <p className="text-sm leading-6 text-text-secondary">
                  {t('education.subjectStudentsHubDescription')}
                </p>
              </Card>
              <DataTable
                columns={[
                  {
                    key: 'student',
                    header: t('education.student'),
                    render: (row) => {
                      const user = userMap.get(row.userId)
                      return (
                        <div className="flex items-start gap-3">
                          <UserAvatar email={user?.email} username={user?.username} size="sm" />
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
                    render: (row) => t(`education.memberRole.${row.role}`),
                  },
                  {
                    key: 'subgroup',
                    header: t('education.subgroup'),
                    render: (row) => t(`education.subgroups.${row.subgroup}`),
                  },
                  {
                    key: 'groupMembershipCount',
                    header: t('education.groupMemberships'),
                    render: (row) => row.groupMembershipCount,
                  },
                {
                  key: 'groups',
                  header: t('navigation.shared.groups'),
                  render: () => subject.groupIds.length,
                },
                ]}
                rows={groupedStudents}
              />
            </>
          ) : (
            <EmptyState
              description={t('education.noStudents')}
              title={t('education.subjectTabs.students')}
            />
          )}
        </div>
      ) : null}

      {activeTab === 'analytics' ? (
        <div className="space-y-6">
          {isStudent ? (
            subjectAnalyticsRow ? (
              <Card className="space-y-4">
                <PageHeader title={t('education.subjectTabs.analytics')} />
                <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                  <MetricCard title={t('dashboard.metrics.averageScore')} value={subjectAnalyticsRow.averageScore ?? '-'} />
                  <MetricCard title={t('analytics.completionRate')} value={`${Math.round(subjectAnalyticsRow.completionRate * 100)}%`} />
                  <MetricCard title={t('education.overview.missedDeadlines')} value={`${Math.round(subjectAnalyticsRow.missedDeadlineRate * 100)}%`} />
                  <MetricCard title={t('education.overview.lateSubmissionRate')} value={`${Math.round(subjectAnalyticsRow.lateSubmissionRate * 100)}%`} />
                </div>
              </Card>
            ) : (
              <EmptyState description={t('education.emptyAnalytics')} title={t('education.subjectTabs.analytics')} />
            )
          ) : subjectAnalyticsQuery.isLoading ? (
            <LoadingState />
          ) : subjectAnalyticsQuery.isError ? (
            <ErrorState description={t('common.states.error')} title={t('education.subjectTabs.analytics')} />
          ) : analyticsRows.length === 0 ? (
            <EmptyState description={t('education.emptyAnalytics')} title={t('education.subjectTabs.analytics')} />
          ) : (
            <DataTable
              columns={[
                {
                  key: 'groupId',
                  header: t('navigation.shared.groups'),
                  render: (row) => groupMap.get(row.groupId) ?? t('education.group'),
                },
                {
                  key: 'averageScore',
                  header: t('dashboard.metrics.averageScore'),
                  render: (row) => row.averageScore ?? '-',
                },
                {
                  key: 'completionRate',
                  header: t('analytics.completionRate'),
                  render: (row) => `${Math.round(row.completionRate * 100)}%`,
                },
                {
                  key: 'atRiskStudentsCount',
                  header: t('education.overview.atRiskStudents'),
                  render: (row) => row.atRiskStudentsCount,
                },
              ]}
              rows={analyticsRows}
            />
          )}
        </div>
      ) : null}

      {activeTab === 'settings' ? (
        <Card className="space-y-4">
          <PageHeader
            description={t('education.settingsDescription')}
            title={t('education.subjectTabs.settings')}
          />
          {canManageSettings ? (
            <div className="space-y-4">
              <FormField label={t('common.labels.name')}>
                <Input
                  value={settingsName}
                  onChange={(event) => setSettingsForm((current) => ({ ...current, name: event.target.value }))}
                />
              </FormField>
              <FormField label={t('common.labels.description')}>
                <Textarea
                  value={settingsDescription}
                  onChange={(event) => setSettingsForm((current) => ({ ...current, description: event.target.value }))}
                />
              </FormField>
              <EntityPicker
                label={t('navigation.shared.groups')}
                value={settingsForm.pendingGroupId}
                options={groupOptions}
                placeholder={t('education.selectGroup')}
                emptyLabel={t('education.noGroupOptions')}
                loading={groupOptionsQuery.isLoading}
                searchLabel={t('common.actions.search')}
                searchPlaceholder={t('education.groupSearchPlaceholder')}
                searchValue={groupSearch}
                onChange={(value) => setSettingsForm((current) => ({ ...current, pendingGroupId: value }))}
                onSearchChange={setGroupSearch}
              />
              <Button
                variant="secondary"
                disabled={!settingsForm.pendingGroupId}
                onClick={() => setSettingsForm((current) => ({
                  ...current,
                  pendingGroupId: '',
                  selectedGroupIds: current.selectedGroupIds.includes(current.pendingGroupId)
                    ? current.selectedGroupIds
                    : [...current.selectedGroupIds, current.pendingGroupId],
                }))}
              >
                {t('common.actions.add')}
              </Button>
              <div className="flex flex-wrap gap-3">
                {settingsGroupIds.map((groupId) => (
                  <button
                    key={groupId}
                    className="rounded-full border border-border bg-surface-muted px-3 py-2 text-sm text-text-primary"
                    type="button"
                    onClick={() => setSettingsForm((current) => ({
                      ...current,
                      selectedGroupIds: current.selectedGroupIds.filter((value) => value !== groupId),
                    }))}
                  >
                    {groupMap.get(groupId) ?? t('education.group')}
                  </button>
                ))}
              </div>
              <EntityPicker
                label={t('education.subjectTeachers')}
                value={settingsForm.pendingTeacherId}
                options={teacherOptions}
                placeholder={t('education.selectTeacher')}
                emptyLabel={t('education.noTeacherOptions')}
                loading={teacherCandidatesQuery.isLoading}
                searchLabel={t('common.actions.search')}
                searchPlaceholder={t('education.teacherSearchPlaceholder')}
                searchValue={teacherSearch}
                onChange={(value) => setSettingsForm((current) => ({ ...current, pendingTeacherId: value }))}
                onSearchChange={setTeacherSearch}
              />
              <Button
                variant="secondary"
                disabled={!settingsForm.pendingTeacherId}
                onClick={() => setSettingsForm((current) => ({
                  ...current,
                  pendingTeacherId: '',
                  selectedTeacherIds: current.selectedTeacherIds.includes(current.pendingTeacherId)
                    ? current.selectedTeacherIds
                    : [...current.selectedTeacherIds, current.pendingTeacherId],
                }))}
              >
                {t('common.actions.add')}
              </Button>
              <div className="flex flex-wrap gap-3">
                {settingsTeacherIds.map((teacherId) => {
                  const teacher = userMap.get(teacherId)
                  return (
                    <button
                      key={teacherId}
                      className="inline-flex items-center gap-2 rounded-full border border-border bg-surface-muted px-3 py-2 text-sm text-text-primary"
                      type="button"
                      onClick={() => setSettingsForm((current) => ({
                        ...current,
                        selectedTeacherIds: current.selectedTeacherIds.filter((value) => value !== teacherId),
                      }))}
                    >
                      <UserAvatar email={teacher?.email} username={teacher?.username} size="sm" />
                      <span>{teacher?.username ?? t('education.unknownTeacher')}</span>
                    </button>
                  )
                })}
              </div>
              <Button
                disabled={!settingsName.trim() || settingsGroupIds.length === 0 || updateSubjectMutation.isPending}
                onClick={() => updateSubjectMutation.mutate()}
              >
                {t('common.actions.save')}
              </Button>
            </div>
          ) : (
            <div className="grid gap-4 md:grid-cols-2">
              <MetricCard title={t('education.subjectCreatedAt')} value={formatDate(subject.createdAt)} />
              <MetricCard title={t('navigation.shared.groups')} value={subject.groupIds.length} />
            </div>
          )}
        </Card>
      ) : null}
    </div>
  )
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

function CourseFlowSection({
  actions,
  description,
  emptyLabel,
  items,
  title,
}: {
  actions?: ReactNode
  description?: string
  emptyLabel?: string
  items: CourseFlowItem[]
  title: string
}) {
  return (
    <div className="space-y-3 rounded-[18px] border border-border bg-surface-muted p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <h3 className="text-lg font-semibold text-text-primary">{title}</h3>
          {description ? <p className="text-sm leading-6 text-text-secondary">{description}</p> : null}
        </div>
        {actions}
      </div>
      {items.length === 0 ? (
        emptyLabel ? <EmptyState description={emptyLabel} title={title} /> : null
      ) : (
        <div className="space-y-3">
          {items.map((item) => {
            const Icon = item.icon

            return (
              <div
                key={item.id}
                className="flex items-start justify-between gap-4 rounded-[16px] border border-border bg-surface p-4 transition hover:border-border-strong"
              >
                <Link className="flex min-w-0 flex-1 gap-3" to={item.to}>
                  <span className="mt-0.5 inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-[12px] bg-accent-muted text-accent">
                    <Icon className="h-4 w-4" />
                  </span>
                  <div className="min-w-0 space-y-1">
                    <p className="font-semibold text-text-primary">{item.title}</p>
                    <p className="text-sm leading-6 text-text-secondary">{item.meta}</p>
                  </div>
                </Link>
                <div className="flex shrink-0 flex-wrap justify-end gap-2">
                  <span className="rounded-full border border-border bg-surface-muted px-2.5 py-1 text-xs font-semibold text-text-secondary">
                    {item.type}
                  </span>
                  {item.status ? (
                    <span className="rounded-full border border-border bg-surface-muted px-2.5 py-1 text-xs font-semibold text-text-secondary">
                      {item.status}
                    </span>
                  ) : null}
                  {item.actions}
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
