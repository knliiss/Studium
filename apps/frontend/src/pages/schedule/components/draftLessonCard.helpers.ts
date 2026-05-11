import type { SubgroupValue } from '@/shared/types/api'

export function getDraftBadgeKeys(draft: { subgroup: SubgroupValue; weekType: 'ALL' | 'ODD' | 'EVEN' }) {
  return {
    subgroupKey: `education.subgroups.${draft.subgroup}`,
    weekKey: `schedule.weekType.${draft.weekType}`,
  }
}

export function getCompactDraftLessonDisplay({
  lessonType,
  location,
  subject,
  teacher,
}: {
  lessonType: string
  location: string
  subject: string
  teacher: string
}) {
  return {
    meta: `${lessonType} · ${location}`,
    subject,
    teacher,
  }
}
