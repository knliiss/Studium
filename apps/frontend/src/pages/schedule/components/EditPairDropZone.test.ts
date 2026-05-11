import { describe, expect, it } from 'vitest'

import { resolveWeekDraftSections } from '@/pages/schedule/components/editPairDropZone.helpers'

describe('resolveWeekDraftSections', () => {
  it('treats ALL subgroup lesson as full-group card candidate', () => {
    const state = resolveWeekDraftSections([
      { deleted: false, subgroup: 'ALL', weekType: 'ODD' },
    ], 'ODD')

    expect(state.sharedDrafts).toHaveLength(1)
    expect(state.slotIsEmpty).toBe(false)
  })

  it('keeps FIRST and SECOND lanes in same selected week section', () => {
    const state = resolveWeekDraftSections([
      { deleted: false, subgroup: 'FIRST', weekType: 'ODD' },
      { deleted: false, subgroup: 'SECOND', weekType: 'ODD' },
    ], 'ODD')

    expect(state.firstDrafts).toHaveLength(1)
    expect(state.secondDrafts).toHaveLength(1)
    expect(state.slotIsEmpty).toBe(false)
  })

  it('reports missing sibling subgroup lane as addable', () => {
    const state = resolveWeekDraftSections([
      { deleted: false, subgroup: 'FIRST', weekType: 'ODD' },
    ], 'ODD')

    expect(state.showSecondAdd).toBe(true)
    expect(state.showFirstAdd).toBe(false)
  })
})
