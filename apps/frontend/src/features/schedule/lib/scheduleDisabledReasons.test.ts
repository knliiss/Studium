import { describe, expect, it } from 'vitest'

import {
  getScheduleDraftValidationReasonKey,
  getScheduleSaveDisabledReasonKey,
} from './scheduleDisabledReasons'

describe('schedule disabled reason helpers', () => {
  it('returns expected draft validation keys', () => {
    expect(getScheduleDraftValidationReasonKey({
      hasActiveSemester: true,
      hasLessonContext: true,
      hasRoomForOffline: true,
      hasSlot: true,
      hasSubject: false,
      hasTeacher: true,
    })).toBe('schedule.disabledReasons.chooseSubject')

    expect(getScheduleDraftValidationReasonKey({
      hasActiveSemester: true,
      hasLessonContext: true,
      hasRoomForOffline: true,
      hasSlot: true,
      hasSubject: true,
      hasTeacher: false,
    })).toBe('schedule.disabledReasons.chooseTeacher')

    expect(getScheduleDraftValidationReasonKey({
      hasActiveSemester: true,
      hasLessonContext: true,
      hasRoomForOffline: false,
      hasSlot: true,
      hasSubject: true,
      hasTeacher: true,
    })).toBe('schedule.disabledReasons.chooseRoomOffline')

    expect(getScheduleDraftValidationReasonKey({
      hasActiveSemester: false,
      hasLessonContext: true,
      hasRoomForOffline: true,
      hasSlot: true,
      hasSubject: true,
      hasTeacher: true,
    })).toBe('schedule.disabledReasons.activeSemesterNotConfigured')

    expect(getScheduleDraftValidationReasonKey({
      hasActiveSemester: true,
      hasLessonContext: true,
      hasRoomForOffline: true,
      hasSlot: false,
      hasSubject: true,
      hasTeacher: true,
    })).toBe('schedule.disabledReasons.slotNotResolved')
  })

  it('returns expected save disabled keys', () => {
    expect(getScheduleSaveDisabledReasonKey({
      dirtyDraftCount: 3,
      draftValidationReasonKey: '',
      draftsNeedingConflictCheckCount: 1,
      pendingConflictCount: 0,
      failedConflictCount: 0,
    })).toBe('schedule.disabledReasons.checkConflicts')

    expect(getScheduleSaveDisabledReasonKey({
      dirtyDraftCount: 3,
      draftValidationReasonKey: '',
      draftsNeedingConflictCheckCount: 0,
      pendingConflictCount: 1,
      failedConflictCount: 0,
    })).toBe('schedule.disabledReasons.conflictPending')

    expect(getScheduleSaveDisabledReasonKey({
      dirtyDraftCount: 3,
      draftValidationReasonKey: '',
      draftsNeedingConflictCheckCount: 0,
      pendingConflictCount: 0,
      failedConflictCount: 1,
    })).toBe('schedule.disabledReasons.conflictExists')

    expect(getScheduleSaveDisabledReasonKey({
      dirtyDraftCount: 0,
      draftValidationReasonKey: '',
      draftsNeedingConflictCheckCount: 0,
      pendingConflictCount: 0,
      failedConflictCount: 0,
    })).toBe('schedule.disabledReasons.noChanges')
  })
})
