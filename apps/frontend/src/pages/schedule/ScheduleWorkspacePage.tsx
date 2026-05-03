import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  ArrowLeft,
  ArrowRight,
  CalendarDays,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  CircleAlert,
  Copy,
  DoorOpen,
  GripVertical,
  MonitorUp,
  Pencil,
  Plus,
  RotateCcw,
  Save,
  School,
  Trash2,
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
  scheduleService,
  userDirectoryService,
} from '@/shared/api/services'
import { getLocalizedRequestErrorMessage, normalizeApiError } from '@/shared/lib/api-errors'
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
import { MetricCard } from '@/widgets/common/MetricCard'

type TeacherUser = AdminUserResponse | UserSummaryResponse
type ScheduleScope = 'group' | 'teacher' | 'room' | 'me'
type ScheduleWeekType = 'ODD' | 'EVEN'
type EditableWeekType = 'ALL' | ScheduleWeekType
type ScheduleLessonType = ScheduleTemplateResponse['lessonType']
type ScheduleLessonFormat = ScheduleTemplateResponse['lessonFormat']
type DayOfWeekValue = ScheduleTemplateResponse['dayOfWeek']
type ScheduleViewMode = 'VIEW' | 'EDIT'
type ConflictStatus = 'idle' | 'pending' | 'clear' | 'conflict' | 'error'
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
  weekOffset: number
  weekType: ScheduleWeekType
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
    const label = `${teacher.username} ${teacher.email}`.toLowerCase()
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
                    <p className="truncate text-sm text-text-secondary">{teacher.email}</p>
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
  const [drawer, setDrawer] = useState<DraftEditorState | null>(null)
  const [deleteTargetId, setDeleteTargetId] = useState<string | null>(null)
  const [cancelLesson, setCancelLesson] = useState<ResolvedLessonResponse | null>(null)
  const [copiedLessonId, setCopiedLessonId] = useState<string | null>(null)
  const [movingLessonId, setMovingLessonId] = useState<string | null>(null)
  const [copiedDay, setCopiedDay] = useState<{ dayOfWeek: DayOfWeekValue; drafts: ScheduleTemplateDraft[] } | null>(null)
  const [initializedDraftSignature, setInitializedDraftSignature] = useState('')
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

  const semesterOptions = useMemo(
    () => buildScheduleSemesterOptions(activeSemesterQuery.data, semestersQuery.data ?? [], t),
    [activeSemesterQuery.data, semestersQuery.data, t],
  )
  const selectedSemester = semesterOptions.find((option) => option.key === selectedSemesterKey)?.semester ?? activeSemesterQuery.data
  const today = useMemo(() => new Date(), [])
  const baseWeekStart = useMemo(
    () => getBaseWeekStart(selectedSemester, today),
    [selectedSemester, today],
  )
  const baseWeekType = useMemo(
    () => getWeekTypeForWeekStart(baseWeekStart, selectedSemester?.weekOneStartDate),
    [baseWeekStart, selectedSemester?.weekOneStartDate],
  )
  const selectedSemesterId = selectedSemester?.id ?? 'none'
  const selectedWeekType = weekSelection?.semesterId === selectedSemesterId ? weekSelection.weekType : baseWeekType
  const weekOffset = weekSelection?.semesterId === selectedSemesterId ? weekSelection.weekOffset : 0
  const setSelectedWeekType = useCallback(
    (weekType: ScheduleWeekType) => {
      setWeekSelection({
        semesterId: selectedSemesterId,
        weekOffset,
        weekType,
      })
    },
    [selectedSemesterId, weekOffset],
  )
  const setWeekOffset = useCallback(
    (updater: number | ((current: number) => number)) => {
      setWeekSelection((current) => {
        const currentWeekType = current?.semesterId === selectedSemesterId ? current.weekType : baseWeekType
        const currentWeekOffset = current?.semesterId === selectedSemesterId ? current.weekOffset : 0
        const nextWeekOffset = typeof updater === 'function' ? updater(currentWeekOffset) : updater

        return {
          semesterId: selectedSemesterId,
          weekOffset: nextWeekOffset,
          weekType: currentWeekType,
        }
      })
    },
    [baseWeekType, selectedSemesterId],
  )

  const selectedWeekStart = useMemo(() => {
    const adjustedBase = selectedWeekType === baseWeekType
      ? baseWeekStart
      : addDays(baseWeekStart, 7)

    return addDays(adjustedBase, weekOffset * 14)
  }, [baseWeekStart, baseWeekType, selectedWeekType, weekOffset])
  const weekDays = useMemo(() => buildWeekDays(selectedWeekStart), [selectedWeekStart])
  const range = useMemo(
    () => ({
      dateFrom: toIsoDate(selectedWeekStart),
      dateTo: toIsoDate(addDays(selectedWeekStart, 6)),
    }),
    [selectedWeekStart],
  )

  const subjectsForGroupQuery = useQuery({
    queryKey: ['schedule', 'group-subjects', entityId],
    queryFn: () => educationService.getSubjectsByGroup(entityId ?? '', {
      page: 0,
      size: 100,
      sortBy: 'name',
      direction: 'asc',
    }),
    enabled: scope === 'group' && Boolean(entityId),
  })

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
      ...((subjectsForGroupQuery.data?.items ?? []).flatMap((subject) => subject.teacherIds)),
    ]),
    [drafts, scheduleQuery.data, subjectsForGroupQuery.data?.items],
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
  const unresolvedDrafts = useMemo(
    () => drafts.filter((draft) => draft.changeReason !== null && !draft.deleted),
    [drafts],
  )
  const draftValidationReasons = useMemo(
    () => unresolvedDrafts
      .map((draft) => getDraftValidationReason(draft, canonicalSlotByPairNumber, t))
      .filter((reason): reason is string => Boolean(reason)),
    [canonicalSlotByPairNumber, t, unresolvedDrafts],
  )
  const pendingConflictCount = useMemo(
    () => unresolvedDrafts.filter((draft) => draft.conflict.status === 'pending').length,
    [unresolvedDrafts],
  )
  const failedConflictCount = useMemo(
    () => unresolvedDrafts.filter((draft) => draft.conflict.status === 'conflict' || draft.conflict.status === 'error').length,
    [unresolvedDrafts],
  )

  const saveDisabledReason = useMemo(() => {
    if (dirtyDraftCount === 0) {
      return t('schedule.disabledReasons.noChanges')
    }
    if (draftValidationReasons.length > 0) {
      return draftValidationReasons[0]
    }
    if (pendingConflictCount > 0) {
      return t('schedule.disabledReasons.conflictPending')
    }
    if (failedConflictCount > 0) {
      return t('schedule.disabledReasons.conflictExists')
    }
    return ''
  }, [dirtyDraftCount, draftValidationReasons, failedConflictCount, pendingConflictCount, t])

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

    const validationReason = getDraftValidationReason(draft, canonicalSlotByPairNumber, t)
    if (validationReason) {
      applyConflictResult(draft.localId, buildDraftConflictHash(draft), 'error', [], [validationReason])
      return
    }

    const hash = buildDraftConflictHash(draft)
    if (!force && draft.conflict.lastCheckedHash === hash && draft.conflict.status !== 'idle') {
      return
    }

    applyConflictResult(draft.localId, hash, 'pending', [], [t('schedule.conflictChecking')])

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
      applyConflictResult(
        draft.localId,
        hash,
        'error',
        [],
        [getLocalizedRequestErrorMessage(error, t)],
      )
    }
  }, [applyConflictResult, canonicalSlotByPairNumber, t])

  const draftsToAutoCheck = useMemo(
    () => unresolvedDrafts.filter((draft) => {
      const validationReason = getDraftValidationReason(draft, canonicalSlotByPairNumber, t)
      if (validationReason) {
        return false
      }
      const hash = buildDraftConflictHash(draft)
      return draft.conflict.lastCheckedHash !== hash || draft.conflict.status === 'idle'
    }),
    [canonicalSlotByPairNumber, t, unresolvedDrafts],
  )

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

      setCopiedDay(null)
      setCopiedLessonId(null)
      setMovingLessonId(null)
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

  const editorTeacherIds = useMemo(
    () => uniqueIds((subjectsForGroupQuery.data?.items ?? []).flatMap((subject) => subject.teacherIds)),
    [subjectsForGroupQuery.data?.items],
  )
  const editorTeachersQuery = useQuery({
    queryKey: ['schedule', 'editor-teachers', editorTeacherIds.join(',')],
    queryFn: () => userDirectoryService.lookup(editorTeacherIds),
    enabled: drawer !== null && editorTeacherIds.length > 0,
  })

  const filteredLessons = useMemo(
    () => (scheduleQuery.data ?? []).filter((lesson) => {
      if (scope !== 'group') {
        return true
      }

      return matchesSubgroupFilter(lesson.subgroup, viewSubgroup)
    }),
    [scope, scheduleQuery.data, viewSubgroup],
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
      if (!matchesSubgroupFilter(draft.subgroup, viewSubgroup)) {
        continue
      }

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
  }, [slotById, viewSubgroup, visibleDrafts])

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
          { label: formatRoomLabel(selectedRoom) },
        ],
        description: t('schedule.roomWorkspaceDescription'),
        title: formatRoomLabel(selectedRoom),
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
  }, [groupQuery.data, scope, selectedRoom, t, teacherQuery.data])

  const visibleSummaryLessons = filteredLessons.length
  const onlineLessons = filteredLessons.filter((lesson) => lesson.lessonFormat === 'ONLINE').length
  const offlineLessons = filteredLessons.filter((lesson) => lesson.lessonFormat === 'OFFLINE').length

  const isLoading = activeSemesterQuery.isLoading
    || slotsQuery.isLoading
    || roomsQuery.isLoading
    || (scope === 'group' && groupQuery.isLoading)
    || (scope === 'teacher' && teacherQuery.isLoading)
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
            <FormField label={t('schedule.weekTypeLabel')}>
              <SegmentedControl
                ariaLabel={t('schedule.weekTypeLabel')}
                options={[
                  { value: 'ODD', label: t('schedule.weekType.ODD') },
                  { value: 'EVEN', label: t('schedule.weekType.EVEN') },
                ]}
                value={selectedWeekType}
                onChange={(value) => setSelectedWeekType(value as ScheduleWeekType)}
              />
            </FormField>
            {canEditTemplates ? (
              <FormField label={t('schedule.workspaceModeLabel')}>
                <SegmentedControl
                  ariaLabel={t('schedule.workspaceModeLabel')}
                  options={[
                    { value: 'VIEW', label: t('schedule.viewMode') },
                    { value: 'EDIT', label: t('schedule.editMode') },
                  ]}
                  value={viewMode}
                  onChange={(value) => setViewMode(value as ScheduleViewMode)}
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

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <MetricCard title={t('schedule.summary.lessons')} value={visibleSummaryLessons} />
        <MetricCard title={t('schedule.summary.online')} value={onlineLessons} />
        <MetricCard title={t('schedule.summary.offline')} value={offlineLessons} />
        <MetricCard title={t('schedule.summary.activeSemester')} value={selectedSemester?.name ?? '—'} />
      </div>

      {canManageTemplates && scope !== 'group' ? (
        <Card className="space-y-2 border border-border bg-surface-muted">
          <p className="text-sm font-semibold text-text-primary">{t('schedule.editScopeInfoTitle')}</p>
          <p className="text-sm leading-6 text-text-secondary">{t('schedule.editScopeInfoDescription')}</p>
        </Card>
      ) : null}

      <Card className="space-y-4 overflow-hidden">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="space-y-1">
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">{t('schedule.weekRange')}</p>
            <h2 className="text-xl font-semibold text-text-primary">
              {formatDate(range.dateFrom)} - {formatDate(range.dateTo)}
            </h2>
          </div>
          <div className="flex gap-2">
            <Button
              variant="secondary"
              onClick={() => setWeekOffset((current) => current - 1)}
            >
              <ChevronLeft className="mr-2 h-4 w-4" />
              {t('schedule.previousWeek')}
            </Button>
            <Button
              variant="secondary"
              onClick={() => setWeekOffset((current) => current + 1)}
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

        {scheduleQuery.isLoading ? (
          <LoadingState />
        ) : scheduleQuery.isError ? (
          <ErrorState description={t('common.states.error')} title={detailHeader.title} />
        ) : (
          <div ref={carouselRef} className="flex snap-x snap-mandatory gap-4 overflow-x-auto pb-2">
            {weekDays.map((date, index) => {
              const dayOfWeek = orderedDays[index]
              return viewMode === 'EDIT' && canEditTemplates ? (
                <EditDayCard
                  key={date}
                  canonicalSlotByPairNumber={canonicalSlotByPairNumber}
                  copiedDay={copiedDay}
                  copiedLessonId={copiedLessonId}
                  dayOfWeek={dayOfWeek}
                  date={date}
                  drafts={draftGroupsByDayAndPair}
                  movingLessonId={movingLessonId}
                  roomById={roomById}
                  subjectNameById={subjectNameById}
                  teacherById={teacherById}
                  onAddLesson={(pairNumber) => {
                    const slot = canonicalSlotByPairNumber.get(pairNumber)
                    if (!slot) {
                      setFeedback({ tone: 'error', message: t('schedule.slotSetupError') })
                      return
                    }
                    setDrawer({
                      dayOfWeek,
                      forBothWeeks: viewSubgroup !== 'ALL' ? false : true,
                      forWholeGroup: viewSubgroup === 'ALL',
                      lessonFormat: 'OFFLINE',
                      lessonType: 'LECTURE',
                      localId: null,
                      notes: '',
                      onlineMeetingUrl: '',
                      pairNumber,
                      roomId: '',
                      slotId: slot.id,
                      subgroupChoice: viewSubgroup === 'SECOND' ? 'SECOND' : 'FIRST',
                      subjectId: '',
                      teacherId: '',
                      weekType: selectedWeekType,
                    })
                  }}
                  onCopyDay={() => {
                    const sourceDrafts = drafts
                      .filter((draft) => !draft.deleted && draft.dayOfWeek === dayOfWeek)
                      .map((draft) => ({ ...draft }))
                    setCopiedDay({ dayOfWeek, drafts: sourceDrafts })
                  }}
                  onDeleteLesson={(localId) => setDeleteTargetId(localId)}
                  onEditLesson={(localId) => {
                    const target = drafts.find((draft) => draft.localId === localId)
                    if (!target) {
                      return
                    }

                    const pairNumber = slotById.get(target.slotId)?.number ?? 1
                    setDrawer({
                      dayOfWeek: target.dayOfWeek,
                      forBothWeeks: target.weekType === 'ALL',
                      forWholeGroup: target.subgroup === 'ALL',
                      lessonFormat: target.lessonFormat,
                      lessonType: target.lessonType,
                      localId: target.localId,
                      notes: target.notes,
                      onlineMeetingUrl: target.onlineMeetingUrl ?? '',
                      pairNumber,
                      roomId: target.roomId ?? '',
                      slotId: target.slotId,
                      subgroupChoice: target.subgroup === 'SECOND' ? 'SECOND' : 'FIRST',
                      subjectId: target.subjectId,
                      teacherId: target.teacherId,
                      weekType: target.weekType === 'ALL' ? selectedWeekType : target.weekType,
                    })
                  }}
                  onPasteDay={(targetDay) => {
                    if (!copiedDay || copiedDay.dayOfWeek === targetDay) {
                      return
                    }

                    const createdDrafts = copiedDay.drafts.map((draft) => ({
                      ...cloneDraft(draft),
                      dayOfWeek: targetDay,
                      changeReason: 'COPY_DAY' as const,
                      conflict: createIdleDraftConflict(),
                    }))
                    setDrafts((current) => [...current, ...createdDrafts])
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
                  onRestoreLesson={(localId) => {
                    updateDraft(localId, (draft) => ({
                      ...draft,
                      changeReason: null,
                      conflict: createIdleDraftConflict(),
                      deleted: false,
                    }))
                  }}
                  onSetCopiedLesson={setCopiedLessonId}
                  onSetMovingLesson={setMovingLessonId}
                  onUseMoveTarget={(targetDay, pairNumber) => {
                    const slot = canonicalSlotByPairNumber.get(pairNumber)
                    const source = drafts.find((draft) => draft.localId === movingLessonId)
                    if (!slot || !source) {
                      return
                    }

                    updateDraft(source.localId, (draft) => ({
                      ...draft,
                      changeReason: draft.templateId ? 'MOVE_TEMPLATE' : draft.changeReason ?? 'CREATE_TEMPLATE',
                      conflict: createIdleDraftConflict(),
                      dayOfWeek: targetDay,
                      slotId: slot.id,
                    }))
                    setMovingLessonId(null)
                  }}
                />
              ) : (
                <ViewDayCard
                  key={date}
                  date={date}
                  lessons={lessonsByDate.get(date) ?? []}
                  roomById={roomById}
                  slotById={slotById}
                  subjectNameById={subjectNameById}
                  teacherById={teacherById}
                  onCancelLesson={(lesson) => setCancelLesson(lesson)}
                  showCancelAction={isTeacher}
                  currentUserId={session?.user.id ?? ''}
                />
              )
            })}
          </div>
        )}
      </Card>

      {viewMode === 'EDIT' && canEditTemplates ? (
        <Card className="sticky bottom-4 z-10 border border-border bg-background/95 px-4 py-4 backdrop-blur">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div className="space-y-1">
              <p className="text-sm font-semibold text-text-primary">
                {t('schedule.unsavedChangesCount', { count: dirtyDraftCount })}
              </p>
              <p className="text-sm text-text-secondary">{saveDisabledReason || t('schedule.readyToSave')}</p>
            </div>
            <div className="flex flex-wrap gap-3">
              <Button
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
                disabled={dirtyDraftCount === 0}
                variant="secondary"
                onClick={() => {
                  if (!templatesQuery.data) {
                    return
                  }
                  setDrafts(templatesQuery.data.map(createDraftFromTemplate))
                  setCopiedDay(null)
                  setCopiedLessonId(null)
                  setMovingLessonId(null)
                }}
              >
                <RotateCcw className="mr-2 h-4 w-4" />
                {t('schedule.discardChanges')}
              </Button>
              <Button disabled={Boolean(saveDisabledReason) || saveMutation.isPending} onClick={() => saveMutation.mutate()}>
                <Save className="mr-2 h-4 w-4" />
                {t('schedule.saveChanges')}
              </Button>
            </div>
          </div>
        </Card>
      ) : null}

      {drawer ? (
        <LessonDrawer
          key={getDrawerKey(drawer)}
          drawer={drawer}
          rooms={roomsQuery.data ?? []}
          subjects={subjectsForGroupQuery.data?.items ?? []}
          teachers={editorTeachersQuery.data ?? []}
          onClose={() => setDrawer(null)}
          onSave={(state) => {
            const subject = (subjectsForGroupQuery.data?.items ?? []).find((item) => item.id === state.subjectId)
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
            } else {
              setDrafts((current) => [
                ...current,
                {
                  localId: createLocalId(),
                  templateId: null,
                  semesterId: selectedSemester.id,
                  groupId: entityId,
                  subjectId: state.subjectId,
                  teacherId: state.teacherId,
                  dayOfWeek: state.dayOfWeek,
                  slotId: state.slotId,
                  weekType: nextWeekType,
                  subgroup: nextSubgroup,
                  lessonType: state.lessonType,
                  lessonFormat: state.lessonFormat,
                  roomId,
                  onlineMeetingUrl,
                  notes: state.notes.trim(),
                  changeReason: 'CREATE_TEMPLATE',
                  conflict: createIdleDraftConflict(),
                  deleted: false,
                  original: null,
                },
              ])
            }

            setDrawer(null)
          }}
        />
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
          } else {
            updateDraft(deleteTargetId, (draft) => ({
              ...draft,
              changeReason: 'DELETE_TEMPLATE',
              deleted: true,
            }))
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

function ViewDayCard({
  currentUserId,
  date,
  lessons,
  onCancelLesson,
  roomById,
  showCancelAction,
  slotById,
  subjectNameById,
  teacherById,
}: {
  currentUserId: string
  date: string
  lessons: ResolvedLessonResponse[]
  onCancelLesson: (lesson: ResolvedLessonResponse) => void
  roomById: Map<string, RoomResponse>
  showCancelAction: boolean
  slotById: Map<string, LessonSlotResponse>
  subjectNameById: Map<string, string>
  teacherById: Map<string, TeacherUser>
}) {
  const { t } = useTranslation()
  const dayOfWeek = getDayOfWeekValue(date)

  return (
    <div className="min-w-[min(86vw,420px)] max-w-[420px] flex-1 snap-start rounded-[20px] border border-border bg-surface-muted p-4 md:min-w-[390px] xl:min-w-[440px]">
      <div className="mb-4 space-y-1">
        <p className="text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">{t(`schedule.dayOfWeekValues.${dayOfWeek}`)}</p>
        <h3 className="text-lg font-semibold text-text-primary">{formatDate(date)}</h3>
      </div>

      {lessons.length === 0 ? (
        <div className="rounded-[16px] border border-dashed border-border-strong bg-surface px-4 py-6 text-sm text-text-secondary">
          {t('schedule.noLessonsYet')}
        </div>
      ) : (
        <div className="space-y-3">
          {lessons.map((lesson) => {
            const slot = slotById.get(lesson.slotId)
            const room = lesson.roomId ? roomById.get(lesson.roomId) : null
            const teacher = teacherById.get(lesson.teacherId)
            const pairLabel = slot
              ? t('schedule.pairSummary', {
                end: formatShortTime(slot.endTime),
                number: slot.number,
                start: formatShortTime(slot.startTime),
              })
              : t('schedule.pairFallback')
            const location = lesson.lessonFormat === 'OFFLINE'
              ? (room ? formatRoomLabel(room) : t('schedule.roomAssigned'))
              : lesson.onlineMeetingUrl || t('schedule.linkWillBeAddedLater')
            const canCancel = showCancelAction
              && lesson.teacherId === currentUserId
              && Boolean(lesson.templateId)

            return (
              <div key={`${lesson.date}-${lesson.slotId}-${lesson.subjectId}-${lesson.teacherId}`} className="rounded-[18px] border border-border bg-surface p-4">
                <div className="mb-3 flex flex-wrap items-start justify-between gap-3 border-b border-border pb-3">
                  <div className="space-y-1">
                    <p className="text-sm font-semibold text-text-primary">{pairLabel}</p>
                    <p className="text-xs font-semibold uppercase tracking-[0.14em] text-text-muted">
                      {getLessonTypeLabel(lesson.lessonType)}
                    </p>
                  </div>
                  {canCancel ? (
                    <Button variant="ghost" onClick={() => onCancelLesson(lesson)}>
                      {t('schedule.cancelOccurrence')}
                    </Button>
                  ) : null}
                </div>
                <div className="space-y-2">
                  <p className="text-base font-semibold text-text-primary">
                    {subjectNameById.get(lesson.subjectId) ?? t('education.subject')}
                  </p>
                  <p className="text-sm text-text-secondary">{getTeacherDisplayName(teacher)}</p>
                  <p className="text-sm text-text-secondary">
                    {getLessonFormatLabel(lesson.lessonFormat)}
                    {' · '}
                    {location}
                  </p>
                  <div className="flex flex-wrap gap-2">
                    <span className="rounded-full border border-border px-2.5 py-1 text-xs font-semibold text-text-secondary">
                      {t(`education.subgroups.${lesson.subgroup}`)}
                    </span>
                    <span className="rounded-full border border-border px-2.5 py-1 text-xs font-semibold text-text-secondary">
                      {t(`schedule.weekType.${lesson.weekType}`)}
                    </span>
                    {lesson.overrideType ? (
                      <span className="rounded-full border border-warning/30 bg-warning/10 px-2.5 py-1 text-xs font-semibold text-warning">
                        {t(`schedule.overrideTypes.${lesson.overrideType}`)}
                      </span>
                    ) : null}
                  </div>
                  {lesson.lessonFormat === 'ONLINE' && lesson.onlineMeetingUrl ? (
                    <a
                      className="inline-flex min-h-9 items-center rounded-[10px] border border-border bg-surface-muted px-3 text-sm font-medium text-accent transition hover:border-accent/40"
                      href={lesson.onlineMeetingUrl}
                      rel="noreferrer"
                      target="_blank"
                    >
                      <MonitorUp className="mr-2 h-4 w-4" />
                      {t('schedule.joinLesson')}
                    </a>
                  ) : null}
                  {lesson.notes ? <p className="text-sm text-text-secondary">{lesson.notes}</p> : null}
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}

function EditDayCard({
  canonicalSlotByPairNumber,
  copiedDay,
  copiedLessonId,
  dayOfWeek,
  date,
  drafts,
  movingLessonId,
  onAddLesson,
  onCopyDay,
  onDeleteLesson,
  onEditLesson,
  onPasteDay,
  onPasteLesson,
  onRestoreLesson,
  onSetCopiedLesson,
  onSetMovingLesson,
  onUseMoveTarget,
  roomById,
  subjectNameById,
  teacherById,
}: {
  canonicalSlotByPairNumber: Map<number, LessonSlotResponse>
  copiedDay: { dayOfWeek: DayOfWeekValue; drafts: ScheduleTemplateDraft[] } | null
  copiedLessonId: string | null
  dayOfWeek: DayOfWeekValue
  date: string
  drafts: Map<string, ScheduleTemplateDraft[]>
  movingLessonId: string | null
  onAddLesson: (pairNumber: number) => void
  onCopyDay: () => void
  onDeleteLesson: (localId: string) => void
  onEditLesson: (localId: string) => void
  onPasteDay: (dayOfWeek: DayOfWeekValue) => void
  onPasteLesson: (dayOfWeek: DayOfWeekValue, pairNumber: number) => void
  onRestoreLesson: (localId: string) => void
  onSetCopiedLesson: (localId: string | null) => void
  onSetMovingLesson: (localId: string | null) => void
  onUseMoveTarget: (dayOfWeek: DayOfWeekValue, pairNumber: number) => void
  roomById: Map<string, RoomResponse>
  subjectNameById: Map<string, string>
  teacherById: Map<string, TeacherUser>
}) {
  const { t } = useTranslation()

  return (
    <div className="min-w-[min(92vw,520px)] max-w-[520px] flex-1 snap-start rounded-[20px] border border-border bg-surface-muted p-4 xl:min-w-[500px]">
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">{t(`schedule.dayOfWeekValues.${dayOfWeek}`)}</p>
          <h3 className="text-lg font-semibold text-text-primary">{formatDate(date)}</h3>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" onClick={onCopyDay}>
            <Copy className="mr-2 h-4 w-4" />
            {t('schedule.copyDay')}
          </Button>
          {copiedDay && copiedDay.dayOfWeek !== dayOfWeek ? (
            <Button variant="secondary" onClick={() => onPasteDay(dayOfWeek)}>
              <Plus className="mr-2 h-4 w-4" />
              {t('schedule.pasteDay')}
            </Button>
          ) : null}
        </div>
      </div>

      <div className="space-y-3">
        {canonicalPairs.map((pair) => {
          const slot = canonicalSlotByPairNumber.get(pair.pairNumber)
          const pairDrafts = drafts.get(`${dayOfWeek}:${pair.pairNumber}`) ?? []
          const dropActive = Boolean(movingLessonId)
          const validDropTarget = Boolean(slot)

          return (
            <div
              key={`${dayOfWeek}-${pair.pairNumber}`}
              className={cn(
                'rounded-[18px] border bg-surface p-4 transition',
                dropActive && validDropTarget ? 'border-accent/60 bg-accent-muted/20' : 'border-border',
                dropActive && !validDropTarget ? 'border-danger/40 bg-danger/5 opacity-70' : '',
              )}
              onDragOver={(event) => {
                if (!validDropTarget || !movingLessonId) {
                  return
                }
                event.preventDefault()
              }}
              onDrop={(event) => {
                event.preventDefault()
                if (validDropTarget && movingLessonId) {
                  onUseMoveTarget(dayOfWeek, pair.pairNumber)
                }
              }}
            >
              <div className="flex flex-wrap items-start justify-between gap-3 border-b border-border pb-3">
                <div className="space-y-1">
                  <p className="text-sm font-semibold text-text-primary">
                    {t('schedule.pairSummary', {
                      end: pair.endTime,
                      number: pair.pairNumber,
                      start: pair.startTime,
                    })}
                  </p>
                  {!slot ? (
                    <p className="text-sm text-danger">{t('schedule.slotSetupError')}</p>
                  ) : null}
                </div>
                {slot ? (
                  <div className="flex flex-wrap gap-2">
                    <Button variant="ghost" onClick={() => onAddLesson(pair.pairNumber)}>
                      <Plus className="mr-2 h-4 w-4" />
                      {t('schedule.addLesson')}
                    </Button>
                    {copiedLessonId ? (
                      <Button variant="ghost" onClick={() => onPasteLesson(dayOfWeek, pair.pairNumber)}>
                        <Copy className="mr-2 h-4 w-4" />
                        {t('schedule.pasteLesson')}
                      </Button>
                    ) : null}
                    {movingLessonId ? (
                      <Button variant="ghost" onClick={() => onUseMoveTarget(dayOfWeek, pair.pairNumber)}>
                        <ArrowRight className="mr-2 h-4 w-4" />
                        {t('schedule.moveHere')}
                      </Button>
                    ) : null}
                  </div>
                ) : null}
              </div>

              {pairDrafts.length === 0 ? (
                <div className="mt-3 rounded-[14px] border border-dashed border-border-strong bg-surface-muted px-4 py-4 text-sm text-text-secondary">
                  {slot ? t('schedule.editSlotEmpty') : t('schedule.slotSetupError')}
                </div>
              ) : (
                <div className="mt-3 space-y-3">
                  {pairDrafts.map((draft) => (
                    <DraftLessonCard
                      key={draft.localId}
                      draft={draft}
                      roomById={roomById}
                      subjectNameById={subjectNameById}
                      teacherById={teacherById}
                      onCopy={() => onSetCopiedLesson(draft.localId)}
                      onDelete={() => onDeleteLesson(draft.localId)}
                      onEdit={() => onEditLesson(draft.localId)}
                      onMove={() => onSetMovingLesson(draft.localId)}
                      onDragStart={() => onSetMovingLesson(draft.localId)}
                      onDragEnd={() => onSetMovingLesson(null)}
                      onRestore={() => onRestoreLesson(draft.localId)}
                      onStopMove={() => onSetMovingLesson(null)}
                      copied={copiedLessonId === draft.localId}
                      moving={movingLessonId === draft.localId}
                    />
                  ))}
                </div>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}

function DraftLessonCard({
  copied,
  draft,
  moving,
  onCopy,
  onDelete,
  onDragEnd,
  onDragStart,
  onEdit,
  onMove,
  onRestore,
  onStopMove,
  roomById,
  subjectNameById,
  teacherById,
}: {
  copied: boolean
  draft: ScheduleTemplateDraft
  moving: boolean
  onCopy: () => void
  onDelete: () => void
  onDragEnd: () => void
  onDragStart: () => void
  onEdit: () => void
  onMove: () => void
  onRestore: () => void
  onStopMove: () => void
  roomById: Map<string, RoomResponse>
  subjectNameById: Map<string, string>
  teacherById: Map<string, TeacherUser>
}) {
  const { t } = useTranslation()
  const room = draft.roomId ? roomById.get(draft.roomId) : null
  const teacher = teacherById.get(draft.teacherId)
  const localChangeLabel = draft.changeReason ? t(`schedule.localChangeType.${draft.changeReason}`) : null

  return (
    <div
      className={cn(
        'rounded-[16px] border p-4 transition',
        draft.deleted
          ? 'border-danger/30 bg-danger/5'
          : draft.conflict.status === 'conflict' || draft.conflict.status === 'error'
            ? 'border-danger/30 bg-danger/5'
            : draft.changeReason
              ? 'border-accent/30 bg-accent/5'
              : 'border-border bg-surface',
        moving && 'ring-4 ring-accent/15',
      )}
      draggable={!draft.deleted}
      onDragEnd={onDragEnd}
      onDragStart={(event) => {
        event.dataTransfer.effectAllowed = 'move'
        event.dataTransfer.setData('text/plain', draft.localId)
        onDragStart()
      }}
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 items-start gap-3">
          {!draft.deleted ? (
            <span className="mt-1 inline-flex h-8 w-8 shrink-0 cursor-grab items-center justify-center rounded-[10px] border border-border bg-surface-muted text-text-muted" title={t('schedule.dragLesson')}>
              <GripVertical className="h-4 w-4" />
            </span>
          ) : null}
          <div className="min-w-0 space-y-2">
            <p className="text-base font-semibold text-text-primary">
              {subjectNameById.get(draft.subjectId) ?? t('education.subject')}
            </p>
            <p className="text-sm text-text-secondary">{getTeacherDisplayName(teacher)}</p>
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          {localChangeLabel ? (
            <span className="rounded-full bg-accent-muted px-2.5 py-1 text-xs font-semibold text-accent">
              {localChangeLabel}
            </span>
          ) : null}
          {copied ? (
            <span className="rounded-full border border-border px-2.5 py-1 text-xs font-semibold text-text-secondary">
              {t('schedule.copiedState')}
            </span>
          ) : null}
          {moving ? (
            <span className="rounded-full border border-border px-2.5 py-1 text-xs font-semibold text-text-secondary">
              {t('schedule.movingState')}
            </span>
          ) : null}
        </div>
      </div>
      <div className="mt-3 space-y-2">
        <p className="text-sm text-text-secondary">
          {getLessonTypeLabel(draft.lessonType)}
          {' · '}
          {getLessonFormatLabel(draft.lessonFormat)}
        </p>
        <p className="text-sm text-text-secondary">
          {draft.lessonFormat === 'OFFLINE'
            ? (room ? formatRoomLabel(room) : t('schedule.roomAssigned'))
            : draft.onlineMeetingUrl || t('schedule.linkWillBeAddedLater')}
        </p>
        <div className="flex flex-wrap gap-2">
          <span className="rounded-full border border-border px-2.5 py-1 text-xs font-semibold text-text-secondary">
            {t(`education.subgroups.${draft.subgroup}`)}
          </span>
          <span className="rounded-full border border-border px-2.5 py-1 text-xs font-semibold text-text-secondary">
            {t(`schedule.weekType.${draft.weekType}`)}
          </span>
        </div>
        {draft.notes ? <p className="text-sm text-text-secondary">{draft.notes}</p> : null}
      </div>
      {draft.conflict.status !== 'idle' || draft.conflict.messages.length > 0 ? (
        <div className={cn(
          'mt-3 space-y-2 rounded-[14px] border px-3 py-3',
          getConflictStateClasses(draft.conflict.status),
        )}>
          <p className="text-xs font-semibold uppercase tracking-[0.14em] text-text-muted">
            {t(`schedule.conflictStatus.${draft.conflict.status}`)}
          </p>
          {draft.conflict.messages.map((message) => (
            <div key={message} className={cn(
              'flex items-start gap-2 text-sm',
              draft.conflict.status === 'clear' ? 'text-success' : draft.conflict.status === 'pending' ? 'text-text-secondary' : 'text-danger',
            )}>
              <CircleAlert className="mt-0.5 h-4 w-4 shrink-0" />
              <span>{message}</span>
            </div>
          ))}
          {draft.conflict.items.map((item) => {
            const hintKey = getConflictHintKey(item)
            return hintKey ? (
              <p key={`${draft.localId}-${hintKey}`} className="text-xs leading-5 text-text-secondary">
                {buildConflictDetail(item, t)}
                {' '}
                {t(hintKey)}
              </p>
            ) : null
          })}
        </div>
      ) : null}
      <div className="mt-4 flex flex-wrap gap-2">
        {draft.deleted ? (
          <Button variant="secondary" onClick={onRestore}>
            <RotateCcw className="mr-2 h-4 w-4" />
            {t('schedule.restoreLesson')}
          </Button>
        ) : (
          <>
            <Button variant="secondary" onClick={onEdit}>
              <Pencil className="mr-2 h-4 w-4" />
              {t('common.actions.edit')}
            </Button>
            <Button variant="secondary" onClick={onCopy}>
              <Copy className="mr-2 h-4 w-4" />
              {t('schedule.copyLesson')}
            </Button>
            <Button variant="secondary" onClick={moving ? onStopMove : onMove}>
              {moving ? <RotateCcw className="mr-2 h-4 w-4" /> : <ArrowRight className="mr-2 h-4 w-4" />}
              {moving ? t('schedule.stopMoving') : t('schedule.moveLesson')}
            </Button>
            <Button variant="danger" onClick={onDelete}>
              <Trash2 className="mr-2 h-4 w-4" />
              {t('schedule.deleteLesson')}
            </Button>
          </>
        )}
      </div>
    </div>
  )
}

function LessonDrawer({
  drawer,
  onClose,
  onSave,
  rooms,
  subjects,
  teachers,
}: {
  drawer: DraftEditorState
  onClose: () => void
  onSave: (state: DraftEditorState) => void
  rooms: RoomResponse[]
  subjects: SubjectResponse[]
  teachers: TeacherUser[]
}) {
  const { t } = useTranslation()
  const [state, setState] = useState(drawer)

  const subject = subjects.find((item) => item.id === state.subjectId)
  const teacherChoices = teachers.filter((teacher) => !subject || subject.teacherIds.includes(teacher.id))
  const saveBlockedReason = getDrawerValidationReason(state, t)

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-overlay p-0 sm:p-4">
      <div className="h-full w-full max-w-2xl overflow-y-auto border-l border-border bg-surface p-5 shadow-soft sm:rounded-[18px] sm:border">
        <div className="space-y-5">
          <div className="flex items-start justify-between gap-3">
            <div className="space-y-1">
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">{getDayOfWeekLabel(state.dayOfWeek)}</p>
              <h2 className="text-xl font-semibold text-text-primary">
                {state.localId ? t('schedule.editLesson') : t('schedule.addLesson')}
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

          <div className="grid gap-4 md:grid-cols-2">
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
                  </option>
                ))}
              </Select>
            </FormField>

            <FormField label={t('schedule.teacherLabel')}>
              <Select
                disabled={!state.subjectId || teacherChoices.length === 0}
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

            {state.subjectId && teacherChoices.length === 0 ? (
              <div className="md:col-span-2 rounded-[14px] border border-warning/30 bg-warning/5 px-4 py-3">
                <p className="text-sm font-semibold text-text-primary">{t('schedule.emptyTeachersForSubject')}</p>
                <Link className="mt-2 inline-flex text-sm font-medium text-accent" to={`/subjects/${state.subjectId}`}>
                  {t('schedule.actions.manageSubjectTeachers')}
                </Link>
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

          <Card className="space-y-4 bg-surface-muted">
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
          </Card>

          {state.lessonFormat === 'OFFLINE' ? (
            <FormField label={t('schedule.roomLabel')}>
              <Select
                value={state.roomId}
                onChange={(event) => {
                  setState((current) => ({ ...current, roomId: event.target.value }))
                }}
              >
                <option value="">{t('schedule.selectRoom')}</option>
                {rooms.map((room) => (
                  <option key={room.id} value={room.id}>
                    {formatRoomLabel(room)}
                  </option>
                ))}
              </Select>
            </FormField>
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

          <FormField label={t('common.labels.notes')}>
            <Textarea
              value={state.notes}
              onChange={(event) => {
                setState((current) => ({ ...current, notes: event.target.value }))
              }}
            />
          </FormField>

          {saveBlockedReason ? (
            <Card className="border-warning/30 bg-warning/5 px-4 py-3">
              <p className="text-sm font-semibold text-text-primary">{saveBlockedReason}</p>
            </Card>
          ) : null}

          <div className="flex flex-wrap gap-3">
            <Button variant="secondary" onClick={onClose}>
              {t('common.actions.cancel')}
            </Button>
            <Button disabled={Boolean(saveBlockedReason)} onClick={() => onSave(state)}>
              {t('common.actions.save')}
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}

function getDrawerValidationReason(
  drawer: DraftEditorState,
  t: (key: string) => string,
) {
  if (!drawer.subjectId) {
    return t('schedule.disabledReasons.chooseSubject')
  }
  if (!drawer.teacherId) {
    return t('schedule.disabledReasons.chooseTeacher')
  }
  if (!drawer.slotId) {
    return t('schedule.disabledReasons.invalidPairMapping')
  }
  if (drawer.lessonFormat === 'OFFLINE' && !drawer.roomId) {
    return t('schedule.disabledReasons.chooseRoom')
  }

  return ''
}

function getDraftValidationReason(
  draft: ScheduleTemplateDraft,
  canonicalSlotByPairNumber: Map<number, LessonSlotResponse>,
  t: (key: string) => string,
) {
  const pairNumber = canonicalPairs.find((pair) => pair.pairNumber === findPairNumberBySlotId(canonicalSlotByPairNumber, draft.slotId))?.pairNumber
  if (!pairNumber) {
    return t('schedule.disabledReasons.invalidPairMapping')
  }
  if (!draft.subjectId) {
    return t('schedule.disabledReasons.chooseSubject')
  }
  if (!draft.teacherId) {
    return t('schedule.disabledReasons.chooseTeacher')
  }
  if (draft.lessonFormat === 'OFFLINE' && !draft.roomId) {
    return t('schedule.disabledReasons.chooseRoom')
  }

  return ''
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

function getConflictStateClasses(status: ConflictStatus) {
  if (status === 'clear') {
    return 'border-success/30 bg-success/5'
  }
  if (status === 'conflict' || status === 'error') {
    return 'border-danger/30 bg-danger/5'
  }
  if (status === 'pending') {
    return 'border-border bg-surface-muted'
  }
  return 'border-border bg-surface-muted'
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

function formatRoomLabel(room: RoomResponse) {
  return `${room.building} · ${room.code}`
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

function getDrawerKey(drawer: DraftEditorState) {
  return [
    drawer.localId ?? 'new',
    drawer.dayOfWeek,
    drawer.slotId,
    drawer.weekType,
    drawer.subjectId,
    drawer.teacherId,
  ].join(':')
}

function getBaseWeekStart(semester: AcademicSemesterResponse | null | undefined, now: Date) {
  if (!semester) {
    return startOfWeek(now)
  }

  const todayIso = toIsoDate(now)
  if (todayIso < semester.startDate) {
    return startOfWeek(new Date(`${semester.weekOneStartDate}T00:00:00`))
  }
  if (todayIso > semester.endDate) {
    return startOfWeek(new Date(`${semester.endDate}T00:00:00`))
  }

  return startOfWeek(now)
}

function getWeekTypeForWeekStart(weekStart: Date, weekOneStartDate?: string | null): ScheduleWeekType {
  const baseline = startOfWeek(new Date(`${weekOneStartDate ?? toIsoDate(weekStart)}T00:00:00`))
  const diffDays = Math.floor((weekStart.getTime() - baseline.getTime()) / 86400000)
  const diffWeeks = Math.floor(diffDays / 7)
  return Math.abs(diffWeeks) % 2 === 0 ? 'ODD' : 'EVEN'
}

function startOfWeek(date: Date) {
  const value = new Date(date)
  const day = value.getDay()
  const offset = day === 0 ? -6 : 1 - day
  value.setDate(value.getDate() + offset)
  value.setHours(0, 0, 0, 0)
  return value
}

function addDays(date: Date, amount: number) {
  const value = new Date(date)
  value.setDate(value.getDate() + amount)
  return value
}

function buildWeekDays(weekStart: Date) {
  return Array.from({ length: 7 }, (_, index) => toIsoDate(addDays(weekStart, index)))
}

function getDayOfWeekValue(date: string) {
  const value = new Date(`${date}T00:00:00`)
  const index = value.getDay() === 0 ? 6 : value.getDay() - 1
  return orderedDays[index]
}

function toIsoDate(value: Date) {
  return value.toISOString().slice(0, 10)
}

function createLocalId() {
  return `local-${Math.random().toString(36).slice(2, 10)}`
}

function formatShortTime(value: string) {
  return value.slice(0, 5)
}
