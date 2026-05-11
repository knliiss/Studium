import { MonitorUp } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { getLessonFormatLabel, getLessonTypeLabel } from '@/shared/lib/enum-labels'
import { formatDate } from '@/shared/lib/format'
import type {
  AdminUserResponse,
  LessonSlotResponse,
  ResolvedLessonResponse,
  RoomResponse,
  UserSummaryResponse,
} from '@/shared/types/api'
import { Button } from '@/shared/ui/Button'
import { isViewDayEmpty } from '@/pages/schedule/components/viewModeDayCard.helpers'

type TeacherUser = AdminUserResponse | UserSummaryResponse

interface ViewModeDayCardProps {
  currentUserId: string
  date: string
  lessons: ResolvedLessonResponse[]
  onCancelLesson: (lesson: ResolvedLessonResponse) => void
  roomById: Map<string, RoomResponse>
  showCancelAction: boolean
  slotById: Map<string, LessonSlotResponse>
  subjectNameById: Map<string, string>
  teacherById: Map<string, TeacherUser>
  dayLabel: string
  formatShortTime: (value: string) => string
  formatRoomLabel: (room: RoomResponse) => string
  getTeacherDisplayName: (teacher: TeacherUser | null | undefined) => string
}

export function ViewModeDayCard({
  currentUserId,
  date,
  lessons,
  onCancelLesson,
  roomById,
  showCancelAction,
  slotById,
  subjectNameById,
  teacherById,
  dayLabel,
  formatShortTime,
  formatRoomLabel,
  getTeacherDisplayName,
}: ViewModeDayCardProps) {
  const { t } = useTranslation()

  return (
    <div className="min-w-[min(86vw,360px)] max-w-[380px] flex-1 snap-start rounded-[12px] border border-border bg-surface-muted p-3 md:min-w-[340px] xl:min-w-[360px]">
      <div className="mb-3 space-y-1">
        <p className="text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">{dayLabel}</p>
        <h3 className="text-base font-semibold text-text-primary">{formatDate(date)}</h3>
      </div>

      {isViewDayEmpty(lessons) ? (
        <div className="rounded-[8px] border border-dashed border-border-strong bg-surface px-3 py-5 text-sm text-text-secondary">
          {t('schedule.noLessonsYet')}
        </div>
      ) : (
        <div className="space-y-2">
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
              <div key={`${lesson.date}-${lesson.slotId}-${lesson.subjectId}-${lesson.teacherId}`} className="rounded-[10px] border border-border bg-surface p-3">
                <div className="mb-2 flex flex-wrap items-start justify-between gap-2 border-b border-border pb-2">
                  <div className="space-y-1">
                    <p className="text-xs font-semibold text-text-primary">{pairLabel}</p>
                    <p className="text-xs font-semibold uppercase tracking-[0.14em] text-text-muted">
                      {getLessonTypeLabel(lesson.lessonType)}
                    </p>
                  </div>
                  {canCancel ? (
                    <Button className="min-h-8 rounded-[8px] px-2 text-xs" variant="ghost" onClick={() => onCancelLesson(lesson)}>
                      {t('schedule.cancelOccurrence')}
                    </Button>
                  ) : null}
                </div>
                <div className="space-y-1.5">
                  <p className="truncate text-sm font-semibold text-text-primary">
                    {subjectNameById.get(lesson.subjectId) ?? t('education.subject')}
                  </p>
                  <p className="truncate text-xs text-text-secondary">{getTeacherDisplayName(teacher)}</p>
                  <p className="truncate text-xs text-text-secondary">
                    {getLessonFormatLabel(lesson.lessonFormat)}
                    {' · '}
                    {location}
                  </p>
                  <div className="flex flex-wrap gap-1.5">
                    <span className="rounded-full border border-border px-2 py-0.5 text-[10px] font-semibold text-text-secondary">
                      {t(`education.subgroups.${lesson.subgroup}`)}
                    </span>
                    <span className="rounded-full border border-border px-2 py-0.5 text-[10px] font-semibold text-text-secondary">
                      {t(`schedule.weekType.${lesson.weekType}`)}
                    </span>
                    {lesson.overrideType ? (
                      <span className="rounded-full border border-warning/30 bg-warning/10 px-2 py-0.5 text-[10px] font-semibold text-warning">
                        {t(`schedule.overrideTypes.${lesson.overrideType}`)}
                      </span>
                    ) : null}
                  </div>
                  {lesson.lessonFormat === 'ONLINE' && lesson.onlineMeetingUrl ? (
                    <a
                      className="inline-flex min-h-8 items-center rounded-[8px] border border-border bg-surface-muted px-2.5 text-xs font-medium text-accent transition hover:border-accent/40"
                      href={lesson.onlineMeetingUrl}
                      rel="noreferrer"
                      target="_blank"
                    >
                      <MonitorUp className="mr-1.5 h-3.5 w-3.5" />
                      {t('schedule.joinLesson')}
                    </a>
                  ) : null}
                  {lesson.notes ? <p className="line-clamp-2 text-xs text-text-secondary">{lesson.notes}</p> : null}
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
