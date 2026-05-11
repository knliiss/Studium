import type { ResolvedLessonResponse } from '@/shared/types/api'

export function isViewDayEmpty(lessons: ResolvedLessonResponse[]) {
  return lessons.length === 0
}
