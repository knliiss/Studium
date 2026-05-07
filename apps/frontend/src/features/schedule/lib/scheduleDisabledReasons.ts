export interface ScheduleDraftValidationInput {
  hasActiveSemester: boolean
  hasLessonContext: boolean
  hasRoomForOffline: boolean
  hasSlot: boolean
  hasSubject: boolean
  hasTeacher: boolean
}

export function getScheduleDraftValidationReasonKey(input: ScheduleDraftValidationInput) {
  if (!input.hasActiveSemester) {
    return 'schedule.disabledReasons.activeSemesterNotConfigured'
  }
  if (!input.hasLessonContext) {
    return 'schedule.disabledReasons.checkConflicts'
  }
  if (!input.hasSlot) {
    return 'schedule.disabledReasons.slotNotResolved'
  }
  if (!input.hasSubject) {
    return 'schedule.disabledReasons.chooseSubject'
  }
  if (!input.hasTeacher) {
    return 'schedule.disabledReasons.chooseTeacher'
  }
  if (!input.hasRoomForOffline) {
    return 'schedule.disabledReasons.chooseRoomOffline'
  }
  return ''
}

export function getScheduleSaveDisabledReasonKey(params: {
  dirtyDraftCount: number
  draftValidationReasonKey: string
  draftsNeedingConflictCheckCount: number
  pendingConflictCount: number
  failedConflictCount: number
}) {
  const {
    dirtyDraftCount,
    draftValidationReasonKey,
    draftsNeedingConflictCheckCount,
    pendingConflictCount,
    failedConflictCount,
  } = params
  if (dirtyDraftCount === 0) {
    return 'schedule.disabledReasons.noChanges'
  }
  if (draftValidationReasonKey) {
    return draftValidationReasonKey
  }
  if (draftsNeedingConflictCheckCount > 0) {
    return 'schedule.disabledReasons.checkConflicts'
  }
  if (pendingConflictCount > 0) {
    return 'schedule.disabledReasons.conflictPending'
  }
  if (failedConflictCount > 0) {
    return 'schedule.disabledReasons.conflictExists'
  }
  return ''
}
