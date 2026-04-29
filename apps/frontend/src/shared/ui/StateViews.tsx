import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'

export function LoadingState({ label }: { label?: string }) {
  const { t } = useTranslation()

  return (
    <Card className="flex min-h-56 items-center justify-center text-sm text-text-secondary">
      {label ?? t('common.states.loading')}
    </Card>
  )
}

export function EmptyState({
  action,
  description,
  title,
}: {
  action?: ReactNode
  description?: string
  title: string
}) {
  return (
    <Card className="gradient-card flex min-h-56 flex-col items-start justify-center gap-4">
      <div className="space-y-2">
        <h2 className="text-xl font-semibold text-text-primary">{title}</h2>
        {description ? (
          <p className="max-w-2xl text-sm leading-6 text-text-secondary">{description}</p>
        ) : null}
      </div>
      {action}
    </Card>
  )
}

export function ErrorState({
  actionLabel,
  description,
  onRetry,
  title,
}: {
  actionLabel?: string
  description?: string
  onRetry?: () => void
  title: string
}) {
  const { t } = useTranslation()

  return (
    <Card className="flex min-h-56 flex-col items-start justify-center gap-4 border-danger/20">
      <div className="space-y-2">
        <h2 className="text-xl font-semibold text-text-primary">{title}</h2>
        {description ? (
          <p className="max-w-2xl text-sm leading-6 text-text-secondary">{description}</p>
        ) : null}
      </div>
      {onRetry ? (
        <Button variant="secondary" onClick={onRetry}>
          {actionLabel ?? t('common.actions.retry')}
        </Button>
      ) : null}
    </Card>
  )
}
