import { describe, expect, it } from 'vitest'

import { moveScheduleDraft } from './scheduleMoveDraft'

describe('moveScheduleDraft', () => {
  it('moves draft to new day and slot while preserving lesson fields', () => {
    const draft = {
      changeReason: 'UPDATE_TEMPLATE',
      conflict: { items: [{ type: 'x' }], lastCheckedHash: 'abc', messages: ['m'], status: 'conflict' as const },
      dayOfWeek: 'MONDAY',
      lessonFormat: 'OFFLINE',
      lessonType: 'LECTURE',
      notes: 'Intro',
      onlineMeetingUrl: null,
      roomId: 'room-1',
      slotId: 'slot-1',
      subgroup: 'ALL',
      subjectId: 'subject-1',
      teacherId: 'teacher-1',
      templateId: 'template-1',
      weekType: 'ODD',
    }

    const moved = moveScheduleDraft({
      createIdleConflict: () => ({ items: [], lastCheckedHash: null, messages: [], status: 'idle' }),
      draft,
      targetDay: 'TUESDAY',
      targetSlotId: 'slot-3',
    })

    expect(moved.dayOfWeek).toBe('TUESDAY')
    expect(moved.slotId).toBe('slot-3')
    expect(moved.changeReason).toBe('MOVE_TEMPLATE')
    expect(moved.conflict.status).toBe('idle')
    expect(moved.subjectId).toBe('subject-1')
    expect(moved.teacherId).toBe('teacher-1')
    expect(moved.lessonType).toBe('LECTURE')
    expect(moved.lessonFormat).toBe('OFFLINE')
    expect(moved.subgroup).toBe('ALL')
    expect(moved.weekType).toBe('ODD')
  })
})
