interface ConflictLike {
  items: unknown[]
  lastCheckedHash: string | null
  messages: string[]
  status: 'idle' | 'checking' | 'clear' | 'conflict' | 'error'
}

export interface ScheduleMoveDraftShape {
  changeReason: string | null
  conflict: ConflictLike
  dayOfWeek: string
  slotId: string
  templateId: string | null
}

export function moveScheduleDraft<TDraft extends ScheduleMoveDraftShape>(params: {
  createIdleConflict: () => ConflictLike
  draft: TDraft
  targetDay: string
  targetSlotId: string
}): TDraft {
  const { createIdleConflict, draft, targetDay, targetSlotId } = params
  return {
    ...draft,
    changeReason: draft.templateId ? 'MOVE_TEMPLATE' : draft.changeReason ?? 'CREATE_TEMPLATE',
    conflict: createIdleConflict(),
    dayOfWeek: targetDay,
    slotId: targetSlotId,
  }
}
