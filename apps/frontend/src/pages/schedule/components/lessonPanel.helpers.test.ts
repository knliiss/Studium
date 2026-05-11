import { describe, expect, it } from 'vitest'

import { resolveLessonPanelMode } from '@/pages/schedule/components/lessonPanel.helpers'

describe('resolveLessonPanelMode', () => {
  it('opens view mode when a lesson is selected', () => {
    expect(resolveLessonPanelMode({
      editorLocalId: null,
      hasEditor: false,
      hasSelectedLesson: true,
      movingSelectedLesson: false,
    })).toBe('view')
  })

  it('changes to edit mode when the edit action opens an editor for a lesson', () => {
    expect(resolveLessonPanelMode({
      editorLocalId: 'lesson-1',
      hasEditor: true,
      hasSelectedLesson: true,
      movingSelectedLesson: false,
    })).toBe('edit')
  })

  it('changes to create mode when an empty slot add target opens an editor', () => {
    expect(resolveLessonPanelMode({
      editorLocalId: null,
      hasEditor: true,
      hasSelectedLesson: false,
      movingSelectedLesson: false,
    })).toBe('create')
  })

  it('shows move mode while the selected lesson is being moved', () => {
    expect(resolveLessonPanelMode({
      editorLocalId: null,
      hasEditor: false,
      hasSelectedLesson: true,
      movingSelectedLesson: true,
    })).toBe('move')
  })
})
