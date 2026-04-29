import { educationService, scheduleService } from '@/shared/api/services'
import type { GroupResponse, Role, SubjectResponse } from '@/shared/types/api'

export function buildPlanningRange(daysAhead = 35) {
  const today = new Date()
  const until = new Date(today)
  until.setDate(today.getDate() + daysAhead)

  return {
    dateFrom: today.toISOString().slice(0, 10),
    dateTo: until.toISOString().slice(0, 10),
  }
}

export function dedupeById<T extends { id: string }>(items: T[]) {
  return Array.from(new Map(items.map((item) => [item.id, item])).values())
}

export async function loadAccessibleSubjects(role: Role, userId: string) {
  if (role === 'STUDENT') {
    const memberships = await educationService.getGroupsByUser(userId)
    const pages = await Promise.all(
      memberships.map((membership) =>
        educationService.getSubjectsByGroup(membership.groupId, { page: 0, size: 100 }),
      ),
    )

    return dedupeById(pages.flatMap((page) => page.items))
  }

  if (role === 'TEACHER') {
    const range = buildPlanningRange()
    const lessons = await scheduleService.getMyRange(range.dateFrom, range.dateTo)
    const subjectIds = Array.from(new Set(lessons.map((lesson) => lesson.subjectId)))
    const subjects = await Promise.all(subjectIds.map((id) => educationService.getSubject(id)))

    return dedupeById(subjects)
  }

  return [] as SubjectResponse[]
}

export async function loadAccessibleGroups(role: Role, userId: string) {
  if (role === 'STUDENT') {
    const memberships = await educationService.getGroupsByUser(userId)
    const groups = await Promise.all(memberships.map((membership) => educationService.getGroup(membership.groupId)))
    return dedupeById(groups)
  }

  if (role === 'TEACHER') {
    const range = buildPlanningRange()
    const lessons = await scheduleService.getMyRange(range.dateFrom, range.dateTo)
    const groupIds = Array.from(new Set(lessons.map((lesson) => lesson.groupId)))
    const groups = await Promise.all(groupIds.map((id) => educationService.getGroup(id)))

    return dedupeById(groups)
  }

  return [] as GroupResponse[]
}
