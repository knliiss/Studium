import type { PropsWithChildren } from 'react'
import { useTranslation } from 'react-i18next'

import { LanguageSwitcher } from '@/shared/ui/LanguageSwitcher'

export function AuthLayout({ children }: PropsWithChildren) {
  const { t } = useTranslation()

  return (
    <div className="min-h-screen bg-background px-4 py-6 lg:px-8 lg:py-8">
      <div className="mx-auto flex min-h-[calc(100vh-3rem)] max-w-7xl flex-col justify-between rounded-[28px] border border-border bg-surface shadow-[var(--shadow-soft)]">
        <header className="flex items-center justify-between border-b border-border px-6 py-5">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.24em] text-accent">
              {t('app.name')}
            </p>
            <p className="mt-1 text-sm text-text-secondary">{t('app.tagline')}</p>
          </div>
          <LanguageSwitcher />
        </header>
        <main className="grid flex-1 gap-6 px-6 py-8 lg:grid-cols-[1.2fr_0.8fr] lg:px-10 lg:py-12">
          <section className="hidden rounded-[24px] bg-background-elevated p-8 lg:flex lg:flex-col lg:justify-between">
            <div className="space-y-5">
              <span className="inline-flex w-fit rounded-full bg-accent-muted px-3 py-1 text-xs font-semibold uppercase tracking-[0.22em] text-accent">
                {t('auth.layout.badge')}
              </span>
              <div className="space-y-4">
                <h1 className="max-w-xl text-5xl font-bold tracking-[-0.05em] text-text-primary">
                  {t('app.name')}
                </h1>
                <p className="max-w-2xl text-lg leading-8 text-text-secondary">
                  {t('app.tagline')}
                </p>
                <p className="max-w-2xl text-sm leading-7 text-text-secondary">
                  {t('auth.layout.description')}
                </p>
              </div>
            </div>
            <div className="grid gap-3 xl:grid-cols-3">
              <div className="grid gap-2 rounded-[24px] border border-border bg-surface p-5 shadow-[var(--shadow-card)]">
                <p className="text-sm font-semibold text-text-primary">
                  {t('auth.layout.highlights.schedule.title')}
                </p>
                <p className="text-sm leading-6 text-text-secondary">
                  {t('auth.layout.highlights.schedule.description')}
                </p>
              </div>
              <div className="grid gap-2 rounded-[24px] border border-border bg-surface p-5 shadow-[var(--shadow-card)]">
                <p className="text-sm font-semibold text-text-primary">
                  {t('auth.layout.highlights.assignments.title')}
                </p>
                <p className="text-sm leading-6 text-text-secondary">
                  {t('auth.layout.highlights.assignments.description')}
                </p>
              </div>
              <div className="grid gap-2 rounded-[24px] border border-border bg-surface p-5 shadow-[var(--shadow-card)]">
                <p className="text-sm font-semibold text-text-primary">
                  {t('auth.layout.highlights.analytics.title')}
                </p>
                <p className="text-sm leading-6 text-text-secondary">
                  {t('auth.layout.highlights.analytics.description')}
                </p>
              </div>
            </div>
          </section>
          <section className="flex items-center justify-center">{children}</section>
        </main>
      </div>
    </div>
  )
}
