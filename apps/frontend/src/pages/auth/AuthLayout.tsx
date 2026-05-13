import type { PropsWithChildren } from 'react'
import { useTranslation } from 'react-i18next'

import { LanguageSwitcher } from '@/shared/ui/LanguageSwitcher'

export function AuthLayout({ children }: PropsWithChildren) {
  const { t } = useTranslation()

  return (
    <div className="min-h-screen bg-background px-4 py-5 lg:px-8 lg:py-8">
      <div className="mx-auto flex min-h-[calc(100vh-2.5rem)] max-w-6xl flex-col rounded-[24px] border border-border bg-surface shadow-[var(--shadow-soft)]">
        <header className="flex items-center justify-between border-b border-border px-5 py-4 lg:px-7">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.24em] text-accent">
              {t('app.name')}
            </p>
            <p className="mt-1 text-sm text-text-secondary">{t('app.tagline')}</p>
          </div>
          <LanguageSwitcher />
        </header>
        <main className="flex flex-1 items-center justify-center px-4 py-6 lg:px-8 lg:py-8">
          <div className="w-full max-w-5xl space-y-6">
            <div className="mx-auto w-full max-w-md">
              {children}
            </div>
            <section className="hidden gap-3 lg:grid lg:grid-cols-3">
              <div className="space-y-2 rounded-[16px] border border-border bg-surface-muted p-4">
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-accent">
                  {t('auth.layout.badge')}
                </p>
                <p className="text-sm leading-6 text-text-secondary">
                  {t('auth.layout.description')}
                </p>
              </div>
              <div className="grid gap-2 rounded-[16px] border border-border bg-surface-muted p-4">
                <p className="text-sm font-semibold text-text-primary">
                  {t('auth.layout.highlights.schedule.title')}
                </p>
                <p className="text-sm leading-6 text-text-secondary">
                  {t('auth.layout.highlights.schedule.description')}
                </p>
              </div>
              <div className="grid gap-2 rounded-[16px] border border-border bg-surface-muted p-4">
                <p className="text-sm font-semibold text-text-primary">
                  {t('auth.layout.highlights.assignments.title')}
                </p>
                <p className="text-sm leading-6 text-text-secondary">
                  {t('auth.layout.highlights.assignments.description')}
                </p>
              </div>
            </section>
          </div>
        </main>
      </div>
    </div>
  )
}
