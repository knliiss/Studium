import type { PropsWithChildren } from 'react'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { AuthContext, type AuthContextValue } from '@/features/auth/AuthContext'
import { authApi } from '@/shared/api/auth'
import { registerSessionHandler, registerUnauthorizedHandler } from '@/shared/api/client'
import { getPrimaryRole, normalizeRoles } from '@/shared/lib/roles'
import { readStoredSession, writeStoredSession } from '@/shared/lib/storage'
import type {
  AuthResponse,
  AuthSession,
  AuthUser,
  MfaChallengeResponse,
} from '@/shared/types/api'

function normalizeAuthUser(user: AuthUser): AuthUser {
  return {
    ...user,
    roles: normalizeRoles(user.roles),
  }
}

function normalizeAuthSession(session: AuthSession | null) {
  if (!session) {
    return null
  }

  return {
    ...session,
    user: normalizeAuthUser(session.user),
  }
}

export function AuthProvider({ children }: PropsWithChildren) {
  const navigate = useNavigate()
  const [session, setSession] = useState<AuthSession | null>(() => normalizeAuthSession(readStoredSession()))
  const [mfaChallenge, setMfaChallenge] = useState<MfaChallengeResponse | null>(null)

  useEffect(() => {
    registerUnauthorizedHandler((reason) => {
      setSession(null)
      setMfaChallenge(null)
      navigate('/login', { state: reason ? { reason } : undefined })
    })
  }, [navigate])

  useEffect(() => {
    registerSessionHandler((nextSession) => {
      setSession(normalizeAuthSession(nextSession))
      if (!nextSession) {
        setMfaChallenge(null)
      }
    })
  }, [])

  const value = useMemo<AuthContextValue>(() => {
    const applyAuthResponse = (response: AuthResponse) => {
      if (response.status === 'MFA_REQUIRED' && response.mfaChallenge) {
        setMfaChallenge(response.mfaChallenge)
        setSession(null)
        writeStoredSession(null)
        return
      }

      if (response.status === 'AUTHENTICATED' && response.accessToken && response.user) {
        const nextSession = normalizeAuthSession({
          accessToken: response.accessToken,
          refreshToken: response.refreshToken,
          user: response.user,
        })

        if (!nextSession) {
          return
        }

        setSession(nextSession)
        setMfaChallenge(null)
        writeStoredSession(nextSession)
      }
    }

    return {
      session,
      mfaChallenge,
      isAuthenticated: Boolean(session?.accessToken),
      roles: session?.user.roles ?? [],
      primaryRole: getPrimaryRole(session?.user.roles ?? []),
      login: async (payload) => {
        const response = await authApi.login(payload)
        applyAuthResponse(response)
        return response
      },
      register: async (payload) => {
        const response = await authApi.register(payload)
        applyAuthResponse(response)
        return response
      },
      acceptAuthResponse: applyAuthResponse,
      logout: async () => {
        const refreshToken = session?.refreshToken
        setSession(null)
        setMfaChallenge(null)
        writeStoredSession(null)
        if (refreshToken) {
          await authApi.logout(refreshToken)
        }
      },
    }
  }, [mfaChallenge, session])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
