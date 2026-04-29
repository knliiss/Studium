import type { ReactNode } from 'react'

import { Card } from '@/shared/ui/Card'

export function MetricCard({
  description,
  title,
  value,
}: {
  description?: string
  title: string
  value: ReactNode
}) {
  return (
    <Card className="gradient-card space-y-2">
      <p className="text-xs font-semibold uppercase tracking-[0.22em] text-text-muted">{title}</p>
      <p className="text-3xl font-bold tracking-[-0.04em] text-text-primary">{value}</p>
      {description ? <p className="text-sm leading-6 text-text-secondary">{description}</p> : null}
    </Card>
  )
}
