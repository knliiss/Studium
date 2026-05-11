import { describe, expect, it } from 'vitest'

import {
  getCompactDraftLessonDisplay,
  getDraftBadgeKeys,
} from '@/pages/schedule/components/draftLessonCard.helpers'

const draftBase = {
  changeReason: null,
  conflict: { items: [], messages: [], status: 'idle' as const },
  dayOfWeek: 'MONDAY',
  deleted: false,
  groupId: 'g1',
  lessonFormat: 'OFFLINE' as const,
  lessonType: 'LECTURE' as const,
  localId: 'l1',
  notes: '',
  onlineMeetingUrl: null,
  roomId: null,
  slotId: 's1',
  subjectId: 'sub1',
  teacherId: 't1',
}

describe('getDraftBadgeKeys', () => {
  it('maps subgroup and week keys', () => {
    const keys = getDraftBadgeKeys({
      ...draftBase,
      subgroup: 'FIRST',
      weekType: 'ODD',
    })

    expect(keys.subgroupKey).toBe('education.subgroups.FIRST')
    expect(keys.weekKey).toBe('schedule.weekType.ODD')
  })
})

describe('getCompactDraftLessonDisplay', () => {
  it('accepts long subject and teacher values for truncated card rendering', () => {
    const display = getCompactDraftLessonDisplay({
      lessonType: 'Laboratory',
      location: 'Main building · 401',
      subject: 'Advanced Distributed Systems Architecture With Extremely Long Seminar Name',
      teacher: 'Professor Oleksandr Very Long Surname With Multiple Parts',
    })

    expect(display.subject).toContain('Advanced Distributed Systems')
    expect(display.teacher).toContain('Professor Oleksandr')
    expect(display.meta).toBe('Laboratory · Main building · 401')
  })
})
