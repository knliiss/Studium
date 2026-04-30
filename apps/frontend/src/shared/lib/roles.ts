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

export function rank(roles: readonly string[]) {
  const normalized = new Set(normalizeRoles(roles))
  if (normalized.has('OWNER')) return 3
  if (normalized.has('ADMIN')) return 2
  return 1
}

export function canManage(actorRoles: readonly string[], targetRoles: readonly string[]) {
  return rank(actorRoles) > rank(targetRoles)
}

export function isRoleAssignable(actorRoles: readonly string[], targetRoles: readonly string[], candidateRole: Role) {
  if (candidateRole === 'OWNER') return false

  if (!canManage(actorRoles, targetRoles)) return false

  if (candidateRole === 'ADMIN' && !normalizeRoles(actorRoles).includes('OWNER' as Role)) return false

  const actorRank = rank(actorRoles)
  const candidateRank = candidateRole === 'ADMIN' ? 2 : 1
  return actorRank > candidateRank
}

export function getDashboardPath(roles: readonly string[]) {
  const primaryRole = getPrimaryRole(roles)

  if (primaryRole === 'OWNER' || primaryRole === 'ADMIN' || primaryRole === 'TEACHER' || primaryRole === 'STUDENT') {
    return '/dashboard'
  }

  return '/profile'
}
