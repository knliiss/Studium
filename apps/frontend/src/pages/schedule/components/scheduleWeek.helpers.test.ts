import { describe, expect, it } from 'vitest'

import {
  clampDateToSemesterWeek,
  generateSemesterWeeks,
  getWeekNavigationState,
  getWeekTabWindow,
  getWeekTypeForWeekNumber,
} from '@/pages/schedule/components/scheduleWeek.helpers'

const semester = {
  endDate: '2026-05-31',
  startDate: '2026-02-02',
  weekOneStartDate: '2026-02-02',
}

describe('semester week helpers', () => {
  it('starts semester numbering from Week 1', () => {
    const weeks = generateSemesterWeeks(semester)

    expect(weeks[0]).toMatchObject({
      endDate: '2026-02-08',
      startDate: '2026-02-02',
      weekNumber: 1,
      weekType: 'ODD',
    })
  })

  it('keeps absolute numbering in later week tab windows', () => {
    const weeks = generateSemesterWeeks(semester)

    expect(getWeekTabWindow(weeks, 7).map((week) => week.weekNumber)).toEqual([4, 5, 6, 7, 8, 9])
  })

  it('disables previous on the first week and next on the last week', () => {
    const weeks = generateSemesterWeeks(semester)

    expect(getWeekNavigationState(weeks, 1)).toMatchObject({
      isFirstWeek: true,
      isLastWeek: false,
    })
    expect(getWeekNavigationState(weeks, weeks[weeks.length - 1].weekNumber)).toMatchObject({
      isFirstWeek: false,
      isLastWeek: true,
    })
  })

  it('clamps dates outside the semester to the nearest available week', () => {
    const weeks = generateSemesterWeeks(semester)

    expect(clampDateToSemesterWeek(weeks, '2026-01-10')?.weekNumber).toBe(1)
    expect(clampDateToSemesterWeek(weeks, '2026-07-10')?.weekNumber).toBe(weeks[weeks.length - 1].weekNumber)
  })

  it('resolves odd and even week types from absolute week numbers', () => {
    expect(getWeekTypeForWeekNumber(1)).toBe('ODD')
    expect(getWeekTypeForWeekNumber(2)).toBe('EVEN')
  })
})
