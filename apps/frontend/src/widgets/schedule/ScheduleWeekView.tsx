import { useQuery } from '@tanstack/react-query'
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'

import { educationService } from '@/shared/api/services'
import { getLessonFormatLabel, getLessonTypeLabel, getWeekTypeLabel } from '@/shared/lib/enum-labels'
import { formatDate } from '@/shared/lib/format'
import type { ResolvedLessonResponse, SubjectResponse } from '@/shared/types/api'
import { Card } from '@/shared/ui/Card'
import { EmptyState } from '@/shared/ui/StateViews'

export function ScheduleWeekView({ lessons }: { lessons: ResolvedLessonResponse[] }) {
  const { t } = useTranslation()
  const subjectIds = useMemo(
    () => Array.from(new Set(lessons.map((lesson) => lesson.subjectId).filter(Boolean))),
    [lessons],
  )
  const subjectsQuery = useQuery({
    queryKey: ['schedule-week-view', 'subjects', subjectIds.join(',')],
    queryFn: () => loadSubjectsById(subjectIds),
    enabled: subjectIds.length > 0,
  })
  const subjectNameById = useMemo(
    () => new Map((subjectsQuery.data ?? []).map((subject) => [subject.id, subject.name])),
    [subjectsQuery.data],
  )

  if (lessons.length === 0) {
    return <EmptyState description={t('schedule.noLessonsYet')} title={t('navigation.shared.schedule')} />
  }

  return (
    <div className="grid gap-4 lg:grid-cols-2 xl:grid-cols-3">
      {lessons.map((lesson) => (
        <Card key={`${lesson.date}-${lesson.slotId}`} className="space-y-3">
          <div className="flex items-start justify-between gap-3">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">
                {formatDate(lesson.date)}
              </p>
              <p className="mt-2 text-lg font-semibold text-text-primary">{getLessonTypeLabel(lesson.lessonType)}</p>
            </div>
            <span className="rounded-full bg-accent-muted px-2.5 py-1 text-xs font-semibold text-accent">
              {getWeekTypeLabel(lesson.weekType)}
            </span>
          </div>
          <div className="space-y-1 text-sm text-text-secondary">
            <p>{getLessonFormatLabel(lesson.lessonFormat)}</p>
            <p>{subjectNameById.get(lesson.subjectId) ?? t('education.subject')}</p>
            <p>{lesson.onlineMeetingUrl ? t('schedule.lessonFormat.ONLINE') : t('schedule.roomAssigned')}</p>
          </div>
        </Card>
      ))}
    </div>
  )
}

async function loadSubjectsById(subjectIds: string[]) {
  const subjects = await Promise.all(subjectIds.map(async (subjectId) => {
    try {
      return await educationService.getSubject(subjectId)
    } catch {
      return null
    }
  }))

  return subjects.filter((subject): subject is SubjectResponse => Boolean(subject))
}
