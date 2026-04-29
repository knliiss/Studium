import { useEffect, useMemo, useState } from 'react'
import type { PropsWithChildren } from 'react'

import { ThemeContext } from '@/features/theme/ThemeContext'
import type { ThemePreference } from '@/features/theme/ThemeContext'

const themeStorageKey = 'studium.theme'
const darkQuery = '(prefers-color-scheme: dark)'

function readStoredTheme(): ThemePreference {
  const storedTheme = globalThis.localStorage?.getItem(themeStorageKey)
  if (storedTheme === 'light' || storedTheme === 'dark' || storedTheme === 'system') {
    return storedTheme
  }

  return 'system'
}

function resolveTheme(preference: ThemePreference) {
  if (preference !== 'system') {
    return preference
  }

  return globalThis.matchMedia?.(darkQuery).matches ? 'dark' : 'light'
}

function applyTheme(preference: ThemePreference) {
  const resolvedTheme = resolveTheme(preference)
  document.documentElement.dataset.theme = resolvedTheme
  document.documentElement.style.colorScheme = resolvedTheme
}

export function ThemeProvider({ children }: PropsWithChildren) {
  const [preference, setPreference] = useState<ThemePreference>(() => readStoredTheme())

  useEffect(() => {
    globalThis.localStorage?.setItem(themeStorageKey, preference)
    applyTheme(preference)
  }, [preference])

  useEffect(() => {
    const mediaQuery = globalThis.matchMedia?.(darkQuery)
    if (!mediaQuery) {
      return undefined
    }

    const handleChange = () => applyTheme(readStoredTheme())
    mediaQuery.addEventListener('change', handleChange)
    return () => mediaQuery.removeEventListener('change', handleChange)
  }, [])

  const value = useMemo(() => ({ preference, setPreference }), [preference])

  return (
    <ThemeContext.Provider value={value}>
      {children}
    </ThemeContext.Provider>
  )
}
