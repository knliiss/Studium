import { Monitor, Moon, Sun } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { useTheme } from '@/features/theme/useTheme'
import type { ThemePreference } from '@/features/theme/ThemeContext'
import { cn } from '@/shared/lib/cn'

const themeOptions: Array<{ value: ThemePreference; icon: typeof Sun }> = [
  { value: 'light', icon: Sun },
  { value: 'dark', icon: Moon },
  { value: 'system', icon: Monitor },
]

export function ThemeSwitcher() {
  const { preference, setPreference } = useTheme()
  const { t } = useTranslation()

  return (
    <div
      aria-label={t('theme.label')}
      className="inline-flex rounded-[14px] border border-border bg-surface p-1"
      role="group"
    >
      {themeOptions.map((option) => {
        const Icon = option.icon
        const active = preference === option.value

        return (
          <button
            key={option.value}
            aria-label={t(`theme.${option.value}`)}
            className={cn(
              'inline-flex min-h-9 min-w-9 items-center justify-center rounded-[10px] text-text-secondary transition hover:bg-surface-muted hover:text-text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/35',
              active ? 'bg-accent text-accent-foreground hover:bg-accent hover:text-accent-foreground' : '',
            )}
            title={t(`theme.${option.value}`)}
            type="button"
            onClick={() => setPreference(option.value)}
          >
            <Icon className="h-4 w-4" />
          </button>
        )
      })}
    </div>
  )
}
