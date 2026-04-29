import { i18n } from '@/shared/i18n/config'

function translate(key: string, fallback: string) {
  const value = i18n.t(key)
  return value === key ? fallback : value
}

export function getLessonTypeLabel(value: string | null | undefined) {
  if (!value) {
    return '-'
  }

  return translate(`schedule.lessonType.${value}`, value)
}

export function getLessonFormatLabel(value: string | null | undefined) {
  if (!value) {
    return '-'
  }

  return translate(`schedule.lessonFormat.${value}`, value)
}

export function getWeekTypeLabel(value: string | null | undefined) {
  if (!value) {
    return '-'
  }

  return translate(`schedule.weekType.${value}`, value)
}

export function getStatusLabel(value: string | null | undefined) {
  if (!value) {
    return '-'
  }

  return translate(`common.status.${value}`, value)
}

export function getRiskLabel(value: string | null | undefined) {
  if (!value) {
    return '-'
  }

  return translate(`analytics.riskLevel.${value}`, value)
}

export function getDayOfWeekLabel(value: string | null | undefined) {
  if (!value) {
    return '-'
  }

  return translate(`schedule.dayOfWeekValues.${value}`, value)
}

export function getNotificationTypeLabel(value: string | null | undefined) {
  if (!value) {
    return '-'
  }

  return translate(`notifications.types.${value}`, value)
}

export function getSearchResultTypeLabel(value: string | null | undefined) {
  if (!value) {
    return '-'
  }

  return translate(`search.types.${value}`, value)
}

export function getAuditActionLabel(value: string | null | undefined) {
  if (!value) {
    return '-'
  }

  return translate(`audit.actions.${value}`, value)
}

export function getAuditEntityTypeLabel(value: string | null | undefined) {
  if (!value) {
    return '-'
  }

  return translate(`audit.entityTypes.${value}`, value)
}

export function getDashboardDeadlineTypeLabel(value: string | null | undefined) {
  if (!value) {
    return '-'
  }

  return translate(`dashboard.deadlineType.${value}`, value)
}
