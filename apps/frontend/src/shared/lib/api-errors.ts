import { isAxiosError } from 'axios'
import type { TFunction } from 'i18next'

import type { ApiErrorResponse, NormalizedApiError } from '@/shared/types/api'

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function readString(value: unknown) {
  return typeof value === 'string' && value.trim() ? value : undefined
}

function normalizeFieldErrors(details: unknown) {
  if (!isRecord(details) || !isRecord(details.fieldErrors)) {
    return undefined
  }

  const fieldErrors: Record<string, string[]> = {}

  Object.entries(details.fieldErrors).forEach(([field, messages]) => {
    if (Array.isArray(messages)) {
      const normalizedMessages = messages.filter(
        (message): message is string => typeof message === 'string' && Boolean(message.trim()),
      )

      if (normalizedMessages.length > 0) {
        fieldErrors[field] = normalizedMessages
      }
      return
    }

    if (typeof messages === 'string' && messages.trim()) {
      fieldErrors[field] = [messages]
    }
  })

  return Object.keys(fieldErrors).length > 0 ? fieldErrors : undefined
}

export function normalizeApiErrorPayload(payload: unknown): NormalizedApiError | null {
  if (!isRecord(payload) || typeof payload.status !== 'number') {
    return null
  }

  const code = readString(payload.errorCode) ?? readString(payload.code)
  const message = readString(payload.message) ?? readString(payload.error)

  if (!code && !message) {
    return null
  }

  return {
    status: payload.status,
    code: code ?? (payload.status >= 500 ? 'INTERNAL_ERROR' : 'UNKNOWN_ERROR'),
    message,
    requestId: readString(payload.requestId),
    details: payload.details,
    fieldErrors: normalizeFieldErrors(payload.details),
  }
}

export function normalizeApiError(error: unknown): NormalizedApiError | null {
  if (isAxiosError(error)) {
    return normalizeApiErrorPayload(error.response?.data)
      ?? (typeof error.response?.status === 'number'
        ? {
            status: error.response.status,
            code: error.response.status === 429
              ? 'TOO_MANY_REQUESTS'
              : error.response.status >= 500
                ? 'INTERNAL_ERROR'
                : 'UNKNOWN_ERROR',
          }
        : null)
  }

  return normalizeApiErrorPayload(error)
}

export function isApiError(value: unknown): value is ApiErrorResponse {
  return normalizeApiErrorPayload(value) !== null
}

export function getLocalizedApiErrorMessage(
  error: NormalizedApiError | null,
  t: TFunction,
  fallbackKey = 'errors:fallback',
) {
  if (!error) {
    return t(fallbackKey)
  }

  const translationKey = `errors:${error.code}`
  const translatedMessage = t(translationKey, { defaultValue: '' })

  if (translatedMessage && translatedMessage !== translationKey) {
    return translatedMessage
  }

  return error.message ?? t(fallbackKey)
}

export function isNetworkError(error: unknown) {
  return isAxiosError(error) && !error.response
}

export function getLocalizedRequestErrorMessage(
  error: unknown,
  t: TFunction,
  fallbackKey = 'errors:fallback',
) {
  if (isNetworkError(error)) {
    return t('errors:NETWORK_ERROR')
  }

  return getLocalizedApiErrorMessage(normalizeApiError(error), t, fallbackKey)
}

export function getApiErrorField(error: NormalizedApiError | null) {
  if (!error || !isRecord(error.details)) {
    return null
  }

  return typeof error.details.field === 'string' ? error.details.field : null
}

export function buildApiFieldErrors(error: NormalizedApiError | null, localizedMessage: string) {
  const nextFieldErrors: Record<string, string> = {}

  Object.entries(error?.fieldErrors ?? {}).forEach(([field, messages]) => {
    if (messages[0]) {
      nextFieldErrors[field] = messages[0]
    }
  })

  const field = getApiErrorField(error)
  if (field && !nextFieldErrors[field]) {
    nextFieldErrors[field] = localizedMessage
  }

  return nextFieldErrors
}

export function hasApiFieldErrors(error: NormalizedApiError | null) {
  return Object.keys(error?.fieldErrors ?? {}).length > 0 || Boolean(getApiErrorField(error))
}

export function isAccessDeniedApiError(error: unknown) {
  const normalizedError = normalizeApiError(error)
  return normalizedError?.status === 403 || normalizedError?.code === 'ACCESS_DENIED'
}
