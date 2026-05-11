export type ScheduleWeekType = 'ODD' | 'EVEN'

export interface SemesterWeekSource {
  startDate: string
  endDate: string
  weekOneStartDate?: string | null
}

export interface SemesterWeek {
  key: string
  weekNumber: number
  startDate: string
  endDate: string
  weekType: ScheduleWeekType
}

export function generateSemesterWeeks(semester: SemesterWeekSource | null | undefined) {
  if (!semester?.startDate || !semester.endDate) {
    return [] as SemesterWeek[]
  }

  const firstWeekStart = startOfWeek(parseIsoDate(semester.weekOneStartDate || semester.startDate))
  const semesterEnd = parseIsoDate(semester.endDate)
  if (Number.isNaN(firstWeekStart.getTime()) || Number.isNaN(semesterEnd.getTime())) {
    return [] as SemesterWeek[]
  }

  const weeks: SemesterWeek[] = []
  let weekStart = firstWeekStart
  let weekNumber = 1

  while (weekStart.getTime() <= semesterEnd.getTime()) {
    const weekEnd = addDays(weekStart, 6)
    weeks.push({
      endDate: toIsoDate(weekEnd),
      key: String(weekNumber),
      startDate: toIsoDate(weekStart),
      weekNumber,
      weekType: getWeekTypeForWeekNumber(weekNumber),
    })
    weekStart = addDays(weekStart, 7)
    weekNumber += 1
  }

  return weeks
}

export function clampDateToSemesterWeek(weeks: SemesterWeek[], dateIso: string) {
  if (weeks.length === 0) {
    return null
  }

  if (dateIso <= weeks[0].startDate) {
    return weeks[0]
  }

  const lastWeek = weeks[weeks.length - 1]
  if (dateIso >= lastWeek.endDate) {
    return lastWeek
  }

  return weeks.find((week) => dateIso >= week.startDate && dateIso <= week.endDate) ?? lastWeek
}

export function findSemesterWeek(weeks: SemesterWeek[], weekNumber: number) {
  return weeks.find((week) => week.weekNumber === weekNumber) ?? null
}

export function getWeekTabWindow(weeks: SemesterWeek[], selectedWeekNumber: number, visibleCount = 6) {
  if (weeks.length <= visibleCount) {
    return weeks
  }

  const selectedIndex = Math.max(weeks.findIndex((week) => week.weekNumber === selectedWeekNumber), 0)
  const windowSize = Math.min(visibleCount, weeks.length)
  const preferredStart = selectedIndex - Math.floor(windowSize / 2)
  const startIndex = Math.min(Math.max(preferredStart, 0), weeks.length - windowSize)

  return weeks.slice(startIndex, startIndex + windowSize)
}

export function getWeekNavigationState(weeks: SemesterWeek[], selectedWeekNumber: number) {
  const selectedIndex = weeks.findIndex((week) => week.weekNumber === selectedWeekNumber)
  const safeIndex = selectedIndex >= 0 ? selectedIndex : 0

  return {
    isFirstWeek: safeIndex <= 0,
    isLastWeek: safeIndex >= weeks.length - 1,
    nextWeek: safeIndex < weeks.length - 1 ? weeks[safeIndex + 1] : null,
    previousWeek: safeIndex > 0 ? weeks[safeIndex - 1] : null,
  }
}

export function getWeekTypeForWeekNumber(weekNumber: number): ScheduleWeekType {
  return weekNumber % 2 === 1 ? 'ODD' : 'EVEN'
}

export function buildWeekDays(weekStart: Date | string) {
  const start = typeof weekStart === 'string' ? parseIsoDate(weekStart) : weekStart
  return Array.from({ length: 7 }, (_, index) => toIsoDate(addDays(start, index)))
}

export function startOfWeek(date: Date) {
  const value = new Date(date)
  const day = value.getDay()
  const offset = day === 0 ? -6 : 1 - day
  value.setDate(value.getDate() + offset)
  value.setHours(0, 0, 0, 0)
  return value
}

export function addDays(date: Date, amount: number) {
  const value = new Date(date)
  value.setDate(value.getDate() + amount)
  return value
}

export function toIsoDate(value: Date) {
  const year = value.getFullYear()
  const month = `${value.getMonth() + 1}`.padStart(2, '0')
  const day = `${value.getDate()}`.padStart(2, '0')
  return `${year}-${month}-${day}`
}

function parseIsoDate(value: string) {
  return new Date(`${value}T00:00:00`)
}
