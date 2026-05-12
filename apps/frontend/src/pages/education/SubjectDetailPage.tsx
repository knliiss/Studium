import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import {
  ArrowDown,
  ArrowUp,
  BookOpen,
  BookText,
  ClipboardCheck,
  ExternalLink,
  FileText,
  GraduationCap,
  Layers3,
  Link2,
  NotebookTabs,
  Plus,
  Settings2,
  TestTube2,
  Users,
} from 'lucide-react'

import { useAuth } from '@/features/auth/useAuth'
import { loadSubjectCardMetrics } from '@/pages/education/helpers'
import { AccessDeniedPage } from '@/pages/shared/AccessDeniedPage'
import {
  adminUserService,
  analyticsService,
  assignmentService,
  dashboardService,
  educationService,
  fileService,
  scheduleService,
  testingService,
  userDirectoryService,
} from '@/shared/api/services'
import { isAccessDeniedApiError } from '@/shared/lib/api-errors'
import { formatDate, formatDateTime } from '@/shared/lib/format'
import { hasAnyRole } from '@/shared/lib/roles'
import type {
  AssignmentResponse,
  DashboardAssignmentItemResponse,
  DashboardTestItemResponse,
  LectureResponse,
  TestResponse,
  TopicMaterialResponse,
  TopicMaterialType,
  TopicResponse,
  UserSummaryResponse,
} from '@/shared/types/api'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { CardPicker } from '@/shared/ui/CardPicker'
import type { CardPickerItem } from '@/shared/ui/CardPicker'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { SectionTabs } from '@/shared/ui/SectionTabs'
import { Select } from '@/shared/ui/Select'
import { Textarea } from '@/shared/ui/Textarea'
import { Breadcrumbs } from '@/widgets/common/Breadcrumbs'
import { MetricCard } from '@/widgets/common/MetricCard'
import { StatusBadge } from '@/widgets/common/StatusBadge'

type BuilderState =
  | { mode: 'topic' }
  | { mode: 'assignment'; topicId: string }
  | { mode: 'lecture'; topicId: string }
  | { mode: 'material'; topicId: string }
  | { mode: 'test'; topicId: string }

type CourseItem =
  | {
      id: string
      kind: 'text'
      title: string
      description: string
      label: string
      meta: string[]
      status: string | null
      topicId: null
      orderIndex: number
      to?: string
    }
  | {
      id: string
      kind: 'assignment' | 'lecture' | 'material' | 'test'
      title: string
      description: string | null
      label: string
      meta: string[]
      status: string | null
      topicId: string
      orderIndex: number
      to: string
    }

type SubjectTab = 'overview' | 'topics' | 'assignments' | 'tests' | 'materials' | 'students' | 'analytics' | 'settings'
const SUBJECT_TABS: SubjectTab[] = ['overview', 'topics', 'assignments', 'tests', 'materials', 'students', 'analytics', 'settings']

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
  const { primaryRole, roles, session } = useAuth()
  const queryClient = useQueryClient()
  const range = useMemo(() => buildRange(), [])
  const [searchParams, setSearchParams] = useSearchParams()
  const currentUserId = session?.user.id ?? ''
  const isStudent = primaryRole === 'STUDENT'
  const isAdmin = hasAnyRole(roles, ['ADMIN', 'OWNER'])

  const [builder, setBuilder] = useState<BuilderState | null>(null)
  const [openAddMenu, setOpenAddMenu] = useState<string | null>(null)
  const [topicForm, setTopicForm] = useState({ title: '' })
  const [assignmentForm, setAssignmentForm] = useState({
    title: '',
    description: '',
    deadline: '',
    allowLateSubmissions: true,
    maxSubmissions: 1,
    allowResubmit: true,
    acceptedFileTypes: 'application/pdf,image/png,image/jpeg',
    maxFileSizeMb: 10,
  })
  const [lectureForm, setLectureForm] = useState({
    title: '',
    content: '',
  })
  const [materialForm, setMaterialForm] = useState({
    title: '',
    description: '',
    type: 'TEXT' as TopicMaterialType,
    url: '',
    visible: true,
  })
  const [materialFile, setMaterialFile] = useState<File | null>(null)
  const [testForm, setTestForm] = useState({
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
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [groupSearch, setGroupSearch] = useState('')
  const [teacherSearch, setTeacherSearch] = useState('')
  const [settingsForm, setSettingsForm] = useState({
    name: '',
    description: '',
    selectedGroupIds: [] as string[],
    selectedTeacherIds: [] as string[],
  })
  const requestedTab = searchParams.get('tab')
  const activeTab = SUBJECT_TABS.includes(requestedTab as SubjectTab)
    ? requestedTab as SubjectTab
    : 'overview'

  function handleTabChange(nextTab: SubjectTab) {
    setSearchParams((previous) => {
      const next = new URLSearchParams(previous)
      if (nextTab === 'overview') {
        next.delete('tab')
      } else {
        next.set('tab', nextTab)
      }
      return next
    }, { replace: true })
  }

  const subjectQuery = useQuery({
    queryKey: ['education', 'subject', subjectId],
    queryFn: () => educationService.getSubject(subjectId),
    enabled: Boolean(subjectId),
  })
  const topicsQuery = useQuery({
    queryKey: ['education', 'subject-topics', subjectId],
    queryFn: () => educationService.getTopicsBySubject(subjectId, {
      page: 0,
      size: 100,
      sortBy: 'orderIndex',
      direction: 'asc',
    }),
    enabled: Boolean(subjectId),
  })
  const assignmentsQuery = useQuery({
    queryKey: ['education', 'subject-assignments', subjectId],
    queryFn: async () => {
      const topics = topicsQuery.data?.items ?? []
      const pages = await Promise.all(
        topics.map((topic) => assignmentService.getAssignmentsByTopic(topic.id, {
          page: 0,
          size: 50,
          sortBy: 'orderIndex',
          direction: 'asc',
        })),
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
        topics.map((topic) => testingService.getTestsByTopic(topic.id, {
          page: 0,
          size: 50,
          sortBy: 'orderIndex',
          direction: 'asc',
        })),
      )

      return pages.flatMap((page) => page.items)
    },
    enabled: Boolean(topicsQuery.data),
  })
  const lecturesQuery = useQuery({
    queryKey: ['education', 'subject-lectures', subjectId],
    queryFn: async () => {
      const topics = topicsQuery.data?.items ?? []
      const pages = await Promise.all(
        topics.map((topic) => educationService.getLecturesByTopic(topic.id, {
          page: 0,
          size: 50,
          sortBy: 'orderIndex',
          direction: 'asc',
        })),
      )

      return pages.flatMap((page) => page.items)
    },
    enabled: Boolean(topicsQuery.data),
  })
  const materialsQuery = useQuery({
    queryKey: ['education', 'subject-materials', subjectId],
    queryFn: async () => {
      const topics = topicsQuery.data?.items ?? []
      const pages = await Promise.all(
        topics.map((topic) => educationService.getTopicMaterials(topic.id, {
          page: 0,
          size: 50,
          sortBy: 'orderIndex',
          direction: 'asc',
        })),
      )
      return pages.flatMap((page) => page.items)
    },
    enabled: Boolean(topicsQuery.data),
  })
  const scheduleQuery = useQuery({
    queryKey: ['education', 'subject-schedule', subjectId, range.dateFrom, range.dateTo],
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
  const subjectMetricsQuery = useQuery({
    queryKey: ['education', 'subject-card-metrics-single', subjectId],
    queryFn: async () => {
      const map = await loadSubjectCardMetrics([subjectId])
      return map.get(subjectId) ?? null
    },
    enabled: Boolean(subjectId),
  })
  const studentDashboardQuery = useQuery({
    queryKey: ['dashboard', 'student', 'subject', subjectId],
    queryFn: () => dashboardService.getStudentDashboard(),
    enabled: isStudent,
  })
  const studentSubjectAnalyticsQuery = useQuery({
    queryKey: ['education', 'student-subject-analytics', subjectId, currentUserId],
    queryFn: async () => {
      const rows = await analyticsService.getStudentSubjects(currentUserId)
      return rows.find((row) => row.subjectId === subjectId) ?? null
    },
    enabled: Boolean(isStudent && currentUserId),
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
  const relatedUserIds = useMemo(
    () => Array.from(new Set(subjectQuery.data?.teacherIds ?? [])),
    [subjectQuery.data?.teacherIds],
  )
  const userDirectoryQuery = useQuery({
    queryKey: ['education', 'subject-users', relatedUserIds.join(',')],
    queryFn: () => userDirectoryService.lookup(relatedUserIds),
    enabled: relatedUserIds.length > 0,
  })
  const teacherDashboardQuery = useQuery({
    queryKey: ['dashboard', 'teacher', 'subject', subjectId],
    queryFn: () => dashboardService.getTeacherDashboard(),
    enabled: Boolean(primaryRole === 'TEACHER' && currentUserId),
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
    enabled: Boolean(isAdmin && settingsOpen),
  })
  const teacherOptionsQuery = useQuery({
    queryKey: ['education', 'subject-teacher-options', teacherSearch],
    queryFn: () => adminUserService.list({
      page: 0,
      size: 20,
      role: 'TEACHER',
      search: teacherSearch.trim() || undefined,
      sortBy: 'username',
      direction: 'asc',
    }),
    enabled: Boolean(isAdmin && settingsOpen),
  })

  const createTopicMutation = useMutation({
    mutationFn: () => educationService.createTopic({
      subjectId,
      title: topicForm.title.trim(),
      orderIndex: (topicsQuery.data?.items.length ?? 0),
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'subject-topics', subjectId] })
      setTopicForm({ title: '' })
      setBuilder(null)
      setOpenAddMenu(null)
    },
  })
  const createAssignmentMutation = useMutation({
    mutationFn: () => {
      if (!builder || builder.mode !== 'assignment') {
        throw new Error('missing-builder-context')
      }

      return assignmentService.createAssignment({
        ...assignmentForm,
        topicId: builder.topicId,
        deadline: new Date(assignmentForm.deadline).toISOString(),
        acceptedFileTypes: assignmentForm.acceptedFileTypes.split(',').map((value) => value.trim()).filter(Boolean),
        orderIndex: nextCourseItemOrderIndex(
          builder.topicId,
          assignmentsQuery.data ?? [],
          testsQuery.data ?? [],
          lecturesQuery.data ?? [],
          materialsQuery.data ?? [],
        ),
      })
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'subject-assignments', subjectId] })
      await queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      setAssignmentForm((current) => ({
        ...current,
        title: '',
        description: '',
        deadline: '',
      }))
      setBuilder(null)
      setOpenAddMenu(null)
    },
  })
  const createLectureMutation = useMutation({
    mutationFn: () => {
      if (!builder || builder.mode !== 'lecture') {
        throw new Error('missing-builder-context')
      }

      return educationService.createLecture(subjectId, builder.topicId, {
        title: lectureForm.title.trim(),
        content: lectureForm.content.trim() || undefined,
        orderIndex: nextCourseItemOrderIndex(
          builder.topicId,
          assignmentsQuery.data ?? [],
          testsQuery.data ?? [],
          lecturesQuery.data ?? [],
          materialsQuery.data ?? [],
        ),
      })
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'subject-lectures', subjectId] })
      await queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      setLectureForm({ title: '', content: '' })
      setBuilder(null)
      setOpenAddMenu(null)
    },
  })
  const createTestMutation = useMutation({
    mutationFn: () => {
      if (!builder || builder.mode !== 'test') {
        throw new Error('missing-builder-context')
      }

      return testingService.createTest({
        ...testForm,
        topicId: builder.topicId,
        availableFrom: testForm.availableFrom ? new Date(testForm.availableFrom).toISOString() : null,
        availableUntil: testForm.availableUntil ? new Date(testForm.availableUntil).toISOString() : null,
        orderIndex: nextCourseItemOrderIndex(
          builder.topicId,
          assignmentsQuery.data ?? [],
          testsQuery.data ?? [],
          lecturesQuery.data ?? [],
          materialsQuery.data ?? [],
        ),
      })
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'subject-tests', subjectId] })
      await queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      setTestForm((current) => ({
        ...current,
        title: '',
        availableFrom: '',
        availableUntil: '',
      }))
      setBuilder(null)
      setOpenAddMenu(null)
    },
  })
  const createMaterialMutation = useMutation({
    mutationFn: async () => {
      if (!builder || builder.mode !== 'material') {
        throw new Error('missing-builder-context')
      }
      const payload: Record<string, unknown> = {
        title: materialForm.title.trim(),
        description: materialForm.description.trim() || undefined,
        type: materialForm.type,
        visible: materialForm.visible,
        orderIndex: nextCourseItemOrderIndex(
          builder.topicId,
          assignmentsQuery.data ?? [],
          testsQuery.data ?? [],
          lecturesQuery.data ?? [],
          materialsQuery.data ?? [],
        ),
      }
      if (materialForm.type === 'FILE') {
        if (!materialFile) {
          throw new Error('file-required')
        }
        const uploaded = await fileService.uploadFile(materialFile, 'ATTACHMENT')
        payload.fileId = uploaded.id
      } else if (materialForm.type === 'LINK') {
        payload.url = materialForm.url.trim()
      }
      return educationService.createTopicMaterial(builder.topicId, payload)
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['education', 'subject-materials', subjectId] })
      setMaterialForm({ title: '', description: '', type: 'TEXT', url: '', visible: true })
      setMaterialFile(null)
      setBuilder(null)
      setOpenAddMenu(null)
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
  const updateSubjectMutation = useMutation({
    mutationFn: async () => {
      await educationService.updateSubject(subjectId, {
        name: settingsForm.name.trim(),
        description: settingsForm.description.trim(),
      })
      await educationService.updateSubjectGroups(subjectId, {
        groupIds: settingsForm.selectedGroupIds,
      })
      await educationService.updateSubjectTeachers(subjectId, {
        teacherIds: settingsForm.selectedTeacherIds,
      })
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['education', 'subject', subjectId] }),
        queryClient.invalidateQueries({ queryKey: ['education', 'subject-groups', subjectId] }),
        queryClient.invalidateQueries({ queryKey: ['education', 'subjects'] }),
      ])
    },
  })

  if (
    subjectQuery.isLoading
    || topicsQuery.isLoading
    || assignmentsQuery.isLoading
    || testsQuery.isLoading
    || lecturesQuery.isLoading
    || materialsQuery.isLoading
    || groupsQuery.isLoading
  ) {
    return <LoadingState />
  }

  if (
    subjectQuery.isError
    || topicsQuery.isError
    || assignmentsQuery.isError
    || testsQuery.isError
    || lecturesQuery.isError
    || materialsQuery.isError
    || groupsQuery.isError
  ) {
    if (
      isAccessDeniedApiError(subjectQuery.error)
      || isAccessDeniedApiError(topicsQuery.error)
      || isAccessDeniedApiError(assignmentsQuery.error)
      || isAccessDeniedApiError(testsQuery.error)
      || isAccessDeniedApiError(lecturesQuery.error)
      || isAccessDeniedApiError(materialsQuery.error)
    ) {
      return <AccessDeniedPage />
    }

    return <ErrorState description={t('common.states.error')} title={t('navigation.shared.subjects')} />
  }

  const subject = subjectQuery.data
  if (!subject) {
    return <ErrorState description={t('common.states.notFound')} title={t('navigation.shared.subjects')} />
  }
  const currentSubject = subject

  const topics = (topicsQuery.data?.items ?? []).slice().sort((left, right) => left.orderIndex - right.orderIndex)
  const assignments = assignmentsQuery.data ?? []
  const lectures = lecturesQuery.data ?? []
  const materials = materialsQuery.data ?? []
  const tests = testsQuery.data ?? []
  const isAssignedTeacher = primaryRole === 'TEACHER' && currentSubject.teacherIds.includes(currentUserId)
  const canManageContent = isAdmin || isAssignedTeacher
  const canManageSettings = isAdmin
  const userMap = new Map((userDirectoryQuery.data ?? []).map((user) => [user.id, user]))
  const teacherNames = currentSubject.teacherIds
    .map((teacherId) => getUserDisplayName(userMap.get(teacherId)))
    .filter(Boolean)
  const studentUpcomingDeadlines = (studentDashboardQuery.data?.upcomingDeadlines ?? [])
    .filter((item) => item.subjectId === subjectId)
    .sort((left, right) => left.deadline.localeCompare(right.deadline))
  const studentAvailableTests = new Map(
    (studentDashboardQuery.data?.availableTests ?? [])
      .filter((item) => item.subjectId === subjectId)
      .map((item) => [item.testId, item] as const),
  )
  const studentPendingAssignments = new Map(
    (studentDashboardQuery.data?.pendingAssignments ?? [])
      .filter((item) => item.subjectId === subjectId)
      .map((item) => [item.assignmentId, item] as const),
  )
  const nextLesson = (scheduleQuery.data ?? [])
    .slice()
    .sort((left, right) => `${left.date}:${left.slotId}`.localeCompare(`${right.date}:${right.slotId}`))[0] ?? null
  const teacherPendingReviewByAssignmentId = new Map<string, number>()
  for (const submission of teacherDashboardQuery.data?.pendingSubmissionsToReview ?? []) {
    teacherPendingReviewByAssignmentId.set(
      submission.assignmentId,
      (teacherPendingReviewByAssignmentId.get(submission.assignmentId) ?? 0) + 1,
    )
  }
  const sidebarAnalyticsRow = isStudent
    ? studentSubjectAnalyticsQuery.data
    : (subjectAnalyticsQuery.data?.items ?? [])[0] ?? null
  const sidebarMetrics = subjectMetricsQuery.data
  const orderedItemsByTopicId = new Map<string, CourseItem[]>()
  for (const topic of topics) {
    orderedItemsByTopicId.set(topic.id, buildTopicItems({
      assignments,
      lectures,
      materials,
      pendingReviewByAssignmentId: teacherPendingReviewByAssignmentId,
      studentAvailableTests,
      studentPendingAssignments,
      t,
      tests,
      topicId: topic.id,
    }))
  }
  const generalItems: CourseItem[] = [
    {
      id: `subject-${subject.id}-description`,
      kind: 'text',
      title: t('education.courseIntroTitle'),
      description: currentSubject.description?.trim() || t('education.subjectDescriptionFallback'),
      label: t('education.contentType.textBlock'),
      meta: [
        t('education.subjectGroupsCount', { count: currentSubject.groupIds.length }),
        t('education.subjectTeachersCount', { count: currentSubject.teacherIds.length }),
      ],
      orderIndex: 0,
      status: null,
      topicId: null,
    },
  ]
  const latestMaterials = materials
    .slice()
    .sort((left, right) => right.updatedAt.localeCompare(left.updatedAt))
    .slice(0, 5)
  const latestAssignments = assignments
    .slice()
    .sort((left, right) => left.deadline.localeCompare(right.deadline))
    .slice(0, 6)
  const latestTests = tests
    .slice()
    .sort((left, right) => {
      const leftDate = left.availableUntil ?? left.createdAt
      const rightDate = right.availableUntil ?? right.createdAt
      return leftDate.localeCompare(rightDate)
    })
    .slice(0, 6)
  const upcomingDeadlines = [
    ...assignments.map((assignment) => ({
      id: assignment.id,
      kind: 'assignment' as const,
      title: assignment.title,
      deadline: assignment.deadline,
      status: assignment.status,
      to: `/assignments/${assignment.id}`,
    })),
    ...tests
      .filter((test) => Boolean(test.availableUntil))
      .map((test) => ({
        id: test.id,
        kind: 'test' as const,
        title: test.title,
        deadline: test.availableUntil ?? test.createdAt,
        status: test.status,
        to: `/tests/${test.id}`,
      })),
  ]
    .sort((left, right) => left.deadline.localeCompare(right.deadline))
    .slice(0, 8)
  const courseTabs = [
    { id: 'overview', label: t('education.subjectTabs.overview') },
    { id: 'topics', label: t('education.subjectTabs.topics') },
    { id: 'assignments', label: t('education.subjectTabs.assignments') },
    { id: 'tests', label: t('education.subjectTabs.tests') },
    { id: 'materials', label: t('education.subjectTabs.materials') },
    { id: 'students', label: t('education.subjectTabs.students') },
    { id: 'analytics', label: t('education.subjectTabs.analytics') },
    ...(canManageSettings ? [{ id: 'settings', label: t('education.subjectTabs.settings') }] : []),
  ] as Array<{ id: SubjectTab; label: string }>
  const settingsGroupItems: CardPickerItem[] = (groupOptionsQuery.data?.items ?? []).map((group) => ({
    id: group.id,
    title: group.name,
    description: t('education.groupCardDescription'),
    meta: t('education.groupScheduleStatusUnknown'),
    leading: <Users className="h-5 w-5 text-accent" />,
  }))
  const settingsTeacherItems: CardPickerItem[] = (teacherOptionsQuery.data?.content ?? []).map((teacher) => ({
    id: teacher.id,
    title: teacher.displayName?.trim() || teacher.username,
    description: teacher.email ?? undefined,
    meta: t('education.teacherLabel'),
    leading: <GraduationCap className="h-5 w-5 text-accent" />,
  }))

  function openTopicBuilder() {
    setBuilder({ mode: 'topic' })
    setOpenAddMenu('general')
  }

  function openAssignmentBuilder(topicId: string) {
    setBuilder({ mode: 'assignment', topicId })
    setOpenAddMenu(topicId)
  }

  function openLectureBuilder(topicId: string) {
    setBuilder({ mode: 'lecture', topicId })
    setOpenAddMenu(topicId)
  }

  function openMaterialBuilder(topicId: string) {
    setBuilder({ mode: 'material', topicId })
    setOpenAddMenu(topicId)
  }

  function openTestBuilder(topicId: string) {
    setBuilder({ mode: 'test', topicId })
    setOpenAddMenu(topicId)
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

  async function moveCourseItem(item: CourseItem, topicId: string, orderIndex: number) {
    if (item.kind === 'text') {
      return
    }

    if (item.kind === 'assignment') {
      await assignmentService.moveAssignment(item.id, { topicId, orderIndex })
    } else if (item.kind === 'lecture') {
      await educationService.moveLecture(item.id, { topicId, orderIndex })
    } else if (item.kind === 'material') {
      await educationService.moveTopicMaterial(item.id, { topicId, orderIndex })
    } else {
      await testingService.moveTest(item.id, { topicId, orderIndex })
    }
  }

  async function moveCourseItemWithinTopic(topicId: string, index: number, direction: -1 | 1) {
    const items = orderedItemsByTopicId.get(topicId) ?? []
    const targetIndex = index + direction
    const current = items[index]
    const target = items[targetIndex]
    if (!current || !target || current.kind === 'text' || target.kind === 'text') {
      return
    }

    await Promise.all([
      moveCourseItem(current, topicId, targetIndex),
      moveCourseItem(target, topicId, index),
    ])
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['education', 'subject-assignments', subjectId] }),
      queryClient.invalidateQueries({ queryKey: ['education', 'subject-lectures', subjectId] }),
      queryClient.invalidateQueries({ queryKey: ['education', 'subject-materials', subjectId] }),
      queryClient.invalidateQueries({ queryKey: ['education', 'subject-tests', subjectId] }),
    ])
  }

  async function moveCourseItemToTopic(item: CourseItem, topicId: string) {
    if (item.kind === 'text') {
      return
    }

    await moveCourseItem(item, topicId, nextCourseItemOrderIndex(topicId, assignments, tests, lectures, materials))
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['education', 'subject-assignments', subjectId] }),
      queryClient.invalidateQueries({ queryKey: ['education', 'subject-lectures', subjectId] }),
      queryClient.invalidateQueries({ queryKey: ['education', 'subject-materials', subjectId] }),
      queryClient.invalidateQueries({ queryKey: ['education', 'subject-tests', subjectId] }),
    ])
  }

  function openSettingsPanel() {
    setSettingsForm({
      name: currentSubject.name,
      description: currentSubject.description ?? '',
      selectedGroupIds: currentSubject.groupIds,
      selectedTeacherIds: currentSubject.teacherIds,
    })
    handleTabChange('settings')
    setSettingsOpen(true)
  }

  const nearestDeadline = upcomingDeadlines[0]?.deadline
    ?? studentUpcomingDeadlines[0]?.deadline
    ?? null

  return (
    <div className="space-y-6">
      <Breadcrumbs
        items={[
          { label: t('navigation.shared.education'), to: '/education' },
          { label: t('navigation.shared.subjects'), to: '/subjects' },
          { label: subject.name },
        ]}
      />
      <Card className="space-y-5 rounded-2xl border border-border-strong bg-surface p-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="flex min-w-0 items-start gap-4">
            <span className="inline-flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl border border-border bg-accent-muted text-accent">
              <NotebookTabs className="h-6 w-6" />
            </span>
            <div className="min-w-0 space-y-2">
              <h1 className="break-words text-2xl font-bold tracking-[-0.03em] text-text-primary md:text-3xl">{subject.name}</h1>
              <p className="text-sm leading-6 text-text-secondary">{subject.description ?? t('education.subjectDescriptionFallback')}</p>
              <div className="flex flex-wrap gap-2">
                <span className="rounded-full border border-border bg-surface-muted px-3 py-1 text-xs font-semibold text-text-secondary">
                  {t('education.subjectGroupsCount', { count: subject.groupIds.length })}
                </span>
                <span className="rounded-full border border-border bg-surface-muted px-3 py-1 text-xs font-semibold text-text-secondary">
                  {t('education.subjectTeachersCount', { count: subject.teacherIds.length })}
                </span>
                <span className="rounded-full border border-border bg-surface-muted px-3 py-1 text-xs font-semibold text-text-secondary">
                  {sidebarMetrics ? t('common.status.ACTIVE') : t('education.statusUnavailable')}
                </span>
              </div>
            </div>
          </div>
          <div className="flex flex-wrap gap-3">
            <Link to="/subjects">
              <Button variant="secondary">{t('education.backToSubjects')}</Button>
            </Link>
            {canManageSettings ? (
              <Button variant="secondary" onClick={openSettingsPanel}>
                <Settings2 className="mr-2 h-4 w-4" />
                {t('education.manageSubjectSettings')}
              </Button>
            ) : null}
          </div>
        </div>
      </Card>

      <SectionTabs
        activeId={activeTab}
        items={courseTabs}
        onChange={(tabId) => handleTabChange(tabId as SubjectTab)}
      />

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_340px]">
        <main className="space-y-5">
          {activeTab === 'overview' ? (
            <div className="space-y-5">
              <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <MetricCard title={t('education.overview.topics')} value={topics.length} />
                <MetricCard title={t('education.overview.assignments')} value={assignments.length} />
                <MetricCard title={t('education.overview.tests')} value={tests.length} />
                <MetricCard title={t('education.courseGroups')} value={subject.groupIds.length} />
              </div>
              <div className="grid gap-4 xl:grid-cols-2">
                <Card className="space-y-3 rounded-2xl border border-border bg-surface p-5">
                  <PageHeader title={t('education.aboutCourse')} />
                  <p className="text-sm leading-6 text-text-secondary">{subject.description ?? t('education.subjectOverviewFallback')}</p>
                </Card>
                <Card className="space-y-3 rounded-2xl border border-border bg-surface p-5">
                  <PageHeader title={t('education.courseProgress')} />
                  {isStudent && studentSubjectAnalyticsQuery.data ? (
                    <div className="grid gap-3 md:grid-cols-2">
                      <SidebarLine
                        label={t('analytics.completionRate')}
                        value={`${Math.round(studentSubjectAnalyticsQuery.data.completionRate * 100)}%`}
                      />
                      <SidebarLine
                        label={t('dashboard.metrics.averageScore')}
                        value={studentSubjectAnalyticsQuery.data.averageScore == null ? '-' : `${studentSubjectAnalyticsQuery.data.averageScore}`}
                      />
                    </div>
                  ) : (
                    <p className="text-sm text-text-secondary">{t('education.emptyAnalytics')}</p>
                  )}
                </Card>
              </div>
              <div className="grid gap-4 xl:grid-cols-2">
                <Card className="space-y-3 rounded-2xl border border-border bg-surface p-5">
                  <PageHeader title={t('education.latestMaterials')} />
                  {latestMaterials.length === 0 ? (
                    <p className="text-sm text-text-secondary">{t('education.noContentAvailable')}</p>
                  ) : (
                    <div className="space-y-2">
                      {latestMaterials.map((material) => (
                        <Link key={material.id} to={`/materials/${material.id}`} className="block rounded-[14px] border border-border bg-surface-muted px-4 py-3 transition hover:border-border-strong">
                          <p className="font-semibold text-text-primary">{material.title}</p>
                          <p className="mt-1 text-xs text-text-muted">
                            {t(`education.materialTypeLabels.${material.type}`)} · {formatDateTime(material.updatedAt)}
                          </p>
                        </Link>
                      ))}
                    </div>
                  )}
                </Card>
                <Card className="space-y-3 rounded-2xl border border-border bg-surface p-5">
                  <PageHeader title={t('education.upcomingDeadlines')} />
                  {upcomingDeadlines.length === 0 ? (
                    <p className="text-sm text-text-secondary">{t('education.noUpcomingDeadline')}</p>
                  ) : (
                    <div className="space-y-2">
                      {upcomingDeadlines.map((item) => (
                        <Link key={`${item.kind}-${item.id}`} to={item.to} className="flex items-start justify-between gap-3 rounded-[14px] border border-border bg-surface-muted px-4 py-3 transition hover:border-border-strong">
                          <div>
                            <p className="font-semibold text-text-primary">{item.title}</p>
                            <p className="mt-1 text-xs uppercase tracking-[0.14em] text-text-muted">
                              {item.kind === 'assignment' ? t('education.contentType.assignment') : t('education.contentType.test')}
                            </p>
                          </div>
                          <div className="text-right">
                            <p className="text-sm font-medium text-text-primary">{formatDateTime(item.deadline)}</p>
                            <p className="mt-1 text-xs text-text-muted">{item.status}</p>
                          </div>
                        </Link>
                      ))}
                    </div>
                  )}
                </Card>
              </div>
            </div>
          ) : null}

          {activeTab === 'topics' ? (
            <div className="space-y-4">
              <CourseSection
                actions={canManageContent ? (
                  <Button
                    variant="secondary"
                    onClick={() => {
                      setOpenAddMenu((current) => current === 'general' ? null : 'general')
                      setBuilder(null)
                    }}
                  >
                    <Plus className="mr-2 h-4 w-4" />
                    {t('education.addContent')}
                  </Button>
                ) : null}
                description={t('education.generalSectionDescription')}
                items={generalItems}
                title={t('education.courseGeneralSection')}
              >
                {openAddMenu === 'general' && canManageContent ? (
                  <AddContentPanel
                    allowSection
                    title={t('education.addContent')}
                    onAddAssignment={undefined}
                    onAddSection={openTopicBuilder}
                    onAddTest={undefined}
                  />
                ) : null}
                {builder?.mode === 'topic' ? (
                  <BuilderPanel
                    title={t('education.createTopic')}
                    onCancel={() => setBuilder(null)}
                  >
                    <FormField label={t('common.labels.title')}>
                      <Input
                        value={topicForm.title}
                        onChange={(event) => setTopicForm({ title: event.target.value })}
                      />
                    </FormField>
                    <div className="flex flex-wrap gap-3">
                      <Button
                        disabled={!topicForm.title.trim() || createTopicMutation.isPending}
                        onClick={() => createTopicMutation.mutate()}
                      >
                        {t('common.actions.create')}
                      </Button>
                      <Button variant="secondary" onClick={() => setBuilder(null)}>
                        {t('common.actions.cancel')}
                      </Button>
                    </div>
                    {!topicForm.title.trim() ? (
                      <p className="text-sm text-text-secondary">{t('courseBuilder.titleRequired')}</p>
                    ) : null}
                  </BuilderPanel>
                ) : null}
              </CourseSection>

              {topics.length === 0 ? (
                <EmptyState
                  action={canManageContent ? (
                    <Button onClick={openTopicBuilder}>{t('education.addFirstSection')}</Button>
                  ) : undefined}
                  description={canManageContent ? t('education.noTopicsTeacher') : t('education.noContentAvailable')}
                  title={t('education.courseContentFlow')}
                />
              ) : (
                topics.map((topic, index) => {
                  const items = orderedItemsByTopicId.get(topic.id) ?? []
                  return (
                    <CourseSection
                      key={topic.id}
                      actions={(
                        <div className="flex flex-wrap gap-2">
                          <Link to={`/subjects/${subjectId}/topics/${topic.id}`}>
                            <Button variant="secondary">{t('common.actions.open')}</Button>
                          </Link>
                          {canManageContent ? (
                            <>
                              <Button
                                disabled={index === 0 || reorderTopicsMutation.isPending}
                                variant="ghost"
                                onClick={() => moveTopic(topic.id, -1)}
                              >
                                <ArrowUp className="h-4 w-4" />
                              </Button>
                              <Button
                                disabled={index === topics.length - 1 || reorderTopicsMutation.isPending}
                                variant="ghost"
                                onClick={() => moveTopic(topic.id, 1)}
                              >
                                <ArrowDown className="h-4 w-4" />
                              </Button>
                              <Button
                                variant="secondary"
                                onClick={() => {
                                  setOpenAddMenu((current) => current === topic.id ? null : topic.id)
                                  setBuilder(null)
                                }}
                              >
                                <Plus className="mr-2 h-4 w-4" />
                                {t('education.addContent')}
                              </Button>
                            </>
                          ) : null}
                        </div>
                      )}
                      description={t('education.topicWorkloadSummary', {
                        assignments: assignments.filter((assignment) => assignment.topicId === topic.id).length,
                        lectures: lectures.filter((lecture) => lecture.topicId === topic.id).length,
                        materials: materials.filter((material) => material.topicId === topic.id).length,
                        tests: tests.filter((test) => test.topicId === topic.id).length,
                      })}
                      emptyLabel={canManageContent ? t('education.emptyTopicTeacher') : t('education.noContentAvailable')}
                      items={items}
                      renderItems={false}
                      title={topic.title}
                    >
                      {openAddMenu === topic.id && canManageContent ? (
                        <AddContentPanel
                          title={t('education.addContent')}
                          onAddAssignment={() => openAssignmentBuilder(topic.id)}
                          onAddLecture={() => openLectureBuilder(topic.id)}
                          onAddMaterial={() => openMaterialBuilder(topic.id)}
                          onAddTest={() => openTestBuilder(topic.id)}
                        />
                      ) : null}

                      {builder?.mode === 'assignment' && builder.topicId === topic.id ? (
                        <BuilderPanel
                          title={t('assignments.createAssignment')}
                          onCancel={() => setBuilder(null)}
                        >
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
                            <Button variant="secondary" onClick={() => setBuilder(null)}>
                              {t('common.actions.cancel')}
                            </Button>
                          </div>
                          {!assignmentForm.title.trim() || !assignmentForm.deadline ? (
                            <p className="text-sm text-text-secondary">{t('courseBuilder.assignmentRequired')}</p>
                          ) : null}
                        </BuilderPanel>
                      ) : null}

                      {builder?.mode === 'lecture' && builder.topicId === topic.id ? (
                        <BuilderPanel
                          title={t('education.createLecture')}
                          onCancel={() => setBuilder(null)}
                        >
                          <FormField label={t('common.labels.title')}>
                            <Input
                              value={lectureForm.title}
                              onChange={(event) => setLectureForm((current) => ({ ...current, title: event.target.value }))}
                            />
                          </FormField>
                          <FormField label={t('common.labels.description')}>
                            <Textarea
                              value={lectureForm.content}
                              onChange={(event) => setLectureForm((current) => ({ ...current, content: event.target.value }))}
                            />
                          </FormField>
                          <div className="flex flex-wrap gap-3">
                            <Button
                              disabled={!lectureForm.title.trim() || createLectureMutation.isPending}
                              onClick={() => createLectureMutation.mutate()}
                            >
                              {t('common.actions.create')}
                            </Button>
                            <Button variant="secondary" onClick={() => setBuilder(null)}>
                              {t('common.actions.cancel')}
                            </Button>
                          </div>
                          {!lectureForm.title.trim() ? (
                            <p className="text-sm text-text-secondary">{t('courseBuilder.titleRequired')}</p>
                          ) : null}
                        </BuilderPanel>
                      ) : null}

                      {builder?.mode === 'material' && builder.topicId === topic.id ? (
                        <BuilderPanel
                          title={t('education.addMaterial')}
                          onCancel={() => setBuilder(null)}
                        >
                          <FormField label={t('education.materialTitle')}>
                            <Input
                              value={materialForm.title}
                              onChange={(event) => setMaterialForm((current) => ({ ...current, title: event.target.value }))}
                            />
                          </FormField>
                          <FormField label={t('education.materialType')}>
                            <Select
                              value={materialForm.type}
                              onChange={(event) => setMaterialForm((current) => ({ ...current, type: event.target.value as TopicMaterialType }))}
                            >
                              <option value="TEXT">{t('education.textMaterial')}</option>
                              <option value="LINK">{t('education.linkMaterial')}</option>
                              <option value="FILE">{t('education.fileMaterial')}</option>
                            </Select>
                          </FormField>
                          <FormField label={t('education.materialDescription')}>
                            <Textarea
                              value={materialForm.description}
                              onChange={(event) => setMaterialForm((current) => ({ ...current, description: event.target.value }))}
                            />
                          </FormField>
                          {materialForm.type === 'LINK' ? (
                            <FormField label={t('education.materialUrl')}>
                              <Input
                                value={materialForm.url}
                                onChange={(event) => setMaterialForm((current) => ({ ...current, url: event.target.value }))}
                              />
                            </FormField>
                          ) : null}
                          {materialForm.type === 'FILE' ? (
                            <FormField label={t('education.uploadFile')}>
                              <Input type="file" onChange={(event) => setMaterialFile(event.target.files?.[0] ?? null)} />
                            </FormField>
                          ) : null}
                          <div className="flex items-center gap-2">
                            <input
                              id={`material-visible-${topic.id}`}
                              checked={materialForm.visible}
                              type="checkbox"
                              onChange={(event) => setMaterialForm((current) => ({ ...current, visible: event.target.checked }))}
                            />
                            <label htmlFor={`material-visible-${topic.id}`} className="text-sm text-text-secondary">
                              {t('education.visibleToStudents')}
                            </label>
                          </div>
                          <div className="flex flex-wrap gap-3">
                            <Button
                              disabled={
                                !materialForm.title.trim()
                                || (materialForm.type === 'FILE' && !materialFile)
                                || (materialForm.type === 'LINK' && !materialForm.url.trim())
                                || (materialForm.type === 'TEXT' && !materialForm.description.trim())
                                || createMaterialMutation.isPending
                              }
                              onClick={() => createMaterialMutation.mutate()}
                            >
                              {t('common.actions.create')}
                            </Button>
                            <Button variant="secondary" onClick={() => setBuilder(null)}>
                              {t('common.actions.cancel')}
                            </Button>
                          </div>
                        </BuilderPanel>
                      ) : null}

                      {builder?.mode === 'test' && builder.topicId === topic.id ? (
                        <BuilderPanel
                          title={t('testing.createTest')}
                          onCancel={() => setBuilder(null)}
                        >
                          <div className="grid gap-4 md:grid-cols-2">
                            <FormField label={t('common.labels.title')}>
                              <Input
                                value={testForm.title}
                                onChange={(event) => setTestForm((current) => ({ ...current, title: event.target.value }))}
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
                          <div className="grid gap-4 md:grid-cols-3">
                            <FormField label={t('testing.maxPoints')}>
                              <Input
                                type="number"
                                value={testForm.maxPoints}
                                onChange={(event) => setTestForm((current) => ({ ...current, maxPoints: Number(event.target.value) }))}
                              />
                            </FormField>
                            <FormField label={t('testing.maxAttempts')}>
                              <Input
                                type="number"
                                value={testForm.maxAttempts}
                                onChange={(event) => setTestForm((current) => ({ ...current, maxAttempts: Number(event.target.value) }))}
                              />
                            </FormField>
                            <FormField label={t('testing.timeLimitMinutes')}>
                              <Input
                                type="number"
                                value={testForm.timeLimitMinutes}
                                onChange={(event) => setTestForm((current) => ({ ...current, timeLimitMinutes: Number(event.target.value) }))}
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
                            <Button variant="secondary" onClick={() => setBuilder(null)}>
                              {t('common.actions.cancel')}
                            </Button>
                          </div>
                          {!testForm.title.trim() ? (
                            <p className="text-sm text-text-secondary">{t('courseBuilder.titleRequired')}</p>
                          ) : null}
                        </BuilderPanel>
                      ) : null}

                      {items.map((item, itemIndex) => (
                        <CourseItemCard
                          key={item.id}
                          item={item}
                          canManageContent={canManageContent}
                          moveControl={item.kind === 'text' ? null : (
                            <div className="flex flex-wrap items-center gap-2">
                              <Button
                                disabled={itemIndex === 0}
                                variant="ghost"
                                onClick={() => void moveCourseItemWithinTopic(topic.id, itemIndex, -1)}
                              >
                                <ArrowUp className="h-4 w-4" />
                              </Button>
                              <Button
                                disabled={itemIndex === items.length - 1}
                                variant="ghost"
                                onClick={() => void moveCourseItemWithinTopic(topic.id, itemIndex, 1)}
                              >
                                <ArrowDown className="h-4 w-4" />
                              </Button>
                              <Select
                                value={item.topicId ?? topic.id}
                                onChange={(event) => void moveCourseItemToTopic(item, event.target.value)}
                              >
                                {topics.map((targetTopic) => (
                                  <option key={targetTopic.id} value={targetTopic.id}>
                                    {targetTopic.title}
                                  </option>
                                ))}
                              </Select>
                            </div>
                          )}
                        />
                      ))}
                    </CourseSection>
                  )
                })
              )}
            </div>
          ) : null}

          {activeTab === 'assignments' ? (
            <Card className="space-y-4 rounded-2xl border border-border bg-surface p-5">
              <PageHeader title={t('education.subjectTabs.assignments')} />
              {latestAssignments.length === 0 ? (
                <EmptyState description={t('assignments.empty')} title={t('education.subjectTabs.assignments')} />
              ) : (
                <div className="space-y-3">
                  {latestAssignments.map((assignment) => (
                    <Link key={assignment.id} to={`/assignments/${assignment.id}`} className="block rounded-[14px] border border-border bg-surface-muted px-4 py-3 transition hover:border-border-strong">
                      <div className="flex flex-wrap items-start justify-between gap-3">
                        <div>
                          <p className="font-semibold text-text-primary">{assignment.title}</p>
                          <p className="mt-1 text-xs text-text-muted">{formatDateTime(assignment.deadline)}</p>
                        </div>
                        <StatusBadge value={assignment.status} />
                      </div>
                    </Link>
                  ))}
                </div>
              )}
            </Card>
          ) : null}

          {activeTab === 'tests' ? (
            <Card className="space-y-4 rounded-2xl border border-border bg-surface p-5">
              <PageHeader title={t('education.subjectTabs.tests')} />
              {latestTests.length === 0 ? (
                <EmptyState description={t('testing.empty')} title={t('education.subjectTabs.tests')} />
              ) : (
                <div className="space-y-3">
                  {latestTests.map((test) => (
                    <Link key={test.id} to={`/tests/${test.id}`} className="block rounded-[14px] border border-border bg-surface-muted px-4 py-3 transition hover:border-border-strong">
                      <div className="flex flex-wrap items-start justify-between gap-3">
                        <div>
                          <p className="font-semibold text-text-primary">{test.title}</p>
                          <p className="mt-1 text-xs text-text-muted">
                            {test.availableUntil ? formatDateTime(test.availableUntil) : t('education.availabilityUnavailable')}
                          </p>
                        </div>
                        <StatusBadge value={test.status} />
                      </div>
                    </Link>
                  ))}
                </div>
              )}
            </Card>
          ) : null}

          {activeTab === 'materials' ? (
            <Card className="space-y-4 rounded-2xl border border-border bg-surface p-5">
              <PageHeader title={t('education.subjectTabs.materials')} />
              {latestMaterials.length === 0 ? (
                <EmptyState description={t('education.noContentAvailable')} title={t('education.subjectTabs.materials')} />
              ) : (
                <div className="space-y-3">
                  {latestMaterials.map((material) => (
                    <Link key={material.id} to={`/materials/${material.id}`} className="block rounded-[14px] border border-border bg-surface-muted px-4 py-3 transition hover:border-border-strong">
                      <p className="font-semibold text-text-primary">{material.title}</p>
                      <p className="mt-1 text-xs text-text-muted">
                        {t(`education.materialTypeLabels.${material.type}`)} · {formatDateTime(material.updatedAt)}
                      </p>
                    </Link>
                  ))}
                </div>
              )}
            </Card>
          ) : null}

          {activeTab === 'students' ? (
            <Card className="space-y-4 rounded-2xl border border-border bg-surface p-5">
              <PageHeader title={t('education.subjectTabs.students')} />
              {groupsQuery.data?.length ? (
                <div className="grid gap-3 md:grid-cols-2">
                  {groupsQuery.data.map((group) => (
                    <div key={group.id} className="rounded-[14px] border border-border bg-surface-muted px-4 py-3">
                      <p className="font-semibold text-text-primary">{group.name}</p>
                      <p className="mt-1 text-xs text-text-muted">{t('education.groupStudentCountSummary')}</p>
                    </div>
                  ))}
                </div>
              ) : (
                <EmptyState description={t('education.noGroups')} title={t('education.subjectTabs.students')} />
              )}
            </Card>
          ) : null}

          {activeTab === 'analytics' ? (
            <Card className="space-y-4 rounded-2xl border border-border bg-surface p-5">
              <PageHeader title={t('education.subjectTabs.analytics')} />
              {sidebarAnalyticsRow ? (
                <div className="grid gap-3 md:grid-cols-2">
                  <SidebarLine
                    label={t('dashboard.metrics.averageScore')}
                    value={sidebarAnalyticsRow.averageScore == null ? '-' : `${sidebarAnalyticsRow.averageScore}`}
                  />
                  <SidebarLine
                    label={t('analytics.completionRate')}
                    value={`${Math.round(sidebarAnalyticsRow.completionRate * 100)}%`}
                  />
                </div>
              ) : (
                <EmptyState description={t('education.emptyAnalytics')} title={t('education.subjectTabs.analytics')} />
              )}
            </Card>
          ) : null}

          {activeTab === 'settings' ? (
            canManageSettings ? (
              <Card className="space-y-4 rounded-2xl border border-border bg-surface p-5">
                <PageHeader
                  title={t('education.subjectTabs.settings')}
                  description={t('education.settingsDescription')}
                />
                <Button variant="secondary" onClick={openSettingsPanel}>
                  <Settings2 className="mr-2 h-4 w-4" />
                  {t('education.manageSubjectSettings')}
                </Button>
              </Card>
            ) : (
              <AccessDeniedPage />
            )
          ) : null}
        </main>

        <aside className="space-y-5">
          <Card className="space-y-4">
            <PageHeader title={t('education.courseTeachers')} />
            {teacherNames.length > 0 ? (
              <div className="space-y-3">
                {teacherNames.map((teacherName) => (
                  <div key={teacherName} className="rounded-[14px] border border-border bg-surface-muted px-4 py-3 text-sm text-text-secondary">
                    {teacherName}
                  </div>
                ))}
              </div>
            ) : (
              <EmptyState description={t('education.noTeachersAssigned')} title={t('education.subjectTeachers')} />
            )}
          </Card>
          <Card className="space-y-4">
            <PageHeader title={t('education.courseGroups')} />
            {groupsQuery.data?.length ? (
              <div className="space-y-2">
                {groupsQuery.data.map((group) => (
                  <Link
                    key={group.id}
                    to={`/groups/${group.id}`}
                    className="block rounded-[14px] border border-border bg-surface-muted px-4 py-3 text-sm text-text-secondary transition hover:border-border-strong"
                  >
                    {group.name}
                  </Link>
                ))}
              </div>
            ) : (
              <EmptyState description={t('education.noGroups')} title={t('education.courseGroups')} />
            )}
          </Card>
          <Card className="space-y-4">
            <PageHeader title={t('education.courseMetadata')} />
            <SidebarLine label={t('education.subjectStatus')} value={sidebarMetrics ? t('common.status.ACTIVE') : t('education.statusUnavailable')} />
            <SidebarLine label={t('education.lastUpdated')} value={formatDateTime(subject.updatedAt)} />
            <SidebarLine label={t('education.nextLesson')} value={nextLesson ? formatDate(nextLesson.date) : t('education.noUpcomingLesson')} />
            <SidebarLine
              label={t('education.nearestDeadline')}
              value={nearestDeadline ? formatDateTime(nearestDeadline) : t('education.noUpcomingDeadline')}
            />
          </Card>
          {(isAssignedTeacher || isAdmin) ? (
            <Card className="space-y-3">
              <PageHeader title={t('education.management')} />
              <Button fullWidth variant="secondary" onClick={openTopicBuilder}>
                <Layers3 className="mr-2 h-4 w-4" />
                {t('education.createTopic')}
              </Button>
              {topics[0] ? (
                <Button fullWidth variant="secondary" onClick={() => openAssignmentBuilder(topics[0].id)}>
                  <ClipboardCheck className="mr-2 h-4 w-4" />
                  {t('assignments.createAssignment')}
                </Button>
              ) : null}
              {topics[0] ? (
                <Button fullWidth variant="secondary" onClick={() => openTestBuilder(topics[0].id)}>
                  <TestTube2 className="mr-2 h-4 w-4" />
                  {t('testing.createTest')}
                </Button>
              ) : null}
            </Card>
          ) : null}
        </aside>
      </div>

      {canManageSettings && settingsOpen && activeTab === 'settings' ? (
        <Card className="space-y-5">
          <PageHeader
            actions={(
              <Button variant="secondary" onClick={() => setSettingsOpen(false)}>
                {t('common.actions.close')}
              </Button>
            )}
            description={t('education.settingsDescription')}
            title={t('education.manageSubjectSettings')}
          />
          <div className="grid gap-4 md:grid-cols-2">
            <FormField label={t('common.labels.name')}>
              <Input
                value={settingsForm.name}
                onChange={(event) => setSettingsForm((current) => ({ ...current, name: event.target.value }))}
              />
            </FormField>
            <FormField label={t('common.labels.description')}>
              <Textarea
                value={settingsForm.description}
                onChange={(event) => setSettingsForm((current) => ({ ...current, description: event.target.value }))}
              />
            </FormField>
          </div>
          <div className="grid gap-4 xl:grid-cols-2">
            <CardPicker
              emptyDescription={t('education.noGroupOptions')}
              emptyTitle={t('navigation.shared.groups')}
              items={settingsGroupItems}
              label={t('navigation.shared.groups')}
              loading={groupOptionsQuery.isLoading}
              multiple
              searchLabel={t('common.actions.search')}
              searchPlaceholder={t('education.groupSearchPlaceholder')}
              searchValue={groupSearch}
              selectedIds={settingsForm.selectedGroupIds}
              onSearchChange={setGroupSearch}
              onToggle={(id) => setSettingsForm((current) => ({
                ...current,
                selectedGroupIds: toggleId(current.selectedGroupIds, id),
              }))}
            />
            <CardPicker
              emptyDescription={t('education.noTeacherOptions')}
              emptyTitle={t('education.subjectTeachers')}
              items={settingsTeacherItems}
              label={t('education.subjectTeachers')}
              loading={teacherOptionsQuery.isLoading}
              multiple
              searchLabel={t('common.actions.search')}
              searchPlaceholder={t('education.teacherSearchPlaceholder')}
              searchValue={teacherSearch}
              selectedIds={settingsForm.selectedTeacherIds}
              onSearchChange={setTeacherSearch}
              onToggle={(id) => setSettingsForm((current) => ({
                ...current,
                selectedTeacherIds: toggleId(current.selectedTeacherIds, id),
              }))}
            />
          </div>
          <Button
            disabled={!settingsForm.name.trim() || settingsForm.selectedGroupIds.length === 0 || updateSubjectMutation.isPending}
            onClick={() => updateSubjectMutation.mutate()}
          >
            {t('common.actions.save')}
          </Button>
        </Card>
      ) : null}
    </div>
  )
}

function CourseSection({
  actions,
  children,
  description,
  emptyLabel,
  items,
  renderItems = true,
  title,
}: {
  actions?: ReactNode
  children?: ReactNode
  description: string
  emptyLabel?: string
  items: CourseItem[]
  renderItems?: boolean
  title: string
}) {
  return (
    <section className="rounded-[18px] border border-border bg-surface p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <h2 className="text-lg font-semibold text-text-primary">{title}</h2>
          <p className="text-sm leading-6 text-text-secondary">{description}</p>
        </div>
        {actions}
      </div>
      {children ? <div className="mt-4">{children}</div> : null}
      <div className="mt-4 space-y-3">
        {renderItems ? items.map((item) => (
          <CourseItemCard
            key={item.id}
            canManageContent={false}
            item={item}
            moveControl={null}
          />
        )) : null}
        {items.length === 0 && emptyLabel ? (
          <div className="rounded-[14px] border border-dashed border-border bg-surface-muted px-4 py-5 text-sm text-text-secondary">
            {emptyLabel}
          </div>
        ) : null}
      </div>
    </section>
  )
}

function CourseItemCard({
  canManageContent,
  item,
  moveControl,
}: {
  canManageContent: boolean
  item: CourseItem
  moveControl: ReactNode
}) {
  const { t } = useTranslation()
  const Icon = item.kind === 'assignment'
    ? ClipboardCheck
    : item.kind === 'test'
      ? TestTube2
      : item.kind === 'lecture'
        ? FileText
        : BookText

  return (
    <div className="rounded-[16px] border border-border bg-surface-muted p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 items-start gap-3">
          <span className="inline-flex h-11 w-11 shrink-0 items-center justify-center rounded-[14px] bg-accent-muted text-accent">
            <Icon className="h-5 w-5" />
          </span>
          <div className="min-w-0 space-y-2">
            <div className="flex flex-wrap items-center gap-2">
              <span className="rounded-full border border-border px-2.5 py-1 text-xs font-semibold uppercase tracking-[0.16em] text-text-secondary">
                {item.label}
              </span>
              {item.status ? <StatusBadge value={item.status} /> : null}
            </div>
            <h3 className="break-words text-base font-semibold text-text-primary">{item.title}</h3>
            {item.description ? (
              <p className="text-sm leading-6 text-text-secondary">{item.description}</p>
            ) : null}
            <div className="flex flex-wrap gap-2">
              {item.meta.map((value) => (
                <span key={value} className="rounded-full border border-border bg-surface px-2.5 py-1 text-xs font-medium text-text-secondary">
                  {value}
                </span>
              ))}
            </div>
          </div>
        </div>
        {canManageContent && moveControl ? moveControl : null}
      </div>
      {item.to ? (
        <div className="mt-4">
          <Link to={item.to}>
            <Button variant="secondary">
              <ExternalLink className="mr-2 h-4 w-4" />
              {t('common.actions.open')}
            </Button>
          </Link>
        </div>
      ) : null}
    </div>
  )
}

function BuilderPanel({
  children,
  onCancel,
  title,
}: {
  children: ReactNode
  onCancel: () => void
  title: string
}) {
  const { t } = useTranslation()

  return (
    <div className="rounded-[16px] border border-accent/25 bg-accent-muted/20 p-4">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h3 className="text-base font-semibold text-text-primary">{title}</h3>
        <Button variant="ghost" onClick={onCancel}>{t('common.actions.close')}</Button>
      </div>
      <div className="space-y-4">{children}</div>
    </div>
  )
}

function AddContentPanel({
  allowSection,
  onAddAssignment,
  onAddLecture,
  onAddMaterial,
  onAddSection,
  onAddTest,
  title,
}: {
  allowSection?: boolean
  onAddAssignment?: () => void
  onAddLecture?: () => void
  onAddMaterial?: () => void
  onAddSection?: () => void
  onAddTest?: () => void
  title: string
}) {
  const { t } = useTranslation()

  return (
    <div className="rounded-[16px] border border-border bg-surface-muted p-4">
      <div className="mb-4">
        <h3 className="text-base font-semibold text-text-primary">{title}</h3>
        <p className="text-sm leading-6 text-text-secondary">{t('education.addContentDescription')}</p>
      </div>
      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
        {allowSection ? (
          <Button variant="secondary" onClick={onAddSection}>
            <BookOpen className="mr-2 h-4 w-4" />
            {t('education.contentType.section')}
          </Button>
        ) : null}
        {onAddAssignment ? (
          <Button variant="secondary" onClick={onAddAssignment}>
            <ClipboardCheck className="mr-2 h-4 w-4" />
            {t('education.contentType.assignment')}
          </Button>
        ) : (
          <DisabledContentAction icon={<ClipboardCheck className="h-4 w-4" />} label={t('education.contentType.assignment')} />
        )}
        {onAddTest ? (
          <Button variant="secondary" onClick={onAddTest}>
            <TestTube2 className="mr-2 h-4 w-4" />
            {t('education.contentType.test')}
          </Button>
        ) : (
          <DisabledContentAction icon={<TestTube2 className="h-4 w-4" />} label={t('education.contentType.test')} />
        )}
        {onAddLecture ? (
          <Button variant="secondary" onClick={onAddLecture}>
            <FileText className="mr-2 h-4 w-4" />
            {t('education.contentType.lecture')}
          </Button>
        ) : (
          <DisabledContentAction icon={<FileText className="h-4 w-4" />} label={t('education.contentType.lecture')} />
        )}
        {onAddMaterial ? (
          <Button variant="secondary" onClick={onAddMaterial}>
            <BookText className="mr-2 h-4 w-4" />
            {t('education.contentType.material')}
          </Button>
        ) : (
          <DisabledContentAction icon={<FileText className="h-4 w-4" />} label={t('education.contentType.material')} />
        )}
        <DisabledContentAction icon={<Link2 className="h-4 w-4" />} label={t('education.contentType.link')} />
      </div>
      <p className="mt-3 text-sm text-text-secondary">{t('education.unsupportedContentHint')}</p>
    </div>
  )
}

function DisabledContentAction({
  icon,
  label,
}: {
  icon: ReactNode
  label: string
}) {
  return (
    <Button disabled variant="secondary">
      <span className="mr-2 inline-flex">{icon}</span>
      {label}
    </Button>
  )
}

function SidebarLine({
  label,
  value,
}: {
  label: string
  value: string
}) {
  return (
    <div className="space-y-1 rounded-[14px] border border-border bg-surface-muted px-4 py-3">
      <p className="text-xs font-semibold uppercase tracking-[0.16em] text-text-muted">{label}</p>
      <p className="text-sm font-medium text-text-primary">{value}</p>
    </div>
  )
}

function buildTopicItems({
  assignments,
  lectures,
  materials,
  pendingReviewByAssignmentId,
  studentAvailableTests,
  studentPendingAssignments,
  t,
  tests,
  topicId,
}: {
  assignments: AssignmentResponse[]
  lectures: LectureResponse[]
  materials: TopicMaterialResponse[]
  pendingReviewByAssignmentId: Map<string, number>
  studentAvailableTests: Map<string, DashboardTestItemResponse>
  studentPendingAssignments: Map<string, DashboardAssignmentItemResponse>
  t: (key: string, values?: Record<string, unknown>) => string
  tests: TestResponse[]
  topicId: string
}) {
  const assignmentItems: CourseItem[] = assignments
    .filter((assignment) => assignment.topicId === topicId)
    .map((assignment) => {
      const pendingReview = pendingReviewByAssignmentId.get(assignment.id)
      const studentAssignment = studentPendingAssignments.get(assignment.id)

      return {
        id: assignment.id,
        kind: 'assignment',
        title: assignment.title,
        description: assignment.description,
        label: t('education.contentType.assignment'),
        meta: [
          `${t('common.labels.deadline')}: ${formatDateTime(assignment.deadline)}`,
          ...(pendingReview ? [t('education.pendingReviewCount', { count: pendingReview })] : []),
          ...(studentAssignment?.submitted ? [t('education.contentState.submitted')] : []),
        ],
        orderIndex: assignment.orderIndex,
        status: assignment.status,
        to: `/assignments/${assignment.id}`,
        topicId,
      }
    })
  const testItems: CourseItem[] = tests
    .filter((test) => test.topicId === topicId)
    .map((test) => {
      const studentTest = studentAvailableTests.get(test.id)

      return {
        id: test.id,
        kind: 'test',
        title: test.title,
        description: null,
        label: t('education.contentType.test'),
        meta: [
          test.availableUntil
            ? `${t('testing.availableUntil')}: ${formatDateTime(test.availableUntil)}`
            : t('education.availabilityUnavailable'),
          ...(studentTest ? [t('education.attemptsSummary', { count: studentTest.attemptsUsed, max: studentTest.maxAttempts })] : []),
        ],
        orderIndex: test.orderIndex,
        status: test.status,
        to: `/tests/${test.id}`,
        topicId,
      }
    })
  const lectureItems: CourseItem[] = lectures
    .filter((lecture) => lecture.topicId === topicId)
    .map((lecture) => {
      return {
        id: lecture.id,
        kind: 'lecture',
        title: lecture.title,
        description: lecture.content ?? null,
        label: t('education.contentType.lecture'),
        meta: [
          `${t('education.updatedLabel')}: ${formatDateTime(lecture.updatedAt)}`,
        ],
        orderIndex: lecture.orderIndex,
        status: lecture.status,
        to: `/lectures/${lecture.id}`,
        topicId,
      }
    })
  const materialItems: CourseItem[] = materials
    .filter((material) => material.topicId === topicId)
    .map((material) => ({
      id: material.id,
      kind: 'material',
      title: material.title,
      description: material.description,
      label: t('education.contentType.material'),
      meta: [
        t(`education.materialTypeLabels.${material.type}`),
        ...(material.type === 'FILE' && material.originalFileName ? [material.originalFileName] : []),
        ...(material.type === 'LINK' && material.url ? [material.url] : []),
      ],
      orderIndex: material.orderIndex,
      status: material.archived
        ? t('education.archivedStatus')
        : (material.visible ? t('education.visibleToStudents') : t('education.hiddenFromStudents')),
      to: `/materials/${material.id}`,
      topicId,
    }))

  return [...assignmentItems, ...lectureItems, ...materialItems, ...testItems].sort((left, right) => left.orderIndex - right.orderIndex)
}

function nextCourseItemOrderIndex(
  topicId: string,
  assignments: AssignmentResponse[],
  tests: TestResponse[],
  lectures: LectureResponse[],
  materials: TopicMaterialResponse[],
) {
  return assignments.filter((assignment) => assignment.topicId === topicId).length
    + lectures.filter((lecture) => lecture.topicId === topicId).length
    + materials.filter((material) => material.topicId === topicId).length
    + tests.filter((test) => test.topicId === topicId).length
}

function getUserDisplayName(user: UserSummaryResponse | null | undefined) {
  if (!user) {
    return ''
  }
  return user.username || user.email
}

function toggleId(values: string[], id: string) {
  return values.includes(id) ? values.filter((value) => value !== id) : [...values, id]
}
