import {
  closestCenter,
  DndContext,
  type DragEndEvent,
  type DragOverEvent,
  type DragStartEvent,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
} from '@dnd-kit/core'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  ArrowLeft,
  ArrowRight,
  CalendarDays,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  DoorOpen,
  RotateCcw,
  Save,
  School,
  UserRound,
  Users,
} from 'lucide-react'
import { Component, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { ErrorInfo, ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Link,
  Navigate,
  useBeforeUnload,
  useLocation,
  useNavigate,
  useParams,
} from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import { loadAccessibleGroups, loadSubjectScope } from '@/pages/education/helpers'
import {
  adminUserService,
  educationService,
  roomCapabilityService,
  scheduleService,
  userDirectoryService,
} from '@/shared/api/services'
import {
  getDragMoveBlockedReasonKey,
  getScheduleMoveTargetState,
  type DragMoveBlockReason,
  type DragMoveTargetState,
} from '@/features/schedule/lib/moveTarget'
import {
  getScheduleDraftValidationReasonKey,
  getScheduleSaveDisabledReasonKey,
} from '@/features/schedule/lib/scheduleDisabledReasons'
import { moveScheduleDraft } from '@/features/schedule/lib/scheduleMoveDraft'
import { getLocalizedApiErrorMessage, getLocalizedRequestErrorMessage, normalizeApiError } from '@/shared/lib/api-errors'
import { cn } from '@/shared/lib/cn'
import { getDayOfWeekLabel, getLessonFormatLabel, getLessonTypeLabel } from '@/shared/lib/enum-labels'
import { formatDate } from '@/shared/lib/format'
import { hasAnyRole } from '@/shared/lib/roles'
import { useDebouncedValue } from '@/shared/lib/useDebouncedValue'
import type {
  AcademicSemesterResponse,
  AdminUserResponse,
  LessonSlotResponse,
  ResolvedLessonResponse,
  RoomCapabilityResponse,
  RoomResponse,
  ScheduleConflictItemResponse,
  ScheduleTemplateResponse,
  SubjectResponse,
  SubgroupValue,
  UserSummaryResponse,
} from '@/shared/types/api'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { SegmentedControl } from '@/shared/ui/SegmentedControl'
import { Select } from '@/shared/ui/Select'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { Textarea } from '@/shared/ui/Textarea'
import { UserAvatar } from '@/shared/ui/UserAvatar'
import { Breadcrumbs } from '@/widgets/common/Breadcrumbs'
import { DraftLessonCard } from '@/pages/schedule/components/DraftLessonCard'
import { EditPairDropZone } from '@/pages/schedule/components/EditPairDropZone'
import { LessonDetailsPanel } from '@/pages/schedule/components/LessonDetailsPanel'
import { ScheduleGrid } from '@/pages/schedule/components/ScheduleGrid'
import { ScheduleLegend } from '@/pages/schedule/components/ScheduleLegend'
import { ScheduleSaveBar } from '@/pages/schedule/components/ScheduleSaveBar'
import { ScheduleSummaryStrip } from '@/pages/schedule/components/ScheduleSummaryStrip'
import { ViewModeDayCard } from '@/pages/schedule/components/ViewModeDayCard'
import { WeekTabs } from '@/pages/schedule/components/WeekTabs'
import { resolveLessonPanelMode, type LessonPanelMode } from '@/pages/schedule/components/lessonPanel.helpers'
import {
  addDays,
  buildWeekDays,
  clampDateToSemesterWeek,
  findSemesterWeek,
  generateSemesterWeeks,
  getWeekNavigationState,
  getWeekTabWindow,
  toIsoDate,
} from '@/pages/schedule/components/scheduleWeek.helpers'

type TeacherUser = AdminUserResponse | UserSummaryResponse
type ScheduleScope = 'group' | 'teacher' | 'room' | 'me'
type ScheduleWeekType = 'ODD' | 'EVEN'
type EditableWeekType = 'ALL' | ScheduleWeekType
type ScheduleLessonType = ScheduleTemplateResponse['lessonType']
type ScheduleLessonFormat = ScheduleTemplateResponse['lessonFormat']
type DayOfWeekValue = ScheduleTemplateResponse['dayOfWeek']
type ScheduleViewMode = 'VIEW' | 'EDIT'
type ConflictStatus = 'idle' | 'checking' | 'clear' | 'conflict' | 'error'
type SemesterOptionKey = 'CURRENT' | 'FUTURE'
type ChangeReason =
  | 'CREATE_TEMPLATE'
  | 'UPDATE_TEMPLATE'
  | 'DELETE_TEMPLATE'
  | 'MOVE_TEMPLATE'
  | 'COPY_TEMPLATE'
  | 'COPY_DAY'

interface CanonicalPair {
  pairNumber: number
  startTime: string
  endTime: string
}

interface DraftConflictState {
  items: ScheduleConflictItemResponse[]
  lastCheckedHash: string | null
  messages: string[]
  status: ConflictStatus
}

interface ScheduleTemplateDraft {
  localId: string
  templateId: string | null
  semesterId: string
  groupId: string
  subjectId: string
  teacherId: string
  dayOfWeek: DayOfWeekValue
  slotId: string
  weekType: EditableWeekType
  subgroup: SubgroupValue
  lessonType: ScheduleLessonType
  lessonFormat: ScheduleLessonFormat
  roomId: string | null
  onlineMeetingUrl: string | null
  notes: string
  changeReason: ChangeReason | null
  conflict: DraftConflictState
  deleted: boolean
  original: ScheduleTemplateSnapshot | null
}

interface ScheduleTemplateSnapshot {
  groupId: string
  lessonFormat: ScheduleLessonFormat
  lessonType: ScheduleLessonType
  notes: string
  onlineMeetingUrl: string | null
  roomId: string | null
  slotId: string
  subgroup: SubgroupValue
  subjectId: string
  teacherId: string
  weekType: EditableWeekType
  dayOfWeek: DayOfWeekValue
}

interface DraftEditorState {
  dayOfWeek: DayOfWeekValue
  forBothWeeks: boolean
  forWholeGroup: boolean
  lessonFormat: ScheduleLessonFormat
  lessonType: ScheduleLessonType
  localId: string | null
  notes: string
  onlineMeetingUrl: string
  pairNumber: number
  roomId: string
  slotId: string
  subgroupChoice: 'FIRST' | 'SECOND'
  subjectId: string
  teacherId: string
  weekType: ScheduleWeekType
}

interface WeekSelectionState {
  semesterId: string
  weekNumber: number
}

interface SemesterOption {
  key: SemesterOptionKey
  label: string
  semester: AcademicSemesterResponse | null
}

interface FeedbackState {
  message: string
  tone: 'error' | 'success'
}

interface ScheduleDropTargetData {
  dayOfWeek: DayOfWeekValue
  pairNumber: number
}

interface RoomCapabilityDraft {
  lessonType: 'LECTURE' | 'PRACTICAL' | 'LABORATORY'
  priority: string
  active: boolean
}

interface GroupScheduleSubjectOption {
  id: string
  name: string
  teacherIds: string[]
  lectureCount: number | null
  practiceCount: number | null
  labCount: number | null
  supportsStreamLecture: boolean
  requiresSubgroupsForLabs: boolean
  source: 'CURRICULUM_PLAN' | 'DIRECT_BINDING' | 'GROUP_OVERRIDE' | null
}

interface ScheduleDetailHeader {
  backLabel: string
  backTo: string
  breadcrumbs: Array<{ label: string; to?: string }>
  description: string
  title: string
}

const pageSize = 12
const canonicalPairs: CanonicalPair[] = [
  { pairNumber: 1, startTime: '08:30', endTime: '09:50' },
  { pairNumber: 2, startTime: '10:05', endTime: '11:25' },
  { pairNumber: 3, startTime: '11:40', endTime: '13:00' },
  { pairNumber: 4, startTime: '13:15', endTime: '14:35' },
  { pairNumber: 5, startTime: '14:50', endTime: '16:10' },
  { pairNumber: 6, startTime: '16:25', endTime: '17:45' },
  { pairNumber: 7, startTime: '18:00', endTime: '19:20' },
  { pairNumber: 8, startTime: '19:35', endTime: '20:55' },
]
const orderedDays: DayOfWeekValue[] = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
]

function buildDropTargetId(dayOfWeek: DayOfWeekValue, pairNumber: number) {
  return `schedule-drop:${dayOfWeek}:${pairNumber}`
}

interface ScheduleErrorBoundaryState {
  failed: boolean
}

class ScheduleErrorBoundary extends Component<{ children: ReactNode }, ScheduleErrorBoundaryState> {
  state: ScheduleErrorBoundaryState = { failed: false }

  static getDerivedStateFromError() {
    return { failed: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Schedule page crashed', error, info)
  }

  render() {
    if (this.state.failed) {
      return (
        <ScheduleCrashFallback onRetry={() => this.setState({ failed: false })} />
      )
    }

    return this.props.children
  }
}

function ScheduleCrashFallback({ onRetry }: { onRetry: () => void }) {
  const { t } = useTranslation()

  return (
    <ErrorState
      description={t('schedule.renderErrorDescription')}
      title={t('schedule.renderErrorTitle')}
      onRetry={onRetry}
    />
  )
}

export function ScheduleWorkspacePage() {
  return (
    <ScheduleErrorBoundary>
      <ScheduleWorkspaceContent />
    </ScheduleErrorBoundary>
  )
}

function ScheduleWorkspaceContent() {
  const { pathname } = useLocation()
  const { groupId, roomId, teacherId } = useParams()

  if (pathname === '/schedule') {
    return <ScheduleLandingPage />
  }

  if (pathname === '/schedule/groups') {
    return <ScheduleGroupsPage />
  }

  if (pathname === '/schedule/teachers') {
    return <ScheduleTeachersPage />
  }

  if (pathname === '/schedule/rooms') {
    return <ScheduleRoomsPage />
  }

  if (pathname === '/schedule/me') {
    return <ScheduleDetailPage scope="me" />
  }

  if (groupId && pathname.startsWith('/schedule/groups/')) {
    return <ScheduleDetailPage entityId={groupId} scope="group" />
  }

  if (teacherId && pathname.startsWith('/schedule/teachers/')) {
    return <ScheduleDetailPage entityId={teacherId} scope="teacher" />
  }

  if (roomId && pathname.startsWith('/schedule/rooms/')) {
    return <ScheduleDetailPage entityId={roomId} scope="room" />
  }

  return <Navigate replace to="/schedule" />
}

function ScheduleLandingPage() {
  const { t } = useTranslation()
  const { primaryRole, session } = useAuth()
  const canOpenMySchedule = primaryRole === 'TEACHER' || primaryRole === 'STUDENT'
  const studentMembershipsQuery = useQuery({
    queryKey: ['schedule', 'landing-memberships', session?.user.id],
    queryFn: () => educationService.getGroupsByUser(session?.user.id ?? ''),
    enabled: primaryRole === 'STUDENT' && Boolean(session?.user.id),
  })

  const showMySchedule = primaryRole === 'TEACHER'
    || (primaryRole === 'STUDENT' && (studentMembershipsQuery.data?.length ?? 0) > 0)

  const cards = [
    {
      description: t('schedule.routeCards.groupsDescription'),
      icon: Users,
      title: t('navigation.shared.groups'),
      to: '/schedule/groups',
    },
    {
      description: t('schedule.routeCards.teachersDescription'),
      icon: UserRound,
      title: t('navigation.shared.teachers'),
      to: '/schedule/teachers',
    },
    {
      description: t('schedule.routeCards.roomsDescription'),
      icon: DoorOpen,
      title: t('schedule.routeCards.roomsTitle'),
      to: '/schedule/rooms',
    },
    ...(canOpenMySchedule && showMySchedule
      ? [{
        description: t('schedule.routeCards.myScheduleDescription'),
        icon: CalendarDays,
        title: t('schedule.mySchedule'),
        to: '/schedule/me',
      }]
      : []),
  ]

  return (
    <div className="mx-auto flex min-h-[calc(100vh-180px)] w-full max-w-6xl flex-col justify-center space-y-8 py-8">
      <div className="max-w-3xl">
        <PageHeader
          description={t('schedule.planningDescription')}
          title={t('navigation.shared.schedule')}
        />
      </div>

      <div className="grid items-stretch gap-4 md:grid-cols-2 xl:grid-cols-3">
        {cards.map((card) => {
          const Icon = card.icon

          return (
            <Link key={card.to} className="group block h-full" to={card.to}>
              <Card className="flex h-full min-h-56 flex-col justify-between gap-5 transition hover:-translate-y-0.5 hover:border-accent/40 hover:shadow-[var(--shadow-soft)]">
                <div className="space-y-4">
                  <span className="inline-flex h-12 w-12 items-center justify-center rounded-[14px] bg-accent-muted text-accent">
                    <Icon className="h-5 w-5" />
                  </span>
                  <div className="space-y-2">
                    <h2 className="text-xl font-semibold text-text-primary">{card.title}</h2>
                    <p className="text-sm leading-6 text-text-secondary">{card.description}</p>
                  </div>
                </div>
                <span className="inline-flex min-h-11 items-center justify-center rounded-[14px] border border-border bg-surface-muted px-3 text-sm font-medium text-text-primary transition group-hover:border-border-strong">
                  {t('common.actions.open')}
                </span>
              </Card>
            </Link>
          )
        })}
      </div>

      {primaryRole === 'STUDENT' && studentMembershipsQuery.isLoading ? (
        <LoadingState label={t('schedule.loadingContext')} />
      ) : null}
      {primaryRole === 'STUDENT' && studentMembershipsQuery.isError ? (
        <ErrorState
          description={t('schedule.contextLoadError')}
          title={t('navigation.shared.schedule')}
          onRetry={() => void studentMembershipsQuery.refetch()}
        />
      ) : null}
      {primaryRole === 'STUDENT' && studentMembershipsQuery.isSuccess && !showMySchedule ? (
        <Card className="space-y-2">
          <p className="text-sm font-semibold text-text-primary">{t('schedule.myScheduleUnavailableTitle')}</p>
          <p className="text-sm leading-6 text-text-secondary">{t('schedule.missingGroupMembership')}</p>
        </Card>
      ) : null}
    </div>
  )
}

function ScheduleGroupsPage() {
  const { t } = useTranslation()
  const { primaryRole, roles, session } = useAuth()
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebouncedValue(search.trim(), 300)
  const isAdmin = hasAnyRole(roles, ['ADMIN', 'OWNER'])
  const accessibleGroupsQuery = useQuery({
    queryKey: ['schedule', 'groups', 'accessible', primaryRole, session?.user.id],
    queryFn: () => loadAccessibleGroups(primaryRole, session?.user.id ?? ''),
    enabled: !isAdmin && Boolean(session?.user.id),
  })
  const groupsQuery = useQuery({
    queryKey: ['schedule', 'groups', 'admin', debouncedSearch, page],
    queryFn: () => educationService.listGroups({
      page,
      size: pageSize,
      q: debouncedSearch || undefined,
      sortBy: 'name',
      direction: 'asc',
    }),
    enabled: isAdmin,
  })

  if ((isAdmin && groupsQuery.isLoading) || (!isAdmin && accessibleGroupsQuery.isLoading)) {
    return <LoadingState />
  }

  if ((isAdmin && groupsQuery.isError) || (!isAdmin && accessibleGroupsQuery.isError)) {
    return <ErrorState description={t('common.states.error')} title={t('schedule.routeCards.groupsTitle')} />
  }

  const localGroups = (accessibleGroupsQuery.data ?? [])
    .filter((group) => !debouncedSearch || group.name.toLowerCase().includes(debouncedSearch.toLowerCase()))
  const visibleGroups = isAdmin
    ? (groupsQuery.data?.items ?? [])
    : localGroups.slice(page * pageSize, (page + 1) * pageSize)
  const totalPages = isAdmin
    ? Math.max(groupsQuery.data?.totalPages ?? 1, 1)
    : Math.max(Math.ceil(Math.max(localGroups.length, 1) / pageSize), 1)

  return (
    <div className="space-y-6">
      <Breadcrumbs items={[{ label: t('navigation.shared.schedule'), to: '/schedule' }, { label: t('navigation.shared.groups') }]} />
      <PageHeader
        description={t('schedule.groupDirectoryDescription')}
        title={t('schedule.routeCards.groupsTitle')}
      />

      <Card>
        <FormField label={t('common.actions.search')}>
          <Input
            placeholder={t('schedule.groupSearchPlaceholder')}
            value={search}
            onChange={(event) => {
              setSearch(event.target.value)
              setPage(0)
            }}
          />
        </FormField>
      </Card>

      {visibleGroups.length === 0 ? (
        <EmptyState description={t('schedule.groupDirectoryEmpty')} title={t('schedule.routeCards.groupsTitle')} />
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {visibleGroups.map((group) => (
            <Link key={group.id} className="group block h-full" to={`/schedule/groups/${group.id}`}>
              <Card className="gradient-card flex h-full flex-col justify-between gap-4 transition hover:-translate-y-0.5 hover:border-accent/40 hover:shadow-[var(--shadow-soft)]">
                <div className="space-y-4">
                  <span className="inline-flex min-h-11 min-w-11 items-center justify-center rounded-[14px] bg-accent-muted text-accent">
                    <Users className="h-5 w-5" />
                  </span>
                  <div className="space-y-2">
                    <p className="text-lg font-semibold text-text-primary">{group.name}</p>
                    <p className="text-sm leading-6 text-text-secondary">{t('schedule.groupCardDescription')}</p>
                  </div>
                </div>
                <span className="inline-flex min-h-11 items-center justify-center rounded-[14px] border border-border bg-surface-muted px-3 text-sm font-medium text-text-primary transition group-hover:border-border-strong">
                  {t('common.actions.open')}
                </span>
              </Card>
            </Link>
          ))}
        </div>
      )}

      <PaginationControls
        page={page}
        totalPages={totalPages}
        onNext={() => setPage((current) => current + 1)}
        onPrevious={() => setPage((current) => Math.max(current - 1, 0))}
      />
    </div>
  )
}

function ScheduleTeachersPage() {
  const { t } = useTranslation()
  const { primaryRole, roles, session } = useAuth()
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebouncedValue(search.trim(), 300)
  const isAdmin = hasAnyRole(roles, ['ADMIN', 'OWNER'])
  const teacherPageQuery = useQuery({
    queryKey: ['schedule', 'teachers', 'admin', debouncedSearch, page],
    queryFn: () => adminUserService.list({
      page,
      size: pageSize,
      role: 'TEACHER',
      search: debouncedSearch || undefined,
      sortBy: 'username',
      direction: 'asc',
    }),
    enabled: isAdmin,
  })
  const subjectScopeQuery = useQuery({
    queryKey: ['schedule', 'teachers', 'scope', primaryRole, session?.user.id],
    queryFn: () => loadSubjectScope(primaryRole, session?.user.id ?? '', isAdmin),
    enabled: Boolean(isAdmin || session?.user.id),
  })
  const teacherIds = useMemo(
    () => Array.from(new Set((subjectScopeQuery.data ?? []).flatMap((subject) => subject.teacherIds))),
    [subjectScopeQuery.data],
  )
  const teacherLookupQuery = useQuery({
    queryKey: ['schedule', 'teachers', 'lookup', teacherIds.join(',')],
    queryFn: () => userDirectoryService.lookup(teacherIds),
    enabled: !isAdmin && teacherIds.length > 0,
  })

  if (
    subjectScopeQuery.isLoading
    || (isAdmin && teacherPageQuery.isLoading)
    || (!isAdmin && teacherLookupQuery.isLoading)
  ) {
    return <LoadingState />
  }

  if (
    subjectScopeQuery.isError
    || (isAdmin && teacherPageQuery.isError)
    || (!isAdmin && teacherLookupQuery.isError)
  ) {
    return <ErrorState description={t('common.states.error')} title={t('schedule.routeCards.teachersTitle')} />
  }

  const localTeachers = (teacherLookupQuery.data ?? []).filter((teacher) => {
    const label = `${teacher.username} ${teacher.email ?? ''}`.toLowerCase()
    return !debouncedSearch || label.includes(debouncedSearch.toLowerCase())
  })
  const visibleTeachers = isAdmin
    ? (teacherPageQuery.data?.content ?? [])
    : localTeachers.slice(page * pageSize, (page + 1) * pageSize)
  const totalPages = isAdmin
    ? Math.max(teacherPageQuery.data?.totalPages ?? 1, 1)
    : Math.max(Math.ceil(Math.max(localTeachers.length, 1) / pageSize), 1)

  return (
    <div className="space-y-6">
      <Breadcrumbs items={[{ label: t('navigation.shared.schedule'), to: '/schedule' }, { label: t('navigation.shared.teachers') }]} />
      <PageHeader
        description={t('schedule.teacherDirectoryDescription')}
        title={t('schedule.routeCards.teachersTitle')}
      />

      <Card>
        <FormField label={t('common.actions.search')}>
          <Input
            placeholder={t('schedule.teacherSearchPlaceholder')}
            value={search}
            onChange={(event) => {
              setSearch(event.target.value)
              setPage(0)
            }}
          />
        </FormField>
      </Card>

      {visibleTeachers.length === 0 ? (
        <EmptyState description={t('schedule.teacherDirectoryEmpty')} title={t('schedule.routeCards.teachersTitle')} />
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {visibleTeachers.map((teacher) => (
            <Link key={teacher.id} className="group block h-full" to={`/schedule/teachers/${teacher.id}`}>
              <Card className="gradient-card flex h-full flex-col justify-between gap-4 transition hover:-translate-y-0.5 hover:border-accent/40 hover:shadow-[var(--shadow-soft)]">
                <div className="flex items-start gap-3">
                  <UserAvatar
                    displayName={getTeacherDisplayName(teacher)}
                    email={teacher.email}
                    size="md"
                    username={teacher.username}
                  />
                  <div className="min-w-0">
                    <p className="truncate text-lg font-semibold text-text-primary">{getTeacherDisplayName(teacher)}</p>
                    <p className="truncate text-sm text-text-muted">@{teacher.username}</p>
                    {teacher.email ? (
                      <p className="truncate text-sm text-text-secondary">{teacher.email}</p>
                    ) : null}
                  </div>
                </div>
                <span className="inline-flex min-h-11 items-center justify-center rounded-[14px] border border-border bg-surface-muted px-3 text-sm font-medium text-text-primary transition group-hover:border-border-strong">
                  {t('common.actions.open')}
                </span>
              </Card>
            </Link>
          ))}
        </div>
      )}

      <PaginationControls
        page={page}
        totalPages={totalPages}
        onNext={() => setPage((current) => current + 1)}
        onPrevious={() => setPage((current) => Math.max(current - 1, 0))}
      />
    </div>
  )
}

function ScheduleRoomsPage() {
  const { t } = useTranslation()
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const debouncedSearch = useDebouncedValue(search.trim().toLowerCase(), 300)
  const roomsQuery = useQuery({
    queryKey: ['schedule', 'rooms', 'directory'],
    queryFn: () => scheduleService.listRooms(),
  })

  if (roomsQuery.isLoading) {
    return <LoadingState />
  }

  if (roomsQuery.isError) {
    return <ErrorState description={t('common.states.error')} title={t('schedule.routeCards.roomsTitle')} />
  }

  const filteredRooms = (roomsQuery.data ?? []).filter((room) => {
    const label = `${room.building} ${room.code}`.toLowerCase()
    return !debouncedSearch || label.includes(debouncedSearch)
  })
  const visibleRooms = filteredRooms.slice(page * pageSize, (page + 1) * pageSize)
  const totalPages = Math.max(Math.ceil(Math.max(filteredRooms.length, 1) / pageSize), 1)

  return (
    <div className="space-y-6">
      <Breadcrumbs items={[{ label: t('navigation.shared.schedule'), to: '/schedule' }, { label: t('schedule.routeCards.roomsTitle') }]} />
      <PageHeader
        description={t('schedule.roomDirectoryDescription')}
        title={t('schedule.routeCards.roomsTitle')}
      />

      <Card>
        <FormField label={t('common.actions.search')}>
          <Input
            placeholder={t('schedule.roomSearchPlaceholder')}
            value={search}
            onChange={(event) => {
              setSearch(event.target.value)
              setPage(0)
            }}
          />
        </FormField>
      </Card>

      {visibleRooms.length === 0 ? (
        <EmptyState description={t('schedule.roomDirectoryEmpty')} title={t('schedule.routeCards.roomsTitle')} />
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {visibleRooms.map((room) => (
            <Link key={room.id} className="group block h-full" to={`/schedule/rooms/${room.id}`}>
              <Card className="gradient-card flex h-full flex-col justify-between gap-4 transition hover:-translate-y-0.5 hover:border-accent/40 hover:shadow-[var(--shadow-soft)]">
                <div className="space-y-4">
                  <span className="inline-flex min-h-11 min-w-11 items-center justify-center rounded-[14px] bg-accent-muted text-accent">
                    <School className="h-5 w-5" />
                  </span>
                  <div className="space-y-2">
                    <p className="text-lg font-semibold text-text-primary">{formatRoomLabel(room)}</p>
                    <p className="text-sm leading-6 text-text-secondary">
                      {t('schedule.roomCardDescription', { count: room.capacity })}
                    </p>
                  </div>
                </div>
                <span className="inline-flex min-h-11 items-center justify-center rounded-[14px] border border-border bg-surface-muted px-3 text-sm font-medium text-text-primary transition group-hover:border-border-strong">
                  {t('common.actions.open')}
                </span>
              </Card>
            </Link>
          ))}
        </div>
      )}

      <PaginationControls
        page={page}
        totalPages={totalPages}
        onNext={() => setPage((current) => current + 1)}
        onPrevious={() => setPage((current) => Math.max(current - 1, 0))}
      />
    </div>
  )
}

function ScheduleDetailPage({
  entityId,
  scope,
}: {
  entityId?: string
  scope: ScheduleScope
}) {
  const { t } = useTranslation()
  const { primaryRole, roles, session } = useAuth()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const carouselRef = useRef<HTMLDivElement | null>(null)
  const canManageTemplates = hasAnyRole(roles, ['ADMIN', 'OWNER'])
  const canEditTemplates = canManageTemplates && scope === 'group'
  const isStudent = primaryRole === 'STUDENT'
  const isTeacher = primaryRole === 'TEACHER'
  const [feedback, setFeedback] = useState<FeedbackState | null>(null)
  const [viewMode, setViewMode] = useState<ScheduleViewMode>('VIEW')
  const [viewSubgroup, setViewSubgroup] = useState<SubgroupValue>('ALL')
  const [selectedSemesterKey, setSelectedSemesterKey] = useState<SemesterOptionKey>('CURRENT')
  const [weekSelection, setWeekSelection] = useState<WeekSelectionState | null>(null)
  const [drafts, setDrafts] = useState<ScheduleTemplateDraft[]>([])
  const [lessonEditor, setLessonEditor] = useState<DraftEditorState | null>(null)
  const [deleteTargetId, setDeleteTargetId] = useState<string | null>(null)
  const [cancelLesson, setCancelLesson] = useState<ResolvedLessonResponse | null>(null)
  const [copiedLessonId, setCopiedLessonId] = useState<string | null>(null)
  const [movingLessonId, setMovingLessonId] = useState<string | null>(null)
  const [selectedDraftId, setSelectedDraftId] = useState<string | null>(null)
  const [hoverDropTargetKey, setHoverDropTargetKey] = useState<string | null>(null)
  const [initializedDraftSignature, setInitializedDraftSignature] = useState('')
  const [roomCapabilityDrafts, setRoomCapabilityDrafts] = useState<RoomCapabilityDraft[]>([])
  const [roomCapabilitiesTouched, setRoomCapabilitiesTouched] = useState(false)
  const [roomCapabilitiesError, setRoomCapabilitiesError] = useState<string | null>(null)
  const dndSensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: { distance: 5 },
    }),
    useSensor(KeyboardSensor),
  )
  const studentMembershipsQuery = useQuery({
    queryKey: ['schedule', 'student-memberships', session?.user.id],
    queryFn: () => educationService.getGroupsByUser(session?.user.id ?? ''),
    enabled: scope === 'me' && isStudent && Boolean(session?.user.id),
  })
  const activeSemesterQuery = useQuery({
    queryKey: ['schedule', 'active-semester', 'safe'],
    queryFn: async () => {
      try {
        return await scheduleService.getActiveSemester()
      } catch (error) {
        const normalizedError = normalizeApiError(error)
        if (normalizedError?.status === 404 || normalizedError?.code === 'ACTIVE_ACADEMIC_SEMESTER_NOT_FOUND') {
          return null
        }
        throw error
      }
    },
  })
  const semestersQuery = useQuery({
    queryKey: ['schedule', 'semesters'],
    queryFn: () => scheduleService.listSemesters(),
    enabled: canManageTemplates,
  })
  const slotsQuery = useQuery({
    queryKey: ['schedule', 'slots'],
    queryFn: () => scheduleService.listSlots(),
  })
  const roomsQuery = useQuery({
    queryKey: ['schedule', 'rooms', 'all'],
    queryFn: () => scheduleService.listRooms(),
  })
  const groupQuery = useQuery({
    queryKey: ['schedule', 'group', entityId],
    queryFn: () => educationService.getGroup(entityId ?? ''),
    enabled: scope === 'group' && Boolean(entityId),
  })
  const teacherQuery = useQuery({
    queryKey: ['schedule', 'teacher', entityId, canManageTemplates],
    queryFn: async () => {
      if (canManageTemplates) {
        return adminUserService.getById(entityId ?? '')
      }

      const [teacher] = await userDirectoryService.lookup(entityId ? [entityId] : [])
      return teacher ?? null
    },
    enabled: scope === 'teacher' && Boolean(entityId),
  })
  const selectedRoom = useMemo(
    () => (roomsQuery.data ?? []).find((room) => room.id === entityId) ?? null,
    [entityId, roomsQuery.data],
  )
  const roomCapabilitiesQuery = useQuery({
    queryKey: ['schedule', 'room-capabilities', entityId],
    queryFn: () => roomCapabilityService.listByRoom(entityId ?? '', true),
    enabled: scope === 'room' && Boolean(entityId),
  })

  const semesterOptions = useMemo(
    () => buildScheduleSemesterOptions(activeSemesterQuery.data, semestersQuery.data ?? [], t),
    [activeSemesterQuery.data, semestersQuery.data, t],
  )
  const selectedSemester = semesterOptions.find((option) => option.key === selectedSemesterKey)?.semester ?? activeSemesterQuery.data
  const selectedSemesterNumber = selectedSemester?.semesterNumber ?? null
  const today = useMemo(() => new Date(), [])
  const semesterWeeks = useMemo(
    () => generateSemesterWeeks(selectedSemester),
    [selectedSemester],
  )
  const preferredSemesterWeek = useMemo(
    () => clampDateToSemesterWeek(semesterWeeks, toIsoDate(today)),
    [semesterWeeks, today],
  )
  const selectedSemesterId = selectedSemester?.id ?? 'none'
  const selectedSemesterWeek = useMemo(() => {
    if (weekSelection?.semesterId === selectedSemesterId) {
      return findSemesterWeek(semesterWeeks, weekSelection.weekNumber) ?? preferredSemesterWeek
    }

    return preferredSemesterWeek
  }, [preferredSemesterWeek, selectedSemesterId, semesterWeeks, weekSelection])
  const selectedWeekType = selectedSemesterWeek?.weekType ?? 'ODD'
  const selectedWeekNumber = selectedSemesterWeek?.weekNumber ?? 1
  const weekNavigation = useMemo(
    () => getWeekNavigationState(semesterWeeks, selectedWeekNumber),
    [selectedWeekNumber, semesterWeeks],
  )
  const visibleWeekTabs = useMemo(
    () => getWeekTabWindow(semesterWeeks, selectedWeekNumber),
    [selectedWeekNumber, semesterWeeks],
  )
  const setSelectedWeekNumber = useCallback(
    (weekNumber: number) => {
      if (!findSemesterWeek(semesterWeeks, weekNumber)) {
        return
      }

      setWeekSelection({
        semesterId: selectedSemesterId,
        weekNumber,
      })
    },
    [selectedSemesterId, semesterWeeks],
  )
  const weekDays = useMemo(
    () => buildWeekDays(selectedSemesterWeek?.startDate ?? toIsoDate(today)),
    [selectedSemesterWeek?.startDate, today],
  )
  const range = useMemo(
    () => ({
      dateFrom: selectedSemesterWeek?.startDate ?? toIsoDate(today),
      dateTo: selectedSemesterWeek?.endDate ?? toIsoDate(addDays(today, 6)),
    }),
    [selectedSemesterWeek?.endDate, selectedSemesterWeek?.startDate, today],
  )

  const resolvedSubjectsQuery = useQuery({
    queryKey: ['schedule', 'group-resolved-subjects', entityId, selectedSemesterNumber],
    queryFn: () => educationService.getResolvedGroupSubjects(
      entityId ?? '',
      typeof selectedSemesterNumber === 'number' ? selectedSemesterNumber : undefined,
    ),
    enabled: scope === 'group' && Boolean(entityId),
    staleTime: 60_000,
  })
  const directSubjectsForGroupQuery = useQuery({
    queryKey: ['schedule', 'group-subjects-direct', entityId],
    queryFn: () => educationService.getSubjectsByGroup(entityId ?? '', {
      page: 0,
      size: 100,
      sortBy: 'name',
      direction: 'asc',
    }),
    enabled: scope === 'group' && Boolean(entityId),
    staleTime: 60_000,
  })
  const usesDirectBindingFallback = scope === 'group'
    && Boolean(groupQuery.data)
    && (!groupQuery.data?.specialtyId || !groupQuery.data?.studyYear)
  const scheduleSubjects = useMemo<GroupScheduleSubjectOption[]>(() => {
    const byId = new Map<string, GroupScheduleSubjectOption>()
    for (const row of resolvedSubjectsQuery.data ?? []) {
      if (row.disabledByOverride) {
        continue
      }
      byId.set(row.subjectId, {
        id: row.subjectId,
        name: row.subjectName,
        teacherIds: row.teacherIds,
        lectureCount: row.lectureCount,
        practiceCount: row.practiceCount,
        labCount: row.labCount,
        supportsStreamLecture: row.supportsStreamLecture,
        requiresSubgroupsForLabs: row.requiresSubgroupsForLabs,
        source: row.source,
      })
    }
    for (const row of directSubjectsForGroupQuery.data?.items ?? []) {
      if (!byId.has(row.id)) {
        byId.set(row.id, {
          id: row.id,
          name: row.name,
          teacherIds: row.teacherIds,
          lectureCount: null,
          practiceCount: null,
          labCount: null,
          supportsStreamLecture: false,
          requiresSubgroupsForLabs: false,
          source: 'DIRECT_BINDING',
        })
      }
    }
    return Array.from(byId.values()).sort((left, right) => left.name.localeCompare(right.name))
  }, [directSubjectsForGroupQuery.data?.items, resolvedSubjectsQuery.data])

  const templatesQuery = useQuery({
    queryKey: ['schedule', 'templates', selectedSemester?.id, entityId],
    queryFn: async () => {
      const templates = await scheduleService.listTemplatesBySemester(selectedSemester?.id ?? '')
      return templates.filter((template) => template.groupId === entityId)
    },
    enabled: canEditTemplates && Boolean(entityId && selectedSemester?.id && activeSemesterQuery.data),
  })

  const scheduleReady = Boolean(activeSemesterQuery.data)
    && (!isStudent || Boolean(activeSemesterQuery.data?.published))
    && !(scope === 'me' && isStudent && (studentMembershipsQuery.data?.length ?? 0) === 0)
    && (scope !== 'group' || Boolean(groupQuery.data))
    && (scope !== 'teacher' || Boolean(teacherQuery.data))
    && (scope !== 'room' || Boolean(selectedRoom))

  const scheduleQuery = useQuery({
    queryKey: ['schedule', scope, entityId, range.dateFrom, range.dateTo, selectedSemester?.id],
    queryFn: () => {
      if (scope === 'group') {
        return scheduleService.getGroupRange(entityId ?? '', range.dateFrom, range.dateTo)
      }
      if (scope === 'teacher') {
        return scheduleService.getTeacherRange(entityId ?? '', range.dateFrom, range.dateTo)
      }
      if (scope === 'room') {
        return scheduleService.getRoomRange(entityId ?? '', range.dateFrom, range.dateTo)
      }
      return scheduleService.getMyRange(range.dateFrom, range.dateTo)
    },
    enabled: scheduleReady,
  })

  const relatedSubjectIds = useMemo(
    () => uniqueIds([
      ...(scheduleQuery.data ?? []).map((lesson) => lesson.subjectId),
      ...drafts.filter((draft) => !draft.deleted).map((draft) => draft.subjectId),
    ]),
    [drafts, scheduleQuery.data],
  )
  const relatedTeacherIds = useMemo(
    () => uniqueIds([
      ...(scheduleQuery.data ?? []).map((lesson) => lesson.teacherId),
      ...drafts.filter((draft) => !draft.deleted).map((draft) => draft.teacherId),
      ...(scheduleSubjects.flatMap((subject) => subject.teacherIds)),
    ]),
    [drafts, scheduleQuery.data, scheduleSubjects],
  )
  const relatedSubjectsQuery = useQuery({
    queryKey: ['schedule', 'subjects-by-id', relatedSubjectIds.join(',')],
    queryFn: async () => {
      const subjects = await Promise.all(relatedSubjectIds.map(async (subjectId) => {
        try {
          return await educationService.getSubject(subjectId)
        } catch {
          return null
        }
      }))
      return subjects.filter((subject): subject is SubjectResponse => Boolean(subject))
    },
    enabled: relatedSubjectIds.length > 0,
  })
  const relatedTeachersQuery = useQuery({
    queryKey: ['schedule', 'teachers-by-id', relatedTeacherIds.join(',')],
    queryFn: () => userDirectoryService.lookup(relatedTeacherIds),
    enabled: relatedTeacherIds.length > 0,
  })
  const slotById = useMemo(
    () => new Map((slotsQuery.data ?? []).map((slot) => [slot.id, slot])),
    [slotsQuery.data],
  )
  const canonicalSlotByPairNumber = useMemo(() => {
    const slots = new Map<number, LessonSlotResponse>()
    for (const slot of slotsQuery.data ?? []) {
      if (slot.active && slot.number >= 1 && slot.number <= 8) {
        slots.set(slot.number, slot)
      }
    }
    return slots
  }, [slotsQuery.data])
  const roomById = useMemo(
    () => new Map((roomsQuery.data ?? []).map((room) => [room.id, room])),
    [roomsQuery.data],
  )
  const formatRoomLabelForDisplay = useCallback(
    (room: RoomResponse) => formatRoomLabelWithStatus(room, t),
    [t],
  )
  const subjectNameById = useMemo(
    () => new Map((relatedSubjectsQuery.data ?? []).map((subject) => [subject.id, subject.name])),
    [relatedSubjectsQuery.data],
  )
  const teacherById = useMemo(
    () => new Map((relatedTeachersQuery.data ?? []).map((teacher) => [teacher.id, teacher])),
    [relatedTeachersQuery.data],
  )
  const draftSignature = useMemo(
    () => (templatesQuery.data ?? []).map((template) => `${template.id}:${template.updatedAt}`).join('|'),
    [templatesQuery.data],
  )
  const dirtyDraftCount = useMemo(
    () => drafts.filter((draft) => draft.changeReason !== null).length,
    [drafts],
  )

  useEffect(() => {
    if (!canEditTemplates || !templatesQuery.data) {
      return
    }

    if (dirtyDraftCount > 0 && initializedDraftSignature === draftSignature) {
      return
    }

    let cancelled = false
    queueMicrotask(() => {
      if (cancelled) {
        return
      }
      setDrafts(templatesQuery.data.map(createDraftFromTemplate))
      setInitializedDraftSignature(draftSignature)
    })

    return () => {
      cancelled = true
    }
  }, [canEditTemplates, dirtyDraftCount, draftSignature, initializedDraftSignature, templatesQuery.data])

  useBeforeUnload(
    useCallback((event) => {
      if (dirtyDraftCount === 0) {
        return
      }

      event.preventDefault()
      event.returnValue = ''
    }, [dirtyDraftCount]),
  )

  const visibleDrafts = useMemo(
    () => drafts.filter((draft) => appliesToWeekType(draft.weekType, selectedWeekType)),
    [drafts, selectedWeekType],
  )
  const selectedDraft = useMemo(
    () => drafts.find((draft) => draft.localId === selectedDraftId) ?? null,
    [drafts, selectedDraftId],
  )
  const unresolvedDrafts = useMemo(
    () => drafts.filter((draft) => draft.changeReason !== null && !draft.deleted),
    [drafts],
  )
  const draftValidationReasonKeys = useMemo(
    () => unresolvedDrafts
      .map((draft) => getDraftValidationReasonKey(draft, canonicalSlotByPairNumber))
      .filter((reason) => reason !== ''),
    [canonicalSlotByPairNumber, unresolvedDrafts],
  )
  const draftsNeedingConflictCheck = useMemo(
    () => unresolvedDrafts.filter((draft) => {
      if (getDraftValidationReasonKey(draft, canonicalSlotByPairNumber)) {
        return false
      }
      const hash = buildDraftConflictHash(draft)
      return draft.conflict.lastCheckedHash !== hash || draft.conflict.status === 'idle'
    }),
    [canonicalSlotByPairNumber, unresolvedDrafts],
  )
  const pendingConflictCount = useMemo(
    () => unresolvedDrafts.filter((draft) => draft.conflict.status === 'checking').length,
    [unresolvedDrafts],
  )
  const failedConflictCount = useMemo(
    () => unresolvedDrafts.filter((draft) => draft.conflict.status === 'conflict' || draft.conflict.status === 'error').length,
    [unresolvedDrafts],
  )

  const saveDisabledReason = useMemo(() => {
    const reasonKey = getScheduleSaveDisabledReasonKey({
      dirtyDraftCount,
      draftValidationReasonKey: draftValidationReasonKeys[0] ?? '',
      draftsNeedingConflictCheckCount: draftsNeedingConflictCheck.length,
      pendingConflictCount,
      failedConflictCount,
    })
    return reasonKey ? t(reasonKey) : ''
  }, [dirtyDraftCount, draftValidationReasonKeys, draftsNeedingConflictCheck.length, failedConflictCount, pendingConflictCount, t])

  const updateDraft = useCallback((localId: string, updater: (draft: ScheduleTemplateDraft) => ScheduleTemplateDraft | null) => {
    setDrafts((current) => current.flatMap((draft) => {
      if (draft.localId !== localId) {
        return [draft]
      }

      const nextDraft = updater(draft)
      return nextDraft ? [nextDraft] : []
    }))
  }, [])

  const applyConflictResult = useCallback((
    localId: string,
    hash: string,
    status: ConflictStatus,
    items: ScheduleConflictItemResponse[],
    messages: string[],
  ) => {
    setDrafts((current) => current.map((draft) => {
      if (draft.localId !== localId) {
        return draft
      }

      return {
        ...draft,
        conflict: {
          items,
          lastCheckedHash: hash,
          messages,
          status,
        },
      }
    }))
  }, [])

  const runConflictCheck = useCallback(async (draft: ScheduleTemplateDraft, force = false) => {
    if (draft.deleted || draft.changeReason === null) {
      return
    }

    const validationReasonKey = getDraftValidationReasonKey(draft, canonicalSlotByPairNumber)
    if (validationReasonKey) {
      applyConflictResult(draft.localId, buildDraftConflictHash(draft), 'idle', [], [t('schedule.conflictsNotChecked')])
      return
    }

    const hash = buildDraftConflictHash(draft)
    if (!force && draft.conflict.lastCheckedHash === hash && draft.conflict.status !== 'idle') {
      return
    }

    applyConflictResult(draft.localId, hash, 'checking', [], [t('schedule.conflictChecking')])

    try {
      const result = await scheduleService.checkConflicts(buildDraftConflictPayload(draft))
      const messages = result.hasConflicts
        ? result.conflicts.map((item) => buildLocalizedConflictMessage(item, t)).filter(Boolean)
        : [t('schedule.conflictsClear')]
      applyConflictResult(
        draft.localId,
        hash,
        result.hasConflicts ? 'conflict' : 'clear',
        result.conflicts,
        messages,
      )
    } catch (error) {
      const normalizedError = normalizeApiError(error)
      const errorMessages = [getLocalizedRequestErrorMessage(error, t)]
      if (normalizedError?.requestId) {
        errorMessages.push(t('schedule.conflictRequestId', { id: normalizedError.requestId }))
      }
      applyConflictResult(
        draft.localId,
        hash,
        'error',
        [],
        errorMessages,
      )
    }
  }, [applyConflictResult, canonicalSlotByPairNumber, t])

  const draftsToAutoCheck = draftsNeedingConflictCheck

  useEffect(() => {
    if (viewMode !== 'EDIT' || draftsToAutoCheck.length === 0) {
      return
    }

    const timeoutId = window.setTimeout(() => {
      draftsToAutoCheck.forEach((draft) => {
        void runConflictCheck(draft)
      })
    }, 350)

    return () => window.clearTimeout(timeoutId)
  }, [draftsToAutoCheck, runConflictCheck, viewMode])

  const saveMutation = useMutation({
    mutationFn: async () => {
      const toDelete = drafts.filter((draft) => draft.changeReason === 'DELETE_TEMPLATE' && draft.templateId)
      const toUpdate = drafts.filter((draft) => (
        (draft.changeReason === 'UPDATE_TEMPLATE' || draft.changeReason === 'MOVE_TEMPLATE')
        && draft.templateId
        && !draft.deleted
      ))
      const toCreate = drafts.filter((draft) => (
        (draft.changeReason === 'CREATE_TEMPLATE'
        || draft.changeReason === 'COPY_TEMPLATE'
        || draft.changeReason === 'COPY_DAY')
        && !draft.deleted
      ))

      for (const draft of toDelete) {
        await scheduleService.deleteTemplate(draft.templateId ?? '')
      }
      for (const draft of toUpdate) {
        await scheduleService.updateTemplate(draft.templateId ?? '', buildTemplatePayload(draft))
      }
      for (const draft of toCreate) {
        await scheduleService.createTemplate(buildTemplatePayload(draft))
      }
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['schedule'] }),
        queryClient.invalidateQueries({ queryKey: ['schedule', 'templates'] }),
      ])

      if (canEditTemplates && selectedSemester?.id && entityId) {
        const freshTemplates = await scheduleService.listTemplatesBySemester(selectedSemester.id)
        const scopedTemplates = freshTemplates.filter((template) => template.groupId === entityId)
        setDrafts(scopedTemplates.map(createDraftFromTemplate))
        setInitializedDraftSignature(scopedTemplates.map((template) => `${template.id}:${template.updatedAt}`).join('|'))
      }

      setCopiedLessonId(null)
      setMovingLessonId(null)
      setSelectedDraftId(null)
      setLessonEditor(null)
      setHoverDropTargetKey(null)
      setFeedback({ tone: 'success', message: t('schedule.saveChangesSuccess') })
    },
    onError: (error) => {
      setFeedback({ tone: 'error', message: getLocalizedRequestErrorMessage(error, t) })
    },
  })

  const cancelOccurrenceMutation = useMutation({
    mutationFn: async (lesson: ResolvedLessonResponse) => {
      return scheduleService.createOverride({
        date: lesson.date,
        groupId: null,
        lessonFormat: null,
        lessonType: null,
        notes: null,
        onlineMeetingUrl: null,
        overrideType: 'CANCEL',
        roomId: null,
        semesterId: lesson.semesterId,
        slotId: null,
        subjectId: null,
        subgroup: null,
        teacherId: null,
        templateId: lesson.templateId,
      })
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['schedule'] })
      setCancelLesson(null)
      setFeedback({ tone: 'success', message: t('schedule.cancelOccurrenceSuccess') })
    },
    onError: (error) => {
      setFeedback({ tone: 'error', message: getLocalizedRequestErrorMessage(error, t) })
    },
  })
  const updateRoomCapabilitiesMutation = useMutation({
    mutationFn: async () => roomCapabilityService.updateByRoom(
      entityId ?? '',
      effectiveRoomCapabilityDrafts.map((item) => ({
        lessonType: item.lessonType,
        priority: Number(item.priority),
        active: item.active,
      })),
    ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['schedule', 'room-capabilities', entityId] })
      setRoomCapabilitiesTouched(false)
      setRoomCapabilitiesError(null)
      setFeedback({ tone: 'success', message: t('academic.roomCapabilities.saveSuccess') })
    },
    onError: (error) => {
      const normalized = normalizeApiError(error)
      setRoomCapabilitiesError(getLocalizedApiErrorMessage(normalized, t))
      setFeedback({ tone: 'error', message: getLocalizedRequestErrorMessage(error, t) })
    },
  })

  const filteredLessons = useMemo(
    () => (scheduleQuery.data ?? []).filter((lesson) => {
      if (scope !== 'group') {
        return true
      }

      if (viewMode === 'EDIT') {
        return true
      }

      return matchesSubgroupFilter(lesson.subgroup, viewSubgroup)
    }),
    [scope, scheduleQuery.data, viewMode, viewSubgroup],
  )
  const lessonsByDate = useMemo(() => {
    const map = new Map<string, ResolvedLessonResponse[]>()
    for (const day of weekDays) {
      map.set(day, [])
    }
    for (const lesson of filteredLessons) {
      const current = map.get(lesson.date) ?? []
      current.push(lesson)
      map.set(lesson.date, current)
    }
    for (const [key, items] of map.entries()) {
      items.sort((left, right) => {
        const leftNumber = slotById.get(left.slotId)?.number ?? 99
        const rightNumber = slotById.get(right.slotId)?.number ?? 99
        return leftNumber - rightNumber
      })
      map.set(key, items)
    }
    return map
  }, [filteredLessons, slotById, weekDays])

  const draftGroupsByDayAndPair = useMemo(() => {
    const map = new Map<string, ScheduleTemplateDraft[]>()

    for (const draft of visibleDrafts) {
      const pairNumber = slotById.get(draft.slotId)?.number
      if (!pairNumber || pairNumber < 1 || pairNumber > 8) {
        continue
      }

      const key = `${draft.dayOfWeek}:${pairNumber}`
      const current = map.get(key) ?? []
      current.push(draft)
      map.set(key, current)
    }

    for (const items of map.values()) {
      items.sort((left, right) => {
        const leftDeleted = left.deleted ? 1 : 0
        const rightDeleted = right.deleted ? 1 : 0
        if (leftDeleted !== rightDeleted) {
          return leftDeleted - rightDeleted
        }
        return left.subjectId.localeCompare(right.subjectId)
      })
    }

    return map
  }, [slotById, visibleDrafts])

  const resolveMoveTargetState = useCallback((
    targetDay: DayOfWeekValue,
    pairNumber: number,
    sourceDraftId?: string | null,
  ): DragMoveTargetState => {
    const resolvedSourceDraftId = sourceDraftId ?? movingLessonId
    const sourceDraft = drafts.find((draft) => draft.localId === resolvedSourceDraftId) ?? null
    const slotId = canonicalSlotByPairNumber.get(pairNumber)?.id ?? null
    return getScheduleMoveTargetState({
      canEditTemplates,
      hasActiveSemester: Boolean(activeSemesterQuery.data),
      sourceDraft: sourceDraft ? {
        dayOfWeek: sourceDraft.dayOfWeek,
        deleted: sourceDraft.deleted,
        localId: sourceDraft.localId,
        slotId: sourceDraft.slotId,
      } : null,
      targetDay,
      targetSlotId: slotId,
    })
  }, [activeSemesterQuery.data, canEditTemplates, canonicalSlotByPairNumber, drafts, movingLessonId])

  const applyMoveTarget = useCallback((
    targetDay: DayOfWeekValue,
    pairNumber: number,
    source: 'action' | 'drag',
    sourceDraftId?: string | null,
  ) => {
    const targetState = resolveMoveTargetState(targetDay, pairNumber, sourceDraftId)
    if (!targetState.canDrop || !targetState.sourceDraftId) {
      setHoverDropTargetKey(null)
      setMovingLessonId(null)
      if (targetState.reason && targetState.reason !== 'NO_SOURCE') {
        setFeedback({ tone: 'error', message: getDragMoveBlockedMessage(targetState.reason, t) })
      } else if (source === 'drag') {
        setFeedback({ tone: 'error', message: t('schedule.moveCancelled') })
      }
      return false
    }

    const slot = canonicalSlotByPairNumber.get(pairNumber)
    if (!slot) {
      setHoverDropTargetKey(null)
      setMovingLessonId(null)
      setFeedback({ tone: 'error', message: t('schedule.cannotMoveSlotUnavailable') })
      return false
    }

    updateDraft(targetState.sourceDraftId, (draft) => moveScheduleDraft({
      createIdleConflict: createIdleDraftConflict,
      draft,
      targetDay,
      targetSlotId: slot.id,
    }))
    setMovingLessonId(null)
    setHoverDropTargetKey(null)
    setFeedback({
      tone: 'success',
      message: `${t('schedule.lessonMoved')} ${t('schedule.movementNeedsConflictCheck')}`,
    })
    return true
  }, [canonicalSlotByPairNumber, resolveMoveTargetState, t, updateDraft])

  const handleDraftDragStart = useCallback((localId: string) => {
    setMovingLessonId(localId)
    setHoverDropTargetKey(null)
  }, [])

  const handleDraftDragEnd = useCallback((localId: string) => {
    setHoverDropTargetKey(null)
    if (movingLessonId === localId) {
      setMovingLessonId(null)
    }
  }, [movingLessonId])

  const handleDragStart = useCallback((event: DragStartEvent) => {
    const localId = String(event.active.id)
    handleDraftDragStart(localId)
  }, [handleDraftDragStart])

  const handleDragOver = useCallback((event: DragOverEvent) => {
    const activeLocalId = String(event.active.id)
    const overData = event.over?.data.current as ScheduleDropTargetData | undefined
    if (!overData) {
      setHoverDropTargetKey(null)
      return
    }
    const targetState = resolveMoveTargetState(overData.dayOfWeek, overData.pairNumber, activeLocalId)
    if (!targetState.valid) {
      setHoverDropTargetKey(null)
      return
    }
    setHoverDropTargetKey(buildDropTargetId(overData.dayOfWeek, overData.pairNumber))
  }, [resolveMoveTargetState])

  const handleDragEnd = useCallback((event: DragEndEvent) => {
    const sourceDraftId = String(event.active.id)
    const overData = event.over?.data.current as ScheduleDropTargetData | undefined
    if (!overData) {
      handleDraftDragEnd(sourceDraftId)
      setFeedback({ tone: 'error', message: t('schedule.moveCancelled') })
      return
    }
    applyMoveTarget(overData.dayOfWeek, overData.pairNumber, 'drag', sourceDraftId)
    handleDraftDragEnd(sourceDraftId)
  }, [applyMoveTarget, handleDraftDragEnd, t])

  const handleDragCancel = useCallback(() => {
    if (movingLessonId) {
      handleDraftDragEnd(movingLessonId)
    }
    setFeedback({ tone: 'error', message: t('schedule.moveCancelled') })
  }, [handleDraftDragEnd, movingLessonId, t])

  const detailHeader = useMemo(() => {
    if (scope === 'group' && groupQuery.data) {
      return {
        backLabel: t('schedule.backToGroups'),
        backTo: '/schedule/groups',
        breadcrumbs: [
          { label: t('navigation.shared.schedule'), to: '/schedule' },
          { label: t('navigation.shared.groups'), to: '/schedule/groups' },
          { label: groupQuery.data.name },
        ],
        description: t('schedule.groupWorkspaceDescription'),
        title: groupQuery.data.name,
      } satisfies ScheduleDetailHeader
    }

    if (scope === 'teacher' && teacherQuery.data) {
      const teacher = teacherQuery.data
      return {
        backLabel: t('schedule.backToTeachers'),
        backTo: '/schedule/teachers',
        breadcrumbs: [
          { label: t('navigation.shared.schedule'), to: '/schedule' },
          { label: t('navigation.shared.teachers'), to: '/schedule/teachers' },
          { label: getTeacherDisplayName(teacher) },
        ],
        description: t('schedule.teacherWorkspaceDescription'),
        title: getTeacherDisplayName(teacher),
      } satisfies ScheduleDetailHeader
    }

    if (scope === 'room' && selectedRoom) {
      return {
        backLabel: t('schedule.backToRooms'),
        backTo: '/schedule/rooms',
        breadcrumbs: [
          { label: t('navigation.shared.schedule'), to: '/schedule' },
          { label: t('schedule.routeCards.roomsTitle'), to: '/schedule/rooms' },
          { label: formatRoomLabelForDisplay(selectedRoom) },
        ],
        description: t('schedule.roomWorkspaceDescription'),
        title: formatRoomLabelForDisplay(selectedRoom),
      } satisfies ScheduleDetailHeader
    }

    return {
      backLabel: t('schedule.backToSchedule'),
      backTo: '/schedule',
      breadcrumbs: [
        { label: t('navigation.shared.schedule'), to: '/schedule' },
        { label: t('schedule.mySchedule') },
      ],
      description: t('schedule.myWorkspaceDescription'),
      title: t('schedule.mySchedule'),
    } satisfies ScheduleDetailHeader
  }, [formatRoomLabelForDisplay, groupQuery.data, scope, selectedRoom, t, teacherQuery.data])

  const visibleSummaryLessons = filteredLessons.length
  const onlineLessons = filteredLessons.filter((lesson) => lesson.lessonFormat === 'ONLINE').length
  const offlineLessons = filteredLessons.filter((lesson) => lesson.lessonFormat === 'OFFLINE').length
  const effectiveRoomCapabilityDrafts = roomCapabilitiesTouched
    ? roomCapabilityDrafts
    : (roomCapabilitiesQuery.data ?? []).map((capability) => ({
        lessonType: capability.lessonType,
        priority: String(capability.priority),
        active: capability.active,
      }))
  const roomCapabilityDisabledReason = resolveRoomCapabilitiesDisabledReason(effectiveRoomCapabilityDrafts, t)
  const selectedDraftSlot = selectedDraft ? slotById.get(selectedDraft.slotId) ?? null : null
  const selectedDraftDate = selectedDraft
    ? weekDays.find((date) => getDayOfWeekValue(date) === selectedDraft.dayOfWeek) ?? null
    : null
  const selectedDraftRoom = selectedDraft?.roomId ? roomById.get(selectedDraft.roomId) ?? null : null
  const selectedDraftSubject = selectedDraft
    ? subjectNameById.get(selectedDraft.subjectId) ?? t('education.subject')
    : ''
  const selectedDraftValidationReasonKey = selectedDraft
    ? getDraftValidationReasonKey(selectedDraft, canonicalSlotByPairNumber)
    : ''
  const selectedDraftConflictMessages = selectedDraft
    ? selectedDraft.conflict.messages.length > 0
      ? selectedDraft.conflict.messages
      : selectedDraft.conflict.status === 'clear'
        ? [t('schedule.conflictsClear')]
        : selectedDraft.conflict.status === 'idle'
          ? [t('schedule.conflictsNotChecked')]
          : []
    : []
  const selectedDraftConflictHints = selectedDraft
    ? selectedDraft.conflict.items.map((item) => {
        const hintKey = getConflictHintKey(item)
        const hint = hintKey ? t(hintKey) : ''
        return [buildConflictDetail(item, t), hint].filter(Boolean).join(' ')
      })
    : []
  const selectedDraftDetails = selectedDraft ? [
    {
      label: t('schedule.dayOfWeek'),
      value: selectedDraftDate
        ? `${t(`schedule.dayOfWeekValues.${selectedDraft.dayOfWeek}`)} · ${formatDate(selectedDraftDate)}`
        : t(`schedule.dayOfWeekValues.${selectedDraft.dayOfWeek}`),
    },
    {
      label: t('schedule.timeLabel'),
      value: selectedDraftSlot
        ? t('schedule.pairSummary', {
            end: formatShortTime(selectedDraftSlot.endTime),
            number: selectedDraftSlot.number,
            start: formatShortTime(selectedDraftSlot.startTime),
          })
        : t('schedule.pairFallback'),
    },
    { label: t('education.group'), value: groupQuery.data?.name ?? t('education.group') },
    { label: t('schedule.teacherLabel'), value: getTeacherDisplayName(teacherById.get(selectedDraft.teacherId)) },
    { label: t('schedule.lessonFormatLabel'), value: getLessonFormatLabel(selectedDraft.lessonFormat) },
    {
      label: selectedDraft.lessonFormat === 'OFFLINE' ? t('schedule.roomLabel') : t('schedule.onlineMeetingUrl'),
      value: selectedDraft.lessonFormat === 'OFFLINE'
        ? (selectedDraftRoom ? formatRoomLabelForDisplay(selectedDraftRoom) : t('schedule.roomAssigned'))
        : selectedDraft.onlineMeetingUrl || t('schedule.linkWillBeAddedLater'),
    },
    { label: t('education.subgroup'), value: t(`education.subgroups.${selectedDraft.subgroup}`) },
    { label: t('schedule.weekTypeLabel'), value: t(`schedule.weekType.${selectedDraft.weekType}`) },
    { label: t('common.labels.notes'), value: selectedDraft.notes || '—' },
    ...(selectedDraftValidationReasonKey ? [{
      label: t('schedule.blockedReasonLabel'),
      value: t(selectedDraftValidationReasonKey),
    }] : []),
  ] : []
  const lessonPanelMode = resolveLessonPanelMode({
    editorLocalId: lessonEditor?.localId ?? null,
    hasEditor: Boolean(lessonEditor),
    hasSelectedLesson: Boolean(selectedDraft),
    movingSelectedLesson: Boolean(selectedDraft && movingLessonId === selectedDraft.localId),
  })
  const handleSaveLessonEditor = useCallback((state: DraftEditorState) => {
    const subject = scheduleSubjects.find((item) => item.id === state.subjectId)
    const roomId = state.lessonFormat === 'OFFLINE' ? state.roomId || null : null
    const onlineMeetingUrl = state.lessonFormat === 'ONLINE'
      ? state.onlineMeetingUrl.trim() || null
      : null
    const nextWeekType: EditableWeekType = state.forBothWeeks ? 'ALL' : state.weekType
    const nextSubgroup: SubgroupValue = state.forWholeGroup ? 'ALL' : state.subgroupChoice

    if (!subject || !selectedSemester || !entityId) {
      setFeedback({ tone: 'error', message: t('schedule.disabledReasons.chooseSubject') })
      return
    }

    const applySnapshot = {
      dayOfWeek: state.dayOfWeek,
      groupId: entityId,
      lessonFormat: state.lessonFormat,
      lessonType: state.lessonType,
      notes: state.notes.trim(),
      onlineMeetingUrl,
      roomId,
      slotId: state.slotId,
      subgroup: nextSubgroup,
      subjectId: state.subjectId,
      teacherId: state.teacherId,
      weekType: nextWeekType,
    } satisfies ScheduleTemplateSnapshot

    if (state.localId) {
      updateDraft(state.localId, (draft) => applyDraftSnapshot(draft, applySnapshot))
      setSelectedDraftId(state.localId)
    } else {
      const localId = createLocalId()
      setDrafts((current) => [
        ...current,
        {
          changeReason: 'CREATE_TEMPLATE',
          conflict: createIdleDraftConflict(),
          dayOfWeek: state.dayOfWeek,
          deleted: false,
          groupId: entityId,
          lessonFormat: state.lessonFormat,
          lessonType: state.lessonType,
          localId,
          notes: state.notes.trim(),
          onlineMeetingUrl,
          original: null,
          roomId,
          semesterId: selectedSemester.id,
          slotId: state.slotId,
          subgroup: nextSubgroup,
          subjectId: state.subjectId,
          teacherId: state.teacherId,
          templateId: null,
          weekType: nextWeekType,
        },
      ])
      setSelectedDraftId(localId)
    }

    setLessonEditor(null)
  }, [entityId, scheduleSubjects, selectedSemester, t, updateDraft])

  const isLoading = activeSemesterQuery.isLoading
    || slotsQuery.isLoading
    || roomsQuery.isLoading
    || (scope === 'group' && groupQuery.isLoading)
    || (scope === 'teacher' && teacherQuery.isLoading)
    || (scope === 'room' && roomCapabilitiesQuery.isLoading)
    || (scope === 'me' && isStudent && studentMembershipsQuery.isLoading)
    || (canEditTemplates && templatesQuery.isLoading)

  if (scope === 'me' && hasAnyRole(roles, ['ADMIN', 'OWNER'])) {
    return <Navigate replace to="/schedule" />
  }

  if (isLoading) {
    return <LoadingState />
  }

  if (
    activeSemesterQuery.isError
    || slotsQuery.isError
    || roomsQuery.isError
    || (scope === 'group' && groupQuery.isError)
    || (scope === 'teacher' && teacherQuery.isError)
    || (scope === 'room' && roomCapabilitiesQuery.isError)
    || (scope === 'me' && isStudent && studentMembershipsQuery.isError)
    || (canEditTemplates && templatesQuery.isError)
  ) {
    return <ErrorState description={t('common.states.error')} title={t('navigation.shared.schedule')} />
  }

  if (scope === 'me' && isStudent && (studentMembershipsQuery.data?.length ?? 0) === 0) {
    return (
      <div className="space-y-6">
        <Breadcrumbs items={detailHeader.breadcrumbs} />
        <Button variant="secondary" onClick={() => navigate(detailHeader.backTo)}>
          {detailHeader.backLabel}
        </Button>
        <EmptyState
          description={t('schedule.missingGroupMembership')}
          title={t('schedule.myScheduleUnavailableTitle')}
        />
      </div>
    )
  }

  if (!activeSemesterQuery.data) {
    return (
      <div className="space-y-6">
        <Breadcrumbs items={detailHeader.breadcrumbs} />
        <Button variant="secondary" onClick={() => navigate(detailHeader.backTo)}>
          {detailHeader.backLabel}
        </Button>
        {canManageTemplates ? (
          <EmptyState
            action={(
              <Button variant="secondary" onClick={() => navigate('/schedule')}>
                {t('schedule.noActiveSemester.configureAction')}
              </Button>
            )}
            description={t('schedule.noActiveSemester.adminDescription')}
            title={t('schedule.noActiveSemester.adminTitle')}
          />
        ) : (
          <EmptyState
            description={t('schedule.noActiveSemester.readOnlyDescription')}
            title={t('schedule.noActiveSemester.readOnlyTitle')}
          />
        )}
      </div>
    )
  }

  if (isStudent && !activeSemesterQuery.data.published) {
    return (
      <div className="space-y-6">
        <Breadcrumbs items={detailHeader.breadcrumbs} />
        <Button variant="secondary" onClick={() => navigate(detailHeader.backTo)}>
          {detailHeader.backLabel}
        </Button>
        <EmptyState
          description={t('schedule.studentUnavailableDescription')}
          title={t('schedule.studentUnavailableTitle')}
        />
      </div>
    )
  }

  const showSemesterSelector = !isStudent && semesterOptions.length > 1

  return (
    <div className="space-y-6">
      <Breadcrumbs items={detailHeader.breadcrumbs} />
      <div className="flex flex-wrap gap-3">
        <Button variant="secondary" onClick={() => navigate(detailHeader.backTo)}>
          {detailHeader.backLabel}
        </Button>
        {scope === 'teacher' && teacherQuery.data ? (
          <Link to={`/teachers/${teacherQuery.data.id}`}>
            <Button variant="secondary">{t('schedule.openTeacherProfile')}</Button>
          </Link>
        ) : null}
        {scope === 'group' && entityId ? (
          <Link to={`/groups/${entityId}`}>
            <Button variant="secondary">{t('schedule.openGroupProfile')}</Button>
          </Link>
        ) : null}
      </div>

      <PageHeader
        actions={(
          <div className="flex flex-wrap items-center gap-3">
            {showSemesterSelector ? (
              <FormField className="min-w-[220px]" label={t('schedule.semesterLabel')}>
                <Select
                  disabled={viewMode === 'EDIT' && dirtyDraftCount > 0}
                  value={selectedSemesterKey}
                  onChange={(event) => setSelectedSemesterKey(event.target.value as SemesterOptionKey)}
                >
                  {semesterOptions.map((option) => (
                    <option key={option.key} value={option.key}>
                      {option.label}
                    </option>
                  ))}
                </Select>
              </FormField>
            ) : null}
            {scope === 'group' ? (
              <FormField label={t('education.subgroup')}>
                <SegmentedControl
                  ariaLabel={t('education.subgroup')}
                  options={[
                    { value: 'ALL', label: t('schedule.subgroupFilter.ALL') },
                    { value: 'FIRST', label: t('schedule.subgroupFilter.FIRST') },
                    { value: 'SECOND', label: t('schedule.subgroupFilter.SECOND') },
                  ]}
                  value={viewSubgroup}
                  onChange={(value) => setViewSubgroup(value as SubgroupValue)}
                />
              </FormField>
            ) : null}
            {canEditTemplates ? (
              <FormField label={t('schedule.workspaceModeLabel')}>
                <SegmentedControl
                  ariaLabel={t('schedule.workspaceModeLabel')}
                  options={[
                    { value: 'VIEW', label: t('schedule.viewMode') },
                    { value: 'EDIT', label: t('schedule.editMode') },
                  ]}
                  value={viewMode}
                  onChange={(value) => {
                    setViewMode(value as ScheduleViewMode)
                    setLessonEditor(null)
                  }}
                />
              </FormField>
            ) : null}
          </div>
        )}
        description={detailHeader.description}
        title={detailHeader.title}
      />

      {feedback ? (
        <Card className={cn(
          'border px-4 py-3',
          feedback.tone === 'success' ? 'border-success/30 bg-success/5' : 'border-danger/20 bg-danger/5',
        )}>
          <p className="text-sm font-semibold text-text-primary">{feedback.message}</p>
        </Card>
      ) : null}

      <ScheduleSummaryStrip
        conflicts={failedConflictCount}
        lessons={visibleSummaryLessons}
        offline={offlineLessons}
        online={onlineLessons}
        titles={{
          conflicts: t('schedule.conflictsFound'),
          lessons: t('schedule.summary.lessons'),
          offline: t('schedule.summary.offline'),
          online: t('schedule.summary.online'),
        }}
      />

      {scope === 'room' ? (
        <Card className="space-y-4">
          <PageHeader
            className="mb-0"
            description={t('academic.roomCapabilities.description')}
            title={t('academic.roomCapabilities.title')}
          />
          <p className="text-sm text-text-secondary">{t('academic.roomCapabilities.priorityHint')}</p>
          <div className="grid gap-3">
            {(['LECTURE', 'PRACTICAL', 'LABORATORY'] as const).map((lessonType) => {
              const row = effectiveRoomCapabilityDrafts.find((item) => item.lessonType === lessonType) ?? {
                lessonType,
                priority: '',
                active: false,
              }
              return (
                <div key={lessonType} className="grid gap-3 rounded-[16px] border border-border bg-surface-muted px-4 py-3 md:grid-cols-[1fr_180px_140px] md:items-center">
                  <p className="text-sm font-semibold text-text-primary">{getLessonTypeLabel(lessonType)}</p>
                  <FormField className="mb-0" label={t('academic.roomCapabilities.priority')}>
                    <Input
                      disabled={!canManageTemplates}
                      min={1}
                      type="number"
                      value={row.priority}
                      onChange={(event) => {
                        setRoomCapabilitiesTouched(true)
                        setRoomCapabilitiesError(null)
                        setRoomCapabilityDrafts(upsertRoomCapabilityDraft(effectiveRoomCapabilityDrafts, {
                          lessonType,
                          priority: event.target.value,
                          active: row.active,
                        }))
                      }}
                    />
                  </FormField>
                  <label className="flex min-h-11 items-center justify-center gap-2 rounded-[12px] border border-border bg-surface px-3 text-sm text-text-primary">
                    <input
                      checked={row.active}
                      disabled={!canManageTemplates}
                      type="checkbox"
                      onChange={(event) => {
                        setRoomCapabilitiesTouched(true)
                        setRoomCapabilitiesError(null)
                        setRoomCapabilityDrafts(upsertRoomCapabilityDraft(effectiveRoomCapabilityDrafts, {
                          lessonType,
                          priority: row.priority || '1',
                          active: event.target.checked,
                        }))
                      }}
                    />
                    {row.active ? t('common.status.ACTIVE') : t('common.status.DISABLED')}
                  </label>
                </div>
              )
            })}
          </div>
          {roomCapabilitiesError ? (
            <div className="rounded-[14px] border border-danger/20 bg-danger/5 px-3 py-2 text-sm text-text-primary">
              {roomCapabilitiesError}
            </div>
          ) : null}
          {roomCapabilityDisabledReason ? (
            <p className="text-sm text-text-secondary">{roomCapabilityDisabledReason}</p>
          ) : null}
          {canManageTemplates ? (
            <div className="flex flex-wrap gap-3">
              <Button
                disabled={Boolean(roomCapabilityDisabledReason) || updateRoomCapabilitiesMutation.isPending}
                onClick={() => updateRoomCapabilitiesMutation.mutate()}
              >
                {t('common.actions.save')}
              </Button>
              <Button
                variant="secondary"
                onClick={() => {
                  setRoomCapabilitiesTouched(false)
                  setRoomCapabilityDrafts([])
                  setRoomCapabilitiesError(null)
                }}
              >
                <RotateCcw className="mr-2 h-4 w-4" />
                {t('common.actions.reset')}
              </Button>
            </div>
          ) : null}
        </Card>
      ) : null}

      {canManageTemplates && scope !== 'group' ? (
        <Card className="space-y-2 border border-border bg-surface-muted">
          <p className="text-sm font-semibold text-text-primary">{t('schedule.editScopeInfoTitle')}</p>
          <p className="text-sm leading-6 text-text-secondary">{t('schedule.editScopeInfoDescription')}</p>
        </Card>
      ) : null}

      <div className={cn(
        'grid gap-4',
        viewMode === 'EDIT' && canEditTemplates ? 'xl:grid-cols-[minmax(0,1fr)_380px]' : '',
      )}>
        <div className="min-w-0 space-y-4">
          <Card className="space-y-4 overflow-hidden rounded-[12px] p-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="space-y-1">
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">{t('schedule.weekRange')}</p>
                <div className="flex flex-wrap items-center gap-2">
                  <h2 className="text-xl font-semibold text-text-primary">
                    {formatDate(range.dateFrom)} - {formatDate(range.dateTo)}
                  </h2>
                  <span className="rounded-full border border-accent/30 bg-accent-muted/40 px-2.5 py-1 text-xs font-semibold text-accent">
                    {t('schedule.weekShortLabel', { number: selectedWeekNumber })}
                    {' · '}
                    {t(`schedule.weekType.${selectedWeekType}`)}
                  </span>
                </div>
              </div>
              <div className="flex gap-2">
                <Button
                  disabled={weekNavigation.isFirstWeek}
                  variant="secondary"
                  onClick={() => {
                    if (weekNavigation.previousWeek) {
                      setSelectedWeekNumber(weekNavigation.previousWeek.weekNumber)
                    }
                  }}
                >
                  <ChevronLeft className="mr-2 h-4 w-4" />
                  {t('schedule.previousWeek')}
                </Button>
                <Button
                  disabled={weekNavigation.isLastWeek}
                  variant="secondary"
                  onClick={() => {
                    if (weekNavigation.nextWeek) {
                      setSelectedWeekNumber(weekNavigation.nextWeek.weekNumber)
                    }
                  }}
                >
                  {t('schedule.nextWeek')}
                  <ChevronRight className="ml-2 h-4 w-4" />
                </Button>
                <Button
                  aria-label={t('schedule.scrollPreviousDay')}
                  variant="ghost"
                  onClick={() => scrollCarousel(carouselRef.current, -1)}
                >
                  <ArrowLeft className="h-4 w-4" />
                </Button>
                <Button
                  aria-label={t('schedule.scrollNextDay')}
                  variant="ghost"
                  onClick={() => scrollCarousel(carouselRef.current, 1)}
                >
                  <ArrowRight className="h-4 w-4" />
                </Button>
              </div>
            </div>
            <WeekTabs
              items={visibleWeekTabs.map((week) => ({
                active: week.weekNumber === selectedWeekNumber,
                key: String(week.weekNumber),
                subtitle: `${formatDate(week.startDate)} - ${formatDate(week.endDate)}`,
                title: t('schedule.weekShortLabel', { number: week.weekNumber }),
              }))}
              onSelect={(key) => {
                setSelectedWeekNumber(Number(key))
              }}
            />

            {scheduleQuery.isLoading ? (
              <LoadingState />
            ) : scheduleQuery.isError ? (
              <ErrorState description={t('common.states.error')} title={detailHeader.title} />
            ) : (
              <DndContext
                collisionDetection={closestCenter}
                sensors={dndSensors}
                onDragCancel={handleDragCancel}
                onDragEnd={handleDragEnd}
                onDragOver={handleDragOver}
                onDragStart={handleDragStart}
              >
                {viewMode === 'EDIT' && canEditTemplates ? (
                  <ScheduleGrid
                    days={weekDays.map((date) => {
                      const dayOfWeek = getDayOfWeekValue(date)
                      return {
                        dateLabel: formatDate(date),
                        dayKey: dayOfWeek,
                        dayLabel: t(`schedule.dayOfWeekValues.${dayOfWeek}`),
                      }
                    })}
                    pairs={canonicalPairs}
                    timeLabel={t('schedule.timeLabel')}
                    renderCell={(dayOfWeek, pair) => (
                      <EditPairDropZone
                        key={`${dayOfWeek}-${pair.pairNumber}`}
                        copiedLessonId={copiedLessonId}
                        dayOfWeek={dayOfWeek}
                        hoverDropTargetKey={hoverDropTargetKey}
                        movingLessonId={movingLessonId}
                        pair={pair}
                        pairDrafts={draftGroupsByDayAndPair.get(`${dayOfWeek}:${pair.pairNumber}`) ?? []}
                        renderDraftCards={(items, density) => (
                          <div
                            className="grid h-full min-h-0 gap-1 overflow-hidden"
                            style={{ gridTemplateRows: `repeat(${items.length}, minmax(0, 1fr))` }}
                          >
                            {items.map((draft) => (
                              <DraftLessonCard
                                key={draft.localId}
                                copied={copiedLessonId === draft.localId}
                                density={density}
                                draft={draft}
                                moving={movingLessonId === draft.localId}
                                roomById={roomById}
                                selected={selectedDraftId === draft.localId}
                                subjectNameById={subjectNameById}
                                teacherById={teacherById}
                                formatRoomLabel={formatRoomLabelForDisplay}
                                getTeacherDisplayName={getTeacherDisplayName}
                                onEdit={() => {
                                  const pairNumber = slotById.get(draft.slotId)?.number ?? 1
                                  setSelectedDraftId(draft.localId)
                                  setLessonEditor({
                                    dayOfWeek: draft.dayOfWeek,
                                    forBothWeeks: draft.weekType === 'ALL',
                                    forWholeGroup: draft.subgroup === 'ALL',
                                    lessonFormat: draft.lessonFormat,
                                    lessonType: draft.lessonType,
                                    localId: draft.localId,
                                    notes: draft.notes,
                                    onlineMeetingUrl: draft.onlineMeetingUrl ?? '',
                                    pairNumber,
                                    roomId: draft.roomId ?? '',
                                    slotId: draft.slotId,
                                    subgroupChoice: draft.subgroup === 'SECOND' ? 'SECOND' : 'FIRST',
                                    subjectId: draft.subjectId,
                                    teacherId: draft.teacherId,
                                    weekType: draft.weekType === 'ALL' ? selectedWeekType : draft.weekType,
                                  })
                                }}
                                onSelect={() => {
                                  setSelectedDraftId(draft.localId)
                                  setLessonEditor(null)
                                }}
                              />
                            ))}
                          </div>
                        )}
                        resolveMoveTargetState={resolveMoveTargetState}
                        selectedWeekType={selectedWeekType}
                        slot={canonicalSlotByPairNumber.get(pair.pairNumber)}
                        onAddLesson={(pairNumber, weekType, subgroupChoice) => {
                          const slot = canonicalSlotByPairNumber.get(pairNumber)
                          if (!slot) {
                            setFeedback({ tone: 'error', message: t('schedule.slotSetupError') })
                            return
                          }
                          setSelectedDraftId(null)
                          setLessonEditor({
                            dayOfWeek,
                            forBothWeeks: false,
                            forWholeGroup: false,
                            lessonFormat: 'OFFLINE',
                            lessonType: 'LECTURE',
                            localId: null,
                            notes: '',
                            onlineMeetingUrl: '',
                            pairNumber,
                            roomId: '',
                            slotId: slot.id,
                            subgroupChoice,
                            subjectId: '',
                            teacherId: '',
                            weekType,
                          })
                        }}
                        onPasteLesson={(targetDay, pairNumber) => {
                          const source = drafts.find((draft) => draft.localId === copiedLessonId)
                          const slot = canonicalSlotByPairNumber.get(pairNumber)
                          if (!source || !slot) {
                            return
                          }

                          const createdDraft = {
                            ...cloneDraft(source),
                            changeReason: 'COPY_TEMPLATE' as const,
                            conflict: createIdleDraftConflict(),
                            dayOfWeek: targetDay,
                            slotId: slot.id,
                          }
                          setDrafts((current) => [...current, createdDraft])
                        }}
                        onUseMoveTarget={(targetDay, pairNumber, source, sourceDraftId) => {
                          applyMoveTarget(targetDay, pairNumber, source, sourceDraftId)
                        }}
                        getDragMoveBlockedMessage={(reason) => (reason ? getDragMoveBlockedMessage(reason, t) : '')}
                      />
                    )}
                  />
                ) : (
                  <div ref={carouselRef} className="flex snap-x snap-mandatory gap-4 overflow-x-auto pb-2">
                    {weekDays.map((date) => (
                      <ViewModeDayCard
                        key={date}
                        currentUserId={session?.user.id ?? ''}
                        dayLabel={t(`schedule.dayOfWeekValues.${getDayOfWeekValue(date)}`)}
                        date={date}
                        formatRoomLabel={formatRoomLabelForDisplay}
                        formatShortTime={formatShortTime}
                        getTeacherDisplayName={getTeacherDisplayName}
                        lessons={lessonsByDate.get(date) ?? []}
                        roomById={roomById}
                        showCancelAction={isTeacher}
                        slotById={slotById}
                        subjectNameById={subjectNameById}
                        teacherById={teacherById}
                        onCancelLesson={(lesson) => setCancelLesson(lesson)}
                      />
                    ))}
                  </div>
                )}
              </DndContext>
            )}
          </Card>
          {viewMode === 'EDIT' && canEditTemplates ? (
            <ScheduleLegend
              items={[
                { label: t('schedule.weekType.ALL'), tone: 'default' },
                { label: t('schedule.weekType.ODD'), tone: 'default' },
                { label: t('schedule.weekType.EVEN'), tone: 'default' },
              ]}
            />
          ) : null}
        </div>

        {viewMode === 'EDIT' && canEditTemplates ? (
          lessonEditor ? (
            <LessonPanelEditor
              key={getLessonEditorKey(lessonEditor)}
              allowManagementActions={canManageTemplates}
              editor={lessonEditor}
              hasDirectBindingFallback={usesDirectBindingFallback}
              hasMissingSemesterNumber={scope === 'group' && selectedSemesterNumber === null}
              mode={lessonPanelMode}
              rooms={roomsQuery.data ?? []}
              roomCapabilitiesLookup={(roomId) => roomCapabilityService.listByRoom(roomId, false)}
              subjects={scheduleSubjects}
              onClose={() => setLessonEditor(null)}
              onDelete={lessonEditor.localId
                ? () => {
                    setDeleteTargetId(lessonEditor.localId)
                    setLessonEditor(null)
                  }
                : null}
              onSave={handleSaveLessonEditor}
            />
          ) : (
            <LessonDetailsPanel
              actions={{
                copy: t('schedule.copyLesson'),
                delete: t('schedule.deleteLesson'),
                edit: t('common.actions.edit'),
                move: t('schedule.moveLesson'),
                restore: t('schedule.restoreLesson'),
                stopMoving: t('schedule.stopMoving'),
              }}
              conflict={selectedDraft ? {
                hints: selectedDraftConflictHints,
                messages: selectedDraftConflictMessages,
                status: selectedDraft.conflict.status,
                statusLabel: t(`schedule.conflictStatus.${selectedDraft.conflict.status}`),
              } : null}
              deleted={Boolean(selectedDraft?.deleted)}
              details={selectedDraftDetails}
              emptyHints={[
                t('schedule.detailsHints.select'),
                t('schedule.detailsHints.drag'),
                t('schedule.detailsHints.add'),
              ]}
              emptyText={t('schedule.selectLessonToViewDetails')}
              hasSelection={Boolean(selectedDraft)}
              lessonType={selectedDraft ? getLessonTypeLabel(selectedDraft.lessonType) : ''}
              moving={lessonPanelMode === 'move'}
              subject={selectedDraftSubject}
              title={lessonPanelMode === 'move' ? t('schedule.moveLesson') : t('schedule.lessonDetails')}
              onCopy={() => {
                if (selectedDraft) {
                  setCopiedLessonId(selectedDraft.localId)
                }
              }}
              onDelete={() => {
                if (selectedDraft) {
                  setDeleteTargetId(selectedDraft.localId)
                }
              }}
              onEdit={() => {
                if (!selectedDraft) {
                  return
                }
                const pairNumber = slotById.get(selectedDraft.slotId)?.number ?? 1
                setLessonEditor({
                  dayOfWeek: selectedDraft.dayOfWeek,
                  forBothWeeks: selectedDraft.weekType === 'ALL',
                  forWholeGroup: selectedDraft.subgroup === 'ALL',
                  lessonFormat: selectedDraft.lessonFormat,
                  lessonType: selectedDraft.lessonType,
                  localId: selectedDraft.localId,
                  notes: selectedDraft.notes,
                  onlineMeetingUrl: selectedDraft.onlineMeetingUrl ?? '',
                  pairNumber,
                  roomId: selectedDraft.roomId ?? '',
                  slotId: selectedDraft.slotId,
                  subgroupChoice: selectedDraft.subgroup === 'SECOND' ? 'SECOND' : 'FIRST',
                  subjectId: selectedDraft.subjectId,
                  teacherId: selectedDraft.teacherId,
                  weekType: selectedDraft.weekType === 'ALL' ? selectedWeekType : selectedDraft.weekType,
                })
              }}
              onMove={() => {
                if (selectedDraft) {
                  setMovingLessonId(selectedDraft.localId)
                }
              }}
              onRestore={() => {
                if (!selectedDraft) {
                  return
                }
                updateDraft(selectedDraft.localId, (currentDraft) => ({
                  ...currentDraft,
                  changeReason: null,
                  conflict: createIdleDraftConflict(),
                  deleted: false,
                }))
              }}
              onStopMove={() => setMovingLessonId(null)}
            />
          )
        ) : null}
      </div>

      {viewMode === 'EDIT' && canEditTemplates && dirtyDraftCount > 0 ? (
        <ScheduleSaveBar>
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div className="space-y-1">
              <p className="text-sm font-semibold text-text-primary">
                {t('schedule.unsavedChangesCount', { count: dirtyDraftCount })}
              </p>
              <p className="text-xs text-text-secondary">{saveDisabledReason || t('schedule.readyToSave')}</p>
            </div>
            <div className="flex flex-wrap justify-end gap-2">
              <Button
                className="min-h-10 rounded-[10px] px-3"
                disabled={dirtyDraftCount === 0 || pendingConflictCount > 0}
                variant="secondary"
                onClick={() => {
                  unresolvedDrafts.forEach((draft) => {
                    void runConflictCheck(draft, true)
                  })
                }}
              >
                <CheckCircle2 className="mr-2 h-4 w-4" />
                {t('schedule.checkAllConflicts')}
              </Button>
              <Button
                className="min-h-10 rounded-[10px] px-3"
                disabled={dirtyDraftCount === 0}
                variant="secondary"
                onClick={() => {
                  if (!templatesQuery.data) {
                    return
                  }
                  setDrafts(templatesQuery.data.map(createDraftFromTemplate))
                  setCopiedLessonId(null)
                  setMovingLessonId(null)
                  setSelectedDraftId(null)
                  setLessonEditor(null)
                  setHoverDropTargetKey(null)
                }}
              >
                <RotateCcw className="mr-2 h-4 w-4" />
                {t('schedule.discardChanges')}
              </Button>
              <Button className="min-h-10 rounded-[10px] px-3" disabled={Boolean(saveDisabledReason) || saveMutation.isPending} onClick={() => saveMutation.mutate()}>
                <Save className="mr-2 h-4 w-4" />
                {t('schedule.saveChanges')}
              </Button>
            </div>
          </div>
        </ScheduleSaveBar>
      ) : null}

      <ConfirmDialog
        description={t('schedule.deleteLessonDescription')}
        open={Boolean(deleteTargetId)}
        title={t('schedule.deleteLesson')}
        onCancel={() => setDeleteTargetId(null)}
        onConfirm={() => {
          if (!deleteTargetId) {
            return
          }

          const target = drafts.find((draft) => draft.localId === deleteTargetId)
          if (!target) {
            setDeleteTargetId(null)
            return
          }

          if (!target.templateId) {
            setDrafts((current) => current.filter((draft) => draft.localId !== deleteTargetId))
            if (selectedDraftId === deleteTargetId) {
              setSelectedDraftId(null)
            }
            setLessonEditor(null)
          } else {
            updateDraft(deleteTargetId, (draft) => ({
              ...draft,
              changeReason: 'DELETE_TEMPLATE',
              deleted: true,
            }))
            setLessonEditor(null)
          }

          setDeleteTargetId(null)
        }}
      />

      <ConfirmDialog
        description={t('schedule.cancelOccurrenceDescription')}
        open={Boolean(cancelLesson)}
        title={t('schedule.cancelOccurrence')}
        onCancel={() => setCancelLesson(null)}
        onConfirm={() => {
          if (!cancelLesson) {
            return
          }
          cancelOccurrenceMutation.mutate(cancelLesson)
        }}
      />
    </div>
  )
}

function PaginationControls({
  page,
  totalPages,
  onNext,
  onPrevious,
}: {
  page: number
  totalPages: number
  onNext: () => void
  onPrevious: () => void
}) {
  const { t } = useTranslation()

  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <p className="text-sm text-text-secondary">{t('cardPicker.pageSummary', { page: page + 1, totalPages })}</p>
      <div className="flex gap-2">
        <Button disabled={page === 0} variant="secondary" onClick={onPrevious}>
          <ChevronLeft className="mr-2 h-4 w-4" />
          {t('common.actions.previous')}
        </Button>
        <Button disabled={page + 1 >= totalPages} variant="secondary" onClick={onNext}>
          {t('common.actions.next')}
          <ChevronRight className="ml-2 h-4 w-4" />
        </Button>
      </div>
    </div>
  )
}

function LessonPanelEditor({
  allowManagementActions,
  editor,
  hasDirectBindingFallback,
  hasMissingSemesterNumber,
  mode,
  onClose,
  onDelete,
  onSave,
  rooms,
  roomCapabilitiesLookup,
  subjects,
}: {
  allowManagementActions: boolean
  editor: DraftEditorState
  hasDirectBindingFallback: boolean
  hasMissingSemesterNumber: boolean
  mode: LessonPanelMode
  onClose: () => void
  onDelete: (() => void) | null
  onSave: (state: DraftEditorState) => void
  rooms: RoomResponse[]
  roomCapabilitiesLookup: (roomId: string) => Promise<RoomCapabilityResponse[]>
  subjects: GroupScheduleSubjectOption[]
}) {
  const { t } = useTranslation()
  const [state, setState] = useState(editor)
  const [roomSearch, setRoomSearch] = useState('')

  const subject = subjects.find((item) => item.id === state.subjectId)
  const subjectTeacherIds = subject?.teacherIds ?? []
  const teachersQuery = useQuery({
    queryKey: ['schedule', 'panel-editor-teachers', state.subjectId, subjectTeacherIds.join(',')],
    queryFn: async () => {
      let failedLookups = 0
      const teacherRows = await Promise.all(subjectTeacherIds.map(async (teacherId) => {
        try {
          const [teacher] = await userDirectoryService.lookup([teacherId])
          return teacher ?? null
        } catch {
          failedLookups += 1
          return null
        }
      }))
      const resolvedTeachers = teacherRows.filter((teacher): teacher is TeacherUser => Boolean(teacher))
      if (resolvedTeachers.length === 0 && subjectTeacherIds.length > 0 && failedLookups === subjectTeacherIds.length) {
        throw new Error('TEACHER_LOOKUP_FAILED')
      }
      return resolvedTeachers
    },
    enabled: Boolean(state.subjectId) && subjectTeacherIds.length > 0,
    staleTime: 60_000,
  })
  const teacherChoices = teachersQuery.data ?? []
  const teachersLookupError = teachersQuery.isError
  const teachersLookupLoading = teachersQuery.isLoading
  const roomCapabilitiesByRoomQuery = useQuery({
    queryKey: ['schedule', 'panel-editor-room-capabilities', rooms.map((room) => room.id).join(',')],
    queryFn: async () => {
      const rows = await Promise.all(
        rooms.map(async (room) => {
          try {
            const capabilities = await roomCapabilitiesLookup(room.id)
            return [room.id, capabilities] as const
          } catch {
            return [room.id, [] as RoomCapabilityResponse[]] as const
          }
        }),
      )
      return new Map(rows)
    },
    enabled: state.lessonFormat === 'OFFLINE' && rooms.length > 0,
    staleTime: 60_000,
  })
  const roomCapabilitiesByRoom = useMemo(
    () => roomCapabilitiesByRoomQuery.data ?? new Map<string, RoomCapabilityResponse[]>(),
    [roomCapabilitiesByRoomQuery.data],
  )
  const sortedRooms = useMemo(() => {
    if (state.lessonFormat !== 'OFFLINE') {
      return rooms
    }
    return rooms
      .map((room) => {
        const capability = (roomCapabilitiesByRoom.get(room.id) ?? [])
          .find((item) => item.lessonType === state.lessonType && item.active)
        return { capability, room }
      })
      .sort((left, right) => {
        const leftPriority = left.capability?.priority ?? -1
        const rightPriority = right.capability?.priority ?? -1
        return rightPriority - leftPriority || left.room.code.localeCompare(right.room.code)
      })
      .map((item) => item.room)
  }, [roomCapabilitiesByRoom, rooms, state.lessonFormat, state.lessonType])
  const activeSortedRooms = useMemo(
    () => sortedRooms.filter((room) => room.active),
    [sortedRooms],
  )
  const selectedRoom = useMemo(
    () => (state.roomId ? rooms.find((room) => room.id === state.roomId) ?? null : null),
    [rooms, state.roomId],
  )
  const filteredActiveRooms = useMemo(() => {
    const normalizedSearch = roomSearch.trim().toLowerCase()
    if (!normalizedSearch) {
      return activeSortedRooms
    }

    return activeSortedRooms.filter((room) => `${room.code} ${room.building} ${room.floor} ${room.capacity}`.toLowerCase().includes(normalizedSearch))
  }, [activeSortedRooms, roomSearch])
  const selectedRoomCapability = useMemo(
    () => (state.roomId ? (roomCapabilitiesByRoom.get(state.roomId) ?? [])
      .find((item) => item.lessonType === state.lessonType && item.active) : null),
    [roomCapabilitiesByRoom, state.lessonType, state.roomId],
  )
  const saveBlockedReason = getLessonEditorValidationReason(state, selectedRoom, t)

  return (
    <Card className="h-fit w-full space-y-4 rounded-[12px] border border-border bg-surface p-4 xl:sticky xl:top-4 xl:max-h-[calc(100vh-2rem)] xl:w-[380px] xl:overflow-y-auto">
      <div className="space-y-5">
          <div className="flex items-start justify-between gap-3">
            <div className="space-y-1">
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">{getDayOfWeekLabel(state.dayOfWeek)}</p>
              <h2 className="text-xl font-semibold text-text-primary">
                {mode === 'create' ? t('schedule.addLesson') : t('schedule.editLesson')}
              </h2>
              <p className="text-sm text-text-secondary">
                {t('schedule.pairSummary', {
                  end: canonicalPairs[state.pairNumber - 1]?.endTime ?? '',
                  number: state.pairNumber,
                  start: canonicalPairs[state.pairNumber - 1]?.startTime ?? '',
                })}
              </p>
            </div>
            <Button variant="ghost" onClick={onClose}>{t('common.actions.close')}</Button>
          </div>

          <div className="grid gap-4">
            {subjects.length === 0 ? (
              <div className="rounded-[12px] border border-warning/30 bg-warning/5 px-3 py-3">
                <p className="text-sm font-semibold text-text-primary">{t('schedule.emptySubjectsForGroup')}</p>
                {hasDirectBindingFallback ? (
                  <p className="mt-1 text-sm text-text-secondary">{t('schedule.directBindingsFallback')}</p>
                ) : null}
                {allowManagementActions ? (
                  <div className="mt-2 flex flex-wrap gap-3">
                    <Link className="inline-flex text-sm font-medium text-accent" to="/academic/specialties">
                      {t('schedule.actions.manageCurriculum')}
                    </Link>
                    <Link className="inline-flex text-sm font-medium text-accent" to="/subjects">
                      {t('schedule.actions.manageSubjectBindings')}
                    </Link>
                  </div>
                ) : null}
              </div>
            ) : null}
            {hasMissingSemesterNumber ? (
              <div className="rounded-[12px] border border-warning/30 bg-warning/5 px-3 py-3">
                <p className="text-sm text-text-primary">{t('schedule.semesterNumberMissing')}</p>
              </div>
            ) : null}
            <FormField label={t('education.subject')}>
              <Select
                value={state.subjectId}
                onChange={(event) => {
                  const nextSubjectId = event.target.value
                  const nextSubject = subjects.find((item) => item.id === nextSubjectId)
                  setState((current) => ({
                    ...current,
                    subjectId: nextSubjectId,
                    teacherId: nextSubject?.teacherIds.includes(current.teacherId) ? current.teacherId : '',
                  }))
                }}
              >
                <option value="">{t('schedule.selectSubject')}</option>
                {subjects.map((subjectOption) => (
                  <option key={subjectOption.id} value={subjectOption.id}>
                    {subjectOption.name}
                    {subjectOption.source ? ` · ${t(`schedule.subjectSource.${subjectOption.source}`)}` : ''}
                  </option>
                ))}
              </Select>
            </FormField>
            {state.subjectId ? (
              <div className="rounded-[12px] border border-border bg-surface-muted px-3 py-3">
                <div className="flex flex-wrap gap-2 text-xs">
                  {subject?.source ? (
                    <span className="rounded-full border border-border px-2 py-1 font-medium text-text-secondary">
                      {t(`schedule.subjectSource.${subject.source}`)}
                    </span>
                  ) : null}
                  {subject?.supportsStreamLecture ? (
                    <span className="rounded-full border border-success/30 bg-success/5 px-2 py-1 font-medium text-text-primary">
                      {t('schedule.streamLectureSupported')}
                    </span>
                  ) : null}
                  {subject?.requiresSubgroupsForLabs ? (
                    <span className="rounded-full border border-warning/30 bg-warning/5 px-2 py-1 font-medium text-text-primary">
                      {t('schedule.requiresSubgroupsForLabs')}
                    </span>
                  ) : null}
                </div>
                <p className="mt-2 text-sm text-text-secondary">
                  {t('schedule.subjectLoadCounts', {
                    lab: subject?.labCount ?? 0,
                    lecture: subject?.lectureCount ?? 0,
                    practice: subject?.practiceCount ?? 0,
                  })}
                </p>
              </div>
            ) : null}

            <FormField label={t('schedule.teacherLabel')}>
              <Select
                disabled={!state.subjectId || teacherChoices.length === 0 || teachersLookupLoading || teachersLookupError}
                value={state.teacherId}
                onChange={(event) => {
                  setState((current) => ({ ...current, teacherId: event.target.value }))
                }}
              >
                <option value="">{state.subjectId ? t('schedule.selectTeacher') : t('schedule.selectSubjectFirst')}</option>
                {teacherChoices.map((teacher) => (
                  <option key={teacher.id} value={teacher.id}>
                    {getTeacherDisplayName(teacher)}
                  </option>
                ))}
              </Select>
            </FormField>

            {state.subjectId && teachersLookupLoading ? (
              <div className="rounded-[12px] border border-border bg-surface-muted px-3 py-3">
                <p className="text-sm font-semibold text-text-primary">{t('schedule.checkingTeachers')}</p>
              </div>
            ) : null}

            {state.subjectId && teachersLookupError ? (
              <div className="rounded-[12px] border border-danger/30 bg-danger/5 px-3 py-3">
                <p className="text-sm font-semibold text-text-primary">{t('schedule.teacherLookupFailed')}</p>
                <div className="mt-2">
                  <Button variant="secondary" onClick={() => void teachersQuery.refetch()}>
                    {t('common.actions.retry')}
                  </Button>
                </div>
              </div>
            ) : null}

            {state.subjectId && !teachersLookupLoading && !teachersLookupError && teacherChoices.length === 0 ? (
              <div className="rounded-[12px] border border-warning/30 bg-warning/5 px-3 py-3">
                <p className="text-sm font-semibold text-text-primary">{t('schedule.emptyTeachersForSubject')}</p>
                {allowManagementActions ? (
                  <Link className="mt-2 inline-flex text-sm font-medium text-accent" to={`/subjects/${state.subjectId}`}>
                    {t('schedule.actions.manageSubjectTeachers')}
                  </Link>
                ) : null}
              </div>
            ) : null}

            {state.subjectId && teacherChoices.length > 0 ? (
              <div className="grid gap-2">
                {teacherChoices.map((teacher) => (
                  <button
                    key={teacher.id}
                    className={cn(
                      'flex w-full items-center gap-3 rounded-[12px] border px-3 py-2 text-left transition',
                      state.teacherId === teacher.id
                        ? 'border-success/40 bg-success/5'
                        : 'border-border bg-surface hover:border-border-strong',
                    )}
                    type="button"
                    onClick={() => {
                      setState((current) => ({ ...current, teacherId: teacher.id }))
                    }}
                  >
                    <UserAvatar
                      displayName={getTeacherDisplayName(teacher)}
                      email={teacher.email}
                      size="sm"
                      username={teacher.username}
                    />
                    <span className="min-w-0">
                      <span className="block truncate text-sm font-semibold text-text-primary">{getTeacherDisplayName(teacher)}</span>
                      <span className="block truncate text-xs text-text-muted">@{teacher.username}</span>
                      {teacher.email ? (
                        <span className="block truncate text-xs text-text-secondary">{teacher.email}</span>
                      ) : null}
                    </span>
                  </button>
                ))}
              </div>
            ) : null}

            <FormField label={t('schedule.lessonTypeLabel')}>
              <SegmentedControl
                ariaLabel={t('schedule.lessonTypeLabel')}
                options={[
                  { value: 'LECTURE', label: t('schedule.lessonType.LECTURE') },
                  { value: 'PRACTICAL', label: t('schedule.lessonType.PRACTICAL') },
                  { value: 'LABORATORY', label: t('schedule.lessonType.LABORATORY') },
                ]}
                value={state.lessonType}
                onChange={(value) => {
                  setState((current) => ({ ...current, lessonType: value as ScheduleLessonType }))
                }}
              />
            </FormField>

            <FormField label={t('schedule.lessonFormatLabel')}>
              <SegmentedControl
                ariaLabel={t('schedule.lessonFormatLabel')}
                options={[
                  { value: 'OFFLINE', label: t('schedule.lessonFormat.OFFLINE') },
                  { value: 'ONLINE', label: t('schedule.lessonFormat.ONLINE') },
                ]}
                value={state.lessonFormat}
                onChange={(value) => {
                  setState((current) => ({
                    ...current,
                    lessonFormat: value as ScheduleLessonFormat,
                    onlineMeetingUrl: value === 'OFFLINE' ? '' : current.onlineMeetingUrl,
                    roomId: value === 'ONLINE' ? '' : current.roomId,
                  }))
                }}
              />
            </FormField>
          </div>

          <div className="space-y-4 rounded-[12px] border border-border bg-surface-muted px-3 py-3">
            <FormField label={t('education.subgroup')}>
              <SegmentedControl
                ariaLabel={t('education.subgroup')}
                options={[
                  { value: 'FIRST', label: t('schedule.subgroupChoice.FIRST') },
                  { value: 'SECOND', label: t('schedule.subgroupChoice.SECOND') },
                ]}
                value={state.subgroupChoice}
                onChange={(value) => {
                  setState((current) => ({ ...current, subgroupChoice: value as 'FIRST' | 'SECOND' }))
                }}
              />
            </FormField>
            <label className="flex items-center gap-3 text-sm text-text-secondary">
              <Input
                checked={state.forWholeGroup}
                type="checkbox"
                onChange={(event) => {
                  setState((current) => ({ ...current, forWholeGroup: event.target.checked }))
                }}
              />
              <span>{t('schedule.forWholeGroup')}</span>
            </label>
            <label className="flex items-center gap-3 text-sm text-text-secondary">
              <Input
                checked={state.forBothWeeks}
                type="checkbox"
                onChange={(event) => {
                  setState((current) => ({ ...current, forBothWeeks: event.target.checked }))
                }}
              />
              <span>{t('schedule.forBothWeeks')}</span>
            </label>
          </div>

          {state.lessonFormat === 'OFFLINE' ? (
            <div className="space-y-3">
              <FormField label={t('schedule.roomLabel')}>
                <Input
                  placeholder={t('schedule.roomSearchPlaceholder')}
                  value={roomSearch}
                  onChange={(event) => setRoomSearch(event.target.value)}
                />
              </FormField>
              <div className="max-h-56 space-y-2 overflow-y-auto pr-1">
                {filteredActiveRooms.length === 0 ? (
                  <p className="rounded-[12px] border border-border bg-surface-muted px-3 py-2 text-sm text-text-secondary">
                    {t('education.rooms.selectorEmpty')}
                  </p>
                ) : (
                  filteredActiveRooms.map((room) => {
                    const isSelected = state.roomId === room.id
                    const roomCapabilities = (roomCapabilitiesByRoom.get(room.id) ?? [])
                      .filter((item) => item.active)
                      .sort((left, right) => right.priority - left.priority)
                    const capabilitySummary = roomCapabilities.length > 0
                      ? roomCapabilities.map((item) => getLessonTypeLabel(item.lessonType)).join(' · ')
                      : t('education.rooms.noCapabilities')

                    return (
                      <button
                        key={room.id}
                        className={cn(
                          'w-full rounded-[12px] border px-3 py-2 text-left transition',
                          isSelected
                            ? 'border-success/40 bg-success/5'
                            : 'border-border bg-surface hover:border-border-strong',
                        )}
                        type="button"
                        onClick={() => setState((current) => ({ ...current, roomId: room.id }))}
                      >
                        <p className="text-sm font-semibold text-text-primary">{formatRoomLabel(room)}</p>
                        <p className="text-xs text-text-secondary">
                          {t('schedule.roomCapacity', { count: room.capacity })}
                          {' · '}
                          {t('schedule.floor')} {room.floor}
                        </p>
                        <p className="mt-1 text-xs text-text-muted">{capabilitySummary}</p>
                      </button>
                    )
                  })
                )}
              </div>
              {selectedRoom && !selectedRoom.active ? (
                <div className="rounded-[12px] border border-warning/30 bg-warning/5 px-3 py-3">
                  <p className="text-sm font-semibold text-text-primary">
                    {t('education.rooms.archivedRoomSelected')}
                  </p>
                </div>
              ) : null}
            </div>
          ) : (
            <FormField hint={!state.onlineMeetingUrl.trim() ? t('schedule.linkWillBeAddedLater') : undefined} label={t('schedule.onlineMeetingUrl')}>
              <Input
                value={state.onlineMeetingUrl}
                onChange={(event) => {
                  setState((current) => ({ ...current, onlineMeetingUrl: event.target.value }))
                }}
              />
            </FormField>
          )}
          {state.lessonFormat === 'OFFLINE' && state.roomId ? (
            <div className={cn(
              'rounded-[12px] border px-3 py-3',
              selectedRoomCapability
                ? 'border-success/30 bg-success/5'
                : 'border-warning/30 bg-warning/5',
            )}>
              {selectedRoomCapability ? (
                <p className="text-sm text-text-primary">
                  {t('schedule.roomFitSupported')}
                  {' '}
                  {t('schedule.roomCapabilityPriority', { priority: selectedRoomCapability.priority })}
                </p>
              ) : (
                <p className="text-sm text-text-primary">{t('schedule.roomFitUnsupported')}</p>
              )}
            </div>
          ) : null}
          {state.lessonType === 'LECTURE' && subject?.supportsStreamLecture ? (
            <div className="rounded-[12px] border border-info/30 bg-info/5 px-3 py-3">
              <p className="text-sm text-text-primary">{t('schedule.streamLectureNote')}</p>
            </div>
          ) : null}

          <FormField label={t('common.labels.notes')}>
            <Textarea
              value={state.notes}
              onChange={(event) => {
                setState((current) => ({ ...current, notes: event.target.value }))
              }}
            />
          </FormField>

          {saveBlockedReason ? (
            <div className="rounded-[12px] border border-warning/30 bg-warning/5 px-3 py-3">
              <p className="text-sm font-semibold text-text-primary">{saveBlockedReason}</p>
            </div>
          ) : null}

          <div className="grid gap-2">
            <Button disabled={Boolean(saveBlockedReason)} onClick={() => onSave(state)}>
              {mode === 'create' ? t('schedule.addLessonLocally') : t('schedule.saveLessonLocally')}
            </Button>
            <Button variant="secondary" onClick={onClose}>
              {t('schedule.cancelEdit')}
            </Button>
            {onDelete ? (
              <Button variant="danger" onClick={onDelete}>
                {t('schedule.deleteLesson')}
              </Button>
            ) : null}
          </div>
      </div>
    </Card>
  )
}

function getLessonEditorValidationReason(
  editor: DraftEditorState,
  selectedRoom: RoomResponse | null,
  t: (key: string) => string,
) {
  if (!editor.subjectId) {
    return t('schedule.disabledReasons.chooseSubject')
  }
  if (!editor.teacherId) {
    return t('schedule.disabledReasons.chooseTeacher')
  }
  if (!editor.slotId) {
    return t('schedule.disabledReasons.slotNotResolved')
  }
  if (editor.lessonFormat === 'OFFLINE' && !editor.roomId) {
    return t('schedule.disabledReasons.chooseRoomOffline')
  }
  if (editor.lessonFormat === 'OFFLINE' && selectedRoom && !selectedRoom.active) {
    return t('education.rooms.archivedRoomSelected')
  }

  return ''
}

function getDraftValidationReasonKey(
  draft: ScheduleTemplateDraft,
  canonicalSlotByPairNumber: Map<number, LessonSlotResponse>,
) {
  const pairNumber = canonicalPairs.find((pair) => pair.pairNumber === findPairNumberBySlotId(canonicalSlotByPairNumber, draft.slotId))?.pairNumber
  return getScheduleDraftValidationReasonKey({
    hasActiveSemester: Boolean(draft.semesterId),
    hasLessonContext: Boolean(draft.groupId && draft.dayOfWeek && draft.weekType && draft.lessonType && draft.lessonFormat),
    hasRoomForOffline: draft.lessonFormat !== 'OFFLINE' || Boolean(draft.roomId),
    hasSlot: Boolean(pairNumber),
    hasSubject: Boolean(draft.subjectId),
    hasTeacher: Boolean(draft.teacherId),
  })
}

function findPairNumberBySlotId(canonicalSlotByPairNumber: Map<number, LessonSlotResponse>, slotId: string) {
  for (const [pairNumber, slot] of canonicalSlotByPairNumber.entries()) {
    if (slot.id === slotId) {
      return pairNumber
    }
  }

  return null
}

function buildTemplatePayload(draft: ScheduleTemplateDraft) {
  return {
    active: true,
    dayOfWeek: draft.dayOfWeek,
    groupId: draft.groupId,
    lessonFormat: draft.lessonFormat,
    lessonType: draft.lessonType,
    notes: draft.notes.trim() || null,
    onlineMeetingUrl: draft.lessonFormat === 'ONLINE' ? draft.onlineMeetingUrl?.trim() || null : null,
    roomId: draft.lessonFormat === 'OFFLINE' ? draft.roomId : null,
    semesterId: draft.semesterId,
    slotId: draft.slotId,
    subgroup: draft.subgroup,
    subjectId: draft.subjectId,
    teacherId: draft.teacherId,
    weekType: draft.weekType,
  }
}

function buildDraftConflictPayload(draft: ScheduleTemplateDraft) {
  return {
    dayOfWeek: draft.dayOfWeek,
    groupId: draft.groupId,
    lessonFormat: draft.lessonFormat,
    lessonType: draft.lessonType,
    onlineMeetingUrl: draft.lessonFormat === 'ONLINE' ? draft.onlineMeetingUrl?.trim() || null : null,
    roomId: draft.lessonFormat === 'OFFLINE' ? draft.roomId : null,
    semesterId: draft.semesterId,
    slotId: draft.slotId,
    subgroup: draft.subgroup,
    subjectId: draft.subjectId,
    teacherId: draft.teacherId,
    templateId: draft.templateId,
    weekType: draft.weekType,
  }
}

function createDraftFromTemplate(template: ScheduleTemplateResponse): ScheduleTemplateDraft {
  const original = toTemplateSnapshot(template)

  return {
    changeReason: null,
    conflict: createIdleDraftConflict(),
    dayOfWeek: template.dayOfWeek,
    deleted: false,
    groupId: template.groupId,
    lessonFormat: template.lessonFormat,
    lessonType: template.lessonType,
    localId: template.id,
    notes: template.notes ?? '',
    onlineMeetingUrl: template.onlineMeetingUrl,
    original,
    roomId: template.roomId,
    semesterId: template.semesterId,
    slotId: template.slotId,
    subgroup: template.subgroup,
    subjectId: template.subjectId,
    teacherId: template.teacherId,
    templateId: template.id,
    weekType: template.weekType,
  }
}

function cloneDraft(draft: ScheduleTemplateDraft): ScheduleTemplateDraft {
  return {
    ...draft,
    conflict: createIdleDraftConflict(),
    localId: createLocalId(),
    original: null,
    templateId: null,
  }
}

function applyDraftSnapshot(
  draft: ScheduleTemplateDraft,
  snapshot: ScheduleTemplateSnapshot,
): ScheduleTemplateDraft {
  const nextDraft: ScheduleTemplateDraft = {
    ...draft,
    ...snapshot,
    conflict: createIdleDraftConflict(),
    deleted: false,
  }

  if (!draft.templateId) {
    return {
      ...nextDraft,
      changeReason: draft.changeReason ?? 'CREATE_TEMPLATE',
    }
  }

  const original = draft.original
  if (!original) {
    return {
      ...nextDraft,
      changeReason: 'UPDATE_TEMPLATE',
    }
  }

  if (isSameTemplateSnapshot(original, snapshot)) {
    return {
      ...nextDraft,
      changeReason: null,
    }
  }

  return {
    ...nextDraft,
    changeReason: original.dayOfWeek !== snapshot.dayOfWeek || original.slotId !== snapshot.slotId
      ? 'MOVE_TEMPLATE'
      : 'UPDATE_TEMPLATE',
  }
}

function toTemplateSnapshot(template: {
  dayOfWeek: DayOfWeekValue
  groupId: string
  lessonFormat: ScheduleLessonFormat
  lessonType: ScheduleLessonType
  notes: string | null
  onlineMeetingUrl: string | null
  roomId: string | null
  slotId: string
  subgroup: SubgroupValue
  subjectId: string
  teacherId: string
  weekType: EditableWeekType
}) {
  return {
    dayOfWeek: template.dayOfWeek,
    groupId: template.groupId,
    lessonFormat: template.lessonFormat,
    lessonType: template.lessonType,
    notes: template.notes ?? '',
    onlineMeetingUrl: template.onlineMeetingUrl,
    roomId: template.roomId,
    slotId: template.slotId,
    subgroup: template.subgroup,
    subjectId: template.subjectId,
    teacherId: template.teacherId,
    weekType: template.weekType,
  } satisfies ScheduleTemplateSnapshot
}

function isSameTemplateSnapshot(left: ScheduleTemplateSnapshot, right: ScheduleTemplateSnapshot) {
  return left.dayOfWeek === right.dayOfWeek
    && left.groupId === right.groupId
    && left.lessonFormat === right.lessonFormat
    && left.lessonType === right.lessonType
    && left.notes === right.notes
    && left.onlineMeetingUrl === right.onlineMeetingUrl
    && left.roomId === right.roomId
    && left.slotId === right.slotId
    && left.subgroup === right.subgroup
    && left.subjectId === right.subjectId
    && left.teacherId === right.teacherId
    && left.weekType === right.weekType
}

function createIdleDraftConflict(): DraftConflictState {
  return {
    items: [],
    lastCheckedHash: null,
    messages: [],
    status: 'idle',
  }
}

function buildDraftConflictHash(draft: ScheduleTemplateDraft) {
  return [
    draft.templateId ?? '',
    draft.dayOfWeek,
    draft.groupId,
    draft.lessonFormat,
    draft.lessonType,
    draft.onlineMeetingUrl ?? '',
    draft.roomId ?? '',
    draft.slotId,
    draft.subgroup,
    draft.subjectId,
    draft.teacherId,
    draft.weekType,
  ].join('|')
}

function buildLocalizedConflictMessage(
  item: ScheduleConflictItemResponse,
  t: (key: string, values?: Record<string, unknown>) => string,
) {
  const messageKey = getConflictMessageKey(item)
  if (messageKey) {
    return t(messageKey)
  }

  return item.message ?? t('schedule.conflictExists')
}

function getConflictMessageKey(item: ScheduleConflictItemResponse) {
  switch (item.type) {
    case 'TEACHER_CONFLICT':
      return 'schedule.conflictMessages.TEACHER_CONFLICT'
    case 'ROOM_CONFLICT':
      return 'schedule.conflictMessages.ROOM_CONFLICT'
    case 'GROUP_SUBGROUP_CONFLICT':
      return 'schedule.conflictMessages.GROUP_SUBGROUP_CONFLICT'
    case 'DUPLICATE_LESSON_CONFLICT':
      return 'schedule.conflictMessages.DUPLICATE_LESSON_CONFLICT'
    default:
      return null
  }
}

function getConflictHintKey(item: ScheduleConflictItemResponse) {
  switch (item.type) {
    case 'TEACHER_CONFLICT':
      return 'schedule.conflictHints.teacher'
    case 'ROOM_CONFLICT':
      return 'schedule.conflictHints.room'
    case 'GROUP_SUBGROUP_CONFLICT':
      return 'schedule.conflictHints.group'
    case 'DUPLICATE_LESSON_CONFLICT':
      return 'schedule.conflictHints.duplicate'
    default:
      return null
  }
}

function getDragMoveBlockedMessage(
  reason: DragMoveBlockReason,
  t: (key: string) => string,
) {
  return t(getDragMoveBlockedReasonKey(reason))
}

function buildConflictDetail(
  item: ScheduleConflictItemResponse,
  t: (key: string, values?: Record<string, unknown>) => string,
) {
  return t('schedule.conflictDetail', {
    entity: item.conflictingEntityType,
    type: item.type,
  })
}

function buildScheduleSemesterOptions(
  activeSemester: AcademicSemesterResponse | null | undefined,
  semesters: AcademicSemesterResponse[],
  t: (key: string) => string,
) {
  if (!activeSemester) {
    return [] as SemesterOption[]
  }

  const futureSemester = semesters
    .filter((semester) => semester.id !== activeSemester.id && semester.startDate > activeSemester.endDate)
    .sort((left, right) => left.startDate.localeCompare(right.startDate))[0] ?? null

  return [
    {
      key: 'CURRENT',
      label: t('schedule.currentSemester'),
      semester: activeSemester,
    },
    ...(futureSemester
      ? [{
        key: 'FUTURE' as const,
        label: t('schedule.futureSemester'),
        semester: futureSemester,
      }]
      : []),
  ]
}

function uniqueIds(values: string[]) {
  return Array.from(new Set(values.filter(Boolean)))
}

function getTeacherDisplayName(teacher: TeacherUser | null | undefined) {
  if (!teacher) {
    return '-'
  }
  if ('displayName' in teacher && teacher.displayName?.trim()) {
    return teacher.displayName
  }
  return teacher.username
}

function upsertRoomCapabilityDraft(
  items: RoomCapabilityDraft[],
  nextItem: RoomCapabilityDraft,
) {
  const index = items.findIndex((item) => item.lessonType === nextItem.lessonType)
  if (index < 0) {
    return [...items, nextItem]
  }

  return items.map((item, currentIndex) => (currentIndex === index ? nextItem : item))
}

function resolveRoomCapabilitiesDisabledReason(
  items: RoomCapabilityDraft[],
  t: (key: string, values?: Record<string, unknown>) => string,
) {
  const activeItems = items.filter((item) => item.active)
  if (activeItems.length === 0) {
    return t('academic.validation.enableAtLeastOneCapability')
  }

  for (const item of activeItems) {
    const priority = Number(item.priority)
    if (!Number.isInteger(priority) || priority <= 0) {
      return t('academic.validation.priorityPositive')
    }
  }

  return null
}

function formatRoomLabel(room: RoomResponse) {
  return `${room.building} · ${room.code}`
}

function formatRoomLabelWithStatus(
  room: RoomResponse,
  t: (key: string) => string,
) {
  return room.active
    ? formatRoomLabel(room)
    : `${formatRoomLabel(room)} · ${t('common.status.ARCHIVED')}`
}

function scrollCarousel(element: HTMLDivElement | null, direction: -1 | 1) {
  if (!element) {
    return
  }

  element.scrollBy({
    behavior: 'smooth',
    left: direction * Math.max(320, element.clientWidth * 0.82),
  })
}

function matchesSubgroupFilter(subgroup: SubgroupValue, filter: SubgroupValue) {
  if (filter === 'ALL') {
    return subgroup === 'ALL'
  }

  return subgroup === 'ALL' || subgroup === filter
}

function appliesToWeekType(weekType: EditableWeekType, filter: ScheduleWeekType) {
  return weekType === 'ALL' || weekType === filter
}

function getLessonEditorKey(editor: DraftEditorState) {
  return [
    editor.localId ?? 'new',
    editor.dayOfWeek,
    editor.slotId,
    editor.weekType,
    editor.subjectId,
    editor.teacherId,
  ].join(':')
}

function getDayOfWeekValue(date: string) {
  const value = new Date(`${date}T00:00:00`)
  const index = value.getDay() === 0 ? 6 : value.getDay() - 1
  return orderedDays[index]
}

function createLocalId() {
  return `local-${Math.random().toString(36).slice(2, 10)}`
}

function formatShortTime(value: string) {
  return value.slice(0, 5)
}
