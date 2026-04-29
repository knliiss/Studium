import { i18n } from '@/shared/i18n/config'

const dateFormats: Record<string, Intl.DateTimeFormatOptions> = {
  date: { day: 'numeric', month: 'short', year: 'numeric' },
  time: { hour: '2-digit', minute: '2-digit' },
  dateTime: {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  },
}

export function formatDate(value?: string | null) {
  return formatIntl(value, dateFormats.date)
}

export function formatTime(value?: string | null) {
  return formatIntl(value, dateFormats.time)
}

export function formatDateTime(value?: string | null) {
  return formatIntl(value, dateFormats.dateTime)
}

function formatIntl(value: string | null | undefined, options: Intl.DateTimeFormatOptions) {
  if (!value) {
    return '—'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat(i18n.resolvedLanguage || 'en', options).format(date)
}
