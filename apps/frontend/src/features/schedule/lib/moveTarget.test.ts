import { describe, expect, it } from 'vitest'

import { getScheduleMoveTargetState } from './moveTarget'

const sourceDraft = {
  dayOfWeek: 'MONDAY',
  deleted: false,
  localId: 'draft-1',
  slotId: 'slot-1',
}

describe('getScheduleMoveTargetState', () => {
  it('returns valid state for compatible target', () => {
    const result = getScheduleMoveTargetState({
      canEditTemplates: true,
      hasActiveSemester: true,
      sourceDraft,
      targetDay: 'TUESDAY',
      targetSlotId: 'slot-2',
    })
    expect(result).toEqual({
      canDrop: true,
      reasonKey: null,
      reason: null,
      sourceDraftId: 'draft-1',
      valid: true,
    })
  })

  it('blocks move when no edit permission', () => {
    const result = getScheduleMoveTargetState({
      canEditTemplates: false,
      hasActiveSemester: true,
      sourceDraft,
      targetDay: 'TUESDAY',
      targetSlotId: 'slot-2',
    })
    expect(result.valid).toBe(false)
    expect(result.reason).toBe('NO_EDIT_PERMISSION')
    expect(result.reasonKey).toBe('schedule.cannotMoveNoEditPermission')
  })

  it('blocks move when active semester is missing', () => {
    const result = getScheduleMoveTargetState({
      canEditTemplates: true,
      hasActiveSemester: false,
      sourceDraft,
      targetDay: 'TUESDAY',
      targetSlotId: 'slot-2',
    })
    expect(result.reason).toBe('ACTIVE_SEMESTER_MISSING')
    expect(result.reasonKey).toBe('schedule.cannotMoveActiveSemesterMissing')
  })

  it('blocks move when slot is unavailable', () => {
    const result = getScheduleMoveTargetState({
      canEditTemplates: true,
      hasActiveSemester: true,
      sourceDraft,
      targetDay: 'TUESDAY',
      targetSlotId: null,
    })
    expect(result.reason).toBe('SLOT_UNAVAILABLE')
    expect(result.reasonKey).toBe('schedule.cannotMoveSlotUnavailable')
  })

  it('blocks move when target is the same position', () => {
    const result = getScheduleMoveTargetState({
      canEditTemplates: true,
      hasActiveSemester: true,
      sourceDraft,
      targetDay: 'MONDAY',
      targetSlotId: 'slot-1',
    })
    expect(result.reason).toBe('SAME_POSITION')
    expect(result.reasonKey).toBe('schedule.cannotMoveSamePosition')
  })

  it('blocks move when source is missing', () => {
    const result = getScheduleMoveTargetState({
      canEditTemplates: true,
      hasActiveSemester: true,
      sourceDraft: null,
      targetDay: 'TUESDAY',
      targetSlotId: 'slot-2',
    })
    expect(result.reason).toBe('NO_SOURCE')
    expect(result.reasonKey).toBe('schedule.cannotMoveNoDragSource')
  })
})
