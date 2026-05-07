import type { TFunction } from 'i18next'

import type { AssignmentGroupAvailabilityResponse, AssignmentResponse } from '@/shared/types/api'

export type AvailabilityStatus = 'HIDDEN' | 'OPENS_LATER' | 'OPEN' | 'CLOSED' | 'DEADLINE_PASSED'

export interface AvailabilityMutationPayload {
  [key: string]: unknown
  groupId: string
  visible: boolean
  availableFrom: string | null
  deadline: string
  allowLateSubmissions: boolean
  maxSubmissions: number
  allowResubmit: boolean
}

export interface AvailabilityFormState {
  groupId: string
  visible: boolean
  availableFrom: string
  deadline: string
  allowLateSubmissions: boolean
  maxSubmissions: number
  allowResubmit: boolean
}

export interface BulkAvailabilityFormState {
  visible: boolean
  availableFrom: string
  deadline: string
  allowLateSubmissions: boolean
  maxSubmissions: number
  allowResubmit: boolean
  copyFromGroupId: string
}

export function toDateTimeLocal(value: string | null | undefined) {
  return value ? value.slice(0, 16) : ''
}

export function createAvailabilityFormState(
  groupId: string,
  assignment: AssignmentResponse,
  availability: AssignmentGroupAvailabilityResponse | null,
): AvailabilityFormState {
  return {
    groupId,
    visible: availability?.visible ?? false,
    availableFrom: toDateTimeLocal(availability?.availableFrom),
    deadline: toDateTimeLocal(availability?.deadline ?? assignment.deadline),
    allowLateSubmissions: availability?.allowLateSubmissions ?? assignment.allowLateSubmissions,
    maxSubmissions: availability?.maxSubmissions ?? assignment.maxSubmissions,
    allowResubmit: availability?.allowResubmit ?? assignment.allowResubmit,
  }
}

export function createAvailabilityPayload(form: AvailabilityFormState): AvailabilityMutationPayload {
  return {
    groupId: form.groupId,
    visible: form.visible,
    availableFrom: form.availableFrom ? new Date(form.availableFrom).toISOString() : null,
    deadline: new Date(form.deadline).toISOString(),
    allowLateSubmissions: form.allowLateSubmissions,
    maxSubmissions: Math.max(1, form.maxSubmissions || 1),
    allowResubmit: form.allowResubmit,
  }
}

export function createBulkAvailabilityPayload(
  groupId: string,
  form: BulkAvailabilityFormState,
  fallbackDeadlineIso: string,
  visibilityOverride?: boolean,
): AvailabilityMutationPayload {
  return {
    groupId,
    visible: visibilityOverride ?? form.visible,
    availableFrom: form.availableFrom ? new Date(form.availableFrom).toISOString() : null,
    deadline: new Date(form.deadline || fallbackDeadlineIso).toISOString(),
    allowLateSubmissions: form.allowLateSubmissions,
    maxSubmissions: Math.max(1, form.maxSubmissions || 1),
    allowResubmit: form.allowResubmit,
  }
}

export function resolveAvailabilityStatus(
  assignmentStatus: AssignmentResponse['status'],
  availability: AssignmentGroupAvailabilityResponse | null,
  now = Date.now(),
): AvailabilityStatus {
  if (!availability || !availability.visible) {
    return 'HIDDEN'
  }
  if (assignmentStatus !== 'PUBLISHED') {
    return 'CLOSED'
  }
  if (availability.availableFrom && new Date(availability.availableFrom).getTime() > now) {
    return 'OPENS_LATER'
  }
  if (new Date(availability.deadline).getTime() <= now) {
    return 'DEADLINE_PASSED'
  }
  return 'OPEN'
}

export function getAvailabilityStatusLabel(status: AvailabilityStatus, t: TFunction) {
  switch (status) {
    case 'HIDDEN':
      return t('availability.hidden')
    case 'OPENS_LATER':
      return t('availability.opensLater')
    case 'OPEN':
      return t('availability.open')
    case 'CLOSED':
      return t('availability.closed')
    case 'DEADLINE_PASSED':
      return t('availability.deadlinePassed')
    default:
      return t('availability.hidden')
  }
}
