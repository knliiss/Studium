import { describe, expect, it } from 'vitest'

import { isViewDayEmpty } from '@/pages/schedule/components/viewModeDayCard.helpers'

describe('isViewDayEmpty', () => {
  it('returns true for empty day', () => {
    expect(isViewDayEmpty([])).toBe(true)
  })

  it('returns false when at least one lesson exists', () => {
    expect(isViewDayEmpty([{ id: 'x' }] as never[])).toBe(false)
  })
})
