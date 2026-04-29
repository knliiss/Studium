import type { LocaleCode } from '@/shared/i18n/config'
import { normalizeRoles } from '@/shared/lib/roles'
import type { AuthSession } from '@/shared/types/api'

const keys = {
  authSession: 'studium.auth.session',
  locale: 'studium.locale',
} as const

export function readStoredSession(): AuthSession | null {
  const rawValue = globalThis.localStorage?.getItem(keys.authSession)
  if (!rawValue) {
    return null
  }

  try {
    const session = JSON.parse(rawValue) as AuthSession

    return {
      ...session,
      user: {
        ...session.user,
        roles: normalizeRoles(session.user.roles),
      },
    }
  } catch {
    return null
  }
}

export function writeStoredSession(session: AuthSession | null) {
  if (!globalThis.localStorage) {
    return
  }

  if (!session) {
    globalThis.localStorage.removeItem(keys.authSession)
    return
  }

  globalThis.localStorage.setItem(
    keys.authSession,
    JSON.stringify({
      ...session,
      user: {
        ...session.user,
        roles: normalizeRoles(session.user.roles),
      },
    }),
  )
}

export function readStoredLocale(): LocaleCode | null {
  const locale = globalThis.localStorage?.getItem(keys.locale)
  return locale === 'en' || locale === 'uk' || locale === 'pl' ? locale : null
}

export function writeStoredLocale(locale: LocaleCode) {
  globalThis.localStorage?.setItem(keys.locale, locale)
}
