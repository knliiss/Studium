import {
  analyticsService,
  assignmentService,
  educationService,
  scheduleService,
  testingService,
} from '@/shared/api/services'
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

export interface SubjectCardMetrics {
  activeAssignmentCount: number
  activeTestCount: number
  topicCount: number
}

export interface GroupCardSummary {
  hasUpcomingLessons: boolean | null
  riskLevel: string | null
  studentCount: number | null
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
    const subjects = await loadManagedSubjects()
    return subjects.filter((subject) => subject.teacherIds.includes(userId))
  }

  return [] as SubjectResponse[]
}

export async function loadManagedSubjects() {
  const subjects: SubjectResponse[] = []
  let page = 0

  while (true) {
    const response = await educationService.listSubjects({
      page,
      size: 100,
      sortBy: 'name',
      direction: 'asc',
    })

    subjects.push(...response.items)

    if (response.last || page + 1 >= response.totalPages) {
      break
    }

    page += 1
  }

  return dedupeById(subjects)
}

export async function loadSubjectScope(role: Role, userId: string, includeManaged: boolean) {
  if (includeManaged) {
    return loadManagedSubjects()
  }

  return loadAccessibleSubjects(role, userId)
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

export async function loadSubjectCardMetrics(subjectIds: string[]) {
  const metrics = await Promise.all(subjectIds.map(async (subjectId) => {
    const topicsPage = await educationService.getTopicsBySubject(subjectId, {
      page: 0,
      size: 100,
      sortBy: 'orderIndex',
      direction: 'asc',
    })
    const topics = topicsPage.items

    const [assignmentPages, testPages] = await Promise.all([
      Promise.all(
        topics.map((topic) =>
          assignmentService.getAssignmentsByTopic(topic.id, {
            page: 0,
            size: 100,
            sortBy: 'orderIndex',
            direction: 'asc',
          }),
        ),
      ),
      Promise.all(
        topics.map((topic) =>
          testingService.getTestsByTopic(topic.id, {
            page: 0,
            size: 100,
            sortBy: 'orderIndex',
            direction: 'asc',
          }),
        ),
      ),
    ])

    return [
      subjectId,
      {
        activeAssignmentCount: assignmentPages
          .flatMap((page) => page.items)
          .filter((assignment) => assignment.status === 'PUBLISHED').length,
        activeTestCount: testPages
          .flatMap((page) => page.items)
          .filter((test) => test.status === 'PUBLISHED').length,
        topicCount: topics.length,
      } satisfies SubjectCardMetrics,
    ] as const
  }))

  return new Map(metrics)
}

export async function loadGroupCardSummaries(groupIds: string[]) {
  const range = buildPlanningRange(21)
  const summaries = await Promise.all(groupIds.map(async (groupId) => {
    const [membersResult, scheduleResult, analyticsResult] = await Promise.allSettled([
      educationService.listGroupStudents(groupId),
      scheduleService.getGroupRange(groupId, range.dateFrom, range.dateTo),
      analyticsService.getGroupOverview(groupId),
    ])

    const studentCount = membersResult.status === 'fulfilled'
      ? membersResult.value.length
      : analyticsResult.status === 'fulfilled'
        ? analyticsResult.value.totalStudentsTracked
        : null
    const hasUpcomingLessons = scheduleResult.status === 'fulfilled'
      ? scheduleResult.value.length > 0
      : null
    const riskLevel = analyticsResult.status === 'fulfilled'
      ? resolveGroupRiskLevel(analyticsResult.value)
      : null

    return [
      groupId,
      {
        hasUpcomingLessons,
        riskLevel,
        studentCount,
      } satisfies GroupCardSummary,
    ] as const
  }))

  return new Map(summaries)
}

export function buildTeacherDirectoryStats(subjects: SubjectResponse[]) {
  const stats = new Map<string, { groupIds: Set<string>; subjectCount: number }>()

  for (const subject of subjects) {
    for (const teacherId of subject.teacherIds) {
      const current = stats.get(teacherId) ?? { groupIds: new Set<string>(), subjectCount: 0 }

      current.subjectCount += 1
      for (const groupId of subject.groupIds) {
        current.groupIds.add(groupId)
      }

      stats.set(teacherId, current)
    }
  }

  return new Map(
    Array.from(stats.entries()).map(([teacherId, value]) => [
      teacherId,
      { groupCount: value.groupIds.size, subjectCount: value.subjectCount },
    ]),
  )
}

function resolveGroupRiskLevel(value: {
  highRiskStudentsCount: number
  lowRiskStudentsCount: number
  mediumRiskStudentsCount: number
  totalStudentsTracked: number
}) {
  if (value.highRiskStudentsCount > 0) {
    return 'HIGH'
  }

  if (value.mediumRiskStudentsCount > 0) {
    return 'MEDIUM'
  }

  if (value.lowRiskStudentsCount > 0 || value.totalStudentsTracked > 0) {
    return 'LOW'
  }

  return null
}
