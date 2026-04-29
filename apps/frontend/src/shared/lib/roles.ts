import type { Role } from '@/shared/types/api'

const rolePriority: Role[] = ['OWNER', 'ADMIN', 'TEACHER', 'STUDENT', 'USER']

export function normalizeRole(role: string): Role | null {
  const normalizedRole = role.trim().toUpperCase().replace(/^ROLE_/, '')

  return rolePriority.includes(normalizedRole as Role) ? (normalizedRole as Role) : null
}

export function normalizeRoles(roles: readonly string[]) {
  const normalizedRoles = new Set<Role>()

  roles.forEach((role) => {
    const normalizedRole = normalizeRole(role)
    if (normalizedRole) {
      normalizedRoles.add(normalizedRole)
    }
  })

  return rolePriority.filter((role) => normalizedRoles.has(role))
}

export function getPrimaryRole(roles: readonly string[]) {
  return normalizeRoles(roles)[0] ?? 'USER'
}

export function hasAnyRole(userRoles: readonly string[], allowedRoles: readonly string[]) {
  const normalizedUserRoles = new Set(normalizeRoles(userRoles))

  return normalizeRoles(allowedRoles).some((role) => normalizedUserRoles.has(role))
}

export function getDashboardPath(roles: readonly string[]) {
  const primaryRole = getPrimaryRole(roles)

  if (primaryRole === 'OWNER' || primaryRole === 'ADMIN' || primaryRole === 'TEACHER' || primaryRole === 'STUDENT') {
    return '/dashboard'
  }

  return '/profile'
}
