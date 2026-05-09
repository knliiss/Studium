export type DragMoveBlockReason =
  | 'ACTIVE_SEMESTER_MISSING'
  | 'NO_EDIT_PERMISSION'
  | 'NO_SOURCE'
  | 'SAME_POSITION'
  | 'SLOT_UNAVAILABLE'

export interface DragMoveTargetState {
  canDrop: boolean
  reason: DragMoveBlockReason | null
  reasonKey: string | null
  sourceDraftId: string | null
  valid: boolean
}

export interface LegacyDragMoveTargetState {
  canDrop: boolean
  reason: DragMoveBlockReason | null
  sourceDraftId: string | null
}

export interface MoveTargetSourceDraft {
  dayOfWeek: string
  deleted: boolean
  localId: string
  slotId: string
}

export function getScheduleMoveTargetState(params: {
  canEditTemplates: boolean
  hasActiveSemester: boolean
  sourceDraft: MoveTargetSourceDraft | null
  targetDay: string
  targetSlotId: string | null
}): DragMoveTargetState {
  const { canEditTemplates, hasActiveSemester, sourceDraft, targetDay, targetSlotId } = params

  if (!sourceDraft || sourceDraft.deleted) {
    return {
      canDrop: false,
      valid: false,
      reason: 'NO_SOURCE',
      reasonKey: getDragMoveBlockedReasonKey('NO_SOURCE'),
      sourceDraftId: null,
    }
  }
  if (!canEditTemplates) {
    return {
      canDrop: false,
      valid: false,
      reason: 'NO_EDIT_PERMISSION',
      reasonKey: getDragMoveBlockedReasonKey('NO_EDIT_PERMISSION'),
      sourceDraftId: sourceDraft.localId,
    }
  }
  if (!hasActiveSemester) {
    return {
      canDrop: false,
      valid: false,
      reason: 'ACTIVE_SEMESTER_MISSING',
      reasonKey: getDragMoveBlockedReasonKey('ACTIVE_SEMESTER_MISSING'),
      sourceDraftId: sourceDraft.localId,
    }
  }
  if (!targetSlotId) {
    return {
      canDrop: false,
      valid: false,
      reason: 'SLOT_UNAVAILABLE',
      reasonKey: getDragMoveBlockedReasonKey('SLOT_UNAVAILABLE'),
      sourceDraftId: sourceDraft.localId,
    }
  }
  if (sourceDraft.dayOfWeek === targetDay && sourceDraft.slotId === targetSlotId) {
    return {
      canDrop: false,
      valid: false,
      reason: 'SAME_POSITION',
      reasonKey: getDragMoveBlockedReasonKey('SAME_POSITION'),
      sourceDraftId: sourceDraft.localId,
    }
  }

  return {
    canDrop: true,
    valid: true,
    reason: null,
    reasonKey: null,
    sourceDraftId: sourceDraft.localId,
  }
}

export function getDragMoveBlockedReasonKey(reason: DragMoveBlockReason): string {
  switch (reason) {
    case 'SLOT_UNAVAILABLE':
      return 'schedule.cannotMoveSlotUnavailable'
    case 'NO_EDIT_PERMISSION':
      return 'schedule.cannotMoveNoEditPermission'
    case 'ACTIVE_SEMESTER_MISSING':
      return 'schedule.cannotMoveActiveSemesterMissing'
    case 'SAME_POSITION':
      return 'schedule.cannotMoveSamePosition'
    case 'NO_SOURCE':
      return 'schedule.cannotMoveNoDragSource'
    default:
      return 'schedule.moveCancelled'
  }
}

export function resolveDragMoveTargetState(params: {
  canEditTemplates: boolean
  hasActiveSemester: boolean
  sourceDraft: MoveTargetSourceDraft | null
  targetDay: string
  targetSlotId: string | null
}): LegacyDragMoveTargetState {
  const result = getScheduleMoveTargetState(params)
  return {
    canDrop: result.canDrop,
    reason: result.reason,
    sourceDraftId: result.sourceDraftId,
  }
}
