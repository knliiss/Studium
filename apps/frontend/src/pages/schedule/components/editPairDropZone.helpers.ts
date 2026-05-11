import type { SubgroupValue } from '@/shared/types/api'

export type EditableWeekType = 'ALL' | 'ODD' | 'EVEN'

export interface DraftSlotLessonLike {
  weekType: EditableWeekType
  subgroup: SubgroupValue
  deleted: boolean
}

export function resolveWeekDraftSections<TDraft extends DraftSlotLessonLike>(
  pairDrafts: TDraft[],
  selectedWeekType: 'ODD' | 'EVEN',
) {
  const selectedWeekDrafts = pairDrafts.filter((draft) => draft.weekType === selectedWeekType || draft.weekType === 'ALL')
  const sharedDrafts = selectedWeekDrafts.filter((draft) => draft.subgroup === 'ALL')
  const firstDrafts = selectedWeekDrafts.filter((draft) => draft.subgroup === 'FIRST')
  const secondDrafts = selectedWeekDrafts.filter((draft) => draft.subgroup === 'SECOND')
  const hasSharedActive = sharedDrafts.some((draft) => !draft.deleted)
  const hasFirstActive = firstDrafts.some((draft) => !draft.deleted)
  const hasSecondActive = secondDrafts.some((draft) => !draft.deleted)

  return {
    firstDrafts,
    hasFirstActive,
    hasSecondActive,
    hasSharedActive,
    hasAnySubgroupDrafts: firstDrafts.length > 0 || secondDrafts.length > 0,
    secondDrafts,
    selectedWeekDrafts,
    sharedDrafts,
    showFirstAdd: !hasSharedActive && !hasFirstActive && hasSecondActive,
    showSecondAdd: !hasSharedActive && hasFirstActive && !hasSecondActive,
    slotIsEmpty: !hasSharedActive && !hasFirstActive && !hasSecondActive,
  }
}
