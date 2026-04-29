import type { PropsWithChildren } from 'react'
import { Navigate, Outlet } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import { hasAnyRole } from '@/shared/lib/roles'
import type { Role } from '@/shared/types/api'

export function RequireAuth({ children }: PropsWithChildren) {
  const { isAuthenticated, mfaChallenge } = useAuth()

  if (mfaChallenge) {
    return <Navigate replace to="/mfa" />
  }

  if (!isAuthenticated) {
    return <Navigate replace to="/login" />
  }

  return children ? <>{children}</> : <Outlet />
}

export function RequireRole({ allowedRoles }: { allowedRoles: Role[] }) {
  const { roles } = useAuth()

  if (!hasAnyRole(roles, allowedRoles)) {
    return <Navigate replace to="/access-denied" />
  }

  return <Outlet />
}
