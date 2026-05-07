export type DragMoveBlockReason =
  | 'ACTIVE_SEMESTER_MISSING'
  | 'NO_EDIT_PERMISSION'
  | 'NO_SOURCE'
  | 'SAME_POSITION'
  | 'SLOT_UNAVAILABLE'

export interface DragMoveTargetState {
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

export function resolveDragMoveTargetState(params: {
  canEditTemplates: boolean
  hasActiveSemester: boolean
  sourceDraft: MoveTargetSourceDraft | null
  targetDay: string
  targetSlotId: string | null
}): DragMoveTargetState {
  const { canEditTemplates, hasActiveSemester, sourceDraft, targetDay, targetSlotId } = params

  if (!sourceDraft || sourceDraft.deleted) {
    return { canDrop: false, reason: 'NO_SOURCE', sourceDraftId: null }
  }
  if (!canEditTemplates) {
    return { canDrop: false, reason: 'NO_EDIT_PERMISSION', sourceDraftId: sourceDraft.localId }
  }
  if (!hasActiveSemester) {
    return { canDrop: false, reason: 'ACTIVE_SEMESTER_MISSING', sourceDraftId: sourceDraft.localId }
  }
  if (!targetSlotId) {
    return { canDrop: false, reason: 'SLOT_UNAVAILABLE', sourceDraftId: sourceDraft.localId }
  }
  if (sourceDraft.dayOfWeek === targetDay && sourceDraft.slotId === targetSlotId) {
    return { canDrop: false, reason: 'SAME_POSITION', sourceDraftId: sourceDraft.localId }
  }

  return { canDrop: true, reason: null, sourceDraftId: sourceDraft.localId }
}
