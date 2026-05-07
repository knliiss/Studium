import { describe, expect, it } from 'vitest'

import { resolveDragMoveTargetState } from './moveTarget'

const sourceDraft = {
  dayOfWeek: 'MONDAY',
  deleted: false,
  localId: 'draft-1',
  slotId: 'slot-1',
}

describe('resolveDragMoveTargetState', () => {
  it('returns valid state for compatible target', () => {
    const result = resolveDragMoveTargetState({
      canEditTemplates: true,
      hasActiveSemester: true,
      sourceDraft,
      targetDay: 'TUESDAY',
      targetSlotId: 'slot-2',
    })
    expect(result).toEqual({
      canDrop: true,
      reason: null,
      sourceDraftId: 'draft-1',
    })
  })

  it('blocks move when no edit permission', () => {
    const result = resolveDragMoveTargetState({
      canEditTemplates: false,
      hasActiveSemester: true,
      sourceDraft,
      targetDay: 'TUESDAY',
      targetSlotId: 'slot-2',
    })
    expect(result.reason).toBe('NO_EDIT_PERMISSION')
  })

  it('blocks move when active semester is missing', () => {
    const result = resolveDragMoveTargetState({
      canEditTemplates: true,
      hasActiveSemester: false,
      sourceDraft,
      targetDay: 'TUESDAY',
      targetSlotId: 'slot-2',
    })
    expect(result.reason).toBe('ACTIVE_SEMESTER_MISSING')
  })

  it('blocks move when slot is unavailable', () => {
    const result = resolveDragMoveTargetState({
      canEditTemplates: true,
      hasActiveSemester: true,
      sourceDraft,
      targetDay: 'TUESDAY',
      targetSlotId: null,
    })
    expect(result.reason).toBe('SLOT_UNAVAILABLE')
  })

  it('blocks move when target is the same position', () => {
    const result = resolveDragMoveTargetState({
      canEditTemplates: true,
      hasActiveSemester: true,
      sourceDraft,
      targetDay: 'MONDAY',
      targetSlotId: 'slot-1',
    })
    expect(result.reason).toBe('SAME_POSITION')
  })

  it('blocks move when source is missing', () => {
    const result = resolveDragMoveTargetState({
      canEditTemplates: true,
      hasActiveSemester: true,
      sourceDraft: null,
      targetDay: 'TUESDAY',
      targetSlotId: 'slot-2',
    })
    expect(result.reason).toBe('NO_SOURCE')
  })
})
