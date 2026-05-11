export type LessonPanelMode = 'empty' | 'view' | 'edit' | 'create' | 'move'

interface LessonPanelModeInput {
  editorLocalId: string | null
  hasEditor: boolean
  hasSelectedLesson: boolean
  movingSelectedLesson: boolean
}

export function resolveLessonPanelMode({
  editorLocalId,
  hasEditor,
  hasSelectedLesson,
  movingSelectedLesson,
}: LessonPanelModeInput): LessonPanelMode {
  if (hasEditor) {
    return editorLocalId ? 'edit' : 'create'
  }

  if (hasSelectedLesson && movingSelectedLesson) {
    return 'move'
  }

  if (hasSelectedLesson) {
    return 'view'
  }

  return 'empty'
}
