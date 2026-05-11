import { useDraggable } from '@dnd-kit/core'
import { CSS } from '@dnd-kit/utilities'
import { CircleAlert, GripVertical, MonitorUp, Pencil } from 'lucide-react'
import { useMemo } from 'react'
import type { CSSProperties } from 'react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/shared/lib/cn'
import { getLessonTypeLabel } from '@/shared/lib/enum-labels'
import type {
  AdminUserResponse,
  RoomResponse,
  ScheduleConflictItemResponse,
  SubgroupValue,
  UserSummaryResponse,
} from '@/shared/types/api'
import {
  getCompactDraftLessonDisplay,
  getDraftBadgeKeys,
} from '@/pages/schedule/components/draftLessonCard.helpers'

export type TeacherUser = AdminUserResponse | UserSummaryResponse
export type ConflictStatus = 'idle' | 'checking' | 'clear' | 'conflict' | 'error'
export type EditableWeekType = 'ALL' | 'ODD' | 'EVEN'

export interface ScheduleTemplateDraftView {
  localId: string
  dayOfWeek: string
  deleted: boolean
  groupId: string
  lessonFormat: 'ONLINE' | 'OFFLINE'
  lessonType: 'LECTURE' | 'PRACTICAL' | 'LABORATORY'
  onlineMeetingUrl: string | null
  roomId: string | null
  slotId: string
  subgroup: SubgroupValue
  subjectId: string
  teacherId: string
  weekType: EditableWeekType
  notes: string
  changeReason: 'CREATE_TEMPLATE' | 'UPDATE_TEMPLATE' | 'DELETE_TEMPLATE' | 'MOVE_TEMPLATE' | 'COPY_TEMPLATE' | 'COPY_DAY' | null
  conflict: {
    items: ScheduleConflictItemResponse[]
    messages: string[]
    status: ConflictStatus
  }
}

export interface ScheduleDragLessonData {
  dayOfWeek: string
  deleted: boolean
  groupId: string
  lessonFormat: 'ONLINE' | 'OFFLINE'
  lessonType: 'LECTURE' | 'PRACTICAL' | 'LABORATORY'
  localId: string
  onlineMeetingUrl: string | null
  roomId: string | null
  slotId: string
  subgroup: SubgroupValue
  subjectId: string
  teacherId: string
  weekType: EditableWeekType
}

interface DraftLessonCardProps {
  copied: boolean
  density?: 'normal' | 'lane'
  draft: ScheduleTemplateDraftView
  moving: boolean
  selected: boolean
  roomById: Map<string, RoomResponse>
  subjectNameById: Map<string, string>
  teacherById: Map<string, TeacherUser>
  onEdit: () => void
  onSelect: () => void
  formatRoomLabel: (room: RoomResponse) => string
  getTeacherDisplayName: (teacher: TeacherUser | null | undefined) => string
}

export function DraftLessonCard({
  copied,
  density = 'normal',
  draft,
  moving,
  selected,
  roomById,
  subjectNameById,
  teacherById,
  onEdit,
  onSelect,
  formatRoomLabel,
  getTeacherDisplayName,
}: DraftLessonCardProps) {
  const { t } = useTranslation()
  const dragData: ScheduleDragLessonData = useMemo(() => ({
    dayOfWeek: draft.dayOfWeek,
    deleted: draft.deleted,
    groupId: draft.groupId,
    lessonFormat: draft.lessonFormat,
    lessonType: draft.lessonType,
    localId: draft.localId,
    onlineMeetingUrl: draft.onlineMeetingUrl,
    roomId: draft.roomId,
    slotId: draft.slotId,
    subgroup: draft.subgroup,
    subjectId: draft.subjectId,
    teacherId: draft.teacherId,
    weekType: draft.weekType,
  }), [draft.dayOfWeek, draft.deleted, draft.groupId, draft.lessonFormat, draft.lessonType, draft.localId, draft.onlineMeetingUrl, draft.roomId, draft.slotId, draft.subgroup, draft.subjectId, draft.teacherId, draft.weekType])
  const {
    attributes,
    isDragging,
    listeners,
    setActivatorNodeRef,
    setNodeRef,
    transform,
  } = useDraggable({
    id: draft.localId,
    data: dragData,
    disabled: draft.deleted,
  })
  const dragTransform = CSS.Translate.toString(transform)
  const dragStyle: CSSProperties = dragTransform ? { transform: dragTransform } : {}
  const room = draft.roomId ? roomById.get(draft.roomId) : null
  const teacher = teacherById.get(draft.teacherId)
  const subject = subjectNameById.get(draft.subjectId) ?? t('education.subject')
  const location = draft.lessonFormat === 'OFFLINE'
    ? (room ? formatRoomLabel(room) : t('schedule.roomAssigned'))
    : t('schedule.lessonFormat.ONLINE')
  const display = getCompactDraftLessonDisplay({
    lessonType: getLessonTypeLabel(draft.lessonType),
    location,
    subject,
    teacher: getTeacherDisplayName(teacher),
  })
  const localChangeLabel = draft.changeReason ? t(`schedule.localChangeType.${draft.changeReason}`) : null
  const badgeKeys = getDraftBadgeKeys(draft)
  const isLane = density === 'lane'
  const hasConflict = draft.conflict.status === 'conflict' || draft.conflict.status === 'error'
  const showMutedConflictIndicator = draft.conflict.status === 'idle' || draft.conflict.status === 'checking'
  const dotTone = draft.lessonType === 'LECTURE'
    ? 'bg-accent'
    : draft.lessonType === 'PRACTICAL'
      ? 'bg-success'
      : 'bg-warning'

  const weekBadge = (
    <span className={cn(
      'shrink-0 rounded-full border border-border bg-surface-muted font-semibold text-text-secondary',
      isLane ? 'px-1.5 py-0 text-[10px] leading-4' : 'px-2 py-0.5 text-[11px] leading-4',
    )}>
      {t(badgeKeys.weekKey)}
    </span>
  )
  const subgroupBadge = (
    <span className={cn(
      'shrink-0 rounded-full border border-border bg-surface-muted font-semibold text-text-secondary',
      isLane ? 'px-1.5 py-0 text-[10px] leading-4' : 'px-2 py-0.5 text-[11px] leading-4',
    )}>
      {t(badgeKeys.subgroupKey)}
    </span>
  )

  return (
    <div
      ref={setNodeRef}
      className={cn(
        'group flex h-full min-h-0 cursor-pointer flex-col overflow-hidden rounded-[8px] border bg-surface text-left transition',
        isLane ? 'gap-1 px-2 py-1.5' : 'gap-1.5 px-2.5 py-2.5',
        draft.deleted
          ? 'border-danger/35 bg-danger/5 opacity-80'
          : hasConflict
            ? 'border-danger/70 bg-danger/5'
            : 'border-border hover:border-accent/45',
        moving && 'ring-2 ring-accent/25',
        selected && 'ring-2 ring-accent/40',
        isDragging && 'shadow-[var(--shadow-soft)] opacity-85',
      )}
      role="button"
      style={dragStyle}
      tabIndex={0}
      onClick={onSelect}
      onDoubleClick={() => {
        if (!draft.deleted) {
          onEdit()
        }
      }}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          onSelect()
        }
      }}
    >
      <div className="flex min-w-0 items-center gap-1.5">
        <span className={cn('h-2 w-2 shrink-0 rounded-full', draft.deleted ? 'bg-danger' : dotTone)} />
        <p className={cn(
          'min-w-0 flex-1 truncate font-semibold text-text-primary',
          isLane ? 'text-xs leading-4' : 'text-[13px] leading-4',
        )}>
          {display.subject}
        </p>
        {hasConflict ? (
          <CircleAlert className="h-3.5 w-3.5 shrink-0 text-danger" aria-label={t(`schedule.conflictStatus.${draft.conflict.status}`)} />
        ) : null}
        {showMutedConflictIndicator ? (
          <span
            className="h-2 w-2 shrink-0 rounded-full border border-text-muted/50"
            title={t(`schedule.conflictStatus.${draft.conflict.status}`)}
          />
        ) : null}
        {!draft.deleted ? (
          <button
            className="hidden h-6 w-6 shrink-0 items-center justify-center rounded-[6px] text-text-muted transition hover:bg-surface-muted hover:text-accent group-hover:inline-flex"
            title={t('common.actions.edit')}
            type="button"
            onClick={(event) => {
              event.stopPropagation()
              onEdit()
            }}
          >
            <Pencil className="h-3.5 w-3.5" />
            <span className="sr-only">{t('common.actions.edit')}</span>
          </button>
        ) : null}
        {!draft.deleted ? (
          <button
            type="button"
            aria-label={t('schedule.dragLesson')}
            className="hidden h-6 w-5 shrink-0 cursor-grab items-center justify-center rounded-[6px] text-text-muted transition hover:bg-surface-muted hover:text-accent group-hover:inline-flex active:cursor-grabbing touch-none"
            title={t('schedule.dragLesson')}
            ref={setActivatorNodeRef}
            onClick={(event) => event.stopPropagation()}
            {...attributes}
            {...(listeners ?? {})}
          >
            <GripVertical className="h-3.5 w-3.5" />
          </button>
        ) : null}
      </div>

      <p className={cn(
        'min-w-0 truncate text-text-secondary',
        isLane ? 'text-[11px] leading-4' : 'text-xs leading-4',
      )}>
        {display.meta}
      </p>

      {isLane ? (
        <div className="flex min-w-0 items-center gap-1">
          <p className="min-w-0 flex-1 truncate text-[11px] leading-4 text-text-secondary">{display.teacher}</p>
          <div className="flex shrink-0 items-center gap-1 overflow-hidden">
            {weekBadge}
            {draft.lessonFormat === 'ONLINE' ? (
              <MonitorUp className="h-3 w-3 shrink-0 text-accent" aria-label={t('schedule.lessonFormat.ONLINE')} />
            ) : null}
          </div>
        </div>
      ) : (
        <>
          <p className="min-w-0 truncate text-xs leading-4 text-text-secondary">{display.teacher}</p>
          <div className="mt-auto flex min-w-0 items-center gap-1 overflow-hidden">
            {weekBadge}
            {subgroupBadge}
            {draft.lessonFormat === 'ONLINE' ? (
              <MonitorUp className="h-3.5 w-3.5 shrink-0 text-accent" aria-label={t('schedule.lessonFormat.ONLINE')} />
            ) : null}
            {localChangeLabel ? (
              <span className="min-w-0 truncate rounded-full bg-accent-muted px-2 py-0.5 text-[10px] font-semibold leading-4 text-accent">
                {localChangeLabel}
              </span>
            ) : null}
            {copied ? (
              <span className="shrink-0 rounded-full border border-border px-2 py-0.5 text-[10px] font-semibold leading-4 text-text-secondary">
                {t('schedule.copiedState')}
              </span>
            ) : null}
            {moving ? (
              <span className="shrink-0 rounded-full border border-accent/40 bg-accent-muted/50 px-2 py-0.5 text-[10px] font-semibold leading-4 text-accent">
                {t('schedule.movingState')}
              </span>
            ) : null}
          </div>
        </>
      )}
    </div>
  )
}
