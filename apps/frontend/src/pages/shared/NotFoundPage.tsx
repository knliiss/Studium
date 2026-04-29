import { Compass, Home, Undo2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import { getDashboardPath } from '@/shared/lib/roles'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { ShellFrame } from '@/widgets/shell/ShellFrame'

export function NotFoundPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { isAuthenticated, roles } = useAuth()

  const content = (
    <div className="mx-auto flex min-h-[calc(100vh-7rem)] w-full max-w-5xl items-center justify-center">
      <Card className="gradient-card w-full rounded-[28px] p-8 lg:p-12">
        <div className="grid gap-8 lg:grid-cols-[1.1fr_0.9fr]">
          <div className="space-y-6">
            <div className="space-y-3">
              <div className="inline-flex items-center gap-2 rounded-full border border-border bg-surface px-3 py-1.5 text-xs font-semibold uppercase tracking-[0.2em] text-accent">
                <Compass className="h-3.5 w-3.5" />
                <span>{t('notFound.eyebrow')}</span>
              </div>
              <div className="space-y-2">
                <h1 className="text-3xl font-bold tracking-[-0.04em] text-text-primary lg:text-4xl">
                  {t('notFound.title')}
                </h1>
                <p className="max-w-2xl text-sm leading-7 text-text-secondary">
                  {t('notFound.description')}
                </p>
              </div>
            </div>

            <div className="flex flex-wrap gap-3">
              {isAuthenticated ? (
                <>
                  <Button variant="secondary" onClick={() => navigate(-1)}>
                    <Undo2 className="mr-2 h-4 w-4" />
                    {t('notFound.goBack')}
                  </Button>
                  <Button
                    onClick={() => {
                      navigate(getDashboardPath(roles))
                    }}
                  >
                    <Home className="mr-2 h-4 w-4" />
                    {t('notFound.goToDashboard')}
                  </Button>
                </>
              ) : (
                <>
                  <Button onClick={() => navigate(-1)}>
                    <Undo2 className="mr-2 h-4 w-4" />
                    {t('notFound.goBack')}
                  </Button>
                  <Button variant="secondary" onClick={() => navigate('/login')}>
                    {t('notFound.goToLogin')}
                  </Button>
                </>
              )}
            </div>
          </div>

          <div className="flex flex-col justify-center gap-5 rounded-[22px] border border-border bg-surface/70 p-6">
            <div className="inline-flex min-h-14 min-w-14 items-center justify-center rounded-[18px] bg-accent-muted text-accent">
              <Compass className="h-7 w-7" />
            </div>
            <div className="space-y-2">
              <p className="text-sm font-semibold text-text-primary">{t('notFound.suggestionsTitle')}</p>
              <p className="text-sm leading-6 text-text-secondary">
                {isAuthenticated ? t('notFound.authenticatedHint') : t('notFound.guestHint')}
              </p>
            </div>
            <ul className="space-y-2 text-sm text-text-secondary">
              <li>{t('notFound.suggestions.dashboard')}</li>
              <li>{t('notFound.suggestions.schedule')}</li>
              <li>{t('notFound.suggestions.subjects')}</li>
            </ul>
          </div>
        </div>
      </Card>
    </div>
  )

  if (isAuthenticated) {
    return <ShellFrame>{content}</ShellFrame>
  }

  return <div className="min-h-screen px-6 py-6 lg:px-8 lg:py-8">{content}</div>
}
