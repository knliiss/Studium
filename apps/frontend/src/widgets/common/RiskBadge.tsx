import { getRiskLabel } from '@/shared/lib/enum-labels'
import { cn } from '@/shared/lib/cn'

const riskTone: Record<string, string> = {
  HIGH: 'bg-danger/10 text-danger',
  LOW: 'bg-success/10 text-success',
  MEDIUM: 'bg-warning/10 text-warning',
}

export function RiskBadge({ value }: { value: string | null | undefined }) {
  return (
    <span
      className={cn(
        'inline-flex rounded-full px-2.5 py-1 text-xs font-semibold uppercase tracking-[0.16em]',
        value ? riskTone[value] ?? 'bg-surface-muted text-text-secondary' : 'bg-surface-muted text-text-secondary',
      )}
    >
      {getRiskLabel(value)}
    </span>
  )
}
