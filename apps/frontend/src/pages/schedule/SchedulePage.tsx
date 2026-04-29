import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, GraduationCap, MonitorUp, Plus, School, Users } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import { loadAccessibleGroups } from '@/pages/education/helpers'
import {
  adminUserService,
  educationService,
  scheduleService,
  userDirectoryService,
} from '@/shared/api/services'
import { getLocalizedRequestErrorMessage, normalizeApiError } from '@/shared/lib/api-errors'
import { cn } from '@/shared/lib/cn'
import { getLessonFormatLabel, getLessonTypeLabel, getWeekTypeLabel } from '@/shared/lib/enum-labels'
import { formatDate } from '@/shared/lib/format'
import type {
  AcademicSemesterResponse,
  LessonSlotResponse,
  ResolvedLessonResponse,
  RoomResponse,
  SubjectResponse,
} from '@/shared/types/api'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { CardPicker } from '@/shared/ui/CardPicker'
import type { CardPickerItem } from '@/shared/ui/CardPicker'
import { FormField } from '@/shared/ui/FormField'
import { Input } from '@/shared/ui/Input'
import { PageHeader } from '@/shared/ui/PageHeader'
import { SegmentedControl } from '@/shared/ui/SegmentedControl'
import { Select } from '@/shared/ui/Select'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/StateViews'
import { Textarea } from '@/shared/ui/Textarea'
import { UserAvatar } from '@/shared/ui/UserAvatar'

type ScheduleContext = 'mine' | 'group' | 'teacher' | 'room'
type LessonType = 'LECTURE' | 'PRACTICAL' | 'LABORATORY'
type LessonFormat = 'ONLINE' | 'OFFLINE'
type WeekType = 'ALL' | 'ODD' | 'EVEN'
type OverrideType = 'CANCEL' | 'REPLACE' | 'EXTRA'
type DrawerMode = 'TEMPLATE' | 'OVERRIDE'
type SubgroupFilter = 'ALL' | 'FIRST' | 'SECOND'

type ScheduleLessonDraftContext = {
  semesterId: string
  semesterLabel: string

  groupId: string
  groupLabel: string

  dayOfWeek: string
  dayLabel: string
  date?: string

  weekType: WeekType
  weekTypeLabel: string

  slotId: string
  pairNumber: number
  timeRange: string

  subgroup: SubgroupFilter

  mode: DrawerMode
}

interface LessonEditorState {
  title: string
  context: ScheduleLessonDraftContext
  templateId: string
  overrideType: OverrideType
  subjectId: string
  teacherId: string
  lessonType: LessonType
  lessonFormat: LessonFormat
  roomId: string
  onlineMeetingUrl: string
  notes: string
}

interface ConflictState {
  checked: boolean
  clear: boolean
  message: string | null
  requestId?: string
}

interface CanonicalPair {
  pairNumber: number
  startTime: string
  endTime: string
}

const contextValues: ScheduleContext[] = ['mine', 'group', 'teacher', 'room']
const lessonTypeValues: LessonType[] = ['LECTURE', 'PRACTICAL', 'LABORATORY']
const lessonFormatValues: LessonFormat[] = ['OFFLINE', 'ONLINE']
const subgroupValues: SubgroupFilter[] = ['ALL', 'FIRST', 'SECOND']
const canonicalPairs: CanonicalPair[] = [
  { pairNumber: 1, startTime: '08:30:00', endTime: '09:50:00' },
  { pairNumber: 2, startTime: '10:05:00', endTime: '11:25:00' },
  { pairNumber: 3, startTime: '11:40:00', endTime: '13:00:00' },
  { pairNumber: 4, startTime: '13:15:00', endTime: '14:35:00' },
  { pairNumber: 5, startTime: '14:50:00', endTime: '16:10:00' },
  { pairNumber: 6, startTime: '16:25:00', endTime: '17:45:00' },
  { pairNumber: 7, startTime: '18:00:00', endTime: '19:20:00' },
  { pairNumber: 8, startTime: '19:35:00', endTime: '20:55:00' },
]

export function SchedulePage() {
  const { t } = useTranslation(['common', 'errors'])
  const { primaryRole, session } = useAuth()
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const carouselRef = useRef<HTMLDivElement | null>(null)
  const initialWeek = useMemo(() => getCurrentWeekRange(), [])
  const canManageTemplates = primaryRole === 'ADMIN' || primaryRole === 'OWNER'
  const canManageOccurrences = canManageTemplates || primaryRole === 'TEACHER'
  const [context, setContext] = useState<ScheduleContext>('mine')
  const [range, setRange] = useState(initialWeek)
  const [selectedSemesterId, setSelectedSemesterId] = useState('')
  const [weekTypeFilter, setWeekTypeFilter] = useState('')
  const [subgroupFilter, setSubgroupFilter] = useState<SubgroupFilter>('ALL')
  const [lessonTypeFilter, setLessonTypeFilter] = useState('')
  const [groupSearch, setGroupSearch] = useState('')
  const [teacherSearch, setTeacherSearch] = useState('')
  const [roomSearch, setRoomSearch] = useState('')
  const [selectedGroupId, setSelectedGroupId] = useState('')
  const [selectedTeacherId, setSelectedTeacherId] = useState('')
  const [selectedRoomId, setSelectedRoomId] = useState('')
  const [drawer, setDrawer] = useState<LessonEditorState | null>(null)
  const [conflict, setConflict] = useState<ConflictState>({ checked: false, clear: false, message: null })
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'error'; message: string } | null>(null)

  const activeSemesterQuery = useQuery({
    queryKey: ['schedule', 'active-semester', 'safe'],
    queryFn: async () => {
      try {
        return await scheduleService.getActiveSemester()
      } catch (error) {
        const normalizedError = normalizeApiError(error)
        if (normalizedError?.code === 'ACTIVE_ACADEMIC_SEMESTER_NOT_FOUND' || normalizedError?.status === 404) {
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
  const accessibleGroupsQuery = useQuery({
    queryKey: ['schedule', 'accessible-groups', primaryRole, session?.user.id],
    queryFn: () => loadAccessibleGroups(primaryRole, session?.user.id ?? ''),
    enabled: Boolean(session?.user.id && (context === 'group' || primaryRole === 'TEACHER')),
  })
  const adminGroupsQuery = useQuery({
    queryKey: ['schedule', 'groups', groupSearch],
    queryFn: () => educationService.listGroups({
      page: 0,
      size: 12,
      q: groupSearch.trim() || undefined,
      sortBy: 'name',
      direction: 'asc',
    }),
    enabled: canManageTemplates && context === 'group',
  })
  const teachersQuery = useQuery({
    queryKey: ['schedule', 'teachers', teacherSearch],
    queryFn: () => adminUserService.list({
      page: 0,
      size: 12,
      role: 'TEACHER',
      search: teacherSearch.trim() || undefined,
      sortBy: 'username',
      direction: 'asc',
    }),
    enabled: canManageTemplates && context === 'teacher',
  })
  const roomsQuery = useQuery({
    queryKey: ['schedule', 'rooms'],
    queryFn: () => scheduleService.listRooms(),
  })
  const slotsQuery = useQuery({
    queryKey: ['schedule', 'slots'],
    queryFn: () => scheduleService.listSlots(),
  })
  const subjectsForEditorQuery = useQuery({
    queryKey: ['schedule', 'editor-subjects', drawer?.context.groupId],
    queryFn: () => educationService.getSubjectsByGroup(drawer?.context.groupId ?? '', { page: 0, size: 100, sortBy: 'name', direction: 'asc' }),
    enabled: Boolean(drawer?.context.groupId),
  })

  const scheduleQuery = useQuery({
    queryKey: ['schedule', context, selectedGroupId, selectedTeacherId, selectedRoomId, range.dateFrom, range.dateTo],
    queryFn: () => {
      if (context === 'group') {
        return scheduleService.getGroupRange(selectedGroupId, range.dateFrom, range.dateTo)
      }
      if (context === 'teacher') {
        return scheduleService.getTeacherRange(selectedTeacherId, range.dateFrom, range.dateTo)
      }
      if (context === 'room') {
        return scheduleService.getRoomRange(selectedRoomId, range.dateFrom, range.dateTo)
      }

      return scheduleService.getMyRange(range.dateFrom, range.dateTo)
    },
    enabled: !requiresContext(context, selectedGroupId, selectedTeacherId, selectedRoomId),
  })

  const lessons = useMemo(
    () => (scheduleQuery.data ?? []).filter((lesson) => {
      if (lessonTypeFilter && lesson.lessonType !== lessonTypeFilter) {
        return false
      }
      if (weekTypeFilter && lesson.weekType !== weekTypeFilter) {
        return false
      }
      return true
    }),
    [lessonTypeFilter, scheduleQuery.data, weekTypeFilter],
  )
  const relatedSubjectIds = useMemo(() => uniqueIds([
    ...lessons.map((lesson) => lesson.subjectId),
    ...(subjectsForEditorQuery.data?.items.map((subject) => subject.id) ?? []),
  ]), [lessons, subjectsForEditorQuery.data?.items])
  const relatedGroupIds = useMemo(() => uniqueIds([
    ...lessons.map((lesson) => lesson.groupId),
    selectedGroupId,
    drawer?.context.groupId ?? '',
    ...(subjectsForEditorQuery.data?.items.flatMap((subject) => subject.groupIds) ?? []),
  ]), [drawer?.context.groupId, lessons, selectedGroupId, subjectsForEditorQuery.data?.items])
  const relatedTeacherIds = useMemo(() => uniqueIds([
    ...lessons.map((lesson) => lesson.teacherId),
    selectedTeacherId,
    drawer?.teacherId ?? '',
    ...(subjectsForEditorQuery.data?.items.flatMap((subject) => subject.teacherIds) ?? []),
  ]), [drawer?.teacherId, lessons, selectedTeacherId, subjectsForEditorQuery.data?.items])

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
  const relatedGroupsQuery = useQuery({
    queryKey: ['schedule', 'groups-by-id', relatedGroupIds.join(',')],
    queryFn: async () => {
      const groups = await Promise.all(relatedGroupIds.map(async (groupId) => {
        try {
          return await educationService.getGroup(groupId)
        } catch {
          return null
        }
      }))
      return groups.filter((group): group is NonNullable<typeof group> => Boolean(group))
    },
    enabled: relatedGroupIds.length > 0,
  })
  const relatedTeachersQuery = useQuery({
    queryKey: ['schedule', 'teachers-by-id', relatedTeacherIds.join(',')],
    queryFn: () => userDirectoryService.lookup(relatedTeacherIds),
    enabled: relatedTeacherIds.length > 0,
  })

  const groupNameById = useMemo(
    () => new Map((relatedGroupsQuery.data ?? []).map((group) => [group.id, group.name])),
    [relatedGroupsQuery.data],
  )
  const subjectNameById = useMemo(
    () => new Map((relatedSubjectsQuery.data ?? []).map((subject) => [subject.id, subject.name])),
    [relatedSubjectsQuery.data],
  )
  const teacherById = useMemo(
    () => new Map((relatedTeachersQuery.data ?? []).map((teacher) => [teacher.id, teacher])),
    [relatedTeachersQuery.data],
  )
  const canonicalPairByNumber = useMemo(
    () => new Map(canonicalPairs.map((pair) => [pair.pairNumber, pair])),
    [],
  )
  const slotById = useMemo(
    () => new Map((slotsQuery.data ?? []).map((slot) => [slot.id, slot])),
    [slotsQuery.data],
  )
  const canonicalSlotByPairNumber = useMemo(() => {
    const map = new Map<number, LessonSlotResponse>()
    for (const slot of slotsQuery.data ?? []) {
      if (!slot.active || !canonicalPairByNumber.has(slot.number)) {
        continue
      }
      map.set(slot.number, slot)
    }
    return map
  }, [canonicalPairByNumber, slotsQuery.data])
  const canonicalSlotById = useMemo(
    () => new Map(Array.from(canonicalSlotByPairNumber.values()).map((slot) => [slot.id, slot])),
    [canonicalSlotByPairNumber],
  )
  const roomById = useMemo(
    () => new Map((roomsQuery.data ?? []).map((room) => [room.id, room])),
    [roomsQuery.data],
  )
  const roomItems = useMemo(
    () => (roomsQuery.data ?? [])
      .filter((room) => roomSearch.trim()
        ? `${room.building} ${room.code}`.toLowerCase().includes(roomSearch.trim().toLowerCase())
        : true)
      .slice(0, 12)
      .map((room) => ({
        id: room.id,
        title: formatRoomLabel(room),
        description: t('schedule.roomCapacity', { count: room.capacity }),
        meta: room.active ? t('common.status.ACTIVE') : t('common.status.ARCHIVED'),
        leading: <School className="h-5 w-5 text-accent" />,
      })),
    [roomSearch, roomsQuery.data, t],
  )
  const viewGroupItems = useMemo(
    () => {
      const groups = canManageTemplates ? (adminGroupsQuery.data?.items ?? []) : (accessibleGroupsQuery.data ?? [])
      return groups.map((group) => ({
        id: group.id,
        title: group.name,
        description: t('education.groupCardDescription'),
        meta: t('education.groupScheduleStatusUnknown'),
        leading: <Users className="h-5 w-5 text-accent" />,
      }))
    },
    [accessibleGroupsQuery.data, adminGroupsQuery.data?.items, canManageTemplates, t],
  )
  const teacherItems = useMemo(
    () => (teachersQuery.data?.content ?? []).map((teacher) => ({
      id: teacher.id,
      title: teacher.displayName?.trim() || teacher.username,
      description: teacher.email,
      meta: t('education.teacherLabel'),
      leading: <UserAvatar email={teacher.email} username={teacher.username} size="sm" />,
    })),
    [t, teachersQuery.data?.content],
  )
  const editorSubjectItems = useMemo(
    () => (subjectsForEditorQuery.data?.items ?? []).map((subject) => ({
      id: subject.id,
      title: subject.name,
      description: subject.description ?? t('education.subjectDescriptionFallback'),
      meta: t('education.subjectTeachersCount', { count: subject.teacherIds.length }),
      leading: <GraduationCap className="h-5 w-5 text-accent" />,
    })),
    [subjectsForEditorQuery.data?.items, t],
  )
  const selectedEditorSubject = subjectsForEditorQuery.data?.items.find((subject) => subject.id === drawer?.subjectId)
  const selectedEditorTeacherIds = selectedEditorSubject?.teacherIds ?? []
  const editorTeachersQuery = useQuery({
    queryKey: ['schedule', 'editor-teachers', selectedEditorTeacherIds.join(',')],
    queryFn: () => userDirectoryService.lookup(selectedEditorTeacherIds),
    enabled: selectedEditorTeacherIds.length > 0 && !(primaryRole === 'TEACHER' && session?.user.id),
  })
  const editorTeacherItems = useMemo(() => {
    if (primaryRole === 'TEACHER' && session?.user.id) {
      return [{
        id: session.user.id,
        title: session.user.username,
        description: session.user.email,
        meta: t('education.teacherLabel'),
        leading: <UserAvatar email={session.user.email} username={session.user.username} size="sm" />,
      }]
    }

    const teachers = editorTeachersQuery.data ?? []
    const query = teacherSearch.trim().toLowerCase()
    return teachers
      .filter((teacher) => query
        ? `${teacher.username} ${teacher.email}`.toLowerCase().includes(query)
        : true)
      .map((teacher) => ({
        id: teacher.id,
        title: teacher.username,
        description: teacher.email,
        meta: t('education.teacherLabel'),
        leading: <UserAvatar email={teacher.email} username={teacher.username} size="sm" />,
      }))
  }, [editorTeachersQuery.data, primaryRole, session?.user.email, session?.user.id, session?.user.username, t, teacherSearch])
  const editorRoomItems = useMemo(
    () => (roomsQuery.data ?? []).map((room) => ({
      id: room.id,
      title: formatRoomLabel(room),
      description: t('schedule.roomCapacity', { count: room.capacity }),
      meta: room.active ? t('common.status.ACTIVE') : t('common.status.ARCHIVED'),
      leading: <School className="h-5 w-5 text-accent" />,
    })),
    [roomsQuery.data, t],
  )
  const semesterOptions = useMemo(
    () => buildSemesterOptions(semestersQuery.data ?? [], activeSemesterQuery.data, t),
    [activeSemesterQuery.data, semestersQuery.data, t],
  )
  const conflictMutation = useMutation({
    mutationFn: (state: LessonEditorState) => scheduleService.checkConflicts(buildConflictPayload(state)),
    onSuccess: (result) => {
      setConflict({
        checked: true,
        clear: !result.hasConflicts,
        message: result.hasConflicts
          ? result.conflicts.map((item) => item.message ?? item.conflictType).filter(Boolean).join(', ') || t('schedule.hasConflicts')
          : t('schedule.conflictsClear'),
        requestId: undefined,
      })
    },
    onError: (error) => {
      const normalizedError = normalizeApiError(error)
      setConflict({
        checked: true,
        clear: false,
        message: getLocalizedRequestErrorMessage(error, t),
        requestId: normalizedError?.requestId,
      })
    },
  })
  const saveMutation = useMutation<unknown, unknown, LessonEditorState>({
    mutationFn: (state: LessonEditorState) => {
      if (state.context.mode === 'TEMPLATE') {
        return scheduleService.createTemplate(buildTemplatePayload(state))
      }

      return scheduleService.createOverride(buildOverridePayload(state))
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['schedule'] })
      setFeedback({ tone: 'success', message: t('schedule.lessonSavedSuccess') })
      setDrawer(null)
      setConflict({ checked: false, clear: false, message: null, requestId: undefined })
    },
    onError: (error) => setFeedback({ tone: 'error', message: getLocalizedRequestErrorMessage(error, t) }),
  })

  const conflictKeyRef = useRef('')
  useEffect(() => {
    if (!drawer) {
      conflictKeyRef.current = ''
      return
    }

    const drawerHasValidPairMapping = canonicalSlotById.has(drawer.context.slotId)

    const key = [
      drawer.context.mode,
      drawer.overrideType,
      drawer.templateId,
      drawer.context.semesterId,
      drawer.context.date ?? '',
      drawer.context.dayOfWeek,
      drawer.context.weekType,
      drawer.context.slotId,
      drawer.context.groupId,
      drawer.subjectId,
      drawer.teacherId,
      drawer.lessonType,
      drawer.lessonFormat,
      drawer.roomId,
      drawer.onlineMeetingUrl.trim(),
    ].join('|')

    if (key !== conflictKeyRef.current) {
      conflictKeyRef.current = key
      setConflict({ checked: false, clear: false, message: null, requestId: undefined })
    }

    if (drawerHasValidPairMapping && canCheckConflict(drawer) && !conflictMutation.isPending && !conflict.checked) {
      conflictMutation.mutate(drawer)
    }
  }, [canonicalSlotById, conflict.checked, conflictMutation, drawer])

  const days = useMemo(() => buildDateRangeDays(range.dateFrom, range.dateTo), [range.dateFrom, range.dateTo])
  const lessonsByDate = useMemo(() => {
    const groups = new Map<string, ResolvedLessonResponse[]>()
    for (const date of days) {
      groups.set(date, [])
    }
    for (const lesson of lessons) {
      const items = groups.get(lesson.date) ?? []
      items.push(lesson)
      groups.set(lesson.date, items)
    }
    return groups
  }, [days, lessons])
  const currentWeekType = getAutoWeekType(range.dateFrom)

  function moveWeek(direction: -1 | 1) {
    const nextStart = new Date(`${range.dateFrom}T00:00:00`)
    nextStart.setDate(nextStart.getDate() + direction * 7)
    const nextEnd = new Date(nextStart)
    nextEnd.setDate(nextStart.getDate() + 6)
    setRange({
      dateFrom: toIsoDate(nextStart),
      dateTo: toIsoDate(nextEnd),
    })
  }

  function scrollCarousel(direction: -1 | 1) {
    const element = carouselRef.current
    if (!element) {
      return
    }
    element.scrollBy({ left: direction * Math.max(320, element.clientWidth * 0.82), behavior: 'smooth' })
  }

  function openCreateDrawer(params: { date: string; pairNumber: number }) {
    if (!activeSemesterQuery.data) {
      setFeedback({ tone: 'error', message: t('schedule.disabledReasons.activeSemesterNotConfigured') })
      return
    }

    const semesterId = selectedSemesterId || activeSemesterQuery.data.id
    if (context !== 'group' || !selectedGroupId) {
      setFeedback({ tone: 'error', message: t('schedule.draft.missingGroupContext') })
      return
    }

    const pair = canonicalPairByNumber.get(params.pairNumber)
    const slot = canonicalSlotByPairNumber.get(params.pairNumber)
    if (!pair || !slot) {
      setFeedback({ tone: 'error', message: t('schedule.disabledReasons.invalidPairMapping') })
      return
    }

    const resolvedWeekType = (weekTypeFilter || currentWeekType) as WeekType
    const semesterLabel = semesterOptions.find((option) => option.value === semesterId)?.label
      ?? activeSemesterQuery.data?.name
      ?? t('schedule.currentSemester')
    const groupLabel = groupNameById.get(selectedGroupId) ?? t('education.group')

    const dayOfWeek = getDayOfWeekValue(params.date)
    const dayLabel = t(`schedule.dayOfWeekValues.${dayOfWeek}`)
    const timeRange = `${pair.startTime}–${pair.endTime}`

    const defaultTeacherId = primaryRole === 'TEACHER'
      ? session?.user.id ?? ''
      : ''

    setDrawer({
      title: canManageTemplates ? t('schedule.createTemplate') : t('schedule.addExtraLesson'),
      context: {
        semesterId,
        semesterLabel,
        groupId: selectedGroupId,
        groupLabel,
        dayOfWeek,
        dayLabel,
        date: params.date,
        weekType: resolvedWeekType,
        weekTypeLabel: t(`schedule.weekType.${resolvedWeekType}`),
        slotId: slot.id,
        pairNumber: pair.pairNumber,
        timeRange,
        subgroup: subgroupFilter,
        mode: canManageTemplates ? 'TEMPLATE' : 'OVERRIDE',
      },
      templateId: '',
      overrideType: 'EXTRA',
      subjectId: '',
      teacherId: defaultTeacherId,
      lessonType: 'LECTURE',
      lessonFormat: 'OFFLINE',
      roomId: '',
      onlineMeetingUrl: '',
      notes: '',
    })
    setConflict({ checked: false, clear: false, message: null, requestId: undefined })
  }

  function openLessonDrawer(lesson: ResolvedLessonResponse) {
    const isOwnTeacherLesson = primaryRole === 'TEACHER' && lesson.teacherId === session?.user.id
    if (!canManageTemplates && !isOwnTeacherLesson) {
      return
    }

    const slot = slotById.get(lesson.slotId)
    const canonicalPair = slot ? canonicalPairByNumber.get(slot.number) : undefined
    const semesterLabel = semesterOptions.find((option) => option.value === lesson.semesterId)?.label
      ?? activeSemesterQuery.data?.name
      ?? t('schedule.currentSemester')
    const groupLabel = groupNameById.get(lesson.groupId) ?? t('education.group')
    const dayOfWeek = getDayOfWeekValue(lesson.date)
    const dayLabel = t(`schedule.dayOfWeekValues.${dayOfWeek}`)
    const timeRange = canonicalPair ? `${canonicalPair.startTime}–${canonicalPair.endTime}` : ''

    setDrawer({
      title: t('schedule.manageOccurrence'),
      context: {
        semesterId: lesson.semesterId,
        semesterLabel,
        groupId: lesson.groupId,
        groupLabel,
        dayOfWeek,
        dayLabel,
        date: lesson.date,
        weekType: lesson.weekType,
        weekTypeLabel: t(`schedule.weekType.${lesson.weekType}`),
        slotId: lesson.slotId,
        pairNumber: canonicalPair?.pairNumber ?? 1,
        timeRange,
        subgroup: subgroupFilter,
        mode: 'OVERRIDE',
      },
      templateId: lesson.templateId ?? '',
      overrideType: lesson.templateId ? 'CANCEL' : 'EXTRA',
      subjectId: lesson.subjectId,
      teacherId: lesson.teacherId,
      lessonType: lesson.lessonType,
      lessonFormat: lesson.lessonFormat,
      roomId: lesson.roomId ?? '',
      onlineMeetingUrl: lesson.onlineMeetingUrl ?? '',
      notes: lesson.notes ?? '',
    })
    setConflict({ checked: false, clear: false, message: null, requestId: undefined })
  }

  const requiresConflictCheck = drawer
    ? !(drawer.context.mode === 'OVERRIDE' && drawer.overrideType === 'CANCEL')
    : false
  const hasValidPairMapping = drawer
    ? canonicalSlotById.has(drawer.context.slotId)
    : false
  const canSave = drawer
    ? canSaveLesson(drawer)
      && hasValidPairMapping
      && !saveMutation.isPending
      && (!requiresConflictCheck || (conflict.checked && conflict.clear))
    : false
  const saveDisabledReason = drawer && !canSave
    ? getSaveDisabledReason(drawer, conflict, hasValidPairMapping, t)
    : ''

  return (
    <div className="space-y-6">
      <PageHeader
        actions={(
          <div className="flex flex-wrap gap-3">
            <Button variant="secondary" onClick={() => moveWeek(-1)}>
              <ChevronLeft className="mr-2 h-4 w-4" />
              {t('schedule.previousWeek')}
            </Button>
            <Button variant="secondary" onClick={() => moveWeek(1)}>
              {t('schedule.nextWeek')}
              <ChevronRight className="ml-2 h-4 w-4" />
            </Button>
          </div>
        )}
        description={t('schedule.workspaceDescription')}
        title={t('schedule.title')}
      />

      {feedback ? (
        <Card className={cn(
          'border px-4 py-3',
          feedback.tone === 'success' ? 'border-success/30 bg-success/5' : 'border-danger/20 bg-danger/5',
        )}
        >
          <p className="text-sm font-semibold text-text-primary">{feedback.message}</p>
        </Card>
      ) : null}

      <Card className="space-y-5">
        <div className="grid gap-4 lg:grid-cols-[1fr_1fr] xl:grid-cols-[1.1fr_1fr_1fr_1fr]">
          <FormField label={t('schedule.contextLabel')}>
            <Select value={context} onChange={(event) => setContext(event.target.value as ScheduleContext)}>
              {contextValues.map((value) => (
                <option key={value} value={value}>
                  {t(`schedule.context.${value}`)}
                </option>
              ))}
            </Select>
          </FormField>
          <FormField label={t('schedule.semesterLabel')}>
            <Select value={selectedSemesterId} onChange={(event) => setSelectedSemesterId(event.target.value)}>
              {semesterOptions.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </Select>
          </FormField>
          <FormField label={t('schedule.weekTypeLabel')}>
            <SegmentedControl
              ariaLabel={t('schedule.weekTypeLabel')}
              options={[
                { value: '', label: t('schedule.weekAuto', { value: t(`schedule.weekType.${currentWeekType}`) }) },
                { value: 'ALL', label: t('schedule.allWeeks') },
                { value: 'ODD', label: t('schedule.weekType.ODD') },
                { value: 'EVEN', label: t('schedule.weekType.EVEN') },
              ]}
              value={weekTypeFilter}
              onChange={setWeekTypeFilter}
            />
          </FormField>
          <FormField label={t('education.subgroup')}>
            <SegmentedControl
              ariaLabel={t('education.subgroup')}
              options={subgroupValues.map((value) => ({
                value,
                label: t(`education.subgroups.${value}`),
              }))}
              value={subgroupFilter}
              onChange={setSubgroupFilter}
            />
          </FormField>
          <FormField label={t('schedule.lessonTypeLabel')}>
            <SegmentedControl
              ariaLabel={t('schedule.lessonTypeLabel')}
              options={[
                { value: '', label: t('schedule.allLessonTypes') },
                ...lessonTypeValues.map((value) => ({
                  value,
                  label: t(`schedule.lessonType.${value}`),
                })),
              ]}
              value={lessonTypeFilter}
              onChange={setLessonTypeFilter}
            />
          </FormField>
        </div>

        {context === 'group' ? (
          <InlinePicker
            items={viewGroupItems}
            label={t('navigation.shared.groups')}
            loading={adminGroupsQuery.isLoading || accessibleGroupsQuery.isLoading}
            searchPlaceholder={canManageTemplates ? t('schedule.groupSearchPlaceholder') : undefined}
            searchValue={canManageTemplates ? groupSearch : undefined}
            selectedId={selectedGroupId}
            onSearchChange={canManageTemplates ? setGroupSearch : undefined}
            onSelect={setSelectedGroupId}
          />
        ) : null}
        {context === 'teacher' ? (
          <InlinePicker
            items={teacherItems}
            label={t('schedule.teacherLabel')}
            loading={teachersQuery.isLoading}
            searchPlaceholder={t('schedule.teacherSearchPlaceholder')}
            searchValue={teacherSearch}
            selectedId={selectedTeacherId}
            onSearchChange={setTeacherSearch}
            onSelect={setSelectedTeacherId}
          />
        ) : null}
        {context === 'room' ? (
          <InlinePicker
            items={roomItems}
            label={t('schedule.roomLabel')}
            loading={roomsQuery.isLoading}
            searchPlaceholder={t('schedule.roomSearchPlaceholder')}
            searchValue={roomSearch}
            selectedId={selectedRoomId}
            onSearchChange={setRoomSearch}
            onSelect={setSelectedRoomId}
          />
        ) : null}
      </Card>

      <Card className="space-y-4 overflow-hidden">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="space-y-1">
            <p className="text-xs font-semibold uppercase text-text-muted">{t('schedule.weekRange')}</p>
            <h2 className="text-xl font-semibold text-text-primary">
              {formatDate(range.dateFrom)} - {formatDate(range.dateTo)}
            </h2>
          </div>
          <div className="flex gap-2">
            <Button variant="ghost" aria-label={t('schedule.scrollPreviousDay')} onClick={() => scrollCarousel(-1)}>
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <Button variant="ghost" aria-label={t('schedule.scrollNextDay')} onClick={() => scrollCarousel(1)}>
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </div>

        {scheduleQuery.isLoading || activeSemesterQuery.isLoading ? (
          <LoadingState />
        ) : scheduleQuery.isError ? (
          <ErrorState description={t('common.states.error')} title={t('schedule.title')} />
        ) : requiresContext(context, selectedGroupId, selectedTeacherId, selectedRoomId) ? (
          <EmptyState description={t(`schedule.contextHints.${context}`)} title={t('schedule.awaitingContext')} />
        ) : !activeSemesterQuery.data ? (
          canManageTemplates ? (
            <EmptyState
              action={(
                <Button variant="secondary" onClick={() => navigate('/admin/schedule')}>
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
          )
        ) : (
          <div
            ref={carouselRef}
            className="flex snap-x snap-mandatory gap-4 overflow-x-auto pb-2"
          >
            {days.map((date) => {
              const dayLessons = (lessonsByDate.get(date) ?? [])
                .slice()
                .sort((left, right) => (canonicalSlotById.get(left.slotId)?.number ?? 99) - (canonicalSlotById.get(right.slotId)?.number ?? 99))

              return (
                <DayCard
                  key={date}
                  canAddLesson={canManageOccurrences}
                  canonicalPairByNumber={canonicalPairByNumber}
                  canonicalPairs={canonicalPairs}
                  canonicalSlotByPairNumber={canonicalSlotByPairNumber}
                  date={date}
                  groupNameById={groupNameById}
                  lessons={dayLessons}
                  roomById={roomById}
                  slotById={slotById}
                  subjectNameById={subjectNameById}
                  teacherById={teacherById}
                  onAddLesson={openCreateDrawer}
                  onOpenLesson={openLessonDrawer}
                />
              )
            })}
          </div>
        )}
      </Card>

      {drawer ? (
        <div className="fixed inset-0 z-50 flex justify-end bg-overlay p-0 sm:p-4">
          <div className="h-full w-full max-w-2xl overflow-y-auto border-l border-border bg-surface p-5 shadow-soft sm:rounded-[18px] sm:border">
            <div className="space-y-5">
              <div className="flex items-start justify-between gap-3">
                <div>
                  {drawer.context.date ? (
                    <p className="text-xs font-semibold uppercase text-text-muted">{formatDate(drawer.context.date)}</p>
                  ) : null}
                  <h2 className="text-xl font-semibold text-text-primary">{drawer.title}</h2>
                </div>
                <Button variant="ghost" onClick={() => setDrawer(null)}>{t('common.actions.close')}</Button>
              </div>

              <Card className="bg-surface-muted px-4 py-3">
                <div className="grid gap-2 text-sm text-text-secondary md:grid-cols-2">
                  <p><span className="font-semibold text-text-primary">{t('schedule.contextSummary.group')}</span> {drawer.context.groupLabel}</p>
                  <p><span className="font-semibold text-text-primary">{t('schedule.contextSummary.semester')}</span> {drawer.context.semesterLabel}</p>
                  <p><span className="font-semibold text-text-primary">{t('schedule.contextSummary.day')}</span> {drawer.context.dayLabel}</p>
                  <p><span className="font-semibold text-text-primary">{t('schedule.contextSummary.weekType')}</span> {drawer.context.weekTypeLabel}</p>
                  <p>
                    <span className="font-semibold text-text-primary">{t('schedule.contextSummary.pair')}</span>
                    {(() => {
                      const [start, end] = drawer.context.timeRange.split('–')
                      if (start && end) {
                        return ` ${t('schedule.pairSummary', { number: drawer.context.pairNumber, start, end })}`
                      }
                      return ` ${t('schedule.pairLabel')} ${drawer.context.pairNumber} · ${drawer.context.timeRange}`
                    })()}
                  </p>
                  <p><span className="font-semibold text-text-primary">{t('schedule.contextSummary.subgroup')}</span> {t(`education.subgroups.${drawer.context.subgroup}`)}</p>
                </div>
              </Card>

              <div className="grid gap-4 md:grid-cols-2">
                {canManageTemplates ? (
                  <FormField label={t('schedule.editScope')}>
                    <SegmentedControl
                      ariaLabel={t('schedule.editScope')}
                      options={[
                        { value: 'TEMPLATE', label: t('schedule.permanentLesson') },
                        { value: 'OVERRIDE', label: t('schedule.oneTimeChange') },
                      ]}
                      value={drawer.context.mode}
                      onChange={(value) => setDrawer((current) => current
                        ? { ...current, context: { ...current.context, mode: value } }
                        : current)}
                    />
                  </FormField>
                ) : null}
                {drawer.context.mode === 'OVERRIDE' ? (
                  <FormField label={t('schedule.overrideType')}>
                    <SegmentedControl
                      ariaLabel={t('schedule.overrideType')}
                      options={[
                        { value: 'CANCEL', label: t('schedule.overrideTypes.CANCEL') },
                        { value: 'REPLACE', label: t('schedule.overrideTypes.REPLACE') },
                        { value: 'EXTRA', label: t('schedule.overrideTypes.EXTRA') },
                      ]}
                      value={drawer.overrideType}
                      onChange={(value) => setDrawer((current) => current ? { ...current, overrideType: value } : current)}
                    />
                  </FormField>
                ) : null}
                <FormField label={t('schedule.lessonTypeLabel')}>
                  <SegmentedControl
                    ariaLabel={t('schedule.lessonTypeLabel')}
                    options={lessonTypeValues.map((value) => ({
                      value,
                      label: t(`schedule.lessonType.${value}`),
                    }))}
                    value={drawer.lessonType}
                    onChange={(value) => setDrawer((current) => current ? { ...current, lessonType: value } : current)}
                  />
                </FormField>
                <FormField label={t('schedule.lessonFormatLabel')}>
                  <SegmentedControl
                    ariaLabel={t('schedule.lessonFormatLabel')}
                    options={lessonFormatValues.map((value) => ({
                      value,
                      label: t(`schedule.lessonFormat.${value}`),
                    }))}
                    value={drawer.lessonFormat}
                    onChange={(value) => setDrawer((current) => current ? {
                      ...current,
                      lessonFormat: value,
                      roomId: value === 'ONLINE' ? '' : current.roomId,
                      onlineMeetingUrl: value === 'OFFLINE' ? '' : current.onlineMeetingUrl,
                    } : current)}
                  />
                </FormField>
              </div>

              {drawer.overrideType !== 'CANCEL' || drawer.context.mode === 'TEMPLATE' ? (
                <>
                  <CardPicker
                    emptyDescription={subjectsForEditorQuery.isLoading
                      ? t('common.states.loading')
                      : editorSubjectItems.length === 0
                        ? t('schedule.emptySubjectsForGroup')
                        : t('common.states.empty')}
                    emptyTitle={t('education.subject')}
                    items={editorSubjectItems}
                    label={t('education.subject')}
                    loading={subjectsForEditorQuery.isLoading}
                    selectedIds={drawer.subjectId ? [drawer.subjectId] : []}
                    onToggle={(id) => setDrawer((current) => current ? { ...current, subjectId: id, teacherId: '' } : current)}
                  />
                  {editorSubjectItems.length === 0 && canManageTemplates ? (
                    <Button variant="secondary" onClick={() => navigate('/subjects')}>
                      {t('schedule.actions.manageSubjects')}
                    </Button>
                  ) : null}
                  <CardPicker
                    emptyDescription={!drawer.subjectId
                      ? t('schedule.selectSubjectFirst')
                      : editorTeacherItems.length === 0
                        ? t('schedule.emptyTeachersForSubject')
                        : t('common.states.empty')}
                    emptyTitle={t('schedule.teacherLabel')}
                    items={editorTeacherItems}
                    label={t('schedule.teacherLabel')}
                    loading={editorTeachersQuery.isLoading}
                    searchLabel={t('common.actions.search')}
                    searchPlaceholder={t('schedule.teacherSearchPlaceholder')}
                    searchValue={teacherSearch}
                    selectedIds={drawer.teacherId ? [drawer.teacherId] : []}
                    onSearchChange={setTeacherSearch}
                    onToggle={(id) => setDrawer((current) => current ? { ...current, teacherId: id } : current)}
                  />
                  {drawer.subjectId && editorTeacherItems.length === 0 && canManageTemplates ? (
                    <Button variant="secondary" onClick={() => navigate(`/subjects/${drawer.subjectId}`)}>
                      {t('schedule.actions.manageSubjectTeachers')}
                    </Button>
                  ) : null}
                  {drawer.lessonFormat === 'OFFLINE' ? (
                    <CardPicker
                      emptyDescription={t('schedule.noRooms')}
                      emptyTitle={t('schedule.roomLabel')}
                      items={editorRoomItems}
                      label={t('schedule.roomLabel')}
                      loading={roomsQuery.isLoading}
                      selectedIds={drawer.roomId ? [drawer.roomId] : []}
                      onToggle={(id) => setDrawer((current) => current ? { ...current, roomId: id } : current)}
                    />
                  ) : (
                    <FormField
                      hint={!drawer.onlineMeetingUrl.trim() ? t('schedule.linkWillBeAddedLater') : undefined}
                      label={t('schedule.onlineMeetingUrl')}
                    >
                      <Input
                        value={drawer.onlineMeetingUrl}
                        onChange={(event) => setDrawer((current) => current ? { ...current, onlineMeetingUrl: event.target.value } : current)}
                      />
                    </FormField>
                  )}
                </>
              ) : null}

              <FormField label={t('common.labels.notes')}>
                <Textarea
                  value={drawer.notes}
                  onChange={(event) => setDrawer((current) => current ? { ...current, notes: event.target.value } : current)}
                />
              </FormField>

              {conflict.message ? (
                <div className="space-y-1">
                  <p className={cn('text-sm font-medium', conflict.clear ? 'text-success' : 'text-danger')}>
                    {conflict.message}
                  </p>
                  {conflict.requestId ? (
                    <p className="text-xs text-text-muted">{t('schedule.conflictRequestId', { id: conflict.requestId })}</p>
                  ) : null}
                </div>
              ) : null}
              {saveDisabledReason ? (
                <Card className="border-warning/30 bg-warning/5 px-4 py-3">
                  <p className="text-sm font-semibold text-text-primary">{saveDisabledReason}</p>
                </Card>
              ) : null}

              <div className="flex flex-wrap gap-3">
                <Button
                  disabled={!canCheckConflict(drawer) || conflictMutation.isPending}
                  variant="secondary"
                  onClick={() => conflictMutation.mutate(drawer)}
                >
                  {t('schedule.conflictCheck')}
                </Button>
                <Button disabled={!canSave} onClick={() => saveMutation.mutate(drawer)}>
                  {t('common.actions.save')}
                </Button>
              </div>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  )
}

function InlinePicker({
  items,
  label,
  loading,
  searchPlaceholder,
  searchValue,
  selectedId,
  onSearchChange,
  onSelect,
}: {
  items: CardPickerItem[]
  label: string
  loading?: boolean
  searchPlaceholder?: string
  searchValue?: string
  selectedId: string
  onSearchChange?: (value: string) => void
  onSelect: (id: string) => void
}) {
  const { t } = useTranslation()

  return (
    <CardPicker
      emptyDescription={t('common.states.empty')}
      emptyTitle={label}
      items={items}
      loading={loading}
      searchLabel={t('common.actions.search')}
      searchPlaceholder={searchPlaceholder}
      searchValue={searchValue}
      selectedIds={selectedId ? [selectedId] : []}
      onSearchChange={onSearchChange}
      onToggle={onSelect}
    />
  )
}

function DayCard({
  canAddLesson,
  canonicalPairByNumber,
  canonicalPairs,
  canonicalSlotByPairNumber,
  date,
  groupNameById,
  lessons,
  roomById,
  slotById,
  subjectNameById,
  teacherById,
  onAddLesson,
  onOpenLesson,
}: {
  canAddLesson: boolean
  canonicalPairByNumber: Map<number, CanonicalPair>
  canonicalPairs: CanonicalPair[]
  canonicalSlotByPairNumber: Map<number, LessonSlotResponse>
  date: string
  groupNameById: Map<string, string>
  lessons: ResolvedLessonResponse[]
  roomById: Map<string, RoomResponse>
  slotById: Map<string, LessonSlotResponse>
  subjectNameById: Map<string, string>
  teacherById: Map<string, { username: string; email: string }>
  onAddLesson: (params: { date: string; pairNumber: number }) => void
  onOpenLesson: (lesson: ResolvedLessonResponse) => void
}) {
  const { t } = useTranslation()
  const dayOfWeek = getDayOfWeekValue(date)
  const occupiedPairNumbers = new Set(
    lessons
      .map((lesson) => slotById.get(lesson.slotId)?.number)
      .filter((value): value is number => typeof value === 'number' && canonicalPairByNumber.has(value)),
  )
  const emptyPairs = canonicalPairs.filter((pair) => !occupiedPairNumbers.has(pair.pairNumber))
  const firstAvailablePair = emptyPairs[0] ?? canonicalPairs[0]

  return (
    <div className="min-w-[min(86vw,420px)] max-w-[420px] flex-1 snap-start rounded-[20px] border border-border bg-surface-muted p-4 md:min-w-[390px] xl:min-w-[440px]">
      <div className="mb-4 flex items-start justify-between gap-3">
        <div className="space-y-1">
          <p className="text-xs font-semibold uppercase text-text-muted">{t(`schedule.dayOfWeekValues.${dayOfWeek}`)}</p>
          <h3 className="text-lg font-semibold text-text-primary">{formatDate(date)}</h3>
          <p className="text-sm text-text-secondary">
            {lessons.length === 0 ? t('schedule.noLessonsYet') : t('schedule.dayLessonsCount', { count: lessons.length })}
          </p>
        </div>
        {canAddLesson ? (
          <Button
            variant="ghost"
            aria-label={t('schedule.addLesson')}
            onClick={() => onAddLesson({ date, pairNumber: firstAvailablePair.pairNumber })}
          >
            <Plus className="h-4 w-4" />
          </Button>
        ) : null}
      </div>

      {lessons.length === 0 ? (
        <div className="rounded-[16px] border border-dashed border-border-strong bg-surface px-4 py-6 text-sm text-text-secondary">
          {t('schedule.noLessonsYet')}
        </div>
      ) : (
        <div className="space-y-3">
          {lessons.map((lesson) => {
            const slot = slotById.get(lesson.slotId)
            const canonicalPair = slot ? canonicalPairByNumber.get(slot.number) : undefined
            const room = lesson.roomId ? roomById.get(lesson.roomId) : null
            const teacher = teacherById.get(lesson.teacherId)
            const location = lesson.lessonFormat === 'OFFLINE'
              ? (room ? formatRoomLabel(room) : t('schedule.roomAssigned'))
              : lesson.onlineMeetingUrl
                ? t('schedule.lessonFormat.ONLINE')
                : t('schedule.linkWillBeAddedLater')

            return (
              <button
                key={`${lesson.date}-${lesson.slotId}-${lesson.subjectId}-${lesson.teacherId}`}
                className="w-full rounded-[18px] border border-border bg-surface p-4 text-left transition hover:border-border-strong focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-accent/15"
                type="button"
                onClick={() => onOpenLesson(lesson)}
              >
                <div className="mb-3 flex flex-wrap items-center justify-between gap-2 border-b border-border pb-3">
                  <p className="text-sm font-semibold text-text-primary">
                    {canonicalPair
                      ? t('schedule.pairSummary', {
                        number: canonicalPair.pairNumber,
                        start: canonicalPair.startTime,
                        end: canonicalPair.endTime,
                      })
                      : t('schedule.pairFallback')}
                  </p>
                  <span className="rounded-full bg-accent-muted px-2.5 py-1 text-xs font-semibold text-accent">
                    {getLessonTypeLabel(lesson.lessonType)}
                  </span>
                </div>
                <div className="space-y-2">
                  <p className="text-base font-semibold text-text-primary">
                    {subjectNameById.get(lesson.subjectId) ?? t('education.subject')}
                  </p>
                  <p className="text-sm text-text-secondary">{teacher?.username ?? t('education.unknownTeacher')}</p>
                  <p className="text-sm text-text-secondary">
                    {getLessonFormatLabel(lesson.lessonFormat)}
                    {' · '}
                    {location}
                  </p>
                  <div className="flex flex-wrap gap-2">
                    <span className="rounded-full border border-border px-2.5 py-1 text-xs font-semibold text-text-secondary">
                      {groupNameById.get(lesson.groupId) ?? t('education.group')}
                    </span>
                    <span className="rounded-full border border-border px-2.5 py-1 text-xs font-semibold text-text-secondary">
                      {getWeekTypeLabel(lesson.weekType)}
                    </span>
                    <span className="rounded-full border border-border px-2.5 py-1 text-xs font-semibold text-text-secondary">
                      {t('education.subgroups.ALL')}
                    </span>
                    {lesson.overrideType ? (
                      <span className="rounded-full border border-warning/30 bg-warning/10 px-2.5 py-1 text-xs font-semibold text-warning">
                        {t(`schedule.overrideTypes.${lesson.overrideType}`)}
                      </span>
                    ) : null}
                  </div>
                  {lesson.onlineMeetingUrl ? (
                    <a
                      className="inline-flex min-h-9 items-center rounded-[10px] border border-border bg-surface-muted px-3 text-sm font-medium text-accent transition hover:border-accent/40"
                      href={lesson.onlineMeetingUrl}
                      rel="noreferrer"
                      target="_blank"
                      onClick={(event) => event.stopPropagation()}
                    >
                      <MonitorUp className="mr-2 h-4 w-4" />
                      {t('schedule.joinLesson')}
                    </a>
                  ) : null}
                  {lesson.notes ? <p className="text-sm text-text-secondary">{lesson.notes}</p> : null}
                </div>
              </button>
            )
          })}
        </div>
      )}

      {canAddLesson && emptyPairs.length > 0 ? (
        <div className="mt-4 space-y-2">
          {emptyPairs.map((pair) => {
            const resolvedSlot = canonicalSlotByPairNumber.get(pair.pairNumber)
            return (
              <button
                key={`${date}-${pair.pairNumber}`}
                className={cn(
                  'flex w-full items-center justify-between gap-3 rounded-[16px] border border-dashed border-border-strong bg-surface px-4 py-3 text-left text-sm text-text-secondary transition focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-accent/15',
                  resolvedSlot ? 'hover:border-border' : 'cursor-not-allowed opacity-70',
                )}
                disabled={!resolvedSlot}
                type="button"
                onClick={() => onAddLesson({ date, pairNumber: pair.pairNumber })}
              >
                <span className="font-semibold text-text-primary">
                  {t('schedule.pairSummary', { number: pair.pairNumber, start: pair.startTime, end: pair.endTime })}
                </span>
                <span className={cn('inline-flex items-center gap-2', resolvedSlot ? 'text-accent' : 'text-text-muted')}>
                  <Plus className="h-4 w-4" />
                  {resolvedSlot ? t('schedule.addLesson') : t('schedule.pairUnavailable')}
                </span>
              </button>
            )
          })}
        </div>
      ) : null}
    </div>
  )
}

function buildTemplatePayload(state: LessonEditorState) {
  return {
    semesterId: state.context.semesterId,
    groupId: state.context.groupId,
    subjectId: state.subjectId,
    teacherId: state.teacherId,
    dayOfWeek: state.context.dayOfWeek,
    slotId: state.context.slotId,
    weekType: state.context.weekType,
    lessonType: state.lessonType,
    lessonFormat: state.lessonFormat,
    roomId: state.lessonFormat === 'OFFLINE' ? state.roomId || null : null,
    onlineMeetingUrl: state.lessonFormat === 'ONLINE' ? state.onlineMeetingUrl.trim() || null : null,
    notes: state.notes.trim() || null,
    active: true,
  }
}

function buildOverridePayload(state: LessonEditorState) {
  return {
    semesterId: state.context.semesterId || null,
    templateId: state.templateId || null,
    overrideType: state.overrideType,
    date: state.context.date ?? '',
    groupId: state.overrideType === 'CANCEL' ? null : state.context.groupId || null,
    subjectId: state.overrideType === 'CANCEL' ? null : state.subjectId || null,
    teacherId: state.overrideType === 'CANCEL' ? null : state.teacherId || null,
    slotId: state.overrideType === 'CANCEL' ? null : state.context.slotId || null,
    lessonType: state.overrideType === 'CANCEL' ? null : state.lessonType,
    lessonFormat: state.overrideType === 'CANCEL' ? null : state.lessonFormat,
    roomId: state.overrideType === 'CANCEL'
      ? null
      : state.lessonFormat === 'OFFLINE'
        ? state.roomId || null
        : null,
    onlineMeetingUrl: state.overrideType === 'CANCEL'
      ? null
      : state.lessonFormat === 'ONLINE'
        ? state.onlineMeetingUrl.trim() || null
        : null,
    notes: state.notes.trim() || null,
  }
}

function buildConflictPayload(state: LessonEditorState) {
  if (state.context.mode === 'TEMPLATE') {
    return {
      semesterId: state.context.semesterId,
      groupId: state.context.groupId,
      subjectId: state.subjectId,
      teacherId: state.teacherId,
      roomId: state.lessonFormat === 'OFFLINE' ? state.roomId || null : null,
      slotId: state.context.slotId,
      dayOfWeek: state.context.dayOfWeek,
      weekType: state.context.weekType,
      lessonType: state.lessonType,
      lessonFormat: state.lessonFormat,
    }
  }

  return {
    semesterId: state.context.semesterId || null,
    templateId: state.templateId || null,
    overrideType: state.overrideType,
    date: state.context.date,
    groupId: state.overrideType === 'CANCEL' ? null : state.context.groupId || null,
    subjectId: state.overrideType === 'CANCEL' ? null : state.subjectId || null,
    teacherId: state.overrideType === 'CANCEL' ? null : state.teacherId || null,
    roomId: state.overrideType === 'CANCEL'
      ? null
      : state.lessonFormat === 'OFFLINE'
        ? state.roomId || null
        : null,
    slotId: state.overrideType === 'CANCEL' ? null : state.context.slotId,
    lessonType: state.overrideType === 'CANCEL' ? null : state.lessonType,
    lessonFormat: state.overrideType === 'CANCEL' ? null : state.lessonFormat,
  }
}

function canSaveLesson(state: LessonEditorState) {
  if (!state.context.semesterId) {
    return false
  }
  if (!state.context.slotId) {
    return false
  }
  if (state.context.mode === 'OVERRIDE') {
    if (!state.context.date) {
      return false
    }
    if (state.overrideType === 'CANCEL') {
      return Boolean(state.templateId)
    }
  }

  const hasLocation = state.lessonFormat === 'OFFLINE' ? Boolean(state.roomId) : true
  return Boolean(state.context.groupId && state.subjectId && state.teacherId && hasLocation)
}

function getSaveDisabledReason(
  state: LessonEditorState,
  conflict: ConflictState,
  hasValidPairMapping: boolean,
  t: (key: string, values?: Record<string, unknown>) => string,
) {
  if (!state.context.semesterId) {
    return t('schedule.disabledReasons.activeSemesterNotConfigured')
  }
  if (!state.context.slotId) {
    return t('schedule.disabledReasons.invalidPairMapping')
  }
  if (!hasValidPairMapping) {
    return t('schedule.disabledReasons.invalidPairMapping')
  }
  if (!state.context.groupId) {
    return t('schedule.disabledReasons.chooseGroup')
  }
  if (state.context.mode === 'OVERRIDE' && state.overrideType === 'CANCEL') {
    if (!state.templateId) {
      return t('schedule.disabledReasons.chooseLesson')
    }
    return ''
  }
  if (!state.subjectId) {
    return t('schedule.disabledReasons.chooseSubject')
  }
  if (!state.teacherId) {
    return t('schedule.disabledReasons.chooseTeacher')
  }
  if (state.lessonFormat === 'OFFLINE' && !state.roomId) {
    return t('schedule.disabledReasons.chooseRoom')
  }

  if (!conflict.checked) {
    return t('schedule.disabledReasons.checkConflicts')
  }
  if (!conflict.clear) {
    return t('schedule.disabledReasons.resolveConflict')
  }

  return ''
}

function canCheckConflict(state: LessonEditorState) {
  if (!state.context.semesterId) {
    return false
  }
  if (!state.context.slotId) {
    return false
  }
  if (!state.context.groupId) {
    return false
  }

  if (state.context.mode === 'TEMPLATE') {
    if (!state.context.dayOfWeek || !state.context.weekType) {
      return false
    }
  }

  if (state.context.mode === 'OVERRIDE') {
    if (!state.context.date) {
      return false
    }
    if (state.overrideType === 'CANCEL') {
      return Boolean(state.templateId)
    }
  }

  if (!state.subjectId || !state.teacherId) {
    return false
  }
  if (!state.lessonType || !state.lessonFormat) {
    return false
  }
  if (state.lessonFormat === 'OFFLINE' && !state.roomId) {
    return false
  }

  return true
}

function getCurrentWeekRange() {
  const today = new Date()
  const day = today.getDay()
  const mondayOffset = day === 0 ? -6 : 1 - day
  const monday = new Date(today)
  monday.setDate(today.getDate() + mondayOffset)
  monday.setHours(0, 0, 0, 0)

  const sunday = new Date(monday)
  sunday.setDate(monday.getDate() + 6)

  return {
    dateFrom: toIsoDate(monday),
    dateTo: toIsoDate(sunday),
  }
}

function buildDateRangeDays(dateFrom: string, dateTo: string) {
  const start = new Date(`${dateFrom}T00:00:00`)
  const end = new Date(`${dateTo}T00:00:00`)
  const days: string[] = []
  for (const current = new Date(start); current <= end; current.setDate(current.getDate() + 1)) {
    days.push(toIsoDate(current))
  }
  return days
}

function toIsoDate(value: Date) {
  return value.toISOString().slice(0, 10)
}

function dayOfWeekValues() {
  return ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']
}

function getDayOfWeekValue(date: string) {
  return dayOfWeekValues()[new Date(`${date}T00:00:00`).getDay() === 0 ? 6 : new Date(`${date}T00:00:00`).getDay() - 1]
}

function getAutoWeekType(dateFrom: string): WeekType {
  const weekNumber = getWeekNumber(new Date(`${dateFrom}T00:00:00`))
  return weekNumber % 2 === 0 ? 'EVEN' : 'ODD'
}

function getWeekNumber(date: Date) {
  const target = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()))
  const dayNumber = target.getUTCDay() || 7
  target.setUTCDate(target.getUTCDate() + 4 - dayNumber)
  const yearStart = new Date(Date.UTC(target.getUTCFullYear(), 0, 1))
  return Math.ceil((((target.getTime() - yearStart.getTime()) / 86400000) + 1) / 7)
}

function requiresContext(context: ScheduleContext, groupId: string, teacherId: string, roomId: string) {
  return (context === 'group' && !groupId)
    || (context === 'teacher' && !teacherId)
    || (context === 'room' && !roomId)
}

function uniqueIds(values: string[]) {
  return Array.from(new Set(values.filter(Boolean)))
}

function formatRoomLabel(room: RoomResponse) {
  return `${room.building}, ${room.code}`
}

function buildSemesterOptions(
  semesters: AcademicSemesterResponse[],
  activeSemester: AcademicSemesterResponse | null | undefined,
  t: (key: string, values?: Record<string, unknown>) => string,
) {
  if (semesters.length === 0) {
    return [{
      value: activeSemester?.id ?? '',
      label: activeSemester?.name ?? t('schedule.currentSemester'),
    }]
  }

  return semesters
    .slice()
    .sort((left, right) => left.startDate.localeCompare(right.startDate))
    .map((semester) => ({
      value: semester.id,
      label: semester.active
        ? `${semester.name} · ${t('schedule.currentSemester')}`
        : `${semester.name} · ${formatDate(semester.startDate)} - ${formatDate(semester.endDate)}`,
    }))
}
