import { getStatusLabel } from '@/shared/lib/enum-labels'
import { cn } from '@/shared/lib/cn'

const statusTone: Record<string, string> = {
  ACTIVE: 'bg-info/10 text-info',
  ARCHIVED: 'bg-text-muted/10 text-text-secondary',
  BANNED: 'bg-danger/10 text-danger',
  CLOSED: 'bg-danger/10 text-danger',
  DISABLED: 'bg-warning/10 text-warning',
  DRAFT: 'bg-warning/10 text-warning',
  PUBLISHED: 'bg-success/10 text-success',
  READY: 'bg-success/10 text-success',
  UNREAD: 'bg-accent/10 text-accent',
}

export function StatusBadge({ value }: { value: string | null | undefined }) {
  const tone = value ? statusTone[value] : 'bg-surface-muted text-text-secondary'
  return (
    <span className={cn('inline-flex rounded-full px-2.5 py-1 text-xs font-semibold uppercase tracking-[0.16em]', tone)}>
      {getStatusLabel(value)}
    </span>
  )
}
