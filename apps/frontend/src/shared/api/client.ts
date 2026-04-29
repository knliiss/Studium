import axios from 'axios'
import type { AxiosRequestConfig } from 'axios'

import { env } from '@/shared/config/env'
import { isApiError } from '@/shared/lib/api-errors'
import { readStoredSession, writeStoredSession } from '@/shared/lib/storage'
import type { AuthResponse, AuthSession } from '@/shared/types/api'

type UnauthorizedReason = 'session-expired' | 'unauthorized'

interface AuthRetryConfig extends AxiosRequestConfig {
  _retry?: boolean
  skipAuthRefresh?: boolean
}

let unauthorizedHandler: ((reason?: UnauthorizedReason) => void) | null = null
let sessionHandler: ((session: AuthSession | null) => void) | null = null
let refreshPromise: Promise<AuthResponse | null> | null = null

export const apiClient = axios.create({
  baseURL: env.apiBaseUrl,
  headers: {
    Accept: 'application/json',
  },
})

function isAuthEndpoint(url?: string) {
  return Boolean(url?.includes('/api/auth/'))
}

function applyStoredSession(session: AuthSession | null) {
  writeStoredSession(session)
  sessionHandler?.(session)
}

async function refreshSession() {
  const session = readStoredSession()
  if (!session?.refreshToken) {
    return null
  }

  if (!refreshPromise) {
    refreshPromise = axios
      .post<AuthResponse>(
        `${env.apiBaseUrl}/api/auth/refresh`,
        { refreshToken: session.refreshToken },
        { headers: { Accept: 'application/json' } },
      )
      .then((response) => response.data)
      .catch(() => null)
      .finally(() => {
        refreshPromise = null
      })
  }

  return refreshPromise
}

apiClient.interceptors.request.use((config) => {
  const session = readStoredSession()
  if (session?.accessToken) {
    config.headers.Authorization = `Bearer ${session.accessToken}`
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const config = error.config as AuthRetryConfig | undefined

    if (!config || config.skipAuthRefresh) {
      return Promise.reject(error)
    }

    if (error.response?.status !== 401) {
      return Promise.reject(error)
    }

    if (config._retry || isAuthEndpoint(config.url)) {
      applyStoredSession(null)
      unauthorizedHandler?.('unauthorized')
      return Promise.reject(error)
    }

    const refreshed = await refreshSession()
    if (!refreshed || refreshed.status !== 'AUTHENTICATED' || !refreshed.accessToken) {
      applyStoredSession(null)
      unauthorizedHandler?.('session-expired')
      return Promise.reject(error)
    }

    const currentSession = readStoredSession()
    const user = refreshed.user ?? currentSession?.user
    if (!user) {
      applyStoredSession(null)
      unauthorizedHandler?.('session-expired')
      return Promise.reject(error)
    }

    applyStoredSession({
      accessToken: refreshed.accessToken,
      refreshToken: refreshed.refreshToken ?? currentSession?.refreshToken ?? null,
      user,
    })

    config._retry = true
    config.headers = {
      ...config.headers,
      Authorization: `Bearer ${refreshed.accessToken}`,
    }

    return apiClient.request(config)
  },
)

export function registerUnauthorizedHandler(handler: (reason?: UnauthorizedReason) => void) {
  unauthorizedHandler = handler
}

export function registerSessionHandler(handler: (session: AuthSession | null) => void) {
  sessionHandler = handler
}

export { isApiError }
