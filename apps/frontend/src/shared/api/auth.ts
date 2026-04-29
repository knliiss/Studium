import type { AxiosRequestConfig } from 'axios'

import { apiClient } from '@/shared/api/client'
import type {
  AcceptedActionResponse,
  AuthResponse,
  MfaChallengeResponse,
} from '@/shared/types/api'

const skipAuthRefreshConfig: AxiosRequestConfig & { skipAuthRefresh: true } = {
  skipAuthRefresh: true,
}

export interface LoginPayload {
  username: string
  password: string
}

export interface RegisterPayload {
  username: string
  email: string
  password: string
}

export interface RequestPasswordResetPayload {
  email: string
}

export interface ConfirmPasswordResetPayload {
  resetToken: string
  newPassword: string
}

export interface MfaDispatchPayload {
  challengeToken: string
  method: string
}

export interface MfaVerifyPayload {
  challengeToken: string
  method: string
  code: string
}

export const authApi = {
  async login(payload: LoginPayload) {
    const response = await apiClient.post<AuthResponse>('/api/auth/login', payload, skipAuthRefreshConfig)
    return response.data
  },
  async register(payload: RegisterPayload) {
    const response = await apiClient.post<AuthResponse>('/api/auth/register', payload, skipAuthRefreshConfig)
    return response.data
  },
  async requestPasswordReset(payload: RequestPasswordResetPayload) {
    const response = await apiClient.post<AcceptedActionResponse>(
      '/api/auth/password-reset/request',
      payload,
      skipAuthRefreshConfig,
    )
    return response.data
  },
  async confirmPasswordReset(payload: ConfirmPasswordResetPayload) {
    await apiClient.post('/api/auth/password-reset/confirm', payload, skipAuthRefreshConfig)
  },
  async logout(refreshToken: string) {
    await apiClient.post('/api/auth/logout', { refreshToken }, skipAuthRefreshConfig)
  },
  async dispatchMfa(payload: MfaDispatchPayload) {
    const response = await apiClient.post<MfaChallengeResponse>(
      '/api/auth/mfa/challenges/dispatch',
      payload,
      skipAuthRefreshConfig,
    )
    return response.data
  },
  async verifyMfa(payload: MfaVerifyPayload) {
    const response = await apiClient.post<AuthResponse>('/api/auth/mfa/challenges/verify', payload, skipAuthRefreshConfig)
    return response.data
  },
}
