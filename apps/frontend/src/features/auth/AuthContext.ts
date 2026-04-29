import { createContext } from 'react'

import type { LoginPayload, RegisterPayload } from '@/shared/api/auth'
import type {
  AuthResponse,
  AuthSession,
  MfaChallengeResponse,
  Role,
} from '@/shared/types/api'

export interface AuthContextValue {
  session: AuthSession | null
  mfaChallenge: MfaChallengeResponse | null
  isAuthenticated: boolean
  roles: Role[]
  primaryRole: Role
  login: (payload: LoginPayload) => Promise<AuthResponse>
  register: (payload: RegisterPayload) => Promise<AuthResponse>
  acceptAuthResponse: (response: AuthResponse) => void
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)
