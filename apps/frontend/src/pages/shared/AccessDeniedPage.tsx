import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'

import { useAuth } from '@/features/auth/useAuth'
import { getDashboardPath } from '@/shared/lib/roles'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { ShellFrame } from '@/widgets/shell/ShellFrame'

export function AccessDeniedPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { isAuthenticated, roles } = useAuth()

  const content = (
    <div className="mx-auto flex min-h-[calc(100vh-7rem)] w-full max-w-4xl items-center justify-center">
      <Card className="w-full rounded-[28px] border-danger/15 p-8 lg:p-10">
        <div className="space-y-6">
          <div className="space-y-3">
            <p className="text-xs font-semibold uppercase tracking-[0.24em] text-danger">403</p>
            <div className="space-y-2">
              <h1 className="text-3xl font-bold tracking-[-0.04em] text-text-primary lg:text-4xl">
                {t('accessDenied.title')}
              </h1>
              <p className="max-w-2xl text-sm leading-7 text-text-secondary">
                {t('accessDenied.description')}
              </p>
            </div>
          </div>

          <div className="flex flex-wrap gap-3">
            <Button
              onClick={() => {
                navigate(isAuthenticated ? getDashboardPath(roles) : '/login')
              }}
            >
              {isAuthenticated ? t('accessDenied.goToDashboard') : t('common.actions.login')}
            </Button>
            <Button variant="secondary" onClick={() => navigate(-1)}>
              {t('accessDenied.goBack')}
            </Button>
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
