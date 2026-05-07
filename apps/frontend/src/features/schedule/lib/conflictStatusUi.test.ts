import { describe, expect, it } from 'vitest'

import { getConflictStatusClasses, getConflictStatusTone } from './conflictStatusUi'

describe('conflict status UI mapping', () => {
  it('maps idle and checking to neutral', () => {
    expect(getConflictStatusTone('idle')).toBe('neutral')
    expect(getConflictStatusTone('checking')).toBe('neutral')
  })

  it('maps clear to success and not danger', () => {
    expect(getConflictStatusTone('clear')).toBe('success')
    expect(getConflictStatusClasses('clear')).toContain('success')
    expect(getConflictStatusClasses('clear')).not.toContain('danger')
  })

  it('maps conflict to danger', () => {
    expect(getConflictStatusTone('conflict')).toBe('danger')
    expect(getConflictStatusClasses('conflict')).toContain('danger')
  })

  it('maps error to warning', () => {
    expect(getConflictStatusTone('error')).toBe('warning')
    expect(getConflictStatusClasses('error')).toContain('warning')
  })
})
