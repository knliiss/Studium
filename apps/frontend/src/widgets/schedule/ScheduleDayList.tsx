import { useMemo } from 'react'

import { formatDate } from '@/shared/lib/format'
import type { ResolvedLessonResponse } from '@/shared/types/api'
import { Card } from '@/shared/ui/Card'
import { ScheduleWeekView } from '@/widgets/schedule/ScheduleWeekView'

export function ScheduleDayList({ lessons }: { lessons: ResolvedLessonResponse[] }) {
  const byDate = useMemo(() => {
    const groups = new Map<string, ResolvedLessonResponse[]>()
    lessons.forEach((lesson) => {
      const items = groups.get(lesson.date) ?? []
      items.push(lesson)
      groups.set(lesson.date, items)
    })
    return Array.from(groups.entries())
  }, [lessons])

  return (
    <div className="space-y-4">
      {byDate.map(([date, items]) => (
        <Card key={date} className="space-y-4">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-text-muted">
              {formatDate(date)}
            </p>
          </div>
          <ScheduleWeekView lessons={items} />
        </Card>
      ))}
    </div>
  )
}
